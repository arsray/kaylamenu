package com.arsray.kaylamenu.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arsray.kaylamenu.data.db.AppDatabase
import com.arsray.kaylamenu.data.model.Recipe
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// 这些是通用调料，不适合作为"想吃XX"的搜索起点
private val CONDIMENT_EXCLUDE = setOf(
    // 基础调味
    "盐", "糖", "白糖", "红糖", "冰糖", "白砂糖",
    "酱油", "生抽", "老抽", "料酒", "醋", "陈醋", "白醋", "米醋", "香醋",
    // 油脂
    "油", "食用油", "花生油", "菜籽油", "菜油", "色拉油", "橄榄油",
    "香油", "芝麻油", "猪油", "黄油", "奶油",
    // 葱姜蒜（调味用途）
    "葱", "大葱", "小葱", "葱花", "葱段", "葱白",
    "姜", "生姜", "姜片", "姜末", "姜丝", "老姜",
    "蒜", "大蒜", "蒜末", "蒜片", "蒜瓣", "蒜泥", "蒜头",
    // 辣椒类调味
    "干辣椒", "小米辣", "辣椒粉", "辣椒面", "花椒粉",
    // 香料
    "花椒", "八角", "桂皮", "香叶", "草果", "丁香", "小茴香",
    "五香粉", "十三香", "胡椒粉", "白胡椒粉", "黑胡椒粉",
    // 酱料
    "蚝油", "耗油", "豆瓣酱", "郫县豆瓣", "番茄酱", "甜面酱",
    "黄豆酱", "沙茶酱", "芝麻酱", "海鲜酱",
    // 增鲜/增稠
    "鸡精", "味精", "鸡粉", "淀粉", "玉米淀粉", "生粉", "水淀粉",
    // 其他辅料
    "清水", "水", "高汤", "小苏打", "泡打粉",
    "白芝麻", "黑芝麻", "熟芝麻", "芝麻",
    "香菜",   // 多作点缀，非主料
    "葱花",
)

data class IngredientSearchUiState(
    val topIngredients: List<String> = emptyList(),
    val searchText: String = "",
    val activeIngredient: String = "",   // 当前选中/输入的食材
    val results: List<Recipe> = emptyList(),
    val isSearching: Boolean = false,
)

@OptIn(FlowPreview::class)
class IngredientSearchViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).recipeDao()

    private val _state = MutableStateFlow(IngredientSearchUiState())
    val state: StateFlow<IngredientSearchUiState> = _state.asStateFlow()

    // 搜索文本流，加防抖
    private val _searchText = MutableStateFlow("")

    init {
        loadTopIngredients()

        // 文本输入 300ms 防抖后触发搜索
        viewModelScope.launch {
            _searchText
                .debounce(300)
                .distinctUntilChanged()
                .collect { text ->
                    if (text.isBlank()) {
                        _state.update { it.copy(activeIngredient = "", results = emptyList(), isSearching = false) }
                    } else {
                        searchByText(text)
                    }
                }
        }
    }

    fun onSearchTextChange(text: String) {
        _searchText.value = text
        _state.update { it.copy(searchText = text) }
    }

    /** 点击热门 Chip */
    fun selectIngredient(name: String) {
        _state.update { it.copy(searchText = "", activeIngredient = name) }
        _searchText.value = ""
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            try {
                val results = dao.recipesByIngredientQuery(name)
                _state.update { it.copy(results = results, activeIngredient = name, isSearching = false) }
            } catch (_: Exception) {
                _state.update { it.copy(isSearching = false) }
            }
        }
    }

    fun clearSearch() {
        _searchText.value = ""
        _state.update { it.copy(searchText = "", activeIngredient = "", results = emptyList()) }
    }

    private fun loadTopIngredients() {
        viewModelScope.launch {
            try {
                val all = dao.ingredientsByFrequency()
                val top = all
                    .filter { it.name !in CONDIMENT_EXCLUDE }
                    .take(24)
                    .map { it.name }
                _state.update { it.copy(topIngredients = top) }
            } catch (_: Exception) {}
        }
    }

    private suspend fun searchByText(text: String) {
        _state.update { it.copy(isSearching = true, activeIngredient = text) }
        try {
            val results = dao.recipesByIngredientQuery(text)
            _state.update { it.copy(results = results, isSearching = false) }
        } catch (_: Exception) {
            _state.update { it.copy(isSearching = false) }
        }
    }
}
