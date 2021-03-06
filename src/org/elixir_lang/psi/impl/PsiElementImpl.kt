package org.elixir_lang.psi.impl

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.elixir_lang.psi.*
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.name.Function.ALIAS
import org.elixir_lang.psi.call.name.Function.CREATE
import org.elixir_lang.psi.call.name.Module.KERNEL
import org.elixir_lang.psi.operation.Match
import org.elixir_lang.psi.operation.Pipe
import org.jetbrains.annotations.Contract
import java.util.*

fun PsiElement.ancestorSequence() = generateSequence(this) { it.parent }
fun PsiElement.document(): Document? = containingFile.viewProvider.document

tailrec fun PsiElement.selfOrEnclosingMacroCall(): Call? =
        when (this) {
            is ElixirDoBlock ->
                parent.let { it as? Call }
            is ElixirAnonymousFunction -> {
                parent.let { it as? ElixirAccessExpression }?.
                        parent.let { it as? Arguments }?.
                        parent.let { it as? ElixirMatchedParenthesesArguments }?.
                        parent.let { it as?  Call }?.
                        let { call ->
                            if (call.isCalling("Enum", "map") ||
                                        call.isCalling("Enum", "each")) {
                                    call
                                } else {
                                    null
                                }
                        }?.
                        parent?.
                        selfOrEnclosingMacroCall()
            }
            is Arguments,
            is AtUnqualifiedNoParenthesesCall<*>,
            is ElixirAccessExpression,
            is ElixirBlockItem,
            is ElixirBlockList,
            is ElixirList,
            is ElixirMatchedParenthesesArguments,
            is ElixirMatchedWhenOperation,
            is ElixirNoParenthesesManyStrictNoParenthesesExpression,
            is ElixirParentheticalStab,
            is ElixirStab,
            is ElixirStabBody,
            is ElixirStabOperation,
            is ElixirTuple,
            is Match,
            is Pipe,
            is QualifiedAlias,
            is QualifiedMultipleAliases ->
                parent.selfOrEnclosingMacroCall()
            is Call ->
                when {
                    isCalling(KERNEL, ALIAS) -> this
                    isCalling(org.elixir_lang.psi.call.name.Module.MODULE, CREATE, 3) -> this
                    else -> null
                }
            is QuotableKeywordPair ->
                if (this.hasKeywordKey("do")) {
                    parent.let { it as? QuotableKeywordList }?.
                            parent.let { it as? ElixirNoParenthesesOneArgument }?.
                            parent.let { it as? Call }
                } else {
                    null
                }
            else -> null
        }

/**
 *
 * @param call
 * @return `null` if call is at top-level
 */
@Contract(pure = true)
fun PsiElement.enclosingMacroCall(): Call? = parent.selfOrEnclosingMacroCall()

fun PsiElement.getModuleName(): String? {
    val isModuleName = { c: PsiElement -> c is MaybeModuleName && c.isModuleName }

    return PsiTreeUtil.findFirstParent(this) { e ->
        e.children.any(isModuleName)
    }?.let { moduleDefinition ->
        moduleDefinition.children.firstOrNull(isModuleName)?.let { moduleName ->
            moduleDefinition.parent.getModuleName()?.let { parentModuleName ->
                "$parentModuleName.${moduleName.text}"
            } ?: moduleName.text
        }
    }
}

fun PsiElement.macroChildCallList(): MutableList<Call> {
    val callList: MutableList<Call>

    if (this is ElixirAccessExpression) {
        callList = this@macroChildCallList.getFirstChild().macroChildCallList()
    } else if (this is ElixirList || this is ElixirStabBody) {
        callList = ArrayList()

        var child: PsiElement? = firstChild
        while (child != null) {
            if (child is Call) {
                callList.add(child)
            } else if (child is ElixirAccessExpression) {
                callList.addAll(child.macroChildCallList())
            }
            child = child.nextSibling
        }
    } else {
        callList = mutableListOf()
    }

    return callList
}

/**
 * @return [Call] for the `defmodule`, `defimpl`, or `defprotocol` that defines
 * `maybeAlias` after it is resolved through any `alias`es.
 */
@Contract(pure = true)
fun PsiElement.maybeModularNameToModular(maxScope: PsiElement): Call? {
    val strippedMaybeModuleName = stripAccessExpression()

    return when (strippedMaybeModuleName) {
        is ElixirAtom -> strippedMaybeModuleName.maybeModularNameToModular()
        is QualifiableAlias -> strippedMaybeModuleName.maybeModularNameToModular(maxScope)
        else -> null
    }
}

fun PsiElement.moduleWithDependentsScope(): GlobalSearchScope {
    val virtualFile = containingFile.virtualFile
    val project = project
    val module = ModuleUtilCore.findModuleForFile(
            virtualFile,
            project
    )

    // module can be null for scratch files
    return if (module != null) {
        GlobalSearchScope.moduleWithDependentsScope(module)
    } else {
        GlobalSearchScope.allScope(project)
    }
}

fun PsiElement.prevSiblingSequence() = generateSequence(this) { it.prevSibling }

@Contract(pure = true)
fun PsiElement.siblingExpression(function: (PsiElement) -> PsiElement): PsiElement? {
    var expression = this

    do {
        expression = function(expression)
    } while (expression is ElixirEndOfExpression ||
            expression is LeafPsiElement ||
            expression is PsiComment ||
            expression is PsiWhiteSpace)

    return expression
}

@Contract(pure = true)
fun PsiElement.stripAccessExpression(): PsiElement = (this as? ElixirAccessExpression)?.stripOnlyChildParent() ?: this

@Contract(pure = true)
fun PsiElement.stripOnlyChildParent(): PsiElement = children.singleOrNull() ?: this
