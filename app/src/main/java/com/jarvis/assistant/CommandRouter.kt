package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

object CommandRouter {

    data class Result(val spokenReply: String, val handled: Boolean)

    private val APPS = mapOf(
        "whatsapp" to "com.whatsapp",
        "chrome" to "com.android.chrome",
        "youtube" to "com.google.android.youtube",
        "maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "spotify" to "com.spotify.music",
        "camera" to "com.android.camera",
        "settings" to "com.android.settings"
    )

    fun handle(context: Context, text: String): Result {
        val t = text.lowercase().trim()

        Regex("open (my )?([a-z]+)").find(t)?.let { m ->
            val name = m.groupValues[2]
            APPS[name]?.let { pkg ->
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                return if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    Result("Opening $name.", true)
                } else {
                    Result("$name doesn't seem to be installed.", true)
                }
            }
        }

        Regex("call ([a-z ]+)").find(t)?.let { m ->
            val name = m.groupValues[1].trim()
            val number = lookupContactNumber(context, name)
            return if (number != null) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Result("Calling $name.", true)
            } else {
                Result("I couldn't find $name in your contacts.", true)
            }
        }

        Regex("(?:send (.+) to ([a-z ]+) on whatsapp)|(?:whatsapp ([a-z ]+) (.+))").find(t)?.let { m ->
            val message = m.groupValues[1].ifBlank { m.groupValues[4] }
            val name = m.groupValues[2].ifBlank { m.groupValues[3] }
            val number = lookupContactNumber(context, name.trim())
            return if (number != null) {
                PendingAction.autoSendWhatsApp = true
                PendingAction.targetContact = name
                val uri = Uri.parse(
                    "https://wa.me/$number?text=" + Uri.encode(message.trim())
                )
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Result(
                    "Sending \"$message\" to $name on WhatsApp. " +
                    "Make sure WhatsApp Control is turned on in accessibility settings, or you'll need to tap send yourself.",
                    true
                )
            } else {
                Result("I couldn't find $name in your contacts.", true)
            }
        }

        return Result("", false)
    }

    private fun lookupContactNumber(context: Context, name: String): String? {
        val resolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$name%")
        resolver.query(uri, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                return cursor.getString(numIdx).replace(Regex("[^0-9+]"), "")
            }
        }
        return null
    }
}
