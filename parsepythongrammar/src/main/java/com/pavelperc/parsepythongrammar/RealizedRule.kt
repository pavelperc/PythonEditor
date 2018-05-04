package com.pavelperc.parsepythongrammar

import com.pavelperc.parsepythongrammar.GenericRule.*

/**
 * Реализация конкретного правила для какого-то кода на питоне.
 *
 * Подклассы - нестатические враперы для статических классов GenericRule.
 *
 * Created by pavel on 29.12.2017.
 */
open class RealizedRule(
        val gRule: GenericRule,
        /** Map со всеми правилами грамматики.*/
        val ruleMap: Map<String, GenericRule>,
        fatherElement: Element?
) {
    val ruleAlteration: Alteration
    
    /** глубина вложенности правил  */
    private val rulesDepth: Int

//    val father: RealizedRule?
//        get() = ruleAlteration.father?.thisRule
    
    
    init {
        rulesDepth = (fatherElement?.thisRule?.rulesDepth ?: -1) + 1
        if (rulesDepth > 2000)
            throw RuntimeException("Too deep recursion in rule " + gRule.id)
        
        // построение всего правила
        ruleAlteration = Alteration(gRule.gAlteration, fatherElement)
    }
    
    
    fun toGv(graph: Graph, onlyChosen: Boolean) {
        val me = graph.newNode()
        me.label = gRule.id
        if (onlyChosen) {
            ruleAlteration.chosen?.repetitions?.forEach {
                it.toGv(graph, me, onlyChosen)
            }
            return
        }
        
        // alt node
        val node = graph.newNode()
        
        node.label = "alt"
        
        val edge = graph.newEdge(me, node)
        if (!ruleAlteration.isChosen) {
            edge.style = "dotted"
        }
        
        ruleAlteration.toGv(graph, node, onlyChosen)
    }
    
    
    inner class Alteration(
            val gAlteration: GenericAlteration,
            /** null for root*/
            var father: Element?
    ) {
        var chosen: Concatenation? = null
        val concatenations: List<Concatenation>
        
        
        /** выбран ли вариант среди альтернатив */
        val isChosen: Boolean
            get() = chosen != null
        
        init {
            // создаём concatenations
            concatenations = gAlteration.gConcatenations.mapIndexed { index, gc ->
                Concatenation(gc, this, index)
            }
            
            //            // автоматический выбор происходит в конструкторе - НЕЛЬЗЯ!!!
            //            if (concatenations.size() == 1) {
            //                chosen = concatenations.get(0);
            //            }
        }
        
        /** Позволяет выбрать определённое concatenation как заполненное */
        fun choose(positionInFather: Int) {
            chosen = concatenations[positionInFather]
        }
        
        
        fun toGv(graph: Graph, me: GVNode, onlyChosen: Boolean) {
            
            for (conc in concatenations) {
                if (onlyChosen && conc != chosen)
                    continue
                
                // conc node
                val node = graph.newNode()
                node.label = "conc"
                val edge = graph.newEdge(me, node)
                
                
                if (conc !== chosen) {
                    edge.style = "dotted"
                }
                
                conc.toGv(graph, node, onlyChosen)
            }
        }
    }
    
    
    inner class Concatenation(
            val gConcatenation: GenericConcatenation,
            var father: Alteration,
            var positionInFather: Int
    ) {
        val repetitions: List<Repetition>
        
        /**
         * Поверка может ли текущая ветвь быть пропущена.
         * То есть в ней ничего не реализовано и все элементы внутри могут быть пропущены.
         */
        val isOptional: Boolean
            get() = repetitions.all { it.isOptional }
        
        init {
            repetitions = gConcatenation.gRepetitions.mapIndexed { index, gr ->
                Repetition(gr, this, index)
            }
        }
        
        
        /**
         * Вызываем [Element.findAlternativesDown] у самого левого element.
         */
        fun findAlternativesDown(): List<ElementLeaf> {
            val ans = mutableListOf<ElementLeaf>()
            for (repetition in repetitions) {
                
                if (repetition.isFull) break
                
                ans.addAll(repetition.createEmptyElement(0).findAlternativesDown())
                
                //  realizedElements.size > 0 уже входит в isOptional
                if (!repetition.isOptional) break
            }
            return ans
        }
        
        fun toGv(graph: Graph, me: GVNode, onlyChosen: Boolean) {
            for (rep in repetitions) {
                
                if (onlyChosen && rep.realizedElements.size == 0)
                    continue
                
                // rep node
                val node = graph.newNode()
                node.label = "rep" + rep.positionInFather
                if (rep.isMult)
                    node.label += "*"
                else if (rep.isPlus)
                    node.label += "+"
                
                
                val edge = graph.newEdge(me, node)
                
                if (rep.realizedElements.size == 0) {
                    edge.style = "dotted"
                }
                
                rep.toGv(graph, node, onlyChosen)
            }
        }
    }
    
    inner class Repetition(
            val gRepetition: GenericRepetition,
            var father: Concatenation,
            var positionInFather: Int
    ) {
        val realizedElements = mutableListOf<Element>()
        
        private val emptyElement: Element? = null
        
        /** Сохранённая ссылка на последний созданный пустой элемент. */
        private var lastCreatedEmptyElement: Element? = null
        
        val isNone: Boolean
            get() = gRepetition.isNone
        
        val isPlus: Boolean
            get() = gRepetition.isPlus
        
        val isMult: Boolean
            get() = gRepetition.isMult
        
        /** Проверка, что в текущее repetition нельзя добавить элементы. */
        val isFull: Boolean
            get() = isNone && realizedElements.size > 0
        
        /** Количество реализованных повторов.
         * По умолчанию - 0, так как элемент ещё не выбирался. */
        val repeatNumber: Int
            get() = realizedElements.size
        
        /** Возвращает либо новый пустой элемент либо первый элемент среди детей  */
        val firstElement: Element
            get() = if (realizedElements.size > 0) {
                realizedElements[0]
            } else {
                createEmptyElement(0)
            }
        
        /**
         * Поверка может ли текущая ветвь быть пропущена.
         * То есть в ней ничего не реализовано и все элементы внутри могут быть пропущены.
         */
        val isOptional: Boolean
            get() =
            // проверка что ничего не реализовано
                if (realizedElements.size > 0)
                    false
                // отбрасываем случаи для элемента с mult или опции, 
                // не создавая при этом новый пустой элемент
                else if (isMult || gRepetition.gElement.isOption)
                    true
                else // уходим в рекурсию по новому пустому элементу.
                    createEmptyElement(0).isOptional
        
        
        /** Создаёт новый пустой элемент и сохраняет его в lastCreatedEmptyElement.
         * Если элемент в данной позиции уже сохранён, возвращаем его. */
        fun createEmptyElement(desiredPosInFather: Int): Element {
            // проверка что мы можем создать ещё однин элемент
            if (isFull)
                throw UnsupportedOperationException("Can not create another element. The repetition is Full.")
            
            
            // проверяем, что сохранённое значение не подходит и создаём новое.
            if (lastCreatedEmptyElement == null || !lastCreatedEmptyElement!!.isEmpty || lastCreatedEmptyElement!!.positionInFather != desiredPosInFather) {
//                lastCreatedEmptyElement = Element(gRepetition.gElement, this, desiredPosInFather, true)
                
                if (gRepetition.gElement is GenericElementNode) {// group or option
                    lastCreatedEmptyElement = ElementNode(gRepetition.gElement, this, desiredPosInFather, false)
                } else if (gRepetition.gElement.isParserRuleId(ruleMap)) {// parser rule
                    lastCreatedEmptyElement = ElementNode(gRepetition.gElement, this, desiredPosInFather, true)
                } else {// lexer rule or string constant
                    lastCreatedEmptyElement = ElementLeaf(gRepetition.gElementLeaf, this, desiredPosInFather)
                }
            }
            
            return lastCreatedEmptyElement!!
        }
        
        /**
         * Проверяет, что все правые repetition в concatenation являются optional.
         * И кладёт все пустые элементы до первого не optional включительно в список.
         * Текущее repetition не обязательно должно быть optional.
         * @param alternatives Сюда добавляются все листы из пустых веток элементов до первой не optional включительно.
         */
        fun isRightOptional(alternatives: MutableList<ElementLeaf>): Boolean {
            for (i in positionInFather + 1 until father.repetitions.size) {
                val rep = father.repetitions[i]
                if (rep.isOptional) {// добавляем новый нулевой элемент из  rep
                    // добавляются не все дети у rep??? - нет, всё нормально. У rep только один opt el может быть
                    alternatives.addAll(rep.createEmptyElement(0).findAlternativesDown())
                } else {
                    // создаём пустое repetition, если можем (или берём уже созданный не optional, но обязательно empty)
                    if (!rep.isFull) {
                        alternatives.addAll(rep.createEmptyElement(0).findAlternativesDown())
                    }
                    return false
                }
            }
            return true
        }
        
        /** Вставляет новый элемент в список. */
        fun insertNewRealized(element: Element, positionInFather: Int) {
            realizedElements.add(positionInFather, element)
        }
        
        
        private fun drawGvElement(
                element: Element,
                graph: Graph,
                me: GVNode,
                onlyChosen: Boolean
        ) {
            // el node
            val node = graph.newNode()
            val edge = graph.newEdge(me, node)
            
            
            node.label = element.gElement.toString()
//            if (element.gElement.isGroup) {
//                node.label = "group"
//            } else if (element.gElement.isOption) {
//                node.label = "option"
//            } else if (element.gElement is GenericElementLeaf) {
//                node.label = (element.gElement as GenericElementLeaf).text
//            }
            
            node.shape = "box"
            
            if (element is ElementNode && element.isParserRuleId) {
                node.color = "blue"
            }
            
            
            if (!element.isEmpty && element is ElementLeaf) {
                node.fillColor = "orange"
            }
            
            if (element.isEmpty) {
                edge.style = "dotted"
            }
            
            //            if (!element.isEmpty) {
            //                element.toGv(nodes, edges, elId);
            //            }
            
            
            element.toGv(graph, node, onlyChosen)
        }
        
        fun toGv(graph: Graph, me: GVNode, onlyChosen: Boolean) {
            // для начала рисуем пустой элемент
            if (!onlyChosen && !isFull && lastCreatedEmptyElement != null && lastCreatedEmptyElement!!.isEmpty) {
                //                createEmptyElement(0);
                drawGvElement(lastCreatedEmptyElement!!, graph, me, onlyChosen)
            }
            
            
            for (element in realizedElements) {
//                if (onlyChosen && element.isEmpty)
//                    continue
                drawGvElement(element, graph, me, onlyChosen)
            }
        }
    }
    
    
    abstract inner class Element
    (
            open val gElement: GenericElement,
            var father: Repetition,
            /** Если элемент помечен как empty у родителя - то позиция всего лишь желаемая. */
            var positionInFather: Int,
            /** true означает, что элемент считается пустым и не лежит в списке детей родителя. */
            var isEmpty: Boolean = true) {

//        /** означает, что для данного элемента нельзя использовать getAlterations. То есть это лист.  */
//        val isSimple: Boolean
//            get() = !isCompound
//        
//        /** означает, что для данного элемента можно использовать getAlterations. То есть это не лист.  */
//        val isCompound: Boolean
//            get() = gElement.isGroup || gElement.isOption || isParserRuleId()


//        /** Fast cast to leaf*/
//        val asLeaf: ElementLeaf
//            get() = this as ElementLeaf
//    
//        /** Fast cast to node*/
//        val asNode: ElementNode
//            get() = this as ElementNode
        
        
        abstract val context: Context
        
        val gContext: GenericContext?
            get() = gElement.gContext
        
        /** father.father.father.father*/
        val fatherElement: Element?
            get() = father.father.father.father
        
        
        val realizedLeaves: Sequence<ElementLeaf>
            get() =
                if (!isRealized)
                    listOf<ElementLeaf>().asSequence()
                else if (this is ElementLeaf)
                    listOf(this).asSequence()
                else if (this is ElementNode)
                    this.alteration.chosen?.repetitions?.asSequence()?.flatMap {
                        it.realizedElements.asSequence().flatMap { it.realizedLeaves }
                    } ?: listOf<ElementLeaf>().asSequence()
                else
                    throw Exception("Unreachable")
        
        
        /**
         * Поверка может ли текущая ветвь быть пропущена.
         * То есть в ней ничего не реализовано и все элементы внутри могут быть пропущены.
         */
        val isOptional: Boolean
            get() =
                if (isRealized) {
                    false
                } else if (gElement.checkedNonOptional) {
                    false
                }
                // if we are unrealized option[] or we are in rep* or non-empty rep+
                else if (gElement.isOption
                        || father.realizedElements.size > 0 && father.isPlus
                        || father.isMult) {
                    true
                }
                // go in recursion if we are not a leaf
                else if (this is ElementNode) {
                    // at least 1 concatenation should be optional
                    val ans = this.alteration.concatenations.any { it.isOptional }
                    
                    // if recursion gave us false and WE ARE NOT IN EMPTY REP+
                    // we can remember at the level of gElement, that we are not optional
                    if (!ans && !(father.isPlus && father.realizedElements.size == 0)) {
                        gElement.checkedNonOptional = true
                    }
                    ans
                } else {
                    // we are unrealized leaf
                    false
                }.also { bool ->
                    log?.println("isOptional is $bool for $gElement")
                }
        
        /** Проверка есть ли в ветви с текущем элементом хоть один реализованный лист. (включая себя)  */
        // проверка через детей:
        //            if (isSimple()) {
        //                return realizedToken != null;
        //            } else {
        //                return getAlteration().isChosen();// TODO не будет работать для множественного current
        //            }
        // проверка через empty:
        val isRealized: Boolean
            get() = !isEmpty
        
        //        /** Возвращает либо список реализованных alteration из дочерних rule, 
        //         * либо поле alterations (для group и option).
        //         * В список добавлять/удалять что-то бессмысленно, так как он может быть локальной копией.*/
        //        List<Alteration> getAlterations() {
        //            if (isParserRuleId())
        //                return getRules().stream().map(r -> r.alteration).collect(Collectors.toList());
        //        
        //            if (isSimple())
        //                throw new NullPointerException("Can not get alterations from simple Element like STRING or lexer rule");
        //        
        //            return alterations;
        //        }
        
        /** Ссылка на текущий объект RealizedRule. */
        val thisRule: RealizedRule
            get() = this@RealizedRule
        
        /**
         * Если элемент простой (лист) - то возвращаем себя.
         * Если сложный - то ищем самый левый лист внизу.
         */
        val deepFirstLeafDown: ElementLeaf
            get() = if (this is ElementLeaf)
                this
            else if (this is ElementNode)
                alteration.concatenations[0].repetitions[0].firstElement.deepFirstLeafDown
            else
                throw Exception("Unreachable")


//        // Saving state: -1 unset 0 false 1 true. because enum is unavailable here(
//        private var isParserRuleId = -1
//        
//        /** обёртка для [GenericElement.isParserRuleId] с сохранением состояния. (спрашивается один раз)*/
//        fun isParserRuleId(): Boolean {
//            if (isParserRuleId == -1) {
//                isParserRuleId = if (gElement.isParserRuleId(ruleMap)) 1 else 0
//            }
//            return isParserRuleId == 1
//        }
        
        /**
         * Проверка, что мы последний элемент в repetition.
         * Текущий element не обязательно должен быть optional.
         * @param alternatives Сюда добавляется либо следующий не empty либо следующий empty
         */
        fun isRightOptional(alternatives: MutableList<ElementLeaf>): Boolean {
            
            if (!isEmpty) {
                // добавляем новый пустой element справа если можно
                if (!father.isFull) {
                    alternatives.addAll(father.createEmptyElement(positionInFather + 1).findAlternativesDown())
                }
                
                // если мы последний реализованный
                if (positionInFather == father.realizedElements.size - 1) {
                    return true
                }
            }
            // если мы последний нереализованный
            val ans = isEmpty && positionInFather == father.realizedElements.size
            log?.println("in isRightOptional in $this: I am empty, return $ans")
            return ans
            
        }
        
        
        //        /**
        //         * Получение хотя бы частично реализованных (не пустых) дочерних правил для parserRuleId.
        //         */
        //        List<RealizedRule> getRules() {
        //            if (!isParserRuleId())
        //                throw new NullPointerException("Can not find child rules for not parserRuleId");
        //            
        //            return rules;
        //        }
        
        
        /**
         * Ищет все доступные для выбора простые элементы правее текущего.
         *
         * Если [wantAlternatives] true - то по умолчанию считаем текущий элемент optional.
         * А если false, то проверяем текущий элемент на [isOptional], когда хотим искать conc справа.
         *
         * True нужен, например, если мы rightOptional, слева от нас есть реализованные, и мы идём по рекурсии наверх.
         *
         * False нужен, чтобы мы не добавляли следующие conc, если мы не optional:
         * Например мы стоим в первом не optional conc.
         * @return список всех элементов вместе с текущим, если он простой
         */
        fun findAlternativesUp(wantAlternatives: Boolean): List<ElementLeaf> {
            //            System.out.println("findAlternativesUp(" + wantAlternatives + ") in " + gElement + " : " + getThisRule().gRule.id);
            log?.println("findAlternativesUp($wantAlternatives) in $gElement")
            
            val alternatives = mutableListOf<ElementLeaf>()
            
            // если мы начали с листа - то добавляем его
            if (this is ElementLeaf) {
                alternatives.add(this)
            }
            
            
            // Текущий элемент и все элементы правее - optional
            var rightOptional = false
            
            // если мы можем пропустить текущий element и искать альтернативы
            
            if (wantAlternatives || isOptional) {
                // проверка, что все, кто справа - optional
                // проходим вниз по всем правым rep и conc если нужно
                rightOptional = isRightOptional(alternatives) && father.isRightOptional(alternatives)
            }
            
            // если мы первые нереализованные в rep и rep первый в conc
            // то проходим по  всем alteration
            if (positionInFather == 0 && isEmpty && father.positionInFather == 0) {
                log?.println("findAlternativesUp: check alterations")
                
                for (conc in father.father.father.concatenations) {
                    if (conc === father.father)
                        continue
                    alternatives.addAll(conc.findAlternativesDown())
                }
            }
            
            
            // рекурсия вверх
            fatherElement?.apply {
                if (rightOptional) {
                    // не проверяем, что мы optional
                    alternatives.addAll(findAlternativesUp(true))
                } else {
                    // проверяем, что мы optional
                    alternatives.addAll(findAlternativesUp(false))
                }
            }
            
            return alternatives
        }
        
        /**
         * Если элемент простой (лист) - то возвращаем список из себя.
         * Если сложный - то ищем все подходящие листы внизу.
         */
        fun findAlternativesDown(): List<ElementLeaf> {
            log?.println("findAlternativesDown in " + gElement + " : " + thisRule.gRule.id)
            
            if (this is ElementLeaf) {
                return listOf(this)
            } else if (this is ElementNode) {// если сложный, то идём по всем conc у alt и собираем у них
                return alteration.concatenations.flatMap {
                    it.findAlternativesDown()
                }
            } else {
                throw Exception("Unreachable")
            }
        }
        
        /** Обновляет все значения выбранных alt, rep count, если текущий элемент выбран  */
        fun updateAllChosen() {
            log?.println("updateAllChosen in $gElement")
            if (isEmpty) {// если rep не выбран - выбираем
                father.insertNewRealized(this, positionInFather)
                isEmpty = false
                
                
                if (!father.father.father.isChosen) {// если alt не выбран - выбираем и уходим в рекурсию
                    father.father.father.choose(father.father.positionInFather)
                    // если мы не корень - уходим в рекурсию
                    fatherElement?.updateAllChosen()
                }
            }
        }
        
        /** Ищет первый(самый левый) следующий элемент, который может быть выбран после этого.
         * Если такого элемента нет - возвращает null  */
        fun findNewCurrent(): ElementLeaf? {
            
            if (!isRealized)
                throw Exception("tried to find new current from not realized element. (findAlternatives before build)")


//            log?.println("in findNewCurrent in $gElement");
            // если у отца элемента (необязательно листа) есть * или +
            // возвращаем новый пустой элемент справа от себя
            if (!father.isFull) {
                return father.createEmptyElement(positionInFather + 1).deepFirstLeafDown
            }
            
            // если справа есть продолжение у concatenation
            if (father.positionInFather < father.father.repetitions.size - 1) {
                // правое rep
                val rep = father.father.repetitions[father.positionInFather + 1]
                //                System.out.println("found new rep: " + rep.gRepetition);
                val ans = rep.firstElement.deepFirstLeafDown
                // если следующий правый элемент реализован - мы ничего больше не ищем и возвращаем null
                return if (ans.isRealized) null else ans
            }
            
            // если справа ничего не получается вставить - идём наверх
            return fatherElement?.findNewCurrent()
        }
        
        override fun toString() =
                if (gElement is GenericElementLeaf && gElement.isString)
                    (gElement as GenericElementLeaf).text// выводим STRING без одинарных кавычек
                else
                    gElement.toString()
        
        
        fun toGv(graph: Graph, me: GVNode, onlyChosen: Boolean) {
            if (this !is ElementNode)
                return
            
            if (onlyChosen) {
                alteration.chosen?.repetitions?.forEach {
                    // пропускаем alt и conc и добавляем сразу все реализованные conc
                    it.toGv(graph, me/*!!!*/, onlyChosen)
                }
                return
            }
            
            // alt node
            val node = graph.newNode()
            node.label = "alt"
            
            val edge = graph.newEdge(me, node)
            
            if (isEmpty) {
                edge.style = "dotted"
            }
            
            
            alteration.toGv(graph, node, onlyChosen)
            
        }
    }
    
    
    /** STRING const or lexer rule*/
    inner class ElementLeaf(
            override val gElement: GenericElementLeaf,
            father: Repetition,
            positionInFather: Int
    ) :
            Element(gElement, father, positionInFather), ButtonContent {
        
        
        override val context: ContextLeaf = ContextLeaf(this)
        
        
        override val nameForButton: String
            get() = gElement.text
        
        override val groupingTagForButton: GroupingTag
            get() = gElement.groupingTag
        
        /** Подходящая string или выбранное значения для lexer id.
         * То есть отдельное слово созданной программы на питоне.  */
        var realizedToken: String? = null
            get() =
                if (gElement.isString)
                    gElement.text
                else
                    field
            set(value) {
                if (value == null)
                    throw Exception("can not assign null to realizedToken.")
                
                if (gContext is GenericContextId
                        && !(gContext as GenericContextId).checkPattern(value))
                    throw Exception("invalid token")
                
                field = value
            }
        
        
//        var onUpdateRealizedToken: (()->Unit)? = null
        
    }
    
    
    /** GROUP, OPTION or parser rule*/
    inner class ElementNode(
            gElement: GenericElement,
            father: Repetition,
            positionInFather: Int,
            val isParserRuleId: Boolean
    ) :
            Element(gElement, father, positionInFather) {
    
        override val context = ContextNode(this)
    
        val alteration: Alteration
        private val rule: RealizedRule?
        
        
        init {
            // создаём дочернее alteration
            if (gElement is GenericElementNode) {
                alteration = Alteration(gElement.gAlteration, this)
                rule = null
            } // создаём дочернее rule
            else if (gElement is GenericElementLeaf && isParserRuleId) {
                // ленивой подгрузкой будем заниматься в repetition
                rule = RealizedRule(ruleMap[gElement.text]!!, ruleMap, this)
                alteration = rule.ruleAlteration
            } else {
                throw Exception("Unreachable")
            }
        }
    }
}