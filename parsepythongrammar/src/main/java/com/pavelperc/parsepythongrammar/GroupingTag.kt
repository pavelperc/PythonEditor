package com.pavelperc.parsepythongrammar

data class GroupingTag(
        val tag: String,
        val color: Int,
        /** The lower value means the higher priority. (1 will be sorted first).*/
        val priority: Int
) : Comparable<GroupingTag> {
    /**
     * Firstly compares [priority] in ascending order.
     * 
     * Secondly compares string [tag] in alphabet order.
     * */
    override fun compareTo(other: GroupingTag) =
            if (priority == other.priority)
                tag.compareTo(other.tag)
            else
                priority.compareTo(other.priority)
    
    
    companion object {
        /*ltGray*/
        val defaultColor = -0x333334
        val defaultTag = GroupingTag("no_tag", defaultColor, 10)
    }
}