package com.pavelperc.parsepythongrammar

import com.pavelperc.parsepythongrammar.GenericRule.*
import com.pavelperc.parsepythongrammar.GenericRule.GenericElement.ElementType
import com.pavelperc.parsepythongrammar.RealizedRule.ElementLeaf

/**
 * Created by pavel on 31.03.2018.
 */

//--------------------Generic contexts

/**
 * Содержит реализацию основных действий контекста
 */
open class GenericContext(open val gElement: GenericElement) {
    
}


open class GenericContextLeaf(override val gElement: GenericElementLeaf) : GenericContext(gElement) {
    
    /** Called right after [ElementLeaf.updateAllChosen]*/
    open fun onChoose(context: ContextLeaf) {}
}

/**
 * Defines actions with lexemes ([ElementType.ID]): NEWLINE, STRING, ID ...
 * Lexeme realization may have [defaultRealizedToken]
 * or be entered manually, with checking by [checkPattern].
 * Also for manual input some [quickHints] can be retrieved.
 */
open class GenericContextId(
        gElement: GenericElementLeaf,
        /** If the lexeme behaves like a [ElementType.STRING],
         * it has its own token, realized by default.*/
        open val defaultRealizedToken: String? = null
) : GenericContextLeaf(gElement) {
    
    /** Если лексему нужно вводить вручную - проверяет подходит ли введённое слово для этой лексемы
     * @param leaf Лист, у которого заполнен [ElementLeaf.realizedToken]*/
    open fun checkPattern(value: String): Boolean = true
    
    
    /** List of hints. Should be already sorted.*/
    open fun quickHints(ctx: Context): List<ButtonContent> = emptyList()
    
}

class GenericContextNewline(element: GenericElementLeaf) : GenericContextId(element) {
    
    companion object {
        const val INDENT_SIZE = 6
        
        const val PREFIX_SPACE = "    "
    }
    
    override val defaultRealizedToken = "?"
    
    override fun onChoose(context: ContextLeaf) {
        updateRealizedToken(context)
    }
    
    /** returns mutableList with indents from storage (and creates it if it doesn't exists)*/
    private val Context.indentTokens: MutableList<ElementLeaf>
        get() {
            if ("indentTokens" !in storage) {
                if (gContext !is GenericContextNewline)
                    throw Exception("tried to fetch indentTokensList from not newline context")
                storage["indentTokens"] = mutableListOf<ElementLeaf>()
            }
            return storage["indentTokens"] as MutableList<ElementLeaf>
        }
    
    private val Context.dedentTokens: MutableList<ElementLeaf>
        get() {
            if ("dedentTokens" !in storage) {
                if (gContext !is GenericContextNewline)
                    throw Exception("tried to fetch dedentTokensList from not newLine context")
                storage["dedentTokens"] = mutableListOf<ElementLeaf>()
            }
            return storage["dedentTokens"] as MutableList<ElementLeaf>
        }
    
    
    /** sum of stmt above + indent tokens - dedent tokens*/
    fun getIndentCount(context: Context): Int {
        var count = context.upStep.asTypedSequence<GenericContextStmt>().count()
        
        if (count > 0)
            count--
        
        
        count += context.indentTokens.size
        count -= context.dedentTokens.size
        
        log?.println("found $count indents for newline")
        return count
    }
    
    fun getLastIndentOrDedentToken(context: Context): ElementLeaf? =
            context.run {
                when {
                    indentTokens.size > 0 -> indentTokens.last()
                    dedentTokens.size > 0 -> dedentTokens.last()
                    else -> null
                }
            }
    
    fun addIndentOrDedentToken(context: Context, token: ElementLeaf) {
        log?.println("in addIndentOrDedentToken in $context")
        
        when (token.gElement.text) {
            "INDENT" -> context.indentTokens.add(token)
            "DEDENT" -> context.dedentTokens.add(token)
            else -> throw Exception("tried to add not indent or dedent element to context")
        }
        log?.println("indentTokens.size = ${context.indentTokens.size}, " +
                "dedentTokens.size = ${context.dedentTokens.size}")
    }
    
    fun updateRealizedToken(context: ContextLeaf) {
        if (context.gContext !is GenericContextNewline)
            throw Exception("tried to update realized token in not newline leaf")
        
        context.element.realizedToken = PREFIX_SPACE +
                (" " + "-".repeat(INDENT_SIZE)).repeat(getIndentCount(context)).trimEnd()
    }
}

class GenericContextIndentOrDedent(
        element: GenericElementLeaf,
        val isIndent: Boolean,
        defaultRealizedToken: String? = ""
) : GenericContextId(element, defaultRealizedToken) {
    
    override fun onChoose(context: ContextLeaf) {
        
        val (newlineCtx, gNewlineCtx) = context.leftLeafStep.goTypedLeaf<GenericContextNewline>()
                ?: throw Exception("No newline before indentOrDedent")
        
        gNewlineCtx.addIndentOrDedentToken(newlineCtx, context.element)
        
        gNewlineCtx.updateRealizedToken(newlineCtx)
    }
    
}

/** Context for '=' token.*/
class GenericContextAssign(element: GenericElementLeaf) : GenericContextLeaf(element) {
    
