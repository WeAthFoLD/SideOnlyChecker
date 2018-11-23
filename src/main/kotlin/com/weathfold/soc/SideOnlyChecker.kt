package com.weathfold.soc

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.compiler.*
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import java.io.DataInput


class SOCRegistration(val p: Project) : ProjectComponent {
    override fun getComponentName(): String = "SideOnlyChecker"

    override fun initComponent() {
    }

    override fun disposeComponent() {
    }

    override fun projectOpened() {
        CompilerManager.getInstance(p).addCompiler(object : Validator {
            override fun createValidityState(`in`: DataInput): ValidityState = TimestampValidityState.load(`in`)

            override fun getProcessingItems(context: CompileContext): Array<FileProcessingCompiler.ProcessingItem> {
                val items = context.compileScope.getFiles(null, true).map { object : FileProcessingCompiler.ProcessingItem {
                    override fun getValidityState(): ValidityState? {
                        return TimestampValidityState(System.currentTimeMillis())
                    }

                    override fun getFile(): VirtualFile {
                        return it.canonicalFile!!
                    }

                } }.toTypedArray<FileProcessingCompiler.ProcessingItem>()

                return items
            }

            override fun validateConfiguration(scope: CompileScope): Boolean {
                return true
            }

            override fun getDescription(): String {
                return "SideOnly checker"
            }

            override fun process(context: CompileContext, items: Array<FileProcessingCompiler.ProcessingItem>): Array<FileProcessingCompiler.ProcessingItem> {
                val psiManager = PsiManager.getInstance(p)
                val documentManager = PsiDocumentManager.getInstance(p)
                // http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html
                ReadAction.run<Nothing> {
                    val ctx = SideOnlyContext(items.mapNotNull { psiManager.findFile(it.file) }.toList())
                    ctx.errorMethodReferences.forEach {
                        val doc = documentManager.getDocument(it.ref.element.containingFile)!!
                        context.addMessage(
                            CompilerMessageCategory.ERROR,
                            "SideOnly: Method ref ${it.method.containingClass!!.name}#${it.method.name} from invalid context",
                            it.ref.element.containingFile.virtualFile.url,
                            doc.getLineNumber(it.ref.element.textOffset) + 1, 0
                        )
                    }
                    ctx.errorFieldReferences.forEach {
                        val doc = documentManager.getDocument(it.ref.element.containingFile)!!
                        context.addMessage(
                            CompilerMessageCategory.ERROR,
                            "SideOnly: Field ref ${it.field.containingClass!!.name}#${it.field.name} from invalid context",
                            it.ref.element.containingFile.virtualFile.url,
                            doc.getLineNumber(it.ref.element.textOffset) + 1, 0
                        )
                    }
                }
                return items
            }

        })
    }

    override fun projectClosed() {
    }


}