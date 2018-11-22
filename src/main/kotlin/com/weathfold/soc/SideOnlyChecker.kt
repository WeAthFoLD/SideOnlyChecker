package com.weathfold.soc

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.compiler.*
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.io.DataInput


class SOCRegistration(val p: Project) : ProjectComponent {
    override fun getComponentName(): String = "SideOnlyChecker"

    override fun initComponent() {
    }

    override fun disposeComponent() {
    }

    override fun projectOpened() {
        Messages.showMessageDialog("SideOnlyChecker init", "Hello", null)
        CompilerManager.getInstance(p).addCompiler(object : Validator {
            override fun createValidityState(`in`: DataInput): ValidityState = TimestampValidityState.load(`in`)

            override fun getProcessingItems(context: CompileContext): Array<FileProcessingCompiler.ProcessingItem> {
                notifyInfo("Compile", "getProcessingItems")
                notifyInfo("Files", context.compileScope.getFiles(null, true).map { it.name }.joinToString())
                return emptyArray()
            }

            override fun validateConfiguration(scope: CompileScope): Boolean {
                return true
            }

            override fun getDescription(): String {
                return "SideOnly checker"
            }

            override fun process(context: CompileContext, items: Array<FileProcessingCompiler.ProcessingItem>): Array<FileProcessingCompiler.ProcessingItem> {
                notifyInfo("Compile", "process")
                return items
            }

        })
    }

    override fun projectClosed() {
    }

    private fun notifyInfo(title: String, content: String) {
        notify(title, content, NotificationType.INFORMATION)
    }

    private fun notify(title: String, content: String, notificationType: NotificationType) {
        Notifications.Bus.notify(Notification(
            "SideOnly Checker",
            title,
            content,
            notificationType
        ))
    }

}