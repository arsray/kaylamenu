"""
下厨房菜谱爬虫（Playwright 版）
数据来源：分类页 40076（家常菜）/ 40077（快手菜）/ 40078（下饭菜）
分类逻辑：菜名含汤类关键词 → soup；食材含肉/禽/海鲜 → meat；其余 → veg
目标：meat 50 + veg 50 + soup 50
输出：recipes.db（SQLite）

运行：
    source .venv/bin/activate
    python scraper.py
"""

import sqlite3
import time
import random
import re
import logging
from pathlib import Path
from dataclasses import dataclass, field
from typing import Optional

from bs4 import BeautifulSoup
from playwright.sync_api import sync_playwright, Page, TimeoutError as PwTimeout
from playwright_stealth import Stealth

# ── 配置 ──────────────────────────────────────────────────────────────────────

BASE_URL = "https://www.xiachufang.com"

CATEGORY_IDS = ["40077", "40076", "40078"]   # 快手菜 / 家常菜 / 下饭菜

TARGET = {"meat": 50, "veg": 40, "soup": 10}

SOUP_TITLE_KEYWORDS = {
    "汤", "羹", "煲", "靓汤", "老火汤", "炖汤", "汤水",
    "粥",   # 粥归 soup 分类（液态主食）
}

MEAT_KEYWORDS = {
    "猪", "牛", "羊", "鸡", "鸭", "鹅", "兔",
    "猪肉", "牛肉", "羊肉", "鸡肉", "鸭肉",
    "排骨", "五花肉", "里脊", "鸡腿", "鸡翅", "鸡胸",
    "腊肉", "培根", "火腿", "香肠", "腊肠", "肉末", "肉馅",
    "虾", "鱼", "蟹", "蛤", "蛏", "贝", "海鲜",
    "扇贝", "花蛤", "花甲", "海虾",
    "鲫鱼", "草鱼", "鲤鱼", "鲈鱼", "黄鱼", "带鱼", "鳕鱼", "三文鱼",
    "对虾", "基围虾", "虾仁", "虾皮",
    "猪蹄", "猪肝", "猪心", "牛肚", "鸡爪",
}

PAGE_DELAY   = (2.0, 4.0)
DETECT_SECS  = 6      # 验证码轮询窗口
POLL_SECS    = 0.5    # 轮询间隔
HEADLESS     = False  # 显示浏览器，方便处理验证码
DB_PATH      = Path(__file__).parent / "data" / "recipes_raw.db"

# ── 日志 ─────────────────────────────────────────────────────────────────────

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler(Path(__file__).parent / "scraper.log", encoding="utf-8"),
    ],
)
log = logging.getLogger(__name__)

# ── 数据结构 ──────────────────────────────────────────────────────────────────

@dataclass
class Ingredient:
    name: str
    amount: str

@dataclass
class Step:
    order: int
    description: str
    image_url: str = ""

@dataclass
class Recipe:
    title: str
    category: str       # "meat" | "veg" | "soup"
    difficulty: str
    image_url: str
    source_url: str
    ingredients: list[Ingredient] = field(default_factory=list)
    steps: list[Step]   = field(default_factory=list)

# ── 数据库 ────────────────────────────────────────────────────────────────────

def init_db(path: Path) -> sqlite3.Connection:
    conn = sqlite3.connect(path)
    conn.executescript("""
        CREATE TABLE IF NOT EXISTS recipes (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            title       TEXT NOT NULL,
            category    TEXT NOT NULL,
            difficulty  TEXT,
            image_url   TEXT,
            source_url  TEXT UNIQUE
        );
        CREATE TABLE IF NOT EXISTS ingredients (
            id        INTEGER PRIMARY KEY AUTOINCREMENT,
            recipe_id INTEGER NOT NULL REFERENCES recipes(id),
            name      TEXT NOT NULL,
            amount    TEXT
        );
        CREATE TABLE IF NOT EXISTS steps (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            recipe_id   INTEGER NOT NULL REFERENCES recipes(id),
            step_order  INTEGER NOT NULL,
            description TEXT,
            image_url   TEXT
        );
    """)
    conn.commit()
    return conn


def save_recipe(conn: sqlite3.Connection, recipe: Recipe) -> Optional[int]:
    try:
        c = conn.execute(
            "INSERT INTO recipes (title, category, difficulty, image_url, source_url)"
            " VALUES (?,?,?,?,?)",
            (recipe.title, recipe.category, recipe.difficulty,
             recipe.image_url, recipe.source_url),
        )
        rid = c.lastrowid
        conn.executemany(
            "INSERT INTO ingredients (recipe_id, name, amount) VALUES (?,?,?)",
            [(rid, i.name, i.amount) for i in recipe.ingredients],
        )
        conn.executemany(
            "INSERT INTO steps (recipe_id, step_order, description, image_url)"
            " VALUES (?,?,?,?)",
            [(rid, s.order, s.description, s.image_url) for s in recipe.steps],
        )
        conn.commit()
        return rid
    except sqlite3.IntegrityError:
        conn.rollback()
        return None


