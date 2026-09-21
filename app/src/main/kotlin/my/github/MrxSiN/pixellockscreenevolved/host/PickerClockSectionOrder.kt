package my.github.MrxSiN.pixellockscreenevolved.host

/** Pure ordering rules for this module's section in Wallpaper & style. */
internal object PickerClockSectionOrder {

    /**
     * Keeps every module clock together where the first module clock already
     * sits. Host clocks keep their relative order; module clocks follow
     * [styleIds], the module's single source of clock order.
     */
    fun <T> group(items: List<T>, styleIds: List<String>, keyOf: (T) -> String?): List<T> {
        val anchorId = styleIds.firstOrNull() ?: return items
        val styleOrder = styleIds.withIndex().associate { (index, id) -> id to index }
        val anchor = items.indexOfFirst { keyOf(it) == anchorId }
        if (anchor < 0) return items

        val moduleItems = items.filter { keyOf(it) in styleOrder }
            .sortedBy { styleOrder.getValue(requireNotNull(keyOf(it))) }
        if (moduleItems.size < 2) return items

        val hostItems = items.filterNot { keyOf(it) in styleOrder }
        val hostItemsBeforeAnchor = items.take(anchor).count { keyOf(it) !in styleOrder }
        return buildList(items.size) {
            addAll(hostItems.take(hostItemsBeforeAnchor))
            addAll(moduleItems)
            addAll(hostItems.drop(hostItemsBeforeAnchor))
        }
    }

    /** Divider after one contiguous module section, when a host clock follows it. */
    fun dividerIndex(keys: List<String?>, styleIds: Set<String>): Int? {
        val first = keys.indexOfFirst { it in styleIds }
        val last = keys.indexOfLast { it in styleIds }
        if (first < 0 || last >= keys.lastIndex) return null
        return last.takeIf { index -> (first..last).all { keys[it] in styleIds } }
    }
}
