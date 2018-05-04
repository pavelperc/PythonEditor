package com.pavelperc.parsepythongrammar

import com.pavelperc.parsepythongrammar.RealizedRule.Element
import com.pavelperc.parsepythongrammar.RealizedRule.ElementLeaf
import com.pavelperc.parsepythongrammar.RealizedRule.ElementNode

/**
 * Несёт функцию сбора контекстов слева и сверху, ориентированию по графу.
 * Предполагается, что все конкретные действия описываются в GenericContext
 */
abstract class Context(open val element: Element) {
    
    val storage = mutableMapOf<String, Any>()
    
    val gContext: GenericContext?
        get() = element.gElement.gContext
    
    
    val upStep: Step get() = UpStep(this)
    val leftStep: Step get() = LeftStep(this)
    val rightStep: Step get() = RightStep(this)
    val downLeftStep: Step get() = DownLeftStep(this)
    val downRightStep: Step get() = DownRightStep(this)
    
    class UpStep(ctx: Context) : Step(ctx) {
        override fun Context.oneStep(): Context? =
                element.fatherElement?.context
    }
    
    
    class LeftStep(ctx: Context) : Step(ctx) {
        override fun Context.oneStep(): Context? =
                element.run {
                    if (positionInFather > 0)
                    // move left inside repetition
                        father.realizedElements[positionInFather - 1].context
                    else
                    // try to move left inside concatenation
                    // but we should skip concs without reps with realized elements
                        father.let { rep ->
                            rep.father.repetitions
                                    .take(rep.positionInFather)// take only left brothers of rep
                                    .lastOrNull { it.realizedElements.size > 0 }// find last with realized reps
                                    ?.realizedElements?.last()?.context
                        }
                }
    }
    
    class RightStep(ctx: Context) : Step(ctx) {
        override fun Context.oneStep(): Context? =
                element.run {
                    if (positionInFather < father.realizedElements.size - 1)
                    // move right inside repetition
                        father.realizedElements[positionInFather + 1].context
                    else
                    // try to move right inside concatenation
                    // but we should skip concs without reps with realized elements
                        father.let { rep ->
                            rep.father.repetitions
                                    .takeLast(rep.father.repetitions.size - rep.positionInFather - 1)
                                    .firstOrNull { it.realizedElements.size > 0 }
                                    ?.realizedElements?.first()?.context
                        }
                }
    }
    
    class DownLeftStep(ctx: Context) : Step(ctx) {
        override fun Context.oneStep(): Context? =
                if (this is ContextNode) {
                    element.alteration.chosen?.repetitions
                            ?.firstOrNull { rep -> rep.realizedElements.size > 0 }
                            ?.realizedElements?.first()?.context
                } else
                    null
    }
    
    class DownRightStep(ctx: Context) : Step(ctx) {
        override fun Context.oneStep(): Context? =
                if (this is ContextNode) {
                    element.alteration.chosen?.repetitions
                            ?.lastOrNull { rep -> rep.realizedElements.size > 0 }
                            ?.realizedElements?.last()?.context
                } else
                    null
    }

//    /** Returns the context of next left element in current depth level of the tree
//     * (or null if it doesn't exist).
//     * The returned element will be definitely realized.*/
//    fun jumpLeft(): Context? =
//            if (element.positionInFather > 0)
//            // move left inside repetition
//                element.father.realizedElements[element.positionInFather - 1].context
//            else
//            // try to move left inside concatenation
//            // but we should skip concs without reps with realized elements
//                element.father.father.repetitions
//                        .take(element.father.positionInFather)// take only left brothers of conc
//                        .lastOrNull { it.realizedElements.size > 0 }// find last with realized reps
//                        ?.realizedElements?.last()?.context// get its context
//                // elvis: if there are no appropriate concs found on the left
//                        ?: let {
//                            // move left in upper level of the tree while we can't return down to this level of tree
//                            var leftAbove = element.fatherElement?.context?.jumpLeft()// recursive call for upper element
//                            while (leftAbove != null && leftAbove.element is RealizedRule.ElementLeaf) {
//                                leftAbove = leftAbove.jumpLeft()
//                            }
//                            // here leftAbove is either null or context of ElementNode with realized concs
//                            leftAbove?.run {
//                                // convert leftAbove to its right realized element (if it is not null)
//                                (element as RealizedRule.ElementNode).alteration.chosen!!.repetitions
//                                        .last { it.realizedElements.size > 0 }.realizedElements.last().context
//                            }
//                        }
//    

//    inline fun <reified T : GenericContext> jumpLeftTyped(): Context? {
//        var left = jumpLeft()
//        while (left != null && left.gContext !is T) {
//            left = jumpLeft()
//        }
//        return left
//    }


//    /** Sequence of all contexts with [gContext] of type [T] */
//    inline fun <reified T : GenericContext> leftTypedSequence() = Sequence {
//        object : Iterator<Context> {
//            var next = jumpLeftTyped<T>()
//            
//            override fun hasNext() = next != null
//            
//            override fun next(): Context {
//                val saved = next
//                next = jumpLeftTyped<T>()
//                return saved ?: throw Exception("next called when hasNext was false")
//            }
//        }
//    }
    
    
    /** Finds first realized leaf on the left.*/
    class LeftLeafStep(ctx: ContextLeaf) : StepLeaf(ctx) {
        override fun Context.oneStep(): ContextLeaf? {
            var curr: Context = this
            
            // up
            
            while (curr.leftStep.go() == null) {
                curr = curr.upStep.go() ?: return null
            }
            
            // left
            
            curr = curr.leftStep.go()!!
            
            // down
            while (curr !is ContextLeaf) {
                // always can go down in not leaf
                curr = curr.downRightStep.go() ?: throw Exception("Can not go down from not ContextLeaf.")
            }
            
            return curr
        }
    }
    
    
    abstract class Step(open val ctx: Context) {
        
