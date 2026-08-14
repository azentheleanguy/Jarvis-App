package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!PendingAction.autoSendWhatsApp) return
        if (event?.packageName != "com.whatsapp") return

        val root = rootInActiveWindow ?: return
        val sendButton = findSendButton(root)
        if (sendButton != null) {
            sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            PendingAction.autoSendWhatsApp = false
            PendingAction.targetContact = null
        }
    }

    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byDesc = node.findAccessibilityNodeInfosByText("Send")
        for (n in byDesc) {
            if (n.isClickable) return n
        }
        val byId = node.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        if (byId.isNotEmpty()) return byId[0]
        return null
    }

    override fun onInterrupt() {}
}
