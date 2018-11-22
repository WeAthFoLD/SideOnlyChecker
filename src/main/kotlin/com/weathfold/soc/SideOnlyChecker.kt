package com.weathfold.soc

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.compiler.*
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.scopes.ModulesScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.AllClassesSearch
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
                val items = context.compileScope.getFiles(null, true).map { object : FileProcessingCompiler.ProcessingItem {
                    override fun getValidityState(): ValidityState? {
                        return EmptyValidityState()
                    }

                    override fun getFile(): VirtualFile {
                        return it.canonicalFile!!
                    }

                } }.toTypedArray<FileProcessingCompiler.ProcessingItem>()

                val psiManager = PsiManager.getInstance(p)
                items
                    .mapNotNull {
                        psiManager.findFile(it.file)
                    }
                    .filter { it.fileType is LanguageFileType }
                    .forEach {
                        when (it) {
                            is PsiJavaFile -> {
                                val javaFile = it
                                javaFile.classes.forEach {
                                    val annotations = it.annotations.filter { it.qualifiedName!! == "net.minecraftforge.fml.relauncher.SideOnly" }
                                    val document = PsiDocumentManager.getInstance(p).getDocument(javaFile)!!
                                    annotations.forEach {
                                        context.addMessage(CompilerMessageCategory.ERROR, "Invalid SideOnly usage", javaFile.virtualFile.url,
                                            document.getLineNumber(it.textOffset) + 1, 0)
                                    }
                                }
                            }
                            else -> {}
                        }
                    }

                return items
            }

            override fun validateConfiguration(scope: CompileScope): Boolean {
                return true
            }

            override fun getDescription(): String {
                return "SideOnly checker"
            }

            override fun process(context: CompileContext, items: Array<FileProcessingCompiler.ProcessingItem>): Array<FileProcessingCompiler.ProcessingItem> {
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