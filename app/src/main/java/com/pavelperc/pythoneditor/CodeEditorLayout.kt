package com.pavelperc.pythoneditor

import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.pavelperc.parsepythongrammar.*
import com.pavelperc.parsepythongrammar.RealizedRule.ElementLeaf
import kotlinx.android.synthetic.main.activity_editor.*
import org.jetbrains.anko.*
import kotlin.concurrent.thread


/**
 * Main layout with code.
 * It contains rows with editTexts with realized tokens.
 *
 * ...Cursor is the part of one of editText
 */
class CodeEditorLayout(
        val mainRuleGraphics: MainRuleGraphics
) : LinearLayout(mainRuleGraphics.activity) {
    
    val activity: EditorActivity = mainRuleGraphics.activity
    
    /** map: element to its line layout in code.
     * Null points to first line. (Made especially for cursor before first element*/
    val leafToLL = mutableMapOf<ElementLeaf?, LineLayout>()
    
    /** map: element to its [TokenEditText] in line*/
    val leafToEt = mutableMapOf<ElementLeaf, TokenEditText>()
    
    
    /** layout rows with editTexts*/
    val lines: Sequence<LineLayout>
        get() = childrenSequence().filter { it is LineLayout }.map { it as LineLayout }
    
    var cursorLayout: LineLayout
    
    /** Index of cursor element in cursorLayout*/
    var cursorPosition: Int
    
    val etCursor = CursorTextView()
    
    val textViewBeforeFirst: TextViewBeforeFirst
    
    init {
        orientation = LinearLayout.VERTICAL
        val lp = LinearLayout.LayoutParams(wrapContent, wrapContent)
        lp.margin = dip(8)
        layoutParams = lp
        
        val firstLine = LineLayout()
        addView(firstLine)
        
        textViewBeforeFirst = TextViewBeforeFirst(firstLine)
        
        // setup cursor
        
        firstLine.addView(etCursor)
        cursorPosition = firstLine.indexOfChild(etCursor)
        cursorLayout = firstLine
    }
    
    /** Should be invoked in UI thread.
     * [prevView] is either [TokenEditText] or [TextViewBeforeFirst].
     * [layout] must contain [prevView].
     *
     * This method DOES NOT calls [MainRuleGraphics.findAlternatives] in separate thread.*/
    fun changeCursor(prevView: View, layout: LineLayout) {
        
        mainRuleGraphics.cursor = if (prevView is TokenEditText) prevView.leaf else null
        
        // remove old cursor
        cursorLayout.removeViewAt(this.cursorPosition)
        
        
        cursorPosition = layout.indexOfChild(prevView) + 1
        
        layout.addView(etCursor, cursorPosition)
        
        cursorLayout = layout

//        Thread() {
//            mainRuleGraphics.findAlternatives()
//        }.start()
    }
    
    
    /** Should be invoked in UI thread.
     * @param noBuildAndFind if it is true -
     * we don't call [MainRuleGraphics.build] and [MainRuleGraphics.findAlternatives].
     * But if the token must be manually entered, Build and Find will be invoked in [TokenEditText]
     * */
    fun addToken(leaf: ElementLeaf, noBuildAndFind: Boolean = false) {
        
        // if text realized token exists or can be entered automatically et fills itself with it
        // else enables editing mode.
        // to check if et is filled you can call isFilled property
        val etToken = TokenEditText(leaf, mainRuleGraphics.trueCursor)
        leafToEt[leaf] = etToken
        
        
        // adding new layout for new line
        
        if (leaf.gContext is GenericContextNewline) {
            if (!noBuildAndFind) {
                mainRuleGraphics.build(mainRuleGraphics.trueCursor, leaf)
                etToken.updateRealizedToken()
            }
            
            val newLineLayout = LineLayout()
            newLineLayout.addView(etToken)
            this.addView(newLineLayout, this.indexOfChild(cursorLayout) + 1)
            
            leafToLL[leaf] = newLineLayout
            changeCursor(etToken, newLineLayout)
            
            if (!noBuildAndFind) {
//                thread {
                mainRuleGraphics.findAlternatives()
//                }
            }
            
        } else {
            cursorLayout.addView(etToken, cursorPosition + 1)
            leafToLL[leaf] = cursorLayout
            
            
            // changeCursor and build should always be together OR NOT
            // because for late build cursor should be already changed
            val savedTrueCursor = mainRuleGraphics.trueCursor
            changeCursor(etToken, cursorLayout)
            
            if (etToken.isFilled && !noBuildAndFind) {
                // TODO simplify savedTrueCursor and methods changeCursor, build
//                thread {
                mainRuleGraphics.build(savedTrueCursor, leaf)
                mainRuleGraphics.findAlternatives()
//                }
            }
            // else wait until user enters the text 
            // and call build and findAlternatives in onTokenTextEnter
        }
    }
    
    /** Just synchronizes [ElementLeaf.realizedToken] and [TokenEditText.setText]
     * for token at [MainRuleGraphics.cursor] (visible cursor, not [MainRuleGraphics.trueCursor]*/
    fun updateTextBeforeCursor() {
        val cursorLeaf = mainRuleGraphics.cursor
                ?: throw Exception("no leaf at cursor position in updateTextBeforeCursor")
        
        val etBeforeCursor = leafToEt[cursorLeaf]!!
        
        etBeforeCursor.updateRealizedToken()
    }
    
    fun convertToText() =
            lines.map { line ->
                line.tokens
                        .map inner@{ etToken ->
                            val leaf = etToken.leaf
                            val gContext = leaf.gContext
                            if (gContext is GenericContextNewline) {
                                val indents = gContext.getIndentCount(etToken.leaf.context)
                                "    ".repeat(indents)
                            } else {
                                leaf.realizedToken + " "
                            }
                        }
                        .joinToString("")
                        .replace(" ( ", "(")
                        .replace(" ) ", ")")
                
            }.joinToString("\n")
    
    
    /** min width in px for token*/
    val tokenMinWidth = dip(20)
    
    /** Line with EditTexts*/
    inner class LineLayout : LinearLayout(activity) {
        init {
            val lp = LinearLayout.LayoutParams(wrapContent, wrapContent)
            lp.margin = 2
            
            layoutParams = lp
            orientation = LinearLayout.HORIZONTAL
        }
        
        val tokens: Sequence<TokenEditText>
            get() = childrenSequence().filter { it is TokenEditText }.map { it as TokenEditText }
    }
    
    
    /** if realized token exists or can be entered automatically etToken fills itself with it,
     * else it enables editing mode.
     * To check if etToken is filled you can call [isFilled] property.
     * @param leftLeaf Is needed for late build in [onEditorAction]*/
    inner class TokenEditText(val leaf: ElementLeaf, private val leftLeaf: ElementLeaf?)
        : EditText(activity), OnClickListener, OnLongClickListener, TextView.OnEditorActionListener {
    
        private val hintButtons: MutableList<ButtonQuickHint> = mutableListOf()
        
        override fun onClick(v: View?) {
            if (inEditMode)
                return
            
            changeCursor(this, leafToLL[leaf]!!)
//            thread {
            try {
                mainRuleGraphics.findAlternatives()
            } catch (e: Exception) {
//                    activity.runOnUiThread {
                activity.longToast("failed to findAlternatives in onTokenClick.\nCursor = ${mainRuleGraphics.cursor}")
//                    }
            }
//            }
        }
        
        override fun onLongClick(v: View?): Boolean {
            activity.vibrator.vibrate(100)
            
            if (!isEditable) {
                activity.toast("Can not change predefined token.")
                return true
            }
            
            unlockEditing()
            return true
        }
        
        
        /** It is called when we want apply entered text in editor.
         * It doesn't call any [MainRuleGraphics.build] or [MainRuleGraphics.findAlternatives].
         * It just tries to apply text to [ElementLeaf.realizedToken]
         * AND CALLS [lockEditing] FOR THIS etToken. (if succeed)
         * @return true if succeed.*/
        private fun applyNewText(): Boolean {
            try {
                leaf.realizedToken = text.toString()
                
                // if we entered the name first time nothing will happen.
                // But if we are in change mode - update... will find '=' on the right 
                // and will update the storage of stmt above
                if (leaf.gContext is GenericContextName) {
                    // TODO simplify calling updateAssignmentInStmt
                    (leaf.gContext as GenericContextName).updateAssignmentInStmt(leaf.context)
                }
            } catch (e: Exception) {
                activity.longToast("Invalid input:\n${e.localizedMessage}")
                return false
            }

//                activity.toast("Successful input")
            
            lockEditing()
            return true
        }
        
        
        /** Handles any key press on keyboard.*/
        override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
            //handle enter key after editing a token.
            if (actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_NEXT
                    || actionId == EditorInfo.IME_ACTION_SEND
                    || actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event?.action == KeyEvent.KEYCODE_ENTER)) {
                
                // lockEditing is inside
                if (!applyNewText()) {
                    return false
                }

//                thread {
                mainRuleGraphics.build(leftLeaf, leaf)
                mainRuleGraphics.findAlternatives()
//                }
                
                
                return true
            } else {
                
                return false
            }
        }
        
        /** Is called after any change of text*/
        private fun afterTextChanged(s: Editable?) {
            if (!inEditMode)
                return
            if (hintButtons.size == 0)
                return
            
            val inputText = this@TokenEditText.text.toString()
    
            val filtered = hintButtons
                    .filter { btn -> btn.content.nameForButton.startsWith(inputText) }
    
            
            // Assume that any added or deleted key changes the number of filtered
            if (activity.llQuickHints.childCount == filtered.size)
                return
            
            activity.llQuickHints.removeAllViews()
            filtered.forEach { activity.llQuickHints.addView(it) }
    
//            activity.toast("in afterTextChanged changed list")
            
        }
        
        private val savedKeyListener = keyListener
