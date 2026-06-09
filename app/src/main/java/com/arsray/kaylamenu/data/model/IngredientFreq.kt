package com.arsray.kaylamenu.data.model

import androidx.room.ColumnInfo

/** DAO 查询食材频率时使用的轻量结果类 */
data class IngredientFreq(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "cnt") val cnt: Int,
)
