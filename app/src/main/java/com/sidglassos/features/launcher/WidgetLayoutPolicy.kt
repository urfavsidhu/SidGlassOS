package com.sidglassos.features.launcher

data class HostedWidget(val id: Int, val heightDp: Int = 220)

object WidgetLayoutPolicy {
    const val MAX_WIDGETS = 12
    fun normalize(items: List<HostedWidget>): List<HostedWidget> = items
        .filter { it.id > 0 }.distinctBy { it.id }.take(MAX_WIDGETS)
        .map { it.copy(heightDp = it.heightDp.coerceIn(48, 1200)) }

    fun height(requested: Int, minimum: Int, maximum: Int): Int {
        val low = minimum.coerceIn(48, 1200)
        val high = maximum.coerceIn(low, 1200)
        return requested.coerceIn(low, high)
    }

    fun move(items: List<HostedWidget>, id: Int, delta: Int): List<HostedWidget> {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return items
        val target = (index + delta.coerceIn(-1, 1)).coerceIn(0, items.lastIndex)
        return items.toMutableList().apply { add(target, removeAt(index)) }
    }
}
