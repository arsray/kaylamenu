-- Kayla Menu — Database Schema
-- Room identity_hash: cf061573bf0aa0cd8250885b085aa329
-- PRAGMA user_version = 1
--
-- 用此文件创建空库后，将生成的 .db 文件放至：
--   app/src/main/assets/recipes.db

CREATE TABLE `recipes` (
    `id`         INTEGER NOT NULL,
    `title`      TEXT    NOT NULL,
    `category`   TEXT    NOT NULL,   -- 'meat' | 'veg' | 'soup'
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

-- Room 版本校验表（必须写入，否则 App 启动时报 schema mismatch）
CREATE TABLE room_master_table (
    id      INTEGER PRIMARY KEY,
    identity_hash TEXT
);

INSERT INTO room_master_table (id, identity_hash)
VALUES (42, 'cf061573bf0aa0cd8250885b085aa329');

PRAGMA user_version = 1;
