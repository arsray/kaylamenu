package com.arsray.kaylamenu.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.arsray.kaylamenu.data.db.dao.RecipeDao
import com.arsray.kaylamenu.data.model.Ingredient
import com.arsray.kaylamenu.data.model.Recipe
import com.arsray.kaylamenu.data.model.Step

@Database(
    entities = [Recipe::class, Ingredient::class, Step::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recipes.db"
                )
                    // 首次运行时从 assets 复制预置数据库
                    .createFromAsset("recipes.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
