"""
菜谱数据 Pipeline — 可重复运行，每次从原始库生成干净的 Android 资源文件

输入：  scripts/data/recipes_raw.db   （爬虫写入，只追加不修改）
输出：  app/src/main/assets/recipes.db （Room 兼容 + 清洗完毕）

用法：
    cd kaylamenu/
    python scripts/pipeline.py

每次新爬完菜谱后跑一次即可。
"""

import sqlite3
import shutil
from pathlib import Path

# ── 路径 ──────────────────────────────────────────────────────────────────────

SCRIPTS_DIR = Path(__file__).parent
PROJECT_ROOT = SCRIPTS_DIR.parent

RAW_DB  = SCRIPTS_DIR / "data" / "recipes_raw.db"
OUT_DB  = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "recipes.db"

# Room identity_hash（从 KSP 生成的 AppDatabase_Impl.kt 提取，表结构变化时需更新）
ROOM_IDENTITY_HASH = "cf061573bf0aa0cd8250885b085aa329"

# ── Room Schema ───────────────────────────────────────────────────────────────

ROOM_SCHEMA = """
CREATE TABLE `recipes` (
    `id`         INTEGER NOT NULL,
    `title`      TEXT    NOT NULL,
    `category`   TEXT    NOT NULL,
    `difficulty` TEXT,
    `image_url`  TEXT,
    `source_url` TEXT,
    PRIMARY KEY(`id`)
);

CREATE TABLE `ingredients` (
    `id`        INTEGER NOT NULL,
    `recipe_id` INTEGER NOT NULL,
    `name`      TEXT    NOT NULL,
    `amount`    TEXT,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE `steps` (
    `id`          INTEGER NOT NULL,
    `recipe_id`   INTEGER NOT NULL,
    `step_order`  INTEGER NOT NULL,
    `description` TEXT,
    `image_url`   TEXT,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE room_master_table (
    id            INTEGER PRIMARY KEY,
    identity_hash TEXT
);
"""

# ── 清洗规则 ──────────────────────────────────────────────────────────────────

# 同义词合并：key=保留名，value=需要合并进来的别名列表
NORMALIZE = {
    # 姜
    "生姜":     ["姜", "姜丝", "姜末", "姜片", "姜粉"],
    # 蒜
    "大蒜":     ["蒜", "蒜头", "大蒜头", "大蒜🧄", "蒜末", "蒜沫", "蒜片", "蒜瓣", "蒜泥"],
    # 葱
    "小葱":     ["小香葱", "小香葱花", "香葱"],
    "大葱":     ["大葱头", "葱段", "葱白", "葱花", "葱"],
    # 淀粉
    "淀粉":     ["玉米淀粉", "土豆淀粉", "红薯淀粉", "地瓜粉", "生粉",
                 "水淀粉", "淀粉水", "玉米淀粉/土豆淀粉(均可)"],
    # 油
    "食用油":   ["植物油", "油", "千岛源有机山茶油", "花生油"],
    # 糖
    "白糖":     ["白砂糖", "砂糖", "白糖或者冰糖", "或黄冰糖", "或冰糖"],
    # 盐
    "盐":       ["食用盐", "盐（焯水用）"],
    # 醋
    "陈醋":     ["香醋", "米醋"],
    # 料酒
    "料酒":     ["米酒", "花雕酒", "黄酒或者花雕酒", "绍兴酒", "花雕"],
    # 胡椒
    "白胡椒粉": ["胡椒粉", "胡椒",
                 "黑胡椒碎/白胡椒粉（去腥增香，孩子吃可以少放）"],
    "黑胡椒粉": ["黑胡椒", "黑胡椒碎"],
    # 香油
    "香油":     ["芝麻油", "芝麻香油"],
    # 酱油
    "生抽":     ["酱油", "味事达味极鲜", "味极鲜酱油", "碗汁：生抽"],
    "蚝油":     ["耗油"],
    # 豆腐
    "豆腐":     ["嫩豆腐", "老豆腐", "嫩豆腐一块"],
    # 豆瓣酱
    "豆瓣酱":   ["郫县豆瓣酱", "郫县豆瓣3勺",
                  "葱伴侣减盐原酿豆瓣酱 锁鲜装"],
    # 肉末
    "肉末":     ["肉沫"],
    "猪肉末":   ["猪肉沫"],
    # 番茄
    "番茄":     ["西红柿"],
    # 香料
    "八角":     ["大料"],
    "花椒":     ["干红花椒", "花椒粒", "花椒粉"],
    # 鲜味
    "鸡精":     ["味精", "味素", "鸡粉", "太太乐鸡汁", "鸡汁"],
    # 水
    "清水":     ["水", "热水", "饮用水"],
    # 辣椒
    "小米辣":   ["小米椒", "红小米辣"],
    # 蔬菜带描述
    "洋葱":     ["洋葱碎"],
    "胡萝卜":   ["胡萝卜丝", "胡萝卜（可用红椒代替）"],
    "香菜":     ["香菜段"],
    "黄瓜":     ["黄瓜丝"],
    "白菜":     ["白菜丝"],
    "红椒":     ["红彩椒粒", "大红椒"],
    "包菜":     ["包菜叶"],
    "豆芽":     ["豆芽菜"],
    "茄子":     ["长茄子"],
    "芹菜":     ["嫩芹菜"],
    # 其他
    "豆豉":     ["永川豆豉"],
    "黄豆酱":   ["黄豆酱(咸的不用放盐)"],
    "葱姜蒜":   ["葱蒜", "葱蒜粒", "姜葱", "姜、葱、蒜", "蒜，姜，葱"],
}

