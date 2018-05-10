package com.pavelperc.pythoneditor

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.pavelperc.parsepythongrammar.*
import kotlinx.android.synthetic.main.activity_editor.*
import org.jetbrains.anko.*
import java.util.logging.Handler


/**
 * Realization of MainRule with graphical interactive buttons.
 */
class MainRuleGraphics(
        val activity: EditorActivity,
        gRule: GenericRule,
        ruleMap: Map<String, GenericRule>
) : MainRule(gRule, ruleMap) {
    
    
    val codeEditorLayout = CodeEditorLayout(this)
    
    
    init {
    }
    
    
    /** onClick action for [ColoredButton] with [RealizedRule.ElementLeaf] inside.
     * It handles choosing one Element from Element leaves, which have been sent to [ask].*/
    private val onAlternativeClick = View.OnClickListener {
        
    }
    
    /** creating columns with buttons.*/
    private fun chunkButtons(buttons: List<ColoredButton>, size: Int = 4): LinearLayout {
        
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
    
    
//    /** Chooses one [leaf] avoiding creating a button.*/
//    override fun autoChoose(leaf: ElementLeaf) {
//        build(trueCursor, leaf)
//        
//        activity.runOnUiThread {
//            chooseOne(leaf, true)
//        }
//    }
    
    /** Chooses one [leaf] and adds/updates token in layout.
     * Must be invoked only in UI thread.
     * 
     * @param noBuildAndFind it supposed to be true for autoChoose when we don't want to call 
     * [build] and [findAlternatives] with standard configuration.
     * ATTENTION: in that case [build] should be invoked earlier!!!
     * Common button should leave it default.*/
    private fun chooseOne(leaf: ElementLeaf, noBuildAndFind: Boolean = false) {
        
        val gContext = leaf.gContext
        
        if (gContext is GenericContextIndentOrDedent) {
            // handle click on indent/dedent button
            // we don't create new etToken, just build and update previous
            if (!noBuildAndFind)
                build(trueCursor, leaf)
            codeEditorLayout.updateTextBeforeCursor()
            findAlternatives()
        } else {
            // build and findAlternatives will be invoked here 
            // or after realized token is entered
            codeEditorLayout.addToken(leaf, noBuildAndFind)
        }
    }
    
    /** Creates dynamic buttons*/
    override fun ask(alternatives: List<RealizedRule.ElementLeaf>) {
        val alternatives = alternatives.sortedBy { it.gElement.groupingTag }
        
        // creating list of buttons
        val buttons = alternatives.map { leaf ->
            ButtonAlternative(leaf)
        }
        
        val ll = chunkButtons(buttons)
        
//        activity.runOnUiThread {
            activity.llButtons.removeAllViews()
            activity.llButtons.addView(ll)
//        }
    }
    
    /** Button for interactive keyboard.*/
    inner class ButtonAlternative(private val leaf: ElementLeaf) : ColoredButton(leaf, activity) {
        
        override fun onClick(v: View?) {
//            autoChoose(leaf)
            chooseOne(leaf)
        }
    }
}

