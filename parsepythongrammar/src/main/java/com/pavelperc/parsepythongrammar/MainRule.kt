package com.pavelperc.parsepythongrammar

import com.pavelperc.parsepythongrammar.RealizedRule.ElementLeaf
import com.pavelperc.parsepythongrammar.RealizedRule.ElementNode

/**
 * Расширение для RealizedRule.
 *
 * Это класс, в котором будет храниться реализация основного (стартового) правило всей грамматики.
 * То есть вся программа на питоне.
 *
 * Поэтому здесь будет храниться текущая позиция курсора,
 * будут методы для поиска всех подходящих элементов для реализации и.т.д.
 *
 * Created by pavel on 06.01.2018.
 */
abstract class MainRule(gRule: GenericRule, ruleMap: Map<String, GenericRule>) : RealizedRule(gRule, ruleMap, null) {
    
    /** Элемент выбранный ранее элемент, после которого должен быть найден currentElement.
    //     *  Если cursor равен null, то он левее самого первого */
//    var cursor: ElementLeaf? = null


//    /** Changes cursor and calls [findAlternatives].*/
//    abstract fun addToChain(leaf: ElementLeaf)
    
    
    /** Настройка начального currentElement */
    protected fun setupFirstElement(): ElementLeaf {
        // идём вниз по своему правилу
        var ans = ruleAlteration.concatenations[0].repetitions[0].firstElement
        
        while (ans is ElementNode) {
            // теперь идём вниз либо по чужим правилам либо по нашему правилу
            ans = ans.alteration.concatenations[0].repetitions[0].firstElement
        }
        println("setup first currentElement: " + ans.gElement)
        return ans as ElementLeaf
    }
    
    
    /**
     * Retrieves alternatives, starting from cursor position.
     * After that it asks alternatives with [ask].
     * Then if an alternative is chosen method [build] should be called inside [ask] .
     *
     */
    @Synchronized
    fun findAlternatives(cursor: ElementLeaf?) {
        // if the cursor is null, then it is to the left of the first Element
        
        /** first element, which will be proposed to choose.
         * may be null when [ElementLeaf.findNewCurrent] returns null */
        val currentElement =
                if (cursor == null)
                    setupFirstElement()
                else
                    cursor.findNewCurrent()
        
        
        currentElement?.apply {
            println("found new currentElement: $gElement : ${thisRule.gRule.id}")
        }
        
        val alternatives = currentElement?.findAlternativesUp(false) ?: emptyList()
        
        ask(alternatives)
    }
    
    /** Outputs all alternatives and chooses one.
     * It must call method [build] itself after it chooses right alternative.*/
    abstract fun ask(alternatives: List<ElementLeaf>)
    
    
    /** Updates tree.
     * */
    fun build(chosen: ElementLeaf) {
        chosen.updateAllChosen()
        
        chosen.gContext.also {
            if (it is GenericContextLeaf)
                it.onChoose(chosen.context)
        }
    }
}