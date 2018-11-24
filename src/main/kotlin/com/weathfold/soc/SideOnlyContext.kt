package com.weathfold.soc

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.search.JavaFilesSearchScope
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentsOfType

class ClassErrorItem(val ref: PsiReference, val klass: PsiClass)
class MethodErrorItem(val ref: PsiReference, val method: PsiMethod)
class FieldErrorItem(val ref: PsiReference, val field: PsiField)

class SideOnlyContext(val project: Project, val files: List<PsiFile>) {

    companion object {
        val SideOnlyAnnoName = "net.minecraftforge.fml.relauncher.SideOnly"
    }

    private val clientOnlyClasses = ArrayList<PsiClass>()
    private val clientOnlyMethods = ArrayList<PsiMethod>()
    private val clientOnlyFields = ArrayList<PsiField>()

    private val ignoreSideOnlyMethods = ArrayList<PsiMethod>()

    private val searchScope = JavaFilesSearchScope(project)

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

            override fun visitAnonymousClass(aClass: PsiAnonymousClass) {
                super.visitAnonymousClass(aClass)
                val containingClass = PsiTreeUtil.getParentOfType(aClass, PsiClass::class.java)!!
                if (isClientOnlyClass(containingClass)) {
                    clientOnlyClasses.add(aClass)
                    return
                }

                val containingMethod = PsiTreeUtil.getParentOfType(aClass, PsiMethod::class.java)
                if (containingMethod != null && containingMethod.hasAnnotation(SideOnlyAnnoName)) {
                    clientOnlyClasses.add(aClass)
                    return
                }

            }

            override fun visitField(field: PsiField) {
                super.visitField(field)
                if (field.hasAnnotation(SideOnlyAnnoName))
                    clientOnlyFields.add(field)
            }

        })}
    }

    val errorClassReference = clientOnlyClasses
        .flatMap { klass -> ReferencesSearch.search(klass, searchScope).map { ClassErrorItem(it, klass) } }
        .filter { !it.ref.element.isInComment() }
        .filter { !isInClientOnlyContext(it.ref.element) }
        .filter {
            val parentMethod = PsiTreeUtil.getParentOfType(it.ref.element, PsiMethod::class.java)
            parentMethod == null || !ignoreSideOnlyMethods.contains(parentMethod)
        }

    val errorMethodReferences = clientOnlyMethods
        .flatMap { method -> MethodReferencesSearch.search(method, searchScope, true).map { MethodErrorItem(it, method) } }
        .filter { !it.ref.element.isInComment() }
        .filter { !isInClientOnlyContext(it.ref.element) }
        .filter {
            val parentMethod = PsiTreeUtil.getParentOfType(it.ref.element, PsiMethod::class.java)
            parentMethod == null || !ignoreSideOnlyMethods.contains(parentMethod)
        }

    val errorFieldReferences = clientOnlyFields
        .flatMap { field -> ReferencesSearch.search(field, searchScope).map { FieldErrorItem(it, field) } }
        .filter { !it.ref.element.isInComment() }
        .filter { !isInClientOnlyContext(it.ref.element) }
        .filter {
            val parentMethod = PsiTreeUtil.getParentOfType(it.ref.element, PsiMethod::class.java)
            parentMethod == null || !ignoreSideOnlyMethods.contains(parentMethod)
        }

    val errorFieldInitializers = clientOnlyFields
        .filter { it.hasInitializer() }

    private fun isClientOnlyClass(c: PsiClass): Boolean {
        val innerAndParentRemoved = c.containingClass?.let { isClientOnlyClass(it) } ?: false
        if (innerAndParentRemoved)
            return true

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

    private fun PsiElement.isInComment(): Boolean {
        return PsiTreeUtil.getParentOfType(this, PsiComment::class.java) != null
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
                return clientOnlyMethods.contains(elem) ||
                    clientOnlyFields.contains(elem) ||
                    with(elem.containingClass) { if (this == null) true else isInClientOnlyContext(this) }
            }
//            is PsiImportStatementBase -> {
//                return true
//            }
            else -> {
                val parent = PsiTreeUtil.getParentOfType(elem, PsiMember::class.java)
//                SDebug.notifyInfo(elem.containingFile!!.name + "/" + elem.toString(), "parent=$parent")
                return if (parent == null) true else isInClientOnlyContext(parent)
            }
//            else -> error("Unsupport type: $elem")
        }
    }

}