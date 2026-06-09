package com.arsray.kaylamenu.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsray.kaylamenu.viewmodel.MenuViewModel
import com.arsray.kaylamenu.viewmodel.ShoppingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: MenuViewModel, onBack: () -> Unit) {
    val items by viewModel.shoppingItems.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("购物清单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("清单为空，请先生成菜单", color = MaterialTheme.colorScheme.outline)
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            // 进度栏
            val checked = items.count { it.checked }
            Text(
                text = "已购 $checked / ${items.size}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            LinearProgressIndicator(
                progress = { checked.toFloat() / items.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(4.dp))

            // 提示文字
            Text(
                text = "长按右侧标签可查看具体菜品",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                itemsIndexed(items, key = { _, item -> item.name }) { index, item ->
                    ShoppingItemRow(
                        item = item,
                        onToggle = { viewModel.toggleShoppingItem(index) }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingItemRow(item: ShoppingItem, onToggle: () -> Unit) {
    val tooltipState = rememberTooltipState(isPersistent = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.checked,
            onCheckedChange = { onToggle() }
        )
        Spacer(Modifier.width(4.dp))

        // 食材名
        Text(
            text = item.name,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            color = if (item.checked) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface
        )

        // 菜品数标签（长按浮出菜名）
        TooltipBox(
            positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
            tooltip = {
                RichTooltip(
                    title = { Text("用到 ${item.name} 的菜：", fontWeight = FontWeight.SemiBold) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        item.dishNames.forEach { name ->
                            Text("• $name", fontSize = 13.sp)
                        }
                    }
                }
            },
            state = tooltipState
        ) {
            SuggestionChip(
                onClick = {},   // 单击无动作，长按由 TooltipBox 处理
                label = {
                    Text(
                        text = "${item.dishCount}道菜用到",
                        fontSize = 12.sp,
                        color = if (item.checked) MaterialTheme.colorScheme.outline
                                else MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.height(28.dp)
            )
        }
    }
}