# 复合食材拆分：key=当前名，value=拆成的食材列表（第一项替换原行，其余新增）
SPLITS = {
    "葱姜蒜":                      ["大葱", "生姜", "大蒜"],
    "葱姜水":                      ["大葱", "生姜"],
    "辣椒丝葱丝":                  ["辣椒", "大葱"],
    "生抽、蚝油、老抽、料酒等":     ["生抽", "蚝油", "老抽", "料酒"],
    "生抽，松茸鲜":                ["生抽", "松茸鲜"],
    "盐，白芷粉，姜粉":            ["盐", "白芷", "生姜"],
    "小葱/白芝麻":                 ["小葱", "白芝麻"],
    "红绿彩椒":                    ["青椒", "红椒"],
    "葱花/大葱":                   ["大葱"],
    "酱油蚝油":                    ["生抽", "蚝油"],
    "枸杞叶（首选）或菠菜/生菜":   ["枸杞叶"],
    "粉丝或者拉皮":                ["粉丝"],
    "五花肉或梅花肉":              ["五花肉"],
}

# 噪音：标记为 [噪音]前缀，购物清单和搜索会过滤掉
import re
NOISE_PATTERNS = [
    r"^主料", r"^辅料$", r"^配料$", r"^调料$", r"^食材$",
    r"^其他", r"^料汁", r"^酱汁", r"^腌制", r"^腌料", r"^腌肉",
    r"^铺底", r"^各种食材$", r"^海鲜类$", r"^肉类$", r"^蔬菜$",
    r"^山珍$", r"^油盐$", r"^香料：",
]
NOISE_EXTRA = [
    "丝瓜烩虾仁", "丝瓜蛏子", "凉拌包菜", "咸菜毛豆烧蛏子",
    "大白菜豆腐", "山珍炒蔬", "山药炒木耳", "毛豆子烧虾",
    "毛豆荷包蛋烧千张", "油面筋菌菇毛豆", "葱烧豆腐", "芦笋炒虾仁",
    "茭白毛豆虾", "绿豆芽肉丝榨菜", "百叶结毛豆咸菜菌菇", "鱼香素什锦",
    "红烧素鸡", "酸菜毛豆豆腐", "虾仁丝瓜菌菇", "西兰花虾仁菌菇",
    "鱼类", "调料汁", "鱼露少于，",
]

# ── Pipeline 步骤 ─────────────────────────────────────────────────────────────

def step1_build_room_db(raw: sqlite3.Connection, dst: sqlite3.Connection):
    """Step 1：转换 schema，迁移所有数据"""
    print("\n[1/4] 建立 Room 兼容 schema，迁移数据…")
    dst.executescript(ROOM_SCHEMA)

    recipes = raw.execute(
        "SELECT id, title, category, difficulty, image_url, source_url FROM recipes"
    ).fetchall()
    dst.executemany(
        "INSERT INTO recipes(id,title,category,difficulty,image_url,source_url)"
        " VALUES(?,?,?,?,?,?)", recipes
    )

    ingredients = raw.execute(
        "SELECT id, recipe_id, name, amount FROM ingredients"
    ).fetchall()
    dst.executemany(
        "INSERT INTO ingredients(id,recipe_id,name,amount) VALUES(?,?,?,?)",
        ingredients
    )

    steps = raw.execute(
        "SELECT id, recipe_id, step_order, description, image_url FROM steps"
    ).fetchall()
    dst.executemany(
        "INSERT INTO steps(id,recipe_id,step_order,description,image_url)"
        " VALUES(?,?,?,?,?)", steps
    )

    dst.commit()
    print(f"    菜谱 {len(recipes)} 条 / 食材 {len(ingredients)} 条 / 步骤 {len(steps)} 条")


