package com.jarvis.assistant

object PendingAction {
    @Volatile var autoSendWhatsApp: Boolean = false
    @Volatile var targetContact: String? = null
}
