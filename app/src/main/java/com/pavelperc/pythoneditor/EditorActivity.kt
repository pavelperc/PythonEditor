package com.pavelperc.pythoneditor

import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.os.Environment.getExternalStoragePublicDirectory
import android.support.v7.app.AppCompatActivity
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import com.pavelperc.parsepythongrammar.setupFullGrammar
import kotlinx.android.synthetic.main.activity_editor.*
import org.jetbrains.anko.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import android.support.v4.app.ActivityCompat
import android.content.pm.PackageManager
import android.support.v4.content.ContextCompat



/** Main activity with editor.*/
class EditorActivity : AppCompatActivity() {
    
    lateinit var mainRuleGraphics: MainRuleGraphics
    
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        requestPermissions()
        
        // setup log
//        com.pavelperc.parsepythongrammar.log = object: com.pavelperc.parsepythongrammar.Log {
//            override fun println(str: Any) {
//                printLine(str)
//                Log.d("my_tag", str.toString())
//            }
//        }
        
        
        setSupportActionBar(toolbar)
        actionBar?.title = "Main Page"
        
        
        toolbar.inflateMenu(R.menu.main_menu)

//        tvInfo.movementMethod = ScrollingMovementMethod()

//        hsvCode.minimumHeight = svCode.height
        
        Thread() {
            val inputStream = resources.openRawResource(R.raw.full_grammar)
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            val grammar = reader.readText()
            printLine("read grammar, size=${grammar.length} symbols")
            
            
            val ruleMap = setupFullGrammar(grammar, true, false)
            
            mainRuleGraphics = MainRuleGraphics(this, ruleMap["file_input"]!!, ruleMap)
            
            runOnUiThread {
                llCode.addView(mainRuleGraphics.codeEditorLayout)
            }
            
            mainRuleGraphics.findAlternatives()
        }
                .apply { isDaemon = true }
                .start()
        
    }
    
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        
        return true
    }
    
    
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        // Handle item selection
        when (item?.itemId) {
            R.id.save_text -> {
                val text = mainRuleGraphics.codeEditorLayout.convertToText()
                
                alert {
                    title = "Result text:"
                    
                    message = text
                    
                    
                    positiveButton("Save to downloads") {
                        saveToFile(text)
                    }
                    
                    negativeButton("Cancel") {
                        it.cancel()
                    }
                    
                }.show()
                
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }
    
    private fun requestPermissions() {
        // Check whether this app has write external storage permission or not.
        val writeExternalStoragePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
// If do not grant write external storage permission.
        if (writeExternalStoragePermission != PackageManager.PERMISSION_GRANTED) {
            // Request user to grant write external storage permission.
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 5)
        }
    }
    
//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if(requestCode == REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION)
//        {
//            val grantResultsLength = grantResults.size
//            if(grantResultsLength > 0 && grantResults[0]==PackageManager.PERMISSION_GRANTED)
//            {
////                Toast.makeText(getApplicationContext(), "You grant write external storage permission. Please click original button again to continue.", Toast.LENGTH_LONG).show();
//            }else
//            {
////                Toast.makeText(getApplicationContext(), "You denied write external storage permission.", Toast.LENGTH_LONG).show();
//            }
//        }
    
    private fun saveToFile(text: String, filename: String = "MY_PROGRAM.py") {
        
         // Get the directory for the app's private pictures directory.
//        val file = File(getExternalFilesDir(""), filename)
        
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    
            val file = File(dir, filename)
    
            dir.mkdirs()
    
            file.writeText(text)
            longToast("Text was saved to ${file.absolutePath}")
        } catch (e: Exception) {
            longToast("Exception: " + e.localizedMessage)
        }
    }
    
    
    fun printLine(str: Any = "") {
        runOnUiThread {
            tvInfo.text = tvInfo.text.toString() + str.toString() + "\n"
        }
        
        svCode.postDelayed({
            svCode.fullScroll(ScrollView.FOCUS_DOWN)
        }, 100)
    }
    
    
    private var keyboardIsHidden = true
    
    /** Shows buttons and hides keyboard.*/
    fun showButtons() {
        if (keyboardIsHidden)
            return
        
        // hide keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(llCode.windowToken, 0)
        keyboardIsHidden = true
        
        llQuickHints.visibility = View.GONE
        
        // show buttons
        llButtons.postDelayed({
            llButtons.visibility = View.VISIBLE
            
            svCode.postDelayed({
                svCode.fullScroll(ScrollView.FOCUS_DOWN)
            }, 100)
        }, 250)
    }
    
    /** Shows keyboard and hides buttons.*/
    fun showKeyboard() {
        if (!keyboardIsHidden)
            return
        
        // hide buttons
        llButtons.visibility = View.GONE
        
        // show keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
        keyboardIsHidden = false
    
        llQuickHints.visibility = View.VISIBLE
        
    }
    
    
//    /** Saved main menu with high level buttons.*/
//    var lastButtonSheet: LinearLayout? = null
    
    
    override fun onBackPressed() {

//        // return to previous buttonSheet
//        if (lastButtonSheet != null) {
//            svButtons.removeAllViews()
//            svButtons.removeView(lastButtonSheet)
//            svButtons.addView(lastButtonSheet)
//            lastButtonSheet = null
//    
//            if (!keyboardIsHidden)
//                showButtons()
//        }
//        else 
        if (!keyboardIsHidden)
            showButtons()
        else
            super.onBackPressed()
    }
}


