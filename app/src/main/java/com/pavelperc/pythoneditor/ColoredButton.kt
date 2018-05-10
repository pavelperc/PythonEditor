package com.pavelperc.pythoneditor

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.pavelperc.parsepythongrammar.ButtonContent
import org.jetbrains.anko.*

/** Button, generated from [content] with text and color.*/
abstract class ColoredButton(val content: ButtonContent, activity: Activity)
    : Button(activity), View.OnClickListener {
    
    init {
        // assign content to tag
//        tag = content
        
        // lparams for button
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.margin = activity.dip(2)
        lp.gravity = Gravity.CENTER_HORIZONTAL
        
        
        text = content.nameForButton
        
        backgroundColor = content.groupingTagForButton.color
        allCaps = false
        
        // only one of this assignments doesn't work for some reason
        minHeight = activity.dip(40)
        minimumHeight = activity.dip(40)
        
        minWidth = activity.dip(60)
        minimumWidth = activity.dip(60)
        
        padding = activity.dip(8)
        textSize = 14f
        
        layoutParams = lp
        
        setOnClickListener(this)
    }
}