package com.arsray.kaylamenu.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arsray.kaylamenu.data.db.AppDatabase
import com.arsray.kaylamenu.data.model.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI 状态 ────────────────────────────────────────────────────────────────────

data class DayMenu(
    val dayIndex: Int,           // 1-based 显示序号
    val dishes: List<Recipe>,    // 任意数量、任意分类
)

data class ShoppingItem(
    val name: String,
    val dishCount: Int,
    val dishNames: List<String>,    // 用到此食材的菜名，长按浮出
    val checked: Boolean = false,
)

sealed class MenuUiState {
    data object Loading : MenuUiState()
    data class Success(val days: List<DayMenu>) : MenuUiState()
    data class Error(val message: String) : MenuUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class MenuViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).recipeDao()

    private val _uiState = MutableStateFlow<MenuUiState>(MenuUiState.Loading)
    val uiState: StateFlow<MenuUiState> = _uiState.asStateFlow()

    private val _shoppingItems = MutableStateFlow<List<ShoppingItem>>(emptyList())
    val shoppingItems: StateFlow<List<ShoppingItem>> = _shoppingItems.asStateFlow()

    // ── 全量生成 ──────────────────────────────────────────────────────────────

    fun generateMenu(daysCount: Int) {
        viewModelScope.launch {
            _uiState.value = MenuUiState.Loading
            try {
                val meats = dao.randomByCategory("meat", daysCount)
                val vegs  = dao.randomByCategory("veg",  daysCount)
                val soups = dao.randomByCategory("soup", daysCount)

                if (meats.size < daysCount || vegs.size < daysCount || soups.size < daysCount) {
                    _uiState.value = MenuUiState.Error(
                        "菜谱数量不足（荤${meats.size}/素${vegs.size}/汤${soups.size}）"
                    )
                    return@launch
                }

                _uiState.value = MenuUiState.Success(
                    (0 until daysCount).map { i ->
                        DayMenu(
                            dayIndex = i + 1,
                            dishes = listOf(meats[i], vegs[i], soups[i])
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = MenuUiState.Error("生成失败：${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    // ── 天管理 ────────────────────────────────────────────────────────────────

    /** 在末尾增加一天（含荤/素/汤各一，最多 10 天） */
    fun addDay() {
        val current = _uiState.value as? MenuUiState.Success ?: return
        if (current.days.size >= 10) return

        viewModelScope.launch {
            try {
                val usedIds = current.days.flatMap { it.dishes }.map { it.id }.toSet()
                val meat = pickRandom("meat", usedIds) ?: return@launch
                val veg  = pickRandom("veg",  usedIds + meat.id) ?: return@launch
                val soup = pickRandom("soup", usedIds + meat.id + veg.id) ?: return@launch

                val newDay = DayMenu(
                    dayIndex = current.days.size + 1,
                    dishes = listOf(meat, veg, soup)
                )
                _uiState.value = current.copy(days = current.days + newDay)
            } catch (_: Exception) {}
        }
    }

    /** 删除某一天，剩余重新排序（最少保留 1 天） */
    fun removeDay(dayIndex: Int) {
        val current = _uiState.value as? MenuUiState.Success ?: return
        if (current.days.size <= 1) return

        val newDays = current.days
            .filter { it.dayIndex != dayIndex }
            .mapIndexed { i, day -> day.copy(dayIndex = i + 1) }
        _uiState.value = current.copy(days = newDays)
    }

    // ── 菜品管理 ──────────────────────────────────────────────────────────────

    /** 随机换掉某道菜（用 recipeId 定位，同分类替换） */
    fun swapDish(dayIndex: Int, recipeId: Int) {
        val current = _uiState.value as? MenuUiState.Success ?: return
        val oldRecipe = current.days.find { it.dayIndex == dayIndex }
            ?.dishes?.find { it.id == recipeId } ?: return

        viewModelScope.launch {
            try {
                val usedIds = current.days.flatMap { it.dishes }
                    .filter { it.category == oldRecipe.category }
                    .map { it.id }.toSet()

                val newRecipe = pickRandom(oldRecipe.category, usedIds) ?: return@launch
                updateDish(current, dayIndex, recipeId, newRecipe)
            } catch (_: Exception) {}
        }
    }

    /** 删除某道菜 */
    fun removeDish(dayIndex: Int, recipeId: Int) {
        val current = _uiState.value as? MenuUiState.Success ?: return
        _uiState.value = current.copy(
            days = current.days.map { day ->
                if (day.dayIndex != dayIndex) day
                else day.copy(dishes = day.dishes.filter { it.id != recipeId })
            }
        )
    }

    /** 在某一天添加一道随机菜（指定分类） */
    fun addDish(dayIndex: Int, category: String) {
        val current = _uiState.value as? MenuUiState.Success ?: return

        viewModelScope.launch {
            try {
                val usedIds = current.days.flatMap { it.dishes }
                    .filter { it.category == category }
                    .map { it.id }.toSet()

                val newRecipe = pickRandom(category, usedIds) ?: return@launch

                _uiState.value = current.copy(
                    days = current.days.map { day ->
                        if (day.dayIndex != dayIndex) day
                        else day.copy(dishes = day.dishes + newRecipe)
                    }
                )
            } catch (_: Exception) {}
        }
    }

    // ── 购物清单 ──────────────────────────────────────────────────────────────

    fun buildShoppingList() {
        val current = _uiState.value as? MenuUiState.Success ?: return
        viewModelScope.launch {
            try {
                val allDishes = current.days.flatMap { it.dishes }
                val recipeIds = allDishes.map { it.id }
                val titleById = allDishes.associate { it.id to it.title }

                val ingredients = dao.ingredientsByRecipeIds(recipeIds)

                // 食材名 → 去重的菜名集合
                val merged = mutableMapOf<String, MutableSet<String>>()
                ingredients
                    .filter { !it.name.startsWith("[噪音]") }
                    .forEach { ing ->
                        val title = titleById[ing.recipeId] ?: return@forEach
                        merged.getOrPut(ing.name) { mutableSetOf() }.add(title)
                    }

                _shoppingItems.value = merged.map { (name, titles) ->
                    ShoppingItem(
                        name = name,
                        dishCount = titles.size,
                        dishNames = titles.sorted()
                    )
                }.sortedBy { it.name }
            } catch (_: Exception) {}
        }
    }

    fun toggleShoppingItem(index: Int) {
        _shoppingItems.value = _shoppingItems.value.mapIndexed { i, item ->
            if (i == index) item.copy(checked = !item.checked) else item
        }
    }

    /** 直接将指定菜谱加入某天（按食材搜索后选定时使用） */
    fun addSpecificDish(dayIndex: Int, recipe: Recipe) {
        val current = _uiState.value as? MenuUiState.Success ?: return
        _uiState.value = current.copy(
            days = current.days.map { day ->
                if (day.dayIndex != dayIndex) day
                else day.copy(dishes = day.dishes + recipe)
            }
        )
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    private suspend fun pickRandom(category: String, excludeIds: Set<Int>): Recipe? {
        val available = dao.idsByCategory(category).filter { it !in excludeIds }
        if (available.isEmpty()) return null
        return dao.getById(available.random())
    }

    private fun updateDish(
        current: MenuUiState.Success,
        dayIndex: Int,
        oldId: Int,
        newRecipe: Recipe,
    ) {
        _uiState.value = current.copy(
            days = current.days.map { day ->
                if (day.dayIndex != dayIndex) day
                else day.copy(dishes = day.dishes.map { if (it.id == oldId) newRecipe else it })
            }
        )
    }
}
