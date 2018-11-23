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

    private val clientOnlyClasses = ArrayList<PsiClass>()
    private val clientOnlyMethods = ArrayList<PsiMethod>()
    private val clientOnlyFields = ArrayList<PsiField>()

    private val ignoreSideOnlyMethods = ArrayList<PsiMethod>()

    init {
        files.forEach { it.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitClass(aClass: PsiClass) {
                super.visitClass(aClass)
                if (isClientOnlyClass(aClass))
                    clientOnlyClasses.add(aClass)
            }

            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                if (method.hasAnnotation(SideOnlyAnnoName))
                    clientOnlyMethods.add(method)
                if (method.doesIgnoreSideOnly())
                    ignoreSideOnlyMethods.add(method)
            }

            override fun visitField(field: PsiField) {
                super.visitField(field)
                if (field.hasAnnotation(SideOnlyAnnoName))
                    clientOnlyFields.add(field)
            }

        })}
    }

    val errorClassReference = clientOnlyClasses
        .flatMap { klass -> ReferencesSearch.search(klass).map { ClassErrorItem(it, klass) } }
        .filter { !isInClientOnlyContext(it.ref.element) }

    val errorMethodReferences = clientOnlyMethods
        .flatMap { method -> MethodReferencesSearch.search(method).map { MethodErrorItem(it, method) } }
        .filter { !isInClientOnlyContext(it.ref.element) }
        .filter {
            val parentMethod = PsiTreeUtil.getParentOfType(it.ref.element, PsiMethod::class.java)
            parentMethod == null || !ignoreSideOnlyMethods.contains(parentMethod)
        }

    val errorFieldReferences = clientOnlyFields
        .flatMap { field -> ReferencesSearch.search(field).map { FieldErrorItem(it, field) } }
        .filter { !isInClientOnlyContext(it.ref.element) }

    val errorFieldInitializers = clientOnlyFields
        .filter { it.hasInitializer() }

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

    private fun PsiMethod.doesIgnoreSideOnly(): Boolean {
        val anno = getAnnotation("java.lang.SuppressWarnings")
        return if (anno != null) {
            val parameterList = PsiTreeUtil.findChildOfType(anno, PsiAnnotationParameterList::class.java)!!
            parameterList.attributes.any { it.literalValue.equals("SIDEONLY", ignoreCase = true) }
        } else {
            false
        }
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