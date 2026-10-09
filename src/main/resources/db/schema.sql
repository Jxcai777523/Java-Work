-- ============================================================
--  个人学习时间管理与效率分析系统 —— 数据库建表脚本
--  数据库：SQLite 3
--  说明：程序首次启动时自动执行本脚本；也可用数据库工具手动执行。
-- ============================================================

PRAGMA foreign_keys = ON;

-- ------------------------------------------------------------
-- 1. 用户表（登录与权限）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT    NOT NULL UNIQUE,                    -- 登录名
    password_hash TEXT    NOT NULL,                           -- SHA-256(密码 + 盐)
    salt          TEXT    NOT NULL,                           -- 每个用户独立的随机盐
    role          TEXT    NOT NULL DEFAULT 'USER'
                          CHECK (role IN ('USER', 'ADMIN')),  -- 角色
    enabled       INTEGER NOT NULL DEFAULT 1
                          CHECK (enabled IN (0, 1)),          -- 是否启用
    created_at    TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    last_login_at TEXT                                        -- 最近登录时间
);

-- ------------------------------------------------------------
-- 2. 科目表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subject (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    name                  TEXT    NOT NULL,                    -- 科目名称
    color                 TEXT    NOT NULL DEFAULT '#4A90D9',  -- 图表配色
    weekly_target_minutes INTEGER NOT NULL DEFAULT 0,          -- 每周目标时长（分钟）
    archived              INTEGER NOT NULL DEFAULT 0
                          CHECK (archived IN (0, 1)),          -- 归档标记（逻辑删除）
    created_at            TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);

-- ------------------------------------------------------------
-- 3. 学习记录表（系统的核心数据）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS study_record (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id          INTEGER NOT NULL,
    subject_id       INTEGER NOT NULL,
    start_time       TEXT    NOT NULL,                         -- 开始时间
    end_time         TEXT,                                     -- 结束时间
    duration_minutes INTEGER NOT NULL DEFAULT 0,               -- 有效时长（分钟，已扣除暂停）
    paused_seconds   INTEGER NOT NULL DEFAULT 0,               -- 累计暂停秒数
    pause_count      INTEGER NOT NULL DEFAULT 0,               -- 暂停次数（用于中断频率指标）
    mode             TEXT    NOT NULL DEFAULT 'FREE'
                     CHECK (mode IN ('FREE', 'POMODORO')),     -- 计时模式
    completed        INTEGER NOT NULL DEFAULT 1
                     CHECK (completed IN (0, 1)),              -- 番茄钟是否完整完成
    source           TEXT    NOT NULL DEFAULT 'AUTO'
                     CHECK (source IN ('AUTO', 'MANUAL')),     -- 自动计时/手动补录
    remark           TEXT,                                     -- 备注
    created_at       TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (user_id)    REFERENCES sys_user(id),
    FOREIGN KEY (subject_id) REFERENCES subject(id)
);

-- ------------------------------------------------------------
-- 4. 待办任务表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS task (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id           INTEGER NOT NULL,
    subject_id        INTEGER,                                 -- 可为空：不属于任何科目
    title             TEXT    NOT NULL,                        -- 任务标题
    priority          TEXT    NOT NULL DEFAULT 'MEDIUM'
                      CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    due_date          TEXT,                                    -- 截止日期
    estimated_minutes INTEGER NOT NULL DEFAULT 0,              -- 预计耗时
    status            TEXT    NOT NULL DEFAULT 'TODO'
                      CHECK (status IN ('TODO', 'DONE')),
    completed_at      TEXT,                                    -- 完成时间
    created_at        TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (user_id)    REFERENCES sys_user(id),
    FOREIGN KEY (subject_id) REFERENCES subject(id)
);

-- ------------------------------------------------------------
-- 5. 学习目标表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS goal (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id        INTEGER NOT NULL,
    subject_id     INTEGER,                                    -- 为空表示总目标
    period_type    TEXT    NOT NULL
                   CHECK (period_type IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    target_minutes INTEGER NOT NULL,                           -- 目标时长（分钟）
    start_date     TEXT    NOT NULL,
    end_date       TEXT    NOT NULL,
    created_at     TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (user_id)    REFERENCES sys_user(id),
    FOREIGN KEY (subject_id) REFERENCES subject(id)
);

-- ------------------------------------------------------------
-- 6. 偏好设置表（外观等可调项，按"键 → 值"存放）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS preferences (
    pref_key   TEXT PRIMARY KEY,                               -- 设置项名称
    pref_value TEXT NOT NULL,                                  -- 设置项取值（统一按字符串保存）
    updated_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

-- ------------------------------------------------------------
-- 索引：加速按时间/科目/用户的统计查询
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_record_start_time ON study_record(start_time);
CREATE INDEX IF NOT EXISTS idx_record_subject    ON study_record(subject_id);
CREATE INDEX IF NOT EXISTS idx_record_user       ON study_record(user_id);
CREATE INDEX IF NOT EXISTS idx_task_user         ON task(user_id);
CREATE INDEX IF NOT EXISTS idx_task_status       ON task(status);
CREATE INDEX IF NOT EXISTS idx_goal_user         ON goal(user_id);

-- ------------------------------------------------------------
-- 默认科目（仅当同名科目不存在时插入）
-- ------------------------------------------------------------
INSERT INTO subject (name, color, weekly_target_minutes)
SELECT '高等数学', '#4A90D9', 300
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE name = '高等数学');

INSERT INTO subject (name, color, weekly_target_minutes)
SELECT '大学英语', '#7BC96F', 180
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE name = '大学英语');

INSERT INTO subject (name, color, weekly_target_minutes)
SELECT '数据结构', '#E8A33D', 240
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE name = '数据结构');

INSERT INTO subject (name, color, weekly_target_minutes)
SELECT 'Java 程序设计', '#C0654E', 300
WHERE NOT EXISTS (SELECT 1 FROM subject WHERE name = 'Java 程序设计');