package com.konoz.market.ui.shop

import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.Category

/** Result of adding or renaming a category. */
enum class CategoryResult { OK, BLANK, NAME_TAKEN }

/**
 * Owner-editable product categories. Categories are never hard-deleted (products keep pointing at them and the name
 * column is unique): "delete" hides the category from lists, and adding the same name again brings it back.
 */
class CategoryManager(private val db: KonozDatabase) {
    suspend fun add(name: String): CategoryResult {
        val n = name.trim()
        if (n.isEmpty()) return CategoryResult.BLANK
        return db.tx {
            val existing = db.categories().findByName(n)
            when {
                existing == null -> { db.categories().insert(Category(name = n, sortOrder = db.categories().count())); CategoryResult.OK }
                existing.isActive -> CategoryResult.NAME_TAKEN
                else -> { db.categories().update(existing.copy(isActive = true)); CategoryResult.OK }
            }
        }
    }

    suspend fun rename(category: Category, newName: String): CategoryResult {
        val n = newName.trim()
        if (n.isEmpty()) return CategoryResult.BLANK
        return db.tx {
            val other = db.categories().findByName(n)
            if (other != null && other.id != category.id) CategoryResult.NAME_TAKEN
            else { db.categories().update(category.copy(name = n)); CategoryResult.OK }
        }
    }

    suspend fun remove(category: Category) {
        db.tx { db.categories().update(category.copy(isActive = false)) }
    }
}
