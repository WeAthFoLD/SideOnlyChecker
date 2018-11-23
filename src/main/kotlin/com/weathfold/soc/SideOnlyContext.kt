package com.weathfold.soc

import com.intellij.psi.*
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentsOfType

class ClassErrorItem(val ref: PsiReference, val klass: PsiClass)
class MethodErrorItem(val ref: PsiReference, val method: PsiMethod)
class FieldErrorItem(val ref: PsiReference, val field: PsiField)

class SideOnlyContext(val files: List<PsiFile>) {

    companion object {
        val SideOnlyAnnoName = "net.minecraftforge.fml.relauncher.SideOnly"
    }

    private val javaClasses: List<PsiClass> = run {
        val res = ArrayList<PsiClass>()
        files.forEach { it.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitClass(aClass: PsiClass) {
                super.visitClass(aClass)
                res += aClass
            }
        } ) }
        res
    }

    private val clientOnlyClasses: List<PsiClass> = javaClasses
        .filter { isClientOnlyClass(it) }

    private val clientOnlyMethods: List<PsiMethod> = run {
        val directMarkedMethods = javaClasses
            .flatMap { it.methods.toList() }
            .filter { it.hasAnnotation(SideOnlyAnnoName) }
//        val indirectMethods = clientOnlyClasses
//            .flatMap { it.methods.toList() }
        directMarkedMethods
    }

    private val clientOnlyFields: List<PsiField> = javaClasses
        .flatMap { it.fields.toList() }
        .filter { it.hasAnnotation(SideOnlyAnnoName) }

    val errorClassReference = clientOnlyClasses
        .flatMap { klass -> ReferencesSearch.search(klass).map { ClassErrorItem(it, klass) } }
        .filter { !isInClientOnlyContext(it.ref.element) }

    val errorMethodReferences = clientOnlyMethods
        .flatMap { method -> MethodReferencesSearch.search(method).map { MethodErrorItem(it, method) } }
        .filter { !isInClientOnlyContext(it.ref.element) }

    val errorFieldReferences = clientOnlyFields
        .flatMap { field -> ReferencesSearch.search(field).map { FieldErrorItem(it, field) } }
        .filter { !isInClientOnlyContext(it.ref.element) }

    private fun isClientOnlyClass(c: PsiClass): Boolean {
        var cur = c
        while (true) {
            if (cur.hasAnnotation(SideOnlyAnnoName))
                return true
            else {
                if (cur.superClass != null) {
                    cur = cur.superClass!!
                } else
                    return false
            }
        }
    }

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
//            is PsiImportStatementBase -> {
//                return true
//            }
            else -> {
                val parent = PsiTreeUtil.getParentOfType(elem, PsiMember::class.java)
                return if (parent == null) true else isInClientOnlyContext(parent)
            }
//            else -> error("Unsupport type: $elem")
        }
    }

}