//        private val savedBackground = background
        
        
        /** False if realized token was assigned in initialization and can't be changed by user.
         * True - otherwise.*/
        val isEditable: Boolean
        
        init {
            val lp = LinearLayout.LayoutParams(wrapContent, wrapContent)
            
            lp.margin = dip(2)
            layoutParams = lp
            
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            background = null
            backgroundColor = leaf.groupingTagForButton.color
            padding = dip(2)
            
            minWidth = tokenMinWidth
            minimumWidth = tokenMinWidth
            
            // disable editing
            keyListener = null
            isFocusableInTouchMode = false
            
            setOnLongClickListener(this)
            setOnClickListener(this)
            setOnEditorActionListener(this)
            
            addTextChangedListener(object: TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    this@TokenEditText.afterTextChanged(s)
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }
            })
            
            
            nextFocusUpId = this.id
            nextFocusDownId = this.id
            
            // trying to manually/auto input value for realizedToken
            
            val gContext = leaf.gContext
            if (gContext is GenericContextId && gContext.defaultRealizedToken != null) {
                // try to autocomplete realizedToken
                leaf.realizedToken = gContext.defaultRealizedToken
            }
            
            if (leaf.realizedToken != null) {
                setText(leaf.realizedToken)
                isEditable = false
            } else {
                isEditable = true
                unlockEditing()
            }
        }
        
        fun updateRealizedToken() {
            setText(leaf.realizedToken)
        }
        
        
        /** true after [unlockEditing] has been called
         * and false after [lockEditing] has been called*/
        var inEditMode = false
            private set
        
        
        /** Checks if any text in [ElementLeaf.realizedToken] has been entered.*/
        val isFilled: Boolean
            get() = leaf.realizedToken != null
        
        fun unlockEditing() {
            hint = " enter ${leaf.toString().toLowerCase()} "
            
            inEditMode = true
            keyListener = savedKeyListener
            isFocusableInTouchMode = true

//            background = savedBackground
//            backgroundColor = leaf.groupingTagForButton.color
            
            
            // SETTING UP INPUT TYPE OF THE KEYBOARD
            if (leaf.gElement.text == "NUMBER") {
                inputType = InputType.TYPE_CLASS_NUMBER
            } else {
                inputType = InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
            }
            
            if (leaf.gElement.text == "STRING" && text.isEmpty()) {
                setText("''")
                setSelection(1)
            }
            
            requestFocus()
            
            activity.llQuickHints.removeAllViews()
            hintButtons.clear()
            
            val gContext = leaf.gContext
            if (gContext is GenericContextId) {
                hintButtons += gContext.quickHints(leaf.context).map { ButtonQuickHint(it) }
                
                hintButtons.forEach { activity.llQuickHints.addView(it) }
            }
            
            
            activity.showKeyboard()
            
        }
        
        inner class ButtonQuickHint(content: ButtonContent)
            : ColoredButton(content, activity) {
            
            init {
                minHeight = 0
                minimumHeight = 0
                padding = activity.dip(4)
            }
            
            /** On hint click.*/
            override fun onClick(v: View?) {
                this@TokenEditText.setText(content.nameForButton)
                
                if (content.groupingTagForButton.tag == GenericContextName.FUNC_HINT_TAG) {
                    addParenthesesForFunction()
                } else {
                    onEditorAction(null, EditorInfo.IME_ACTION_GO, null)
                }
            }
            
            
            private fun addParenthesesForFunction() {
                // name of func for autocomplete
                val nameLeaf = this@TokenEditText.leaf
                
                // if entered by user (or chosen among hints) function is incorrect.
                // (failed assigning to realizedToken)
                // break current function.
                if (!applyNewText())
                    return
                
                // building current name
                mainRuleGraphics.build(leftLeaf, nameLeaf)
                
                // finding opening parenthesis
                
                val openingPar = mainRuleGraphics
                        .findAlternativesAndReturn()
                        .firstOrNull {
                            it.groupingTagForButton.tag == GroupingTag.FUNC_ARG_TAG
                                    && it.gElement.text == "("
                        } ?: return
                
                mainRuleGraphics.build(nameLeaf, openingPar)
                
                // moving cursor inside
                addToken(openingPar, true)
                
                // after we have moved cursor
                // finding closing parenthesis
                val closingPar = mainRuleGraphics
                        .findAlternativesAndReturn()
                        .firstOrNull {
                            it.groupingTagForButton.tag == GroupingTag.FUNC_ARG_TAG
                                    && it.gElement.text == ")"
                        } ?: return
                
                mainRuleGraphics.build(openingPar, closingPar)
                
                addToken(closingPar, true)
                
                // moving cursor back
                changeCursor(leafToEt[openingPar]!!, leafToLL[openingPar]!!)
                // find alternatives in normal way: with buttons
                mainRuleGraphics.findAlternatives()
            }
        }
        
        fun lockEditing() {
            hint = ""
            inEditMode = false
            isFocusableInTouchMode = false
            
            keyListener = null
//            background = null
//            backgroundColor = leaf.groupingTagForButton.color
            
            activity.showButtons()
        }
    }
    
    /** Adds itself to [firstLine] */
    inner class TextViewBeforeFirst(val firstLine: LineLayout) : TextView(activity) {
        init {
            val lp = LinearLayout.LayoutParams(wrapContent, wrapContent)
            lp.margin = dip(2)
            layoutParams = lp
            backgroundColor = Color.GRAY
            padding = dip(2)
            
            minWidth = dip(20)
            minimumWidth = dip(20)
            
            text = GenericContextNewline.PREFIX_SPACE

//            isFocusableInTouchMode = false
            
            firstLine.addView(this)
            
            setOnClickListener {
                changeCursor(this, firstLine)
            }
        }
    }
    
    inner class CursorTextView : TextView(activity) {
        init {
            text = "___"
            layoutParams = LinearLayout.LayoutParams(wrapContent, wrapContent)
        }
    }
}