def db_counts(conn: sqlite3.Connection) -> dict[str, int]:
    return {r[0]: r[1] for r in
            conn.execute("SELECT category, COUNT(*) FROM recipes GROUP BY category")}


def already_scraped(conn: sqlite3.Connection, url: str) -> bool:
    return conn.execute(
        "SELECT 1 FROM recipes WHERE source_url=?", (url,)
    ).fetchone() is not None

# ── 合集过滤 ──────────────────────────────────────────────────────────────────

COLLECTION_KEYWORDS = {"合集", "大全", "款家常菜", "道菜", "份菜", "期菜", "汇总"}

def is_collection(title: str) -> bool:
    """标题含合集关键词，或以纯数字+菜开头，视为合集帖跳过"""
    if any(kw in title for kw in COLLECTION_KEYWORDS):
        return True
    if re.match(r"^\d+[道款份]", title):
        return True
    return False

# ── 分类判断 ──────────────────────────────────────────────────────────────────

def classify_recipe(title: str, ingredients: list[Ingredient]) -> str:
    """优先判断汤，再看食材荤素"""
    if any(kw in title for kw in SOUP_TITLE_KEYWORDS):
        return "soup"
    all_names = " ".join(i.name for i in ingredients)
    if any(kw in all_names for kw in MEAT_KEYWORDS):
        return "meat"
    return "veg"

# ── 验证码处理 ────────────────────────────────────────────────────────────────

CAPTCHA_SELECTORS = [
    "div.nc-container", "div.nc_wrapper",
    "div.geetest_holder", "div.geetest_wrap",
    "#captcha", "iframe[src*='captcha']", "iframe[src*='verify']",
    ".verify-wrap", ".slidecode-wrap",
]
CAPTCHA_TEXTS = ["滑动验证", "拖动滑块", "请完成验证", "安全验证", "人机验证"]


def wait_for_captcha(page: Page):
    """
    轮询 DETECT_SECS 秒检测验证码。
    滑块组件可能在页面加载后 3-5 秒才渲染，所以持续轮询而非一次性检测。
    """
    def _has_captcha() -> bool:
        for sel in CAPTCHA_SELECTORS:
            try:
                if page.query_selector(sel):
                    return True
            except Exception:
                pass
        try:
            body = page.inner_text("body")
            if any(t in body for t in CAPTCHA_TEXTS):
                return True
        except Exception:
            pass
        return False

    deadline = time.time() + DETECT_SECS
    while time.time() < deadline:
        if _has_captcha():
            print("\n" + "=" * 60)
            print("⚠️  检测到验证码/滑块！")
            print("   请在浏览器中完成验证，完成后回到终端按回车继续。")
            print("=" * 60)
            input("   [完成验证后按回车] ")
            try:
                page.wait_for_load_state("domcontentloaded", timeout=15_000)
            except PwTimeout:
                pass
            time.sleep(2)
            return
        time.sleep(POLL_SECS)

# ── 页面导航 ──────────────────────────────────────────────────────────────────

def goto(page: Page, url: str) -> Optional[BeautifulSoup]:
    time.sleep(random.uniform(*PAGE_DELAY))
    try:
        page.goto(url, wait_until="domcontentloaded", timeout=30_000)
        wait_for_captcha(page)
        return BeautifulSoup(page.content(), "lxml")
    except PwTimeout:
        log.warning(f"超时: {url}")
        return None

# ── 列表页解析 ────────────────────────────────────────────────────────────────

def parse_list_page(soup: BeautifulSoup) -> tuple[list[str], Optional[str]]:
    """
    返回 (菜谱URL列表, 下一页URL或None)
    列表项结构：div.recipe.pure-g > a[href=/recipe/XXXXX/]
    """
    urls = []
    for div in soup.find_all("div", class_=lambda c: c and "recipe" in c and "pure-g" in c):
        a = div.find("a", href=re.compile(r"^/recipe/\d+/$"))
        if a:
            urls.append(BASE_URL + a["href"])

    next_url = None
    next_a = soup.select_one("a.next")
    if next_a and next_a.get("href"):
        href = next_a["href"]
        next_url = href if href.startswith("http") else BASE_URL + href

    return list(dict.fromkeys(urls)), next_url

# ── 详情页解析 ────────────────────────────────────────────────────────────────