def step2_normalize(dst: sqlite3.Connection):
    """Step 2：同义词合并"""
    print("\n[2/4] 同义词合并…")
    count = 0
    for canonical, aliases in NORMALIZE.items():
        for alias in aliases:
            cur = dst.execute(
                "UPDATE ingredients SET name=? WHERE name=?", (canonical, alias)
            )
            if cur.rowcount:
                print(f"    {alias:30s} → {canonical}")
                count += cur.rowcount
    dst.commit()
    print(f"    共合并 {count} 条")


def step3_split(dst: sqlite3.Connection):
    """Step 3：复合食材拆分"""
    print("\n[3/4] 复合食材拆分…")
    max_id = dst.execute("SELECT max(id) FROM ingredients").fetchone()[0] or 0
    count = 0
    for compound, parts in SPLITS.items():
        rows = dst.execute(
            "SELECT id, recipe_id FROM ingredients WHERE name=?", (compound,)
        ).fetchall()
        for (row_id, recipe_id) in rows:
            dst.execute(
                "UPDATE ingredients SET name=?, amount=NULL WHERE id=?",
                (parts[0], row_id)
            )
            for part in parts[1:]:
                max_id += 1
                dst.execute(
                    "INSERT INTO ingredients(id,recipe_id,name,amount)"
                    " VALUES(?,?,?,NULL)",
                    (max_id, recipe_id, part)
                )
            print(f"    {compound} → {' + '.join(parts)}  (recipe_id={recipe_id})")
            count += 1
    dst.commit()
    print(f"    共拆分 {count} 条")


def step4_mark_noise(dst: sqlite3.Connection):
    """Step 4：标记噪音食材"""
    print("\n[4/4] 标记噪音条目…")
    noise_re = re.compile("|".join(NOISE_PATTERNS))
    all_names = [r[0] for r in dst.execute(
        "SELECT DISTINCT name FROM ingredients WHERE name NOT LIKE '[噪音]%'"
    ).fetchall()]

    count = 0
    # 正则匹配
    for name in all_names:
        if name and noise_re.match(name):
            dst.execute(
                "UPDATE ingredients SET name=? WHERE name=?",
                (f"[噪音]{name}", name)
            )
            print(f"    [噪音] {name}")
            count += 1
    # 明确列表
    for name in NOISE_EXTRA:
        cur = dst.execute(
            "UPDATE ingredients SET name=? WHERE name=? AND name NOT LIKE '[噪音]%'",
            (f"[噪音]{name}", name)
        )
        if cur.rowcount:
            print(f"    [噪音] {name}")
            count += cur.rowcount

    dst.commit()
    print(f"    共标记 {count} 类")


def step5_write_room_meta(dst: sqlite3.Connection):
    """写入 Room 版本元数据"""
    dst.execute(
        "INSERT OR REPLACE INTO room_master_table(id, identity_hash) VALUES(42, ?)",
        (ROOM_IDENTITY_HASH,)
    )
    dst.execute("PRAGMA user_version = 1")
    dst.commit()


def print_summary(dst: sqlite3.Connection):
    counts = {r[0]: r[1] for r in
              dst.execute("SELECT category, COUNT(*) FROM recipes GROUP BY category")}
    total = dst.execute("SELECT COUNT(*) FROM recipes").fetchone()[0]
    ing   = dst.execute(
        "SELECT COUNT(*) FROM ingredients WHERE name NOT LIKE '[噪音]%'"
    ).fetchone()[0]
    print(f"""
╔══════════════════════════════╗
║  Pipeline 完成               ║
╠══════════════════════════════╣
║  荤菜  {counts.get('meat', 0):>3} 道              ║
║  素菜  {counts.get('veg',  0):>3} 道              ║
║  汤    {counts.get('soup', 0):>3} 道              ║
║  合计  {total:>3} 道              ║
║  有效食材  {ing:>4} 条           ║
╚══════════════════════════════╝
输出：{OUT_DB}
""")


# ── 主流程 ────────────────────────────────────────────────────────────────────

def main():
    if not RAW_DB.exists():
        print(f"❌ 找不到原始数据库：{RAW_DB}")
        print("   请先运行 scraper.py 爬取数据，或把 recipes.db 复制到 scripts/data/recipes_raw.db")
        return

    # 每次从零生成，确保幂等
    tmp = OUT_DB.with_suffix(".tmp")
    if tmp.exists():
        tmp.unlink()

    raw = sqlite3.connect(RAW_DB)
    raw.row_factory = sqlite3.Row
    dst = sqlite3.connect(tmp)

    step1_build_room_db(raw, dst)
    step2_normalize(dst)
    step3_split(dst)
    step4_mark_noise(dst)
    step5_write_room_meta(dst)

    raw.close()
    dst.close()

    # 原子替换
    shutil.move(str(tmp), str(OUT_DB))
    print_summary(sqlite3.connect(OUT_DB))


if __name__ == "__main__":
    main()
