# StudyTimeTracker

**基于 Java Swing 的个人学习时间管理与效率分析系统**

Java 程序设计工程实践 · 课程设计项目

---

## 一、项目简介

本系统是一款面向个人学习者的桌面应用，用于记录学习时间、管理学习任务，
并通过多维度指标与可视化图表分析学习效率，帮助使用者发现自己的高效时段与学习习惯。

## 二、技术栈

| 层次 | 技术选型 |
|---|---|
| 开发语言 | Java 21（LTS） |
| 界面框架 | Java Swing |
| 界面外观 | FlatLaf 3.4（现代扁平化主题，支持深浅色切换） |
| 数据存储 | SQLite 3（嵌入式单文件数据库） |
| 数据访问 | 原生 JDBC + PreparedStatement |
| 图表渲染 | XChart 3.8（柱状图 / 饼图 / 折线图 / 高效时段）+ 自绘 GitHub 风格热力图 |
| 构建工具 | Apache Maven |
| 打包发布 | jpackage（生成自带 JRE 的免安装绿色版） |
| 架构模式 | 三层架构（view → service → dao） |

## 三、环境要求

- **JDK 21** 或更高版本（开发时使用 OpenJDK 21.0.6）
- **Maven 3.8+**（IntelliJ IDEA 已内置，无需单独安装）
- **IntelliJ IDEA**（推荐使用，已在 IDEA Community 2025.2.1 上验证）

## 四、目录结构

```
StudyTimeTracker/
├── pom.xml                      Maven 项目配置
├── README.md                    项目说明（本文件）
├── 开发进度.md                   开发批次、验证结果与代码复查记录
├── .gitignore                   Git 忽略规则
├── build-package.ps1            一键打包脚本（生成绿色版 exe）
├── 启动 StudyTimeTracker.bat     双击启动（先把工作目录切到项目根目录）
├── .mvn/jvm.config              修正本机 user.home 解析问题
├── src/
│   ├── main/
│   │   ├── java/com/study/tracker/
│   │   │   ├── app/             程序入口
│   │   │   ├── model/           实体类（数据模型）
│   │   │   ├── dao/             数据访问层（JDBC 操作）
│   │   │   ├── service/         业务逻辑层（统计、效率指标）
│   │   │   ├── view/            界面层（Swing 窗口与面板）
│   │   │   ├── chart/           图表封装（XChart）+ 自绘热力图
│   │   │   ├── util/            工具类
│   │   │   └── config/          全局配置与外观管理
│   │   └── resources/db/schema.sql   数据库建表脚本
│   └── test/java/com/study/tracker/  单元测试（当前 18 个用例）
├── data/data.db                 运行后生成：数据库文件
├── target/                      构建产物（含可执行 jar）
└── dist/StudyTimeTracker/       打包产物：免安装绿色版
```

## 五、如何运行

### 方式一：IntelliJ IDEA（推荐）

1. 打开 IDEA → `File` → `Open`，选择本目录 `StudyTimeTracker`
2. IDEA 会自动识别为 Maven 项目并下载依赖（首次需联网，约 1~2 分钟）
3. 等待右下角依赖下载完成
4. 打开 `src/main/java/com/study/tracker/app/Main.java`，点击左侧绿色三角运行

### 方式二：命令行

```powershell
# 在项目根目录 StudyTimeTracker 下执行
& "D:\programs\IntelliJ IDEA Community Edition 2025.2.1\plugins\maven\lib\maven3\bin\mvn.cmd" clean package
java -jar target\StudyTimeTracker.jar
```

### 方式三：双击启动脚本（日常使用最方便）

直接双击项目根目录下的 `启动 StudyTimeTracker.bat`，
脚本会先把工作目录切到项目根目录再启动程序。

> **不要直接双击 `target\StudyTimeTracker.jar`。**
> 程序按"当前工作目录 + data"定位数据库，双击 jar 时工作目录是 `target`，
> 程序会在 `target\data\data.db` 新建一个空库，看起来像数据丢失。

## 六、数据存储位置

程序按下面的顺序确定数据目录：

1. **程序运行目录可写时**：使用 `程序运行目录\data\data.db`。
   开发时运行目录就是项目根目录，因此数据库位于 `StudyTimeTracker\data\data.db`；
2. **运行目录不可写时**（例如被安装在 `Program Files` 下）：
   回退到用户目录 `%USERPROFILE%\.study-tracker\data.db`。

同目录下还会存放外观设置里的背景图副本 `background.image`。

也可以在启动时显式指定数据目录，此时与工作目录无关：

```powershell
java -Dstt.data.dir="D:\StudyTimeTrackerData" -jar target\StudyTimeTracker.jar
```

> 注意：数据位置跟着**工作目录**走。所以要从项目根目录启动，
> 或直接双击 `启动 StudyTimeTracker.bat`，不要双击 `target` 里的 jar。
> 备份与恢复功能在"系统设置 → 数据管理"里，复制 `data.db` 即可迁移全部数据。

## 七、打包发布

执行项目根目录下的打包脚本：

```powershell
.\build-package.ps1
```

脚本会先检查程序是否正在运行（程序占用 jar 时打包会失败，或在 `target` 里留下多余的中间文件，
脚本会直接提示先关闭），然后依次完成：Maven 构建 → 准备只含主 jar 的暂存目录 → 清理旧产物 → jpackage 打包，
最终在 `dist\StudyTimeTracker\` 生成**自带 JRE 的免安装绿色版**（约 170 MB，主要是内置运行时），
整个文件夹拷贝到其他 Windows 电脑即可双击运行，**无需对方安装 Java 或数据库**。

> 绿色版的数据存储在程序目录下的 `data\data.db`，随文件夹一起移动；
> 若程序目录不可写（例如放在 `Program Files` 下），会自动改用
> `%USERPROFILE%\.study-tracker\data.db`。
> 也可以用环境变量 `STT_DATA_DIR` 指定数据目录。

## 八、已知限制

- **科目列表为所有账户共用。** `subject` 表没有 `user_id` 字段，
  学习记录、任务、目标都按账户隔离，但科目清单是公共的。
  若要彻底隔离，需要给该表加字段并迁移历史数据。
- **用户只能停用、不能删除。** 学习记录、任务、目标都通过外键引用用户，
  删除账户会让这些数据失去归属，因此管理界面只提供启用/停用。

## 九、开发进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| 第 1 批 | 项目骨架、主窗口页签、数据库建表脚本 | ✅ 已完成 |
| 第 2 批 | 数据库初始化（DBUtil）与实体类 | ✅ 已完成 |
| 第 3 批 | 科目管理 + 学习计时器 | ✅ 已完成 |
| 第 4 批 | 学习记录管理与待办任务 | ✅ 已完成 |
| 第 5 批 | 学习目标与番茄钟模式 | ✅ 已完成 |
| 第 6 批 | 效率分析指标与五种图表 | ✅ 已完成 |
| 第 7 批 | 外观设置（主题/字号/强调色/圆角/行高/背景图） | ✅ 已完成 |
| 第 8 批 | 数据管理：备份、恢复、CSV 导出 | ✅ 已完成 |
| 第 9 批 | 登录注册、密码加盐哈希、用户角色与用户管理 | ✅ 已完成 |
| 第 10 批 | 系统测试与 jpackage 打包发布 | ✅ 已完成 |

> 每个批次的详细改动、验证结果、环境注意事项与代码复查问题见 [`开发进度.md`](开发进度.md)。