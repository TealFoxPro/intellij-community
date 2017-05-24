/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.analysis.analyzeInContext
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptor
import org.jetbrains.kotlin.idea.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.codeInliner.CodeToInlineBuilder
import org.jetbrains.kotlin.idea.codeInliner.PropertyUsageReplacementStrategy
import org.jetbrains.kotlin.idea.core.copied
import org.jetbrains.kotlin.idea.project.builtIns
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.ReferenceAccess
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getAssignmentByLHS
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.TypeUtils

class KotlinInlineValHandler : InlineActionHandler() {

    override fun isEnabledForLanguage(l: Language) = l == KotlinLanguage.INSTANCE

    override fun canInlineElement(element: PsiElement): Boolean {
        return element is KtProperty && element.name != null
    }

    override fun inlineElement(project: Project, editor: Editor?, element: PsiElement) {
        val declaration = element as KtProperty
        val name = declaration.name!!

        val file = declaration.containingKtFile
        if (file.isCompiled) {
            return showErrorHint(project, editor, "Cannot inline '$name' from a decompiled file")
        }

        val getter = declaration.getter?.takeIf { it.hasBody() }
        val setter = declaration.setter?.takeIf { it.hasBody() }

        if ((getter != null || setter != null) && declaration.initializer != null) {
            return showErrorHint(project, editor, "Cannot inline property with accessor(s) and backing field")
        }

        val (referenceExpressions, conflicts) = findUsages(declaration)

        if (referenceExpressions.isEmpty()) {
            val kind = if (declaration.isLocal) "Variable" else "Property"
            return showErrorHint(project, editor, "$kind '$name' is never used") //TODO: foreign usages!
        }

        val referencesInOriginalFile = referenceExpressions.filter { it.containingFile == file }
        val hasHighlightings = referencesInOriginalFile.isNotEmpty()
        highlightElements(project, editor, referencesInOriginalFile)

        val readReplacement: CodeToInline?
        val writeReplacement: CodeToInline?
        val assignmentToDelete: KtBinaryExpression?
        if (getter == null && setter == null) {
            val initialization = extractInitialization(declaration, referenceExpressions, project, editor) ?: return
            readReplacement = buildCodeToInline(declaration, initialization.value)
            writeReplacement = null
            assignmentToDelete = initialization.assignment
        }
        else {
            val descriptor = declaration.resolveToDescriptor() as PropertyDescriptor
            readReplacement = getter?.let { buildCodeToInline(getter, descriptor.type, editor) ?: return }
            writeReplacement = setter?.let { buildCodeToInline(setter, setter.builtIns.unitType, editor) ?: return }
            assignmentToDelete = null
        }

        if (!conflicts.isEmpty) {
            val conflictsCopy = conflicts.copy()
            conflictsCopy.putValue(null, "The following usages are not supported by the Inline refactoring. They won't be processed.")

            project.checkConflictsInteractively(conflictsCopy) {
                performRefactoring(declaration, readReplacement, writeReplacement, assignmentToDelete, editor, hasHighlightings)
            }
        }
        else {
            performRefactoring(declaration, readReplacement, writeReplacement, assignmentToDelete, editor, hasHighlightings)
        }
    }

    private data class Usages(val referenceExpressions: Collection<KtExpression>, val conflicts: MultiMap<PsiElement, String>)

    private fun findUsages(declaration: KtProperty): Usages {
        val references = ReferencesSearch.search(declaration)
        val referenceExpressions = mutableListOf<KtExpression>()
        val conflictUsages = MultiMap.create<PsiElement, String>()
        for (ref in references) {
            val refElement = ref.element ?: continue
            if (refElement !is KtElement) {
                conflictUsages.putValue(refElement, "Non-Kotlin usage: ${refElement.text}")
                continue
            }

            val expression = (refElement as? KtExpression)?.getQualifiedExpressionForSelectorOrThis()
            //TODO: what if null?
            if (expression != null) {
                if (expression.readWriteAccess(useResolveForReadWrite = true) == ReferenceAccess.READ_WRITE) {
                    conflictUsages.putValue(expression, "Unsupported usage: ${expression.parent.text}")
                }
                referenceExpressions.add(expression)
            }
        }
        return Usages(referenceExpressions, conflictUsages)
    }

