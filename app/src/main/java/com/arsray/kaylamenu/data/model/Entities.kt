package com.arsray.kaylamenu.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation

// ── 数据库实体（与 recipes.db 表结构完全对应）─────────────────────────────────

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey val id: Int,
    val title: String,
    val category: String,       // "meat" | "veg" | "soup"
    val difficulty: String?,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "source_url") val sourceUrl: String?
)

@Entity(
    tableName = "ingredients",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipe_id"]
    )]
)
data class Ingredient(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "recipe_id") val recipeId: Int,
    val name: String,
    val amount: String?
)

@Entity(
    tableName = "steps",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipe_id"]
    )]
)
data class Step(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "recipe_id") val recipeId: Int,
    @ColumnInfo(name = "step_order") val stepOrder: Int,
    val description: String?,
    @ColumnInfo(name = "image_url") val imageUrl: String?
)

// ── 关联查询结果 ───────────────────────────────────────────────────────────────

data class RecipeWithDetails(
    @Embedded val recipe: Recipe,
    @Relation(parentColumn = "id", entityColumn = "recipe_id")
    val ingredients: List<Ingredient>,
    @Relation(parentColumn = "id", entityColumn = "recipe_id")
    val steps: List<Step>
)
