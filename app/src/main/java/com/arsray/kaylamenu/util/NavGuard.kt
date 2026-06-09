package com.arsray.kaylamenu.util

import androidx.compose.runtime.*

/**
 * 返回一个带 500ms 防抖的导航回调。
 * App 内所有导航动作共享同一把时间锁，防止快速双击导致重复 pop。
 */
@Composable
fun rememberNavAction(action: () -> Unit): () -> Unit {
    // 用 rememberUpdatedState 确保 action 总是最新引用
    val currentAction by rememberUpdatedState(action)
    // lastClickMs 跨重组保持状态
    var lastClickMs by remember { mutableLongStateOf(0L) }
    return remember {
        {
            val now = System.currentTimeMillis()
            if (now - lastClickMs > 500L) {
                lastClickMs = now
                currentAction()
            }
        }
    }
}
