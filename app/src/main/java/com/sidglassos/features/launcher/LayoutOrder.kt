package com.sidglassos.features.launcher

/** Stable component IDs, rather than labels, keep duplicate app names independent. */
object LayoutOrder {
    fun move(ids: List<String>, itemId: String, targetId: String): List<String> {
        val items = ids.distinct().toMutableList()
        val from = items.indexOf(itemId)
        val to = items.indexOf(targetId)
        if (from < 0 || to < 0 || from == to) return items
        items.removeAt(from)
        items.add(to, itemId)
        return items
    }

    fun moveBy(ids: List<String>, itemId: String, delta: Int): List<String> {
        val items = ids.distinct()
        val index = items.indexOf(itemId)
        if (index < 0 || items.isEmpty()) return items
        return move(items, itemId, items[(index + delta).coerceIn(0, items.lastIndex)])
    }

    fun search(apps: List<LaunchableApp>, query: String): List<LaunchableApp> {
        val term = query.trim()
        if (term.isEmpty()) return apps
        return apps.filter { it.label.contains(term, ignoreCase = true) || it.packageName.contains(term, ignoreCase = true) }
    }
}
