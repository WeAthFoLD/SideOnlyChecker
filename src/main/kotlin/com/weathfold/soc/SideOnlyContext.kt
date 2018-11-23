package com.weathfold.soc

import com.intellij.psi.*
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentsOfType

class MethodErrorItem(val ref: PsiReference, val method: PsiMethod)
class FieldErrorItem(val ref: PsiReference, val field: PsiField)

class SideOnlyContext(val files: List<PsiFile>) {

    companion object {
        val SideOnlyAnnoName = "net.minecraftforge.fml.relauncher.SideOnly"
    }

    val javaClasses: List<PsiClass> = files.filter { it is PsiJavaFile }
        .map { it as PsiJavaFile }
        .flatMap { it.classes.toList() }
        .flatMap {
            val list = ArrayList<PsiClass>()
            collectAllClasses(list, it)
            list
        }

    init {
        SDebug.notifyInfo("All classes", javaClasses.joinToString())
    }

    val clientOnlyClasses: List<PsiClass> = javaClasses
        .filter { it.hasAnnotation(SideOnlyAnnoName) }

    val clientOnlyMethods: List<PsiMethod> = run {
        val directMarkedMethods = javaClasses
            .flatMap { it.methods.toList() }
            .filter { it.hasAnnotation(SideOnlyAnnoName) }
        val indirectMethods = clientOnlyClasses
            .flatMap { it.methods.toList() }
        (directMarkedMethods + indirectMethods).distinct()
    }

    val clientOnlyFields: List<PsiField> = javaClasses
        .flatMap { it.fields.toList() }
        .filter { it.hasAnnotation(SideOnlyAnnoName) }

    val errorMethodReferences = clientOnlyMethods
        .flatMap { method -> MethodReferencesSearch.search(method).map { MethodErrorItem(it, method) } }
        .filter { !isInClientOnlyContext(it.ref.element) }

    val errorFieldReferences = clientOnlyFields
        .flatMap { field -> ReferencesSearch.search(field).map { FieldErrorItem(it, field) } }
        .filter { !isInClientOnlyContext(it.ref.element) }

    private fun collectAllClasses(list: ArrayList<PsiClass>, cur: PsiClass) {
        list += cur
        cur.innerClasses.forEach { collectAllClasses(list, it) }
    }

    private fun isInClientOnlyContext(elem: PsiElement): Boolean {
        when(elem) {
            is PsiClass -> {
                var cur: PsiClass = elem
                while (true) {
                    if (clientOnlyClasses.contains(cur))
                        return true
                    val next = cur.superClass
                    if (next == null)
                        break
                    else
                        cur = next
                }
                return false
            }
            is PsiMember -> {
                return clientOnlyMethods.contains(elem) || isInClientOnlyContext(elem.containingClass!!)
            }
            else -> {
                val parent = PsiTreeUtil.getParentOfType(elem, PsiMember::class.java)
                SDebug.notifyInfo(elem.toString(), "Parent is: " + parent.toString())
                return if (parent == null) false else isInClientOnlyContext(parent)
            }
//            else -> error("Unsupport type: $elem")
        }
    }

}