package com.arsray.kaylamenu.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.arsray.kaylamenu.data.model.Ingredient
import com.arsray.kaylamenu.data.model.IngredientFreq
import com.arsray.kaylamenu.data.model.Recipe
import com.arsray.kaylamenu.data.model.Step

@Dao
interface RecipeDao {

    /** 按分类随机取 n 道菜 */
    @Query("SELECT * FROM recipes WHERE category = :category ORDER BY RANDOM() LIMIT :count")
    suspend fun randomByCategory(category: String, count: Int): List<Recipe>

    /** 取某分类全部菜谱 id（用于换菜时排除已用的） */
    @Query("SELECT id FROM recipes WHERE category = :category")
    suspend fun idsByCategory(category: String): List<Int>

    /** 按 id 取单道菜 */
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: Int): Recipe?

    /** 批量取多道菜的食材（用于生成购物清单） */
    @Query("SELECT * FROM ingredients WHERE recipe_id IN (:recipeIds)")
    suspend fun ingredientsByRecipeIds(recipeIds: List<Int>): List<Ingredient>

    /** 取单道菜的食材 */
    @Query("SELECT * FROM ingredients WHERE recipe_id = :recipeId")
    suspend fun ingredientsByRecipeId(recipeId: Int): List<Ingredient>

    /** 取单道菜的步骤，按顺序 */
    @Query("SELECT * FROM steps WHERE recipe_id = :recipeId ORDER BY step_order")
    suspend fun stepsByRecipeId(recipeId: Int): List<Step>

    /** 按使用菜谱数量降序返回食材频率（用于热门 chip 墙） */
    @Query("""
        SELECT name, count(DISTINCT recipe_id) AS cnt
        FROM ingredients
        WHERE name NOT LIKE '[噪音]%'
        GROUP BY name
        ORDER BY cnt DESC
        LIMIT 60
    """)
    suspend fun ingredientsByFrequency(): List<IngredientFreq>

    /** 模糊搜索含某食材的所有菜谱 */
    @Query("""
        SELECT DISTINCT r.* FROM recipes r
        INNER JOIN ingredients i ON r.id = i.recipe_id
        WHERE i.name LIKE '%' || :query || '%'
        AND i.name NOT LIKE '[噪音]%'
        ORDER BY r.title
    """)
    suspend fun recipesByIngredientQuery(query: String): List<Recipe>
}
