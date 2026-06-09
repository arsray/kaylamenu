package com.arsray.kaylamenu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.arsray.kaylamenu.data.model.Recipe
import com.arsray.kaylamenu.viewmodel.DayMenu
import com.arsray.kaylamenu.viewmodel.MenuUiState
import com.arsray.kaylamenu.viewmodel.MenuViewModel

// ── 分类工具 ───────────────────────────────────────────────────────────────────

private fun categoryLabel(category: String) = when (category) {
    "meat" -> "🥩 荤菜"
    "veg"  -> "🥦 素菜"
    else   -> "🍲 汤"
}

private fun categoryColor(category: String) = when (category) {
    "meat" -> Color(0xFFFFF3F3)
    "veg"  -> Color(0xFFF3FFF5)
    else   -> Color(0xFFF3F8FF)
}

// ── 主屏 ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    viewModel: MenuViewModel,
    onGoToShopping: () -> Unit,
    onGoToRecipe: (recipeId: Int) -> Unit,
    onSearchIngredient: (dayIndex: Int) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val successState = uiState as? MenuUiState.Success

    LaunchedEffect(Unit) {
        if (uiState is MenuUiState.Loading) viewModel.generateMenu(3)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kayla 每日菜单") },
                actions = {
                    if (successState != null) {
                        IconButton(onClick = {
                            viewModel.buildShoppingList()
                            onGoToShopping()
                        }) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "购物清单")
                        }
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
            DaysSelector(
                currentDaysCount = successState?.days?.size ?: 3,
                onPresetSelected = { viewModel.generateMenu(it) },
                onRegenerate = { viewModel.generateMenu(successState?.days?.size ?: 3) }
            )

            when (val state = uiState) {
                is MenuUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is MenuUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is MenuUiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(state.days) { _, day ->
                            DayCard(
                                day = day,
                                canDeleteDay = state.days.size > 1,
                                onDeleteDay  = { viewModel.removeDay(day.dayIndex) },
                                onSwapDish   = { id -> viewModel.swapDish(day.dayIndex, id) },
                                onDeleteDish = { id -> viewModel.removeDish(day.dayIndex, id) },
                                onAddDish    = { cat -> viewModel.addDish(day.dayIndex, cat) },
                                onDishClick  = { id -> onGoToRecipe(id) },
                                onSearchIngredient = { onSearchIngredient(day.dayIndex) }
                            )
                        }

                        // 加一天按钮
                        if (state.days.size < 10) {
                            item {
                                OutlinedButton(
                                    onClick = { viewModel.addDay() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("加一天（当前 ${state.days.size} / 10 天）")
                                }
                            }
                        }

                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

// ── 快捷天数选择栏 ────────────────────────────────────────────────────────────

@Composable
private fun DaysSelector(
    currentDaysCount: Int,
    onPresetSelected: (Int) -> Unit,
    onRegenerate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("快捷：", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color.Gray)
        listOf(3, 5).forEach { days ->
            FilterChip(
                selected = currentDaysCount == days,
                onClick = { onPresetSelected(days) },
                label = { Text("${days}天") }
            )
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onRegenerate) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("重新生成")
        }
    }
}

// ── 单天卡片 ──────────────────────────────────────────────────────────────────

@Composable
private fun DayCard(
    day: DayMenu,
    canDeleteDay: Boolean,
    onDeleteDay: () -> Unit,
    onSwapDish: (recipeId: Int) -> Unit,
    onDeleteDish: (recipeId: Int) -> Unit,
    onAddDish: (category: String) -> Unit,
    onDishClick: (recipeId: Int) -> Unit,
    onSearchIngredient: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {

            // 标题行 + 删除天按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "第 ${day.dayIndex} 天",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (canDeleteDay) {
                    IconButton(
                        onClick = onDeleteDay,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除这一天",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.65f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 菜品列表
            if (day.dishes.isEmpty()) {
                Text(
                    "（暂无菜品，请从下方添加）",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                day.dishes.forEach { recipe ->
                    DishRow(
                        recipe  = recipe,
                        onSwap  = { onSwapDish(recipe.id) },
                        onDelete = { onDeleteDish(recipe.id) },
                        onClick  = { onDishClick(recipe.id) }
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // 添加菜品行
            AddDishRow(onAddDish = onAddDish, onSearchIngredient = onSearchIngredient)
        }
    }
}

// ── 单道菜行 ──────────────────────────────────────────────────────────────────

@Composable
private fun DishRow(
    recipe: Recipe,
    onSwap: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(categoryColor(recipe.category))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = recipe.imageUrl,
            contentDescription = recipe.title,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(categoryLabel(recipe.category), fontSize = 11.sp, color = Color.Gray)
            Text(
                recipe.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
        }
        // 换一换
        IconButton(onClick = onSwap, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "换一换",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        // 删除这道菜
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "删除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
            )
        }
    }
}

// ── 添加菜品栏 ────────────────────────────────────────────────────────────────

@Composable
private fun AddDishRow(
    onAddDish: (category: String) -> Unit,
    onSearchIngredient: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("meat" to "🥩荤", "veg" to "🥦素", "soup" to "🍲汤").forEach { (cat, label) ->
            AssistChip(
                onClick = { onAddDish(cat) },
                label = { Text(label, fontSize = 11.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                },
                modifier = Modifier.height(28.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        AssistChip(
            onClick = onSearchIngredient,
            label = { Text("按食材找菜", fontSize = 11.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(12.dp))
            },
            modifier = Modifier.height(28.dp)
        )
    }
}
