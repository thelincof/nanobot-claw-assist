package com.nanobot.client.platform

import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Toolkit

actual fun notifyTaskCompleted(title: String, message: String) {
    try {
        if (SystemTray.isSupported()) {
            val tray = SystemTray.getSystemTray()
            val image = Toolkit.getDefaultToolkit().createImage("")
            val trayIcon = TrayIcon(image, "Nanobot")
            trayIcon.isImageAutoSize = true
            tray.add(trayIcon)
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO)
            tray.remove(trayIcon)
            return
        }
    } catch (_: Exception) {}

    // Fallback on Linux via notify-send
    try {
        ProcessBuilder("notify-send", title, message).start()
    } catch (_: Exception) {}
}
