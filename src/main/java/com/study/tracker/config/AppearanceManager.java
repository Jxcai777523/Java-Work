package com.study.tracker.config;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.study.tracker.dao.PreferenceDao;
import com.study.tracker.util.PathUtil;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.RootPaneContainer;

import javax.swing.JTable;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * 外观设置管理器。
 * <p>
 * 负责三件事：
 * <ol>
 *   <li>读取与保存外观偏好（主题、字号、强调色、圆角、表格行高、窗口位置）；</li>
 *   <li>把偏好应用到 Swing 全局默认值，并刷新已经存在的窗口；</li>
 *   <li>向图表等自绘组件提供与当前字号一致的基础字体。</li>
 * </ol>
 * 偏好存放在 SQLite 的 preferences 表，键名见本类常量。
 * <p>
 * 注意：{@link #apply()} 会先安装主题再覆盖字号等默认值。
 * 因为安装主题会把 UIManager 中的默认值重置为主题自带的值，
 * 顺序颠倒会让自定义设置失效。
 */
public final class AppearanceManager {

    /** 偏好键：主题。 */
    public static final String KEY_THEME = "ui.theme";

    /** 偏好键：界面字号。 */
    public static final String KEY_FONT_SIZE = "ui.font.size";

    /** 偏好键：强调色。 */
    public static final String KEY_ACCENT = "ui.accent";

    /** 偏好键：圆角半径。 */
    public static final String KEY_ARC = "ui.arc";

    /** 偏好键：表格行高。 */
    public static final String KEY_TABLE_ROW_HEIGHT = "ui.table.rowHeight";

    /** 偏好键：窗口位置与大小。 */
    public static final String KEY_WINDOW_BOUNDS = "ui.window.bounds";

    /** 偏好键：窗口是否最大化。 */
    public static final String KEY_WINDOW_MAXIMIZED = "ui.window.maximized";

    /** 偏好键：背景图片文件路径。 */
    public static final String KEY_BACKGROUND_IMAGE = "ui.background.image";

    /** 偏好键：背景图片不透明度（百分比）。 */
    public static final String KEY_BACKGROUND_OPACITY = "ui.background.opacity";

    /** 偏好键：背景图片填充方式。 */
    public static final String KEY_BACKGROUND_MODE = "ui.background.mode";

    /** 主题键：浅色。 */
    public static final String THEME_LIGHT = "light";

    /** 主题键：深色。 */
    public static final String THEME_DARK = "dark";

    /** 可选主题键，顺序与 {@link #THEME_NAMES} 一致。 */
    public static final String[] THEME_KEYS = {
            THEME_LIGHT, THEME_DARK
    };

    /** 可选主题名称，顺序与 {@link #THEME_KEYS} 一致。 */
    public static final String[] THEME_NAMES = {
            "浅色", "深色"
    };

    /** 可选的字号（磅）。 */
    public static final int[] FONT_SIZES = {12, 13, 14, 15, 16, 18};

    /** 可选的圆角半径（像素）。 */
    public static final int[] ARC_OPTIONS = {0, 6, 14};

    /** 圆角选项名称，顺序与 {@link #ARC_OPTIONS} 一致。 */
    public static final String[] ARC_NAMES = {"直角", "小圆角", "圆角"};

    /** 可选的表格行高（像素）。 */
    public static final int[] ROW_HEIGHTS = {24, 26, 28, 32, 36};

    /** 默认主题。 */
    private static final String DEFAULT_THEME = THEME_LIGHT;

    /** 默认字号。 */
    private static final int DEFAULT_FONT_SIZE = 14;

    /** 默认强调色：空字符串表示沿用主题自带强调色。 */
    private static final String DEFAULT_ACCENT = "";

    /** 默认圆角半径。 */
    private static final int DEFAULT_ARC = 6;

    /** 默认表格行高。 */
    private static final int DEFAULT_ROW_HEIGHT = 28;

    /** 默认背景图片不透明度。 */
    private static final int DEFAULT_BACKGROUND_OPACITY = 100;

    /** 背景图片复制到数据目录后使用的固定文件名。 */
    private static final String BACKGROUND_FILE_NAME = "background.image";

    /** 填充方式：拉伸铺满。 */
    public static final String MODE_STRETCH = "stretch";

    /** 填充方式：平铺。 */
    public static final String MODE_TILE = "tile";

    /** 填充方式：居中原始大小。 */
    public static final String MODE_CENTER = "center";

    /** 填充方式键，顺序与 MODE_NAMES 一致。 */
    public static final String[] MODE_KEYS = {MODE_STRETCH, MODE_TILE, MODE_CENTER};

    /** 填充方式名称，顺序与 MODE_KEYS 一致。 */
    public static final String[] MODE_NAMES = {"拉伸铺满", "平铺", "居中"};

    private static String theme = DEFAULT_THEME;
    private static int fontSize = DEFAULT_FONT_SIZE;
    private static String accent = DEFAULT_ACCENT;
    private static int arc = DEFAULT_ARC;
    private static int tableRowHeight = DEFAULT_ROW_HEIGHT;
    private static Rectangle windowBounds;
    private static boolean windowMaximized;
    private static String backgroundImagePath;
    private static BufferedImage backgroundImage;
    private static int backgroundOpacity = DEFAULT_BACKGROUND_OPACITY;
    private static String backgroundMode = MODE_STRETCH;

    private AppearanceManager() {
    }

    /** 从数据库读取外观偏好，读取失败时保留默认值。 */
    public static void load() {
        Map<String, String> prefs;
        try {
            prefs = new PreferenceDao().findAll();
        } catch (RuntimeException e) {
            System.err.println("[Appearance] 读取外观设置失败，改用默认值: " + e.getMessage());
            return;
        }

        theme = normalizeTheme(prefs.get(KEY_THEME));
        fontSize = parsePositiveInt(prefs.get(KEY_FONT_SIZE), DEFAULT_FONT_SIZE);
        accent = normalizeAccent(prefs.get(KEY_ACCENT));
        arc = parseNonNegativeInt(prefs.get(KEY_ARC), DEFAULT_ARC);
        tableRowHeight = parsePositiveInt(prefs.get(KEY_TABLE_ROW_HEIGHT), DEFAULT_ROW_HEIGHT);
        windowBounds = parseBounds(prefs.get(KEY_WINDOW_BOUNDS));
        windowMaximized = "1".equals(prefs.get(KEY_WINDOW_MAXIMIZED));
        backgroundImagePath = prefs.get(KEY_BACKGROUND_IMAGE);
        backgroundOpacity = parsePositiveInt(prefs.get(KEY_BACKGROUND_OPACITY), DEFAULT_BACKGROUND_OPACITY);
        backgroundMode = normalizeMode(prefs.get(KEY_BACKGROUND_MODE));
        loadBackgroundImage();
    }

    /** 把全部外观偏好写回数据库；写库失败只记录日志，不影响界面。 */
    public static void persist() {
        PreferenceDao dao = new PreferenceDao();
        try {
            dao.save(KEY_THEME, theme);
            dao.save(KEY_FONT_SIZE, String.valueOf(fontSize));
            dao.save(KEY_ACCENT, accent);
            dao.save(KEY_ARC, String.valueOf(arc));
            dao.save(KEY_TABLE_ROW_HEIGHT, String.valueOf(tableRowHeight));
            dao.save(KEY_BACKGROUND_IMAGE, backgroundImagePath == null ? "" : backgroundImagePath);
            dao.save(KEY_BACKGROUND_OPACITY, String.valueOf(backgroundOpacity));
            dao.save(KEY_BACKGROUND_MODE, backgroundMode);
        } catch (RuntimeException e) {
            System.err.println("[Appearance] 保存外观设置失败: " + e.getMessage());
        }
    }

    /**
     * 安装当前主题与各项界面默认值，并刷新已经打开的窗口。
     * <p>
     * 启动阶段必须在创建任何 Swing 组件之前调用；运行期调用即等于实时预览。
     */
    public static void apply() {
        // 强调色通过 FlatLaf 的主题变量注入，必须在创建外观实例之前设置
        FlatLaf.setGlobalExtraDefaults(accent.isEmpty() ? Map.of() : Map.of("@accentColor", accent));

        try {
            UIManager.setLookAndFeel(createLookAndFeel());
        } catch (Exception e) {
            System.err.println("[Appearance] 主题安装失败，回退浅色主题: " + e.getMessage());
            FlatLightLaf.setup();
        }

        // 主题安装会重置以下默认值，必须在其后覆盖
        UIManager.put("defaultFont", new Font(AppConfig.UI_FONT_NAME, Font.PLAIN, fontSize));
        UIManager.put("Table.rowHeight", tableRowHeight);
        applyArcDefaults();

        FlatLaf.updateUI();
        for (Window window : Window.getWindows()) {
            applyTableRowHeight(window);
        }
        applyBackground();
    }

    /** 恢复出厂外观设置（不包含窗口位置）。 */
    public static void resetToDefaults() {
        theme = DEFAULT_THEME;
        fontSize = DEFAULT_FONT_SIZE;
        accent = DEFAULT_ACCENT;
        arc = DEFAULT_ARC;
        tableRowHeight = DEFAULT_ROW_HEIGHT;
        backgroundOpacity = DEFAULT_BACKGROUND_OPACITY;
        backgroundMode = MODE_STRETCH;
        clearBackgroundImage(false);
        persist();
        apply();
    }

    /**
     * 返回与当前界面字号一致的基础字体。
     *
     * @param style     字体样式，取 {@link Font} 中的样式常量
     * @param sizeDelta 相对基础字号的偏移（磅），可为负
     * @return 派生出的字体
     */
    public static Font uiFont(int style, float sizeDelta) {
        Font base = UIManager.getFont("defaultFont");
        if (base == null) {
            base = new Font(AppConfig.UI_FONT_NAME, Font.PLAIN, fontSize);
        }
        return base.deriveFont(style, Math.max(9f, base.getSize() + sizeDelta));
    }

    /**
     * 读取上次保存的窗口位置。
     *
     * @return 与当前屏幕有交集时返回位置，否则返回 {@code null} 表示按默认方式居中
     */
    public static Rectangle loadWindowBounds() {
        if (windowBounds == null || GraphicsEnvironment.isHeadless()) {
            return null;
        }
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if (device.getDefaultConfiguration().getBounds().intersects(windowBounds)) {
                return new Rectangle(windowBounds);
            }
        }
        return null;
    }

    /** 上次退出时窗口是否为最大化。 */
    public static boolean isWindowMaximized() {
        return windowMaximized;
    }

    /**
     * 记录窗口位置与最大化状态并立即保存。
     *
     * @param bounds    窗口位置与大小；最大化时传 null 以免覆盖正常尺寸
     * @param maximized 是否处于最大化状态
     */
    public static void saveWindowState(Rectangle bounds, boolean maximized) {
        if (!maximized && bounds != null) {
            windowBounds = new Rectangle(bounds);
        }
        windowMaximized = maximized;

        PreferenceDao dao = new PreferenceDao();
        try {
            if (windowBounds != null) {
                dao.save(KEY_WINDOW_BOUNDS, windowBounds.x + "," + windowBounds.y
                        + "," + windowBounds.width + "," + windowBounds.height);
            }
            dao.save(KEY_WINDOW_MAXIMIZED, windowMaximized ? "1" : "0");
        } catch (RuntimeException e) {
            System.err.println("[Appearance] 保存窗口状态失败: " + e.getMessage());
        }
    }

    /** 当前背景图片；未设置或读取失败时为 null。 */
    public static BufferedImage getBackgroundImage() {
        return backgroundImage;
    }

    /** 背景图片当前的文件路径，未设置时返回 null。 */
    public static String getBackgroundImagePath() {
        return backgroundImagePath;
    }

    /** 背景图片不透明度（百分比）。 */
    public static int getBackgroundOpacity() {
        return backgroundOpacity;
    }

    public static void setBackgroundOpacity(int value) {
        backgroundOpacity = Math.max(10, Math.min(100, value));
    }

    /** 背景图片填充方式。 */
    public static String getBackgroundMode() {
        return backgroundMode;
    }

    public static void setBackgroundMode(String value) {
        backgroundMode = normalizeMode(value);
    }

    /**
     * 设置背景图片。
     * <p>
     * 图片会被复制到程序数据目录下（固定文件名 background.image），
     * 这样原图被移动或删除后背景仍然有效。
     *
     * @param source 用户选择的图片文件
     * @return 设置成功返回 true；文件无法读取时返回 false
     */
    public static boolean setBackgroundImage(File source) {
        if (source == null) {
            return false;
        }
        try {
            Path target = PathUtil.getDataDir().resolve(BACKGROUND_FILE_NAME);
            Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            BufferedImage loaded = ImageIO.read(target.toFile());
            if (loaded == null) {
                return false;
            }
            backgroundImage = loaded;
            backgroundImagePath = target.toAbsolutePath().toString();
            return true;
        } catch (IOException e) {
            System.err.println("[Appearance] 设置背景图片失败: " + e.getMessage());
            return false;
        }
    }

    /** 清除背景图片；persistNow 为 false 时只改内存状态。 */
    public static void clearBackgroundImage(boolean persistNow) {
        backgroundImage = null;
        backgroundImagePath = null;
        if (persistNow) {
            persist();
            applyBackground();
        }
    }

    /**
     * 按当前是否设置背景图，调整各面板的透明状态并重绘窗口。
     * <p>
     * 背景图存在时把普通面板设为透明，让图片透出来；
     * 图表面板（XChart）与表格、输入框等保持不透明，避免影响内容辨识。
     */
    public static void applyBackground() {
        boolean hasImage = backgroundImage != null;
        for (Window window : Window.getWindows()) {
            if (!(window instanceof RootPaneContainer rootPane)) {
                continue;
            }
            Container content = rootPane.getContentPane();
            if (content == null) {
                continue;
            }
            for (Component child : content.getComponents()) {
                applyBackgroundTransparency(child, hasImage);
            }
            window.repaint();
        }
    }

    public static String getTheme() {
        return theme;
    }

    public static void setTheme(String value) {
        theme = normalizeTheme(value);
    }

    public static int getFontSize() {
        return fontSize;
    }

    public static void setFontSize(int value) {
        fontSize = value;
    }

    public static String getAccent() {
        return accent;
    }

    public static void setAccent(String value) {
        accent = normalizeAccent(value);
    }

    public static int getArc() {
        return arc;
    }

    public static void setArc(int value) {
        arc = Math.max(0, value);
    }

    public static int getTableRowHeight() {
        return tableRowHeight;
    }

    public static void setTableRowHeight(int value) {
        tableRowHeight = Math.max(16, value);
    }

    /** 主题键对应的显示名称。 */
    public static String themeName() {
        for (int i = 0; i < THEME_KEYS.length; i++) {
            if (THEME_KEYS[i].equals(theme)) {
                return THEME_NAMES[i];
            }
        }
        return THEME_NAMES[0];
    }

    /** 安装圆角相关的默认值，键名均已对照 FlatLaf 3.4.1 核实。 */
    private static void applyArcDefaults() {
        UIManager.put("Component.arc", arc);
        UIManager.put("Button.arc", arc);
        UIManager.put("TextComponent.arc", arc);
        UIManager.put("CheckBox.arc", arc);
        UIManager.put("ProgressBar.arc", arc);
        UIManager.put("Table.arc", arc);
        UIManager.put("TabbedPane.tabArc", arc);
        // 滚动条滑块在圆角模式下用足够大的值取整圆形，直角模式下取 0
        UIManager.put("ScrollBar.thumbArc", arc == 0 ? 0 : 999);
        UIManager.put("ScrollBar.trackArc", arc == 0 ? 0 : 999);
    }

    /** 递归设置组件树中所有表格的行高。 */
    public static void applyTableRowHeight(Component component) {
        if (component instanceof JTable table) {
            table.setRowHeight(tableRowHeight);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyTableRowHeight(child);
            }
        }
    }

    /** 递归调整面板透明度；图表面板与容器类控件维持原样。 */
    private static void applyBackgroundTransparency(Component component, boolean hasImage) {
        if ((component instanceof JPanel || component instanceof JTabbedPane)
                && !component.getClass().getName().startsWith("org.knowm.xchart")) {
            ((JComponent) component).setOpaque(!hasImage);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyBackgroundTransparency(child, hasImage);
            }
        }
    }

    /** 从磁盘读取背景图片，文件不存在或损坏时视为未设置。 */
    private static void loadBackgroundImage() {
        backgroundImage = null;
        if (backgroundImagePath == null || backgroundImagePath.isBlank()) {
            return;
        }
        File file = new File(backgroundImagePath);
        if (!file.isFile()) {
            System.err.println("[Appearance] 背景图片不存在，已忽略: " + backgroundImagePath);
            return;
        }
        try {
            backgroundImage = ImageIO.read(file);
        } catch (IOException e) {
            System.err.println("[Appearance] 背景图片读取失败: " + e.getMessage());
        }
    }

    /** 填充方式键非法时回退默认值。 */
    private static String normalizeMode(String value) {
        if (value != null) {
            for (String key : MODE_KEYS) {
                if (key.equals(value)) {
                    return key;
                }
            }
        }
        return MODE_STRETCH;
    }

    /** 根据主题键创建外观实例。 */
    private static LookAndFeel createLookAndFeel() {
        return switch (theme) {
            case THEME_DARK -> new FlatDarkLaf();
            default -> new FlatLightLaf();
        };
    }

    /**
     * 主题只保留浅色与深色。
     * <p>
     * 早期版本存过 intellij / darcula / mac-light，这里按明暗归并：
     * darcula 归入深色，其余一律按浅色处理。
     */
    private static String normalizeTheme(String value) {
        if (value == null) {
            return DEFAULT_THEME;
        }
        String key = value.trim().toLowerCase();
        return switch (key) {
            case THEME_DARK, "darcula" -> THEME_DARK;
            default -> THEME_LIGHT;
        };
    }

    /** 强调色只接受 #RRGGBB 形式，其余一律视为"沿用主题色"。 */
    private static String normalizeAccent(String value) {
        if (value != null && value.matches("#[0-9a-fA-F]{6}")) {
            return value.toLowerCase();
        }
        return DEFAULT_ACCENT;
    }

    /** 解析窗口位置，格式为 x,y,width,height。 */
    private static Rectangle parseBounds(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 4) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int width = Integer.parseInt(parts[2].trim());
            int height = Integer.parseInt(parts[3].trim());
            if (width <= 0 || height <= 0) {
                return null;
            }
            return new Rectangle(x, y, width, height);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parsePositiveInt(String value, int fallback) {
        int parsed = parseNonNegativeInt(value, fallback);
        return parsed > 0 ? parsed : fallback;
    }

    private static int parseNonNegativeInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
