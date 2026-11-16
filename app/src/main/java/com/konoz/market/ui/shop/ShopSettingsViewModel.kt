package com.konoz.market.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.entity.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ShopMessage { SAVED, NAME_TAKEN, BLANK }

data class ShopUi(
    val shopName: String = "",
    val newCategory: String = "",
    val renaming: Category? = null,
    val renameText: String = "",
    val removing: Category? = null,
    val message: ShopMessage? = null,
)

/** Shop settings: the shop name printed on invoices, and the owner-editable product categories. */
class ShopSettingsViewModel(private val app: KonozApp) : ViewModel() {
    private val manager = CategoryManager(app.database)
    private val _ui = MutableStateFlow(ShopUi(shopName = ShopProfile.name(app).orEmpty()))
    val ui: StateFlow<ShopUi> = _ui.asStateFlow()

    val categories: StateFlow<List<Category>> = app.productRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onShopName(v: String) = _ui.update { it.copy(shopName = v.take(ShopProfile.MAX_LENGTH), message = null) }

    fun saveShopName() {
        ShopProfile.setName(app, _ui.value.shopName)
        _ui.update { it.copy(message = ShopMessage.SAVED) }
    }

    fun onNewCategory(v: String) = _ui.update { it.copy(newCategory = v, message = null) }

    fun addCategory() {
        val name = _ui.value.newCategory
        viewModelScope.launch {
            when (manager.add(name)) {
                CategoryResult.OK -> _ui.update { it.copy(newCategory = "", message = null) }
                CategoryResult.BLANK -> _ui.update { it.copy(message = ShopMessage.BLANK) }
                CategoryResult.NAME_TAKEN -> _ui.update { it.copy(message = ShopMessage.NAME_TAKEN) }
            }
        }
    }

    fun startRename(c: Category) = _ui.update { it.copy(renaming = c, renameText = c.name, message = null) }
    fun onRenameText(v: String) = _ui.update { it.copy(renameText = v) }
    fun cancelRename() = _ui.update { it.copy(renaming = null) }

    fun confirmRename() {
        val c = _ui.value.renaming ?: return
        val text = _ui.value.renameText
        viewModelScope.launch {
            when (manager.rename(c, text)) {
                CategoryResult.OK -> _ui.update { it.copy(renaming = null) }
                CategoryResult.BLANK -> _ui.update { it.copy(message = ShopMessage.BLANK) }
                CategoryResult.NAME_TAKEN -> _ui.update { it.copy(message = ShopMessage.NAME_TAKEN) }
            }
        }
    }

    fun askRemove(c: Category) = _ui.update { it.copy(removing = c) }
    fun cancelRemove() = _ui.update { it.copy(removing = null) }

    fun confirmRemove() {
        val c = _ui.value.removing ?: return
        viewModelScope.launch {
            manager.remove(c)
            _ui.update { it.copy(removing = null) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ShopSettingsViewModel(this[APPLICATION_KEY] as KonozApp) }
        }
    }
}
