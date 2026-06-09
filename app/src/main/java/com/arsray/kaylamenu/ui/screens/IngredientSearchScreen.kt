package com.arsray.kaylamenu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.arsray.kaylamenu.data.model.Recipe
import com.arsray.kaylamenu.viewmodel.IngredientSearchViewModel
import com.arsray.kaylamenu.viewmodel.MenuUiState
import com.arsray.kaylamenu.viewmodel.MenuViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IngredientSearchScreen(
    dayIndex: Int,
    searchViewModel: IngredientSearchViewModel,
    menuViewModel: MenuViewModel,
    onBack: () -> Unit,
) {
    val state by searchViewModel.state.collectAsState()
    val menuState = menuViewModel.uiState.collectAsState().value

    // 当前菜单中已使用的所有菜谱 ID
    val usedIds = remember(menuState) {
        (menuState as? MenuUiState.Success)
            ?.days?.flatMap { it.dishes }?.map { it.id }?.toSet()
            ?: emptySet()
    }

    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("按食材找菜 · 加入第 $dayIndex 天") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── 搜索框 ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = state.searchText,
                onValueChange = { searchViewModel.onSearchTextChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("输入食材名，如：牛肉、土豆…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchText.isNotEmpty()) {
                        IconButton(onClick = { searchViewModel.clearSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(12.dp)
            )

            // ── 热门食材 Chip 墙 ─────────────────────────────────────────────
            if (state.topIngredients.isNotEmpty() && state.searchText.isEmpty()) {
                Text(
                    text = "热门食材",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
                FlowRow(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.topIngredients.forEach { name ->
                        FilterChip(
                            selected = state.activeIngredient == name,
                            onClick = { searchViewModel.selectIngredient(name) },
                            label = { Text(name, fontSize = 13.sp) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            HorizontalDivider()

            // ── 搜索结果 ──────────────────────────────────────────────────────
            when {
                state.isSearching -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.activeIngredient.isNotEmpty() && state.results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "没有找到含「${state.activeIngredient}」的菜谱",
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                state.results.isNotEmpty() -> {
                    Text(
                        text = "找到 ${state.results.size} 道菜",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        items(state.results, key = { it.id }) { recipe ->
                            val isUsed = recipe.id in usedIds
                            RecipePickerRow(
                                recipe = recipe,
                                isUsed = isUsed,
                                onClick = {
                                    menuViewModel.addSpecificDish(dayIndex, recipe)
                                    onBack()
                                }
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }

                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "选择上方食材，或输入食材名搜索",
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

// ── 菜谱选择行 ────────────────────────────────────────────────────────────────

@Composable
private fun RecipePickerRow(
    recipe: Recipe,
    isUsed: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (isUsed) 0.4f else 1f

    Surface(
        onClick = if (isUsed) ({}) else onClick,
        enabled = !isUsed,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面图
            AsyncImage(
                model = recipe.imageUrl,
                contentDescription = recipe.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))

            // 标题 + 分类
            Column(Modifier.weight(1f)) {
                Text(
                    text = recipe.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
                Spacer(Modifier.height(3.dp))
                CategoryBadge(recipe.category)
            }

            // 已在菜单角标
            if (isUsed) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "已在菜单",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: String) {
    val (label, color) = when (category) {
        "meat" -> "🥩 荤菜" to Color(0xFFFFD6D6)
        "veg"  -> "🥦 素菜" to Color(0xFFD6F5DC)
        else   -> "🍲 汤"   to Color(0xFFD6E8FF)
    }
    Text(
        text = label,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
