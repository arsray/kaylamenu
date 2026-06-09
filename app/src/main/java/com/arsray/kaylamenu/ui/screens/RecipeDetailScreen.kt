package com.arsray.kaylamenu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.arsray.kaylamenu.data.model.Ingredient
import com.arsray.kaylamenu.data.model.Step
import com.arsray.kaylamenu.viewmodel.DetailUiState
import com.arsray.kaylamenu.viewmodel.RecipeDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: Int,
    viewModel: RecipeDetailViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(recipeId) { viewModel.load(recipeId) }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (uiState as? DetailUiState.Success)?.recipe?.title ?: "菜谱详情"
                    Text(title, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            is DetailUiState.Error -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { Text(state.message, color = MaterialTheme.colorScheme.error) }
            }

            is DetailUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // 封面大图
                    item {
                        AsyncImage(
                            model = state.recipe.imageUrl,
                            contentDescription = state.recipe.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // 基本信息
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = state.recipe.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (!state.recipe.difficulty.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("难度：${state.recipe.difficulty}", fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    // 食材标题
                    if (state.ingredients.isNotEmpty()) {
                        item {
                            SectionTitle(
                                text = "食材清单",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        item {
                            IngredientTable(
                                ingredients = state.ingredients,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // 步骤标题
                    if (state.steps.isNotEmpty()) {
                        item {
                            SectionTitle(
                                text = "制作步骤",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        itemsIndexed(state.steps) { _, step ->
                            StepCard(step = step, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        }
                    }

                    if (state.ingredients.isEmpty() && state.steps.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无详细信息", color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 章节标题 ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

// ── 食材表格 ──────────────────────────────────────────────────────────────────

@Composable
private fun IngredientTable(ingredients: List<Ingredient>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ingredients.forEachIndexed { i, ing ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ing.name,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                if (!ing.amount.isNullOrBlank()) {
                    Text(
                        text = ing.amount,
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }
            if (i < ingredients.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}

// ── 步骤卡片 ──────────────────────────────────────────────────────────────────

@Composable
private fun StepCard(step: Step, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 序号气泡
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${step.stepOrder}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Column(Modifier.weight(1f)) {
            if (!step.description.isNullOrBlank()) {
                Text(
                    text = step.description,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            if (!step.imageUrl.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = step.imageUrl,
                    contentDescription = "步骤 ${step.stepOrder} 图",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
