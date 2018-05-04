package com.pavelperc.pythoneditor

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.pavelperc.parsepythongrammar.*
import kotlinx.android.synthetic.main.activity_editor.*
import org.jetbrains.anko.*


/**
 * Realization of MainRule, which interacts with the interface of [EditorActivity].
 */
class MainRuleGraphics(
        val activity: EditorActivity,
        gRule: GenericRule,
        ruleMap: Map<String, GenericRule>
) : MainRule(gRule, ruleMap) {
    
    
    val codeEditorLayout = CodeEditorLayout(this)
    
    
    init {
        activity.runOnUiThread {
            activity.llCode.addView(codeEditorLayout)
        }
    }
    
    /** finds alternatives with right cursor for indents*/
    fun findAlternatives() {
        // find right pointer for indent or dedent
        val trueCursor = cursor?.gContext?.let {
            if (it is GenericContextNewline)
                it.getLastIndentOrDedentToken(cursor!!.context)// nullable
            else null
        } ?: cursor
        
        findAlternatives(trueCursor)
    }
    
//    /** Формирует форматированную строку */
//    private val chainString: String
//        get() {
//            val ans = StringBuilder("All chain <<<\n")
//            
//            if (cursor == null)
//                ans.append("___ ")
//            
//            
//            for (element in chain) {
//                ans.append(element.realizedToken ?: element.toString()).append(" ")
//                if (element === cursor) {
//                    ans.append("___ ")
//                }
//            }
//            ans.append("\n>>>")
//            return ans.toString()
//        }
    
    
//    /** updates [codeEditorLayout] */
//    fun addToChain(leaf: ElementLeaf) {
//        activity.runOnUiThread {
//            codeEditorLayout.addToken(leaf)
//        }
//    }
    
    /** onClick action for [MyButton] with [RealizedRule.ElementLeaf] inside.
     * It handles choosing one Element from Element leaves, which have been sent to [ask].*/
    private val onAlternativeClick = View.OnClickListener {
        val chosen = it.tag as ElementLeaf
        
        val gContext = chosen.gContext
    
        if (gContext is GenericContextIndentOrDedent) {
            // handle click on indent/dedent button
            build(chosen)
            codeEditorLayout.updateTextBeforeCursor()
            findAlternatives()
        }
        else {
            // build and findAlternatives will be invoked here 
            // or after realized token is entered
            codeEditorLayout.addToken(chosen)
        }
        return@OnClickListener
    }
    
    /** Never points at indent or dedent!!!*/
    var cursor: ElementLeaf? = null
    
    /** creating columns with buttons.*/
    private fun chunkButtons(buttons: List<MyButton>, size:Int = 4): LinearLayout {
        
        val lls = buttons.chunked(size).map {
            val ll = LinearLayout(activity)
            ll.orientation = LinearLayout.VERTICAL
            // through buttons in column
            it.forEach { ll.addView(it) }
            ll.layoutParams = ViewGroup.LayoutParams(wrapContent, wrapContent)
            ll
        }
        
        val container = LinearLayout(activity)
        container.orientation = LinearLayout.HORIZONTAL
        container.layoutParams = ViewGroup.LayoutParams(wrapContent, wrapContent)
        lls.forEach { container.addView(it) }
        
        return container
    }
    
    
    /** Creates dynamic buttons*/
    override fun ask(alternatives: List<RealizedRule.ElementLeaf>) {
        val alternatives = alternatives.sortedBy { it.gElement.groupingTag }
        
        // creating list of buttons
        val buttons = alternatives.map { leaf ->
            MyButton(leaf, onAlternativeClick)
        }
        
        val ll = chunkButtons(buttons)
        
        activity.runOnUiThread {
            activity.llButtons.removeAllViews()
            activity.llButtons.addView(ll)
            // loop back button behaviour
            activity.lastButtonSheet = ll
        }
    }
    
    
//    /** Writes chain*/
//    override fun afterBuild() {
//        // update cursor
////        moveCursor()
//        
//        activity.printLine("\n" + chainString)
//    }
    
    /**
     * Asks while [assignValue] is false or throws an Exception
     * [assignValue] receives got string in params
     */
    private fun askWhile(question: String, assignValue: (String) -> Boolean) {
        var str = ""
        // flag if we should repeat request
        var flag = false
        do {
            activity.printLine(question)
            str = activity.readLine()
            
            try {
                flag = !assignValue(str)
                
            } catch (e: Exception) {
                activity.printLine(e.localizedMessage)
                flag = true
            }
            
        } while (flag)
    }
    
    
    /** Button for interactive keyboard.
     * The tag value contains [content]*/
    inner class MyButton(val content: ButtonContent, onClickListener: OnClickListener) : Button(activity) {
        init {
            // assign content to tag
            tag = content
            
            // lparams for button
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.margin = activity.dip(2)
            lp.gravity = Gravity.CENTER_HORIZONTAL
            
            
            text = content.toString()
            backgroundColor = content.groupingTagForButton.color
            allCaps = false
            
            // only one of this assignments doesn't work for some reason
            minHeight = activity.dip(40)
            minWidth = activity.dip(60)
            
            minimumHeight = activity.dip(40)
            minimumWidth = activity.dip(60)
            
            padding = activity.dip(8)
            textSize = 14f
            
            // setup button onClick
            setOnClickListener(onClickListener)
            layoutParams = lp
        }
    }
}