        /**One step in a given direction.*/
        protected abstract fun Context.oneStep(): Context?
        
        /** public wrapper of [oneStep]. Returns context of next step.*/
        open fun go(): Context? = ctx.oneStep()
                .also { log?.println("after ${this::class.java.simpleName}: ${ctx} to $it") }
        
        open fun asSequence() = Sequence<Context> {
            object : Iterator<Context> {
                
                private var cashedNext: Context? = null
                
                private var curr: Context = ctx
                
                private var hasCashedNext = false
                
                override fun hasNext(): Boolean {
                    if (!hasCashedNext) {
                        cashedNext = curr.oneStep()
                        
                        hasCashedNext = true
                    }
                    
                    log?.println("after hasNext in ${this@Step::class.java.simpleName}: $curr to $cashedNext")
                    
                    return cashedNext != null
                }
                
                override fun next(): Context {
                    if (hasCashedNext) {
                        curr = cashedNext ?: throw Exception("next at the end of sequence")
                        hasCashedNext = false
                    } else {
                        curr = curr.oneStep() ?: throw Exception("next at the end of sequence")
                    }
                    
                    return curr
                }
                
            }
        }
        
        /** Finds first with genericContext of type T.
         * @return a pair of found realized context and its genericContext, casted to T*/
        inline fun <reified T : GenericContext> goTyped(): Pair<Context, T>? {
            return asSequence()
                    .firstOrNull { it.gContext is T }
                    ?.let { Pair(it, it.gContext as T) }
        }
        
        
        inline fun <reified T : GenericContext> asTypedSequence() =
                asSequence()
                        .filter { it.gContext is T }
                        .map { Pair(it, it.gContext as T) }
    }
    
    
    abstract class StepLeaf(override val ctx: ContextLeaf) : Step(ctx) {
        
        /**One step in a given direction.*/
        protected abstract override fun Context.oneStep(): ContextLeaf?
        
        /** public wrapper of [oneStep]. Returns [ContextLeaf] of next step.*/
        override fun go(): ContextLeaf? = (super.go() as ContextLeaf?)
                .also { log?.println("after StepLeaf: ${this::class.java.simpleName}: ${ctx} to $it") }
        
        
        override fun asSequence() = super.asSequence().map { it as ContextLeaf }
        
        /** Wrapper for [goTyped], returning a pair [Context], casted to [ContextLeaf]*/
        inline fun <reified T : GenericContext> goTypedLeaf(): Pair<ContextLeaf, T>? =
                goTyped<T>()?.let { Pair(it.first as ContextLeaf, it.second) }
        
        
        /** Wrapper for [asTypedSequence], returning a pair [Context], casted to [ContextLeaf]*/
        inline fun <reified T : GenericContext> asTypedSequenceLeaves() =
                asTypedSequence<T>().map { Pair(it.first as ContextLeaf, it.second) }
    }
    
    override fun toString(): String {
        return element.toString()
    }
}

class ContextLeaf(override val element: ElementLeaf) : Context(element) {
    val leftLeafStep: StepLeaf get() = LeftLeafStep(this)
}

class ContextNode(override val element: ElementNode) : Context(element) {
    
}