    override fun onChoose(context: ContextLeaf) {
        
        val ctxName = context.leftLeafStep.go()
        val gCtxName = ctxName?.gContext
        
        if (ctxName == null || gCtxName !is GenericContextName)
            throw Exception("not found name before assign. found: $ctxName")
        
        gCtxName.updateAssignmentInStmt(ctxName)
        
    }
}


/**
 * Defines actions with names of variables, functions or classes.
 */
class GenericContextName(
        element: GenericElementLeaf,
        val colorForFunc: Int,
        val colorForVar: Int
) : GenericContextId(element) {
    
    private val defaultFuncs = listOf("print", "len", "int", "input", "float")
    private val defaultVars = listOf("self", "lst", "result", "some_var")
    
    
    override fun checkPattern(value: String): Boolean {
        val regex = Regex("^[a-zA-Z_\$][a-zA-Z_\$0-9]*\$")
        return value.matches(regex)
    }
    
    
    /** get all appropriate variables for this context */
    private fun getVariables(context: Context): List<String> {
        // get context and generic context of stmt
        val (stmt, stmtGeneric) = context.upStep.goTyped<GenericContextStmt>()
                ?: throw Exception("Name without stmt above.")
        
        return stmtGeneric.getAssignmentsLeft(stmt)
    }
    
    private class ButtonContentImpl(
            override val groupingTagForButton: GroupingTag,
            override val nameForButton: String
    ) : ButtonContent
    
    
    override fun quickHints(ctx: Context): List<ButtonContent> {
        val funcTag = GroupingTag("func", colorForFunc, 2)
        val varTag = GroupingTag("var", colorForVar, 1)
        val defVarTag = GroupingTag.defaultTag
        
        val defFuncContent = defaultFuncs.map { ButtonContentImpl(funcTag, it) }
        val defVarContent = defaultVars.map { ButtonContentImpl(defVarTag, it) }
        
        val varContent = getVariables(ctx).map { ButtonContentImpl(varTag, it) }
                .distinct()
        
        // JUST SORT MANUALLY BY PRIORITY
        return (varContent + defFuncContent + defVarContent)
    }
    
//    private var Context.isAssignment: Boolean
//        get() = this.storage.getOrDefault("isAssignment", false) as Boolean
//        set(value) {
//            storage["isAssignment"] = value
//        }
//    
//    /** Sets flag that current name is placed before assign.
//     * Should be invoked in [GenericContextAssign.onChoose]*/
//    fun setAsAssignment(ctx: Context, value: Boolean = true) {
//        ctx.isAssignment = value
//    }
    
    /** If it is placed before assign, updates assignment in stmt above*/
    fun updateAssignmentInStmt(ctx: ContextLeaf) {
        
        if (ctx.rightLeafStep.go()?.gContext !is GenericContextAssign) {
            log?.println("in updateAssign for NAME: not found '=' at the right of $ctx but found ${ctx.rightLeafStep.go()}")
            return
        }
        
        // stmt ctx and generic stmt ctx
        val (stmtCtx, gStmtCtx) = ctx.upStep.goTyped<GenericContextStmt>()
                ?: throw Exception("No stmt above name before assign")
        
        
        gStmtCtx.putAssignment(stmtCtx, ctx.element.realizedToken!!)
        
        log?.println("in updateAssign for NAME: succeed}")
    
    }
}

/** Defines actions with numbers.*/
class GenericContextNumber(element: GenericElementLeaf) : GenericContextId(element) {
    
    override fun checkPattern(value: String): Boolean {
        val regex = Regex("^[0-9]+\$")
        return value.matches(regex)
    }
}

/**
 * Определяет действия со строками (напр. "abc")
 */
class GenericContextString(element: GenericElementLeaf) : GenericContextId(element) {
    
    override fun checkPattern(value: String): Boolean {
        val regex = Regex("^\".*\"$")
        return value.matches(regex)
    }
}


class GenericContextStmt(element: GenericElement) : GenericContext(element) {
    
    private var Context.assigned: String?
        get() = storage["assigned"] as String?
        set(value) {
            if (gContext !is GenericContextStmt)
                throw Exception("put assignment into non stmt context")
            
            if (value == null) {
                storage.remove("assigned")
            } else {
                storage["assigned"] = value
            }
        }
    
    /** Adds variable name from assignment (like 'x' for x = 5).
     * Only one assignment can be stored in stmt.*/
    fun putAssignment(ctx: Context, name: String) {
        ctx.assigned = name
    }
    
    fun getAssignmentsLeft(context: Context): List<String> {
        val ans = mutableListOf<String>()
        
        
        // trying to call recursive for upper level
        val upper = context.upStep.goTyped<GenericContextStmt>()
                ?.let { (ctx, gCtxStmt) -> gCtxStmt.getAssignmentsLeft(ctx) }
        
        if (upper != null) {// we are not on the top level stmts
            ans += upper
            // trying to get nearest stmts on the left
            ans += context.leftStep.asTypedSequence<GenericContextStmt>()
                    .map { it.first.assigned }
                    .filter { it != null }
                    .map { it as String }
                    .toList()
        } else {// we are on the top. trying to jump left
            ans += context.jumpLeftStep.asTypedSequence<GenericContextStmt>()
                    .map { it.first.assigned }
                    .filter { it != null }
                    .map { it as String }
                    .toList()
        }
        
        return ans
    }
}

class GenericContextExpr(gElement: GenericElement) : GenericContext(gElement) {
    
    fun collectLeft(context: Context) {
        
    }
    
}