def parse_detail(page: Page, url: str) -> Optional[Recipe]:
    """
    解析菜谱详情页（选择器基于真实 HTML 验证）：
      - 食材：div.ings table tr > td.name + td.unit
      - 步骤：div.steps li.container > p.text + img
      - 封面：div.cover.image img，备用 og:image
    """
    soup = goto(page, url)
    if not soup:
        return None

    # 菜名
    h1 = soup.select_one("h1.page-title") or soup.find("h1")
    if not h1:
        return None
    title = h1.get_text(strip=True)

    # 合集帖跳过
    if is_collection(title):
        log.debug(f"跳过合集: {title}")
        return None

    # 封面图
    image_url = ""
    cover_img = soup.select_one("div.cover.image img, div.cover img")
    if cover_img:
        image_url = cover_img.get("src", "")
    if not image_url:
        og = soup.find("meta", property="og:image")
        if og:
            image_url = og.get("content", "")

    # 食材
    ingredients: list[Ingredient] = []
    for row in soup.select("div.ings table tr"):
        n_el = row.select_one("td.name")
        u_el = row.select_one("td.unit")
        if not n_el:
            continue
        name   = n_el.get_text(strip=True)
        amount = u_el.get_text(strip=True) if u_el else ""
        if name:
            ingredients.append(Ingredient(name=name, amount=amount))

    # 步骤（跳过无 p.text 的纯图片展示项）
    steps: list[Step] = []
    for i, li in enumerate(soup.select("div.steps li.container"), 1):
        desc_el = li.select_one("p.text")
        if not desc_el:
            continue
        desc = desc_el.get_text(strip=True)
        img_el = li.find("img")
        img = img_el.get("src", "") if img_el else ""
        if desc:
            steps.append(Step(order=i, description=desc, image_url=img))

    # 无步骤说明是合集或特殊页面，跳过
    if not steps:
        log.debug(f"跳过（无步骤）: {title}")
        return None

    category = classify_recipe(title, ingredients)

    return Recipe(
        title=title,
        category=category,
        difficulty="简单",   # 下厨房详情页无难度字段
        image_url=image_url,
        source_url=url,
        ingredients=ingredients,
        steps=steps,
    )

# ── 主爬取流程 ────────────────────────────────────────────────────────────────

def all_done(conn: sqlite3.Connection) -> bool:
    c = db_counts(conn)
    return all(c.get(k, 0) >= v for k, v in TARGET.items())


def scrape(page: Page, conn: sqlite3.Connection):
    for cat_id in CATEGORY_IDS:
        if all_done(conn):
            break

        cat_url = f"{BASE_URL}/category/{cat_id}/"
        log.info(f"══ 分类 {cat_id}: {cat_url} ══")

        next_url: Optional[str] = cat_url
        while next_url and not all_done(conn):
            log.info(f"  列表页: {next_url}")
            soup = goto(page, next_url)
            if not soup:
                break

            recipe_urls, next_url = parse_list_page(soup)
            log.info(f"  本页 {len(recipe_urls)} 个菜谱，下一页: {next_url}")

            for recipe_url in recipe_urls:
                if all_done(conn):
                    break

                c = db_counts(conn)
                log.info(
                    f"  荤{c.get('meat',0)} 素{c.get('veg',0)} "
                    f"汤{c.get('soup',0)}  → {recipe_url}"
                )

                if already_scraped(conn, recipe_url):
                    log.debug(f"  已存在，跳过")
                    continue

                recipe = parse_detail(page, recipe_url)
                if recipe is None:
                    continue

                # 跳过该分类已满的菜
                if c.get(recipe.category, 0) >= TARGET[recipe.category]:
                    log.debug(f"  [{recipe.category}] 已满，跳过「{recipe.title}」")
                    continue

                rid = save_recipe(conn, recipe)
                if rid:
                    c = db_counts(conn)
                    log.info(
                        f"  ✓ 「{recipe.title}」[{recipe.category}]  "
                        f"荤{c.get('meat',0)} 素{c.get('veg',0)} 汤{c.get('soup',0)}"
                    )


def main():
    conn = init_db(DB_PATH)
    log.info(f"数据库: {DB_PATH}")
    log.info(f"目标: {TARGET}")

    # 持久化用户目录：代理认证信息、Cookie 在多次运行间保留
    USER_DATA_DIR = Path(__file__).parent / "chrome_profile"

    with sync_playwright() as pw:
        # launch_persistent_context 直接返回 context（不需要再 new_context）
        ctx = pw.chromium.launch_persistent_context(
            user_data_dir=str(USER_DATA_DIR),
            headless=HEADLESS,
            executable_path="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            args=["--disable-blink-features=AutomationControlled"],
            viewport={"width": 1280, "height": 800},
            locale="zh-CN",
            user_agent=(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/124.0.0.0 Safari/537.36"
            ),
        )
        page = ctx.new_page()
        Stealth().apply_stealth_sync(page)

        log.info("访问主页，如需代理登录请在浏览器中完成后回车继续…")
        # 先不通过 goto()，直接导航并等用户处理代理弹窗
        page.goto(BASE_URL, wait_until="domcontentloaded", timeout=60_000)
        input("主页已打开，确认代理登录完成后按回车开始爬取…")

        scrape(page, conn)
        ctx.close()

    c = db_counts(conn)
    log.info(
        f"\n完成！  荤 {c.get('meat',0)}/{TARGET['meat']}  "
        f"素 {c.get('veg',0)}/{TARGET['veg']}  "
        f"汤 {c.get('soup',0)}/{TARGET['soup']}"
    )
    conn.close()


if __name__ == "__main__":
    main()
