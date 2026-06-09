# Kayla Menu

儿童每日菜单 Android App，基于 Jetpack Compose + Room 构建。

## 技术栈

- Kotlin 2.2 + Jetpack Compose + Material3
- Room 2.7 (KSP)
- Navigation Compose
- Coil（图片加载）

## 项目结构

```
app/src/main/java/com/arsray/kaylamenu/
├── data/
│   ├── db/          # Room Database & DAO
│   └── model/       # 数据实体（Recipe / Ingredient / Step）
├── viewmodel/       # MenuViewModel / RecipeDetailViewModel / IngredientSearchViewModel
└── ui/screens/      # MenuScreen / ShoppingListScreen / RecipeDetailScreen / IngredientSearchScreen
```

## 数据库说明

出于隐私考虑，`app/src/main/assets/recipes.db` **未包含在仓库中**。

表结构见 [`database/schema.sql`](database/schema.sql)。

### 自行构建数据库

1. 按 `schema.sql` 创建 SQLite 文件：
   ```bash
   sqlite3 recipes.db < database/schema.sql
   ```
2. 向 `recipes` / `ingredients` / `steps` 三张表填入菜谱数据
3. 将 `recipes.db` 放至 `app/src/main/assets/recipes.db`
4. 正常编译运行即可

> **注意**：`room_master_table` 中的 `identity_hash` 必须与 `schema.sql` 一致，否则 App 启动时会报 schema mismatch 错误。
