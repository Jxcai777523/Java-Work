package com.study.tracker.view;

import com.study.tracker.config.AppearanceManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * 外观设置面板。
 * <p>
 * 可调整主题、界面字号、强调色、控件圆角与表格行高。
 * 每次修改都会立即预览、写入数据库，并通过回调通知调用方刷新图表等自绘内容。
 * 窗口位置与大小不在本面板中设置，由主窗口在关闭时自动记忆。
 */
public class AppearancePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** 强调色选项名称，第一项表示沿用主题自带强调色。 */
    private static final String[] ACCENT_NAMES = {"主题默认", "蓝", "绿", "橙", "紫", "红"};

    /** 强调色选项取值，空字符串表示沿用主题自带强调色。 */
    private static final String[] ACCENT_VALUES = {"", "#2D7FF9", "#2E7D32", "#E8710A", "#7B4DDB", "#D93025"};

    private final JComboBox<String> themeCombo = new JComboBox<>(AppearanceManager.THEME_NAMES);
    private final JComboBox<String> fontCombo = new JComboBox<>();
    private final JComboBox<String> arcCombo = new JComboBox<>(AppearanceManager.ARC_NAMES);
    private final JComboBox<String> rowHeightCombo = new JComboBox<>();

    private final JSlider opacitySlider = new JSlider(10, 100, 100);
    private final JLabel opacityValue = new JLabel("100%");
    private final JLabel imageStatus = new JLabel("未设置");
    private final JComboBox<String> backgroundModeCombo = new JComboBox<>(AppearanceManager.MODE_NAMES);

    private final List<JButton> accentButtons = new ArrayList<>();

    /** 应用设置后的回调，用于刷新图表等自绘组件；允许为 null。 */
    private final Runnable onApplied;

    /** 载入控件初值期间为 true，用于抑制监听器误触发。 */
    private boolean loading = true;

    /** 不刷新任何外部内容的构造方式。 */
    public AppearancePanel() {
        this(null);
    }

    /**
     * 创建外观设置面板。
     *
     * @param onApplied 设置生效后的回调，用于刷新图表等自绘内容；允许为 null
     */
    public AppearancePanel(Runnable onApplied) {
        this.onApplied = onApplied;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(createForm());
        content.add(Box.createVerticalStrut(14));
        content.add(createFooter());
        content.add(Box.createVerticalGlue());
        add(content, BorderLayout.CENTER);

        loadCurrentValues();
        loading = false;
    }

    /** 设置表单。 */
    private JPanel createForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("界面外观"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(9, 12, 9, 12);
        gbc.anchor = GridBagConstraints.WEST;

        themeCombo.setPreferredSize(new Dimension(190, 30));
        fontCombo.setPreferredSize(new Dimension(190, 30));
        arcCombo.setPreferredSize(new Dimension(190, 30));
        rowHeightCombo.setPreferredSize(new Dimension(190, 30));
        backgroundModeCombo.setPreferredSize(new Dimension(190, 30));
        opacitySlider.setPreferredSize(new Dimension(230, 30));
        opacitySlider.setFocusable(false);

        for (int fontSize : AppearanceManager.FONT_SIZES) {
            fontCombo.addItem(fontSize + " 磅");
        }
        for (int rowHeight : AppearanceManager.ROW_HEIGHTS) {
            rowHeightCombo.addItem(rowHeight + " 像素");
        }

        themeCombo.addActionListener(e -> {
            if (!loading) {
                AppearanceManager.setTheme(AppearanceManager.THEME_KEYS[themeCombo.getSelectedIndex()]);
                applyAndSave();
            }
        });
        fontCombo.addActionListener(e -> {
            if (!loading) {
                AppearanceManager.setFontSize(AppearanceManager.FONT_SIZES[fontCombo.getSelectedIndex()]);
                applyAndSave();
            }
        });
        arcCombo.addActionListener(e -> {
            if (!loading) {
                AppearanceManager.setArc(AppearanceManager.ARC_OPTIONS[arcCombo.getSelectedIndex()]);
                applyAndSave();
            }
        });
        rowHeightCombo.addActionListener(e -> {
            if (!loading) {
                AppearanceManager.setTableRowHeight(AppearanceManager.ROW_HEIGHTS[rowHeightCombo.getSelectedIndex()]);
                applyAndSave();
            }
        });
        backgroundModeCombo.addActionListener(e -> {
            if (!loading) {
                AppearanceManager.setBackgroundMode(AppearanceManager.MODE_KEYS[backgroundModeCombo.getSelectedIndex()]);
                persistAndRefreshBackground();
            }
        });

        addRow(form, gbc, 0, "主题", themeCombo);
        addRow(form, gbc, 1, "界面字号", fontCombo);
        addRow(form, gbc, 2, "强调色", createAccentPanel());
        addRow(form, gbc, 3, "控件圆角", arcCombo);
        addRow(form, gbc, 4, "表格行高", rowHeightCombo);
        addRow(form, gbc, 5, "背景图片", createBackgroundControl());
        addRow(form, gbc, 6, "图片透明度", createOpacityControl());
        addRow(form, gbc, 7, "图片填充方式", backgroundModeCombo);

        return form;
    }

    /** 背景图片选择区：选择、清除与当前状态。 */
    private JPanel createBackgroundControl() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

        JButton chooseButton = new JButton("选择图片…");
        chooseButton.setPreferredSize(new Dimension(106, 30));
        chooseButton.addActionListener(e -> chooseBackgroundImage());

        JButton clearButton = new JButton("清除");
        clearButton.setPreferredSize(new Dimension(72, 30));
        clearButton.addActionListener(e -> {
            AppearanceManager.clearBackgroundImage(true);
            updateImageStatus();
        });

        panel.add(chooseButton);
        panel.add(clearButton);
        panel.add(imageStatus);
        return panel;
    }

    /** 图片透明度控制：滑杆加百分比显示。 */
    private JPanel createOpacityControl() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        opacityValue.setPreferredSize(new Dimension(48, 24));

        opacitySlider.addChangeListener(e -> {
            opacityValue.setText(opacitySlider.getValue() + "%");
            if (!loading && !opacitySlider.getValueIsAdjusting()) {
                AppearanceManager.setBackgroundOpacity(opacitySlider.getValue());
                persistAndRefreshBackground();
            }
        });

        panel.add(opacitySlider);
        panel.add(opacityValue);
        return panel;
    }

    /** 选择背景图片并立即生效。 */
    private void chooseBackgroundImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择背景图片");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "图片文件 (png, jpg, jpeg, gif, bmp)", "png", "jpg", "jpeg", "gif", "bmp"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        if (AppearanceManager.setBackgroundImage(chooser.getSelectedFile())) {
            persistAndRefreshBackground();
            updateImageStatus();
        } else {
            JOptionPane.showMessageDialog(this,
                    "这个文件无法作为背景图片读取，请换一张图片试试。",
                    "设置失败", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** 只保存并刷新背景，不重建整体外观（避免换透明度时闪动）。 */
    private void persistAndRefreshBackground() {
        AppearanceManager.persist();
        AppearanceManager.applyBackground();
    }

    /** 刷新背景图片状态文字。 */
    private void updateImageStatus() {
        String path = AppearanceManager.getBackgroundImagePath();
        imageStatus.setText(path == null ? "未设置" : "已设置");
        imageStatus.setToolTipText(path);
    }

    /** 强调色选择区：一组色块加一个自定义按钮。 */
    private JPanel createAccentPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        accentButtons.clear();

        JButton defaultButton = new JButton(ACCENT_NAMES[0]);
        defaultButton.setPreferredSize(new Dimension(96, 30));
        defaultButton.setToolTipText("使用主题自带的强调色");
        defaultButton.addActionListener(e -> chooseAccent(ACCENT_VALUES[0]));
        panel.add(defaultButton);
        accentButtons.add(defaultButton);

        for (int i = 1; i < ACCENT_VALUES.length; i++) {
            String value = ACCENT_VALUES[i];
            JButton swatch = new JButton();
            swatch.setPreferredSize(new Dimension(46, 30));
            swatch.setToolTipText(ACCENT_NAMES[i] + " " + value);
            swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            swatch.setFocusable(false);
            swatch.addActionListener(e -> chooseAccent(value));
            panel.add(swatch);
            accentButtons.add(swatch);
        }

        JButton customButton = new JButton("自定义…");
        customButton.setPreferredSize(new Dimension(104, 30));
        customButton.addActionListener(e -> pickCustomAccent());
        panel.add(customButton);

        return panel;
    }

    /** 底部说明与恢复默认按钮。 */
    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());

        JLabel hint = new JLabel("修改后立即生效，并自动保存到数据库。窗口大小与位置在退出时自动记忆。");
        Color hintColor = UIManager.getColor("Label.disabledForeground");
        hint.setForeground(hintColor != null ? hintColor : Color.GRAY);

        JButton resetButton = new JButton("恢复默认外观");
        resetButton.addActionListener(e -> {
            AppearanceManager.resetToDefaults();
            loadCurrentValues();
            if (onApplied != null) {
                onApplied.run();
            }
        });

        footer.add(hint, BorderLayout.WEST);
        footer.add(resetButton, BorderLayout.EAST);
        return footer;
    }

    /** 往表单里加一行："标签 + 控件"。 */
    private void addRow(JPanel form, GridBagConstraints gbc, int row, String label, JComponent control) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(control, gbc);
    }

    /** 选择强调色；空字符串表示沿用主题默认色。 */
    private void chooseAccent(String value) {
        AppearanceManager.setAccent(value);
        updateAccentSelection();
        if (!loading) {
            applyAndSave();
        }
    }

    /** 打开调色板自定义强调色。 */
    private void pickCustomAccent() {
        String current = AppearanceManager.getAccent();
        Color initial = current.isEmpty() ? UIManager.getColor("Component.accentColor") : Color.decode(current);
        if (initial == null) {
            initial = new Color(0x2D7FF9);
        }

        Color chosen = JColorChooser.showDialog(this, "选择强调色", initial);
        if (chosen != null) {
            chooseAccent(String.format("#%02x%02x%02x", chosen.getRed(), chosen.getGreen(), chosen.getBlue()));
        }
    }

    /** 把当前设置同步到各个控件。 */
    private void loadCurrentValues() {
        boolean previous = loading;
        loading = true;

        themeCombo.setSelectedIndex(indexOf(AppearanceManager.THEME_KEYS, AppearanceManager.getTheme()));
        fontCombo.setSelectedIndex(indexOf(AppearanceManager.FONT_SIZES, AppearanceManager.getFontSize()));
        arcCombo.setSelectedIndex(indexOf(AppearanceManager.ARC_OPTIONS, AppearanceManager.getArc()));
        rowHeightCombo.setSelectedIndex(indexOf(AppearanceManager.ROW_HEIGHTS, AppearanceManager.getTableRowHeight()));
        backgroundModeCombo.setSelectedIndex(indexOf(AppearanceManager.MODE_KEYS, AppearanceManager.getBackgroundMode()));
        opacitySlider.setValue(AppearanceManager.getBackgroundOpacity());
        opacityValue.setText(opacitySlider.getValue() + "%");
        updateImageStatus();
        updateAccentSelection();

        loading = previous;
    }

    /** 刷新色块的选中状态。 */
    private void updateAccentSelection() {
        String current = AppearanceManager.getAccent();
        if (accentButtons.isEmpty()) {
            return;
        }

        accentButtons.get(0).setIcon(null);
        for (int i = 1; i < accentButtons.size() && i < ACCENT_VALUES.length; i++) {
            String value = ACCENT_VALUES[i];
            accentButtons.get(i).setIcon(new SwatchIcon(Color.decode(value), value.equals(current)));
        }
    }

    /** 保存并立即应用设置。 */
    private void applyAndSave() {
        AppearanceManager.persist();
        AppearanceManager.apply();
        if (onApplied != null) {
            onApplied.run();
        }
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return 0;
    }

    /** 色块图标；被选中时多画一圈深色描边。 */
    private static final class SwatchIcon implements Icon {

        private static final int SIZE = 18;

        private final Color color;
        private final boolean selected;

        SwatchIcon(Color color, boolean selected) {
            this.color = color;
            this.selected = selected;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillRoundRect(x, y, SIZE, SIZE, 5, 5);
                g2.setColor(selected ? Color.DARK_GRAY : new Color(0x808080));
                g2.drawRoundRect(x, y, SIZE - 1, SIZE - 1, 5, 5);
                if (selected) {
                    g2.setColor(Color.WHITE);
                    g2.drawRoundRect(x + 2, y + 2, SIZE - 5, SIZE - 5, 4, 4);
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
