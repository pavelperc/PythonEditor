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
 * Определяет действия с лексемами([ElementType.ID]): NEWLINE, STRING, ID ...
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
                ( " " + "-".repeat(INDENT_SIZE)).repeat(getIndentCount(context)).trimEnd()
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


class GenericContextAssign(element: GenericElementLeaf) : GenericContextLeaf(element) {
    
    override fun onChoose(context: ContextLeaf) {
        
        val name = context.leftLeafStep.go()?.element
        
        if (name == null || name.gElement.text != "NAME")
            throw Exception("not found name before assign. found: $name")
        
        
        // stmt ctx and generic stmt ctx
        val (stmtCtx, gStmtCtx) = context.upStep.goTyped<GenericContextStmt>()
                ?: throw Exception("No stmt above assign")
        
        
        gStmtCtx.putAssignment(stmtCtx, name.realizedToken!!)
        
    }
}


/**
 * Определяет действия с именами переменных, функций и классов
 */
class GenericContextName(element: GenericElementLeaf) : GenericContextId(element) {
    
    val initialFuncs = listOf("print", "len", "int", "float")
    
    override fun checkPattern(value: String): Boolean {
        val regex = Regex("^[a-zA-Z_\$][a-zA-Z_\$0-9]*\$")
        return value.matches(regex)
    }
    
    /** get all appropriate variables for this context */
    fun getVariables(context: Context) {
        // get context and generic context of stmt
        val (stmt, stmtGeneric) = context.upStep.goTyped<GenericContextStmt>()
                ?: throw Exception("Name without stmt above.")
    }
}

/**
 * Определяет действия с числами
 */
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
        get() = storage["assigned"]?.let { it as String }
        set(value) {
            if (gContext !is GenericContextStmt)
                throw Exception("put assignment into non stmt context")
            
            if (value == null) {
                storage.remove("assigned")
            } else {
                storage["assigned"] = value
            }
        }
    
    fun putAssignment(ctx: Context, name: String) {
        ctx.assigned = name
    }

//    fun getAssignmentsLeft(context: Context): List<String> {
//        val left = context.getLeftNeighbors()
//        
//        return left
//                .filter { assignedTag in it.storage }
//                .map { it.storage[assignedTag] as String }
//        
//    }
}

class GenericContextExpr(gElement: GenericElement) : GenericContext(gElement) {
    
    fun collectLeft(context: Context) {
        
    }
    
}
