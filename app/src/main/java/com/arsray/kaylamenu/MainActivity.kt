package com.arsray.kaylamenu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.arsray.kaylamenu.ui.screens.IngredientSearchScreen
import com.arsray.kaylamenu.ui.screens.MenuScreen
import com.arsray.kaylamenu.ui.screens.RecipeDetailScreen
import com.arsray.kaylamenu.ui.screens.ShoppingListScreen
import com.arsray.kaylamenu.ui.theme.KaylaMenuTheme
import com.arsray.kaylamenu.util.rememberNavAction
import com.arsray.kaylamenu.viewmodel.IngredientSearchViewModel
import com.arsray.kaylamenu.viewmodel.MenuViewModel
import com.arsray.kaylamenu.viewmodel.RecipeDetailViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KaylaMenuTheme {
                KaylaMenuApp()
            }
        }
    }
}

@Composable
fun KaylaMenuApp() {
    val navController = rememberNavController()
    // menuViewModel 和 ingredientSearchViewModel 跨页面共享状态，保留在顶层
    val menuViewModel: MenuViewModel = viewModel()
    val ingredientSearchViewModel: IngredientSearchViewModel = viewModel()

    NavHost(navController = navController, startDestination = "menu") {

        composable("menu") {
            MenuScreen(
                viewModel          = menuViewModel,
                onGoToShopping     = { navController.navigate("shopping") },
                onGoToRecipe       = { recipeId -> navController.navigate("recipe/$recipeId") },
                onSearchIngredient = { dayIndex -> navController.navigate("ingredient-search/$dayIndex") }
            )
        }

        composable("shopping") {
            val onBack = rememberNavAction { navController.popBackStack() }
            ShoppingListScreen(
                viewModel = menuViewModel,
                onBack    = onBack,
            )
        }

        composable("recipe/{recipeId}") { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId")?.toIntOrNull()
                ?: return@composable
            // ✅ ViewModel 绑定到这条 back stack entry 的生命周期：
            //    每次导航到详情页都是全新实例，离开页面 ViewModel 自动清除，不会污染主页状态
            val detailViewModel: RecipeDetailViewModel = viewModel(backStackEntry)
            val onBack = rememberNavAction { navController.popBackStack() }
            RecipeDetailScreen(
                recipeId = recipeId,
                viewModel = detailViewModel,
                onBack    = onBack,
            )
        }

        composable("ingredient-search/{dayIndex}") { backStackEntry ->
            val dayIndex = backStackEntry.arguments?.getString("dayIndex")?.toIntOrNull()
                ?: return@composable
            val onBack = rememberNavAction { navController.popBackStack() }
            IngredientSearchScreen(
                dayIndex        = dayIndex,
                searchViewModel = ingredientSearchViewModel,
                menuViewModel   = menuViewModel,
                onBack          = onBack,
            )
        }
    }
}
