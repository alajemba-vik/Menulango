package com.menulango.data.menu

import com.menulango.data.menu.local.normalizeDishName
import com.menulango.data.menu.model.Dish
import com.menulango.data.menu.model.MenuMeta

/**
 * Joins the pages of one menu into a single menu.
 *
 * Pure, and recomputed from every page's dishes whenever a page grows, so the merged list is always
 * a function of what has been read — never of the order the updates happened to arrive in.
 */
internal object PageMerger {
    /**
     * Every page's dishes in page order. A dish printed on two pages — a facing-page repeat, a
     * specials insert — appears once, where it was first seen. Ids are made unique across pages,
     * since each page's slugs were chosen without seeing the others.
     */
    fun dishes(pages: List<List<Dish>>): List<Dish> = merge(pages).first

    /** Which page each merged dish was first printed on, counting from 1, by merged dish id. */
    fun pageOfDish(pages: List<List<Dish>>): Map<String, Int> = merge(pages).second

    private fun merge(pages: List<List<Dish>>): Pair<List<Dish>, Map<String, Int>> {
        val seenNames = mutableSetOf<String>()
        val seenIds = mutableSetOf<String>()
        val merged = mutableListOf<Dish>()
        val pageOf = mutableMapOf<String, Int>()
        for ((pageIndex, page) in pages.withIndex()) {
            for (dish in page) {
                val name = normalizeDishName(dish.originalName)
                if (name.isNotEmpty() && !seenNames.add(name)) continue
                var id = dish.id
                var suffix = 2
                while (!seenIds.add(id)) id = "${dish.id}-${suffix++}"
                merged += if (id == dish.id) dish else dish.copy(id = id)
                pageOf[id] = pageIndex + 1
            }
        }
        return merged to pageOf
    }

    /** What the pages agree the menu is: the first page that could tell wins each field. */
    fun meta(pages: List<MenuMeta>): MenuMeta =
        if (pages.isEmpty()) {
            MenuMeta.Unknown
        } else {
            MenuMeta(
                language = pages.firstNotNullOfOrNull { it.language },
                currency = pages.firstNotNullOfOrNull { it.currency },
                venueType = pages.firstNotNullOfOrNull { it.venueType },
                truncated = pages.any { it.truncated },
                confidence = pages.maxOf { it.confidence },
            )
        }
}
