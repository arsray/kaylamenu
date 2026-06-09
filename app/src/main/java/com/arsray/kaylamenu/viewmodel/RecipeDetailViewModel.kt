package com.arsray.kaylamenu.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arsray.kaylamenu.data.db.AppDatabase
import com.arsray.kaylamenu.data.model.Ingredient
import com.arsray.kaylamenu.data.model.Recipe
import com.arsray.kaylamenu.data.model.Step
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Success(
        val recipe: Recipe,
        val ingredients: List<Ingredient>,
        val steps: List<Step>,
    ) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

class RecipeDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).recipeDao()

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun load(recipeId: Int) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                val recipe = dao.getById(recipeId)
                if (recipe == null) {
                    _uiState.value = DetailUiState.Error("找不到菜谱")
                    return@launch
                }
                val ingredients = dao.ingredientsByRecipeId(recipeId)
                val steps = dao.stepsByRecipeId(recipeId)
                _uiState.value = DetailUiState.Success(recipe, ingredients, steps)
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(e.message ?: "加载失败")
            }
        }
    }
}