    private data class Initialization(val value: KtExpression, val assignment: KtBinaryExpression?)

    private fun extractInitialization(
            declaration: KtProperty,
            referenceExpressions: Collection<KtExpression>,
            project: Project,
            editor: Editor?
    ): Initialization? {
        val writeUsages = referenceExpressions.filter { it.readWriteAccess(useResolveForReadWrite = true) != ReferenceAccess.READ }

        val initializerInDeclaration = declaration.initializer
        if (initializerInDeclaration != null) {
            if (!writeUsages.isEmpty()) {
                reportAmbiguousAssignment(project, editor, declaration.name!!, writeUsages)
                return null
            }
            return Initialization(initializerInDeclaration, assignment = null)
        }
        else {
            val assignment = writeUsages.singleOrNull()
                    ?.getAssignmentByLHS()
                    ?.takeIf { it.operationToken == KtTokens.EQ }
            val initializer = assignment?.right
            if (initializer == null) {
                reportAmbiguousAssignment(project, editor, declaration.name!!, writeUsages)
                return null
            }
            return Initialization(initializer, assignment)
        }
    }

    private fun buildCodeToInline(declaration: KtProperty, initializer: KtExpression): CodeToInline {
        val descriptor = declaration.resolveToDescriptor() as VariableDescriptor
        val expectedType = if (declaration.typeReference != null)
            descriptor.returnType ?: TypeUtils.NO_EXPECTED_TYPE
        else
            TypeUtils.NO_EXPECTED_TYPE

        val initializerCopy = initializer.copied()
        fun analyzeInitializerCopy(): BindingContext {
            return initializerCopy.analyzeInContext(initializer.getResolutionScope(),
                                                    contextExpression = initializer,
                                                    expectedType = expectedType)
        }

        val codeToInlineBuilder = CodeToInlineBuilder(descriptor, declaration.getResolutionFacade())
        return codeToInlineBuilder.prepareCodeToInline(initializerCopy, emptyList(), ::analyzeInitializerCopy)
    }

    private fun performRefactoring(
            declaration: KtProperty,
            readReplacement: CodeToInline?,
            writeReplacement: CodeToInline?,
            assignmentToDelete: KtBinaryExpression?,
            editor: Editor?,
            hasHighlightings: Boolean
    ) {
        val replacementStrategy = PropertyUsageReplacementStrategy(readReplacement, writeReplacement)

        val reference = editor?.let { TargetElementUtil.findReference(it, it.caretModel.offset) } as? KtSimpleNameReference

        val dialog = KotlinInlineValDialog(declaration, reference, replacementStrategy, assignmentToDelete)

        if (!ApplicationManager.getApplication().isUnitTestMode) {
            dialog.show()
            if (!dialog.isOK && hasHighlightings) {
                val statusBar = WindowManager.getInstance().getStatusBar(declaration.project)
                statusBar?.info = RefactoringBundle.message("press.escape.to.remove.the.highlighting")
            }
        }
        else {
            dialog.doAction()
        }
    }

    private fun reportAmbiguousAssignment(project: Project, editor: Editor?, name: String, assignments: Collection<PsiElement>) {
        val key = if (assignments.isEmpty()) "variable.has.no.initializer" else "variable.has.no.dominating.definition"
        val message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message(key, name))
        showErrorHint(project, editor, message)
    }

    private fun showErrorHint(project: Project, editor: Editor?, message: String) {
        CommonRefactoringUtil.showErrorHint(project, editor, message, RefactoringBundle.message("inline.variable.title"), HelpID.INLINE_VARIABLE)
    }

}
