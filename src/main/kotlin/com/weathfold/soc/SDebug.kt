package com.weathfold.soc

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications

internal object SDebug {

    fun notifyInfo(title: String, content: String) {
        notify(title, content, NotificationType.INFORMATION)
    }

    fun notify(title: String, content: String, notificationType: NotificationType) {
        Notifications.Bus.notify(Notification(
            "SideOnly Checker",
            title,
            content,
            notificationType
        ))
    }
}