package com.study.tracker.chart;

import com.study.tracker.config.AppearanceManager;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Scrollable;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * GitHub 风格的区间学习热力图。
 * <p>
 * 每周一列、周一至周日共七行，方块之间留出间隙。方块共有三种状态：
 * <ul>
 *   <li>有学习记录：按当天时长落在五个色阶之一，颜色越深时间越长；</li>
 *   <li>当天没有学习：填充浅灰色，表示"这一天在统计范围内，只是没学"；</li>
 *   <li>统计区间之外：只画极淡的虚线边框，表示"这一天不在本次统计范围内"。</li>
 * </ul>
 * 左侧标注星期，顶部标注月份，底部给出每一档颜色对应的时长区间。
 * 鼠标悬停可预览当天数据，点击方块会弹出详情并高亮该方块。
 */
public final class HeatMapPanel extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;

    /** 色阶，由浅到深；第 0 档用于当天没有学习记录的日期。 */
    private static final Color[] LEVEL_COLORS = {
            new Color(0xD8DEE4), new Color(0xC6E48C), new Color(0x7BC96F),
            new Color(0x3D9A50), new Color(0x1F6B36)
    };

    /** 每一档颜色对应的时长区间说明。 */
    private static final String[] LEVEL_LABELS = {
            "0", "1-29", "30-59", "60-119", "120+"
    };

    /** 统计区间之外的图例说明。 */
    private static final String OUT_OF_RANGE_LABEL = "区间外";

    /** 文字颜色。 */
    private static final Color TEXT_COLOR = new Color(0x586069);

    /** 统计区间之外格子的描边颜色。 */
    private static final Color OUT_OF_RANGE_COLOR = new Color(0xDFE3E8);

    /** 统计区间之外格子的虚线样式。 */
    private static final Stroke OUTLINE_DASH =
            new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{2f, 2f}, 0f);

    /** 选中方块的高亮颜色。 */
    private static final Color SELECT_COLOR = new Color(0x24292F);

    /** 方块最大边长。 */
    private static final int CELL_MAX = 20;

    /** 方块最小边长，区间过长时按此值压缩。 */
    private static final int CELL_MIN = 7;

    /** 方块间距。 */
    private static final int CELL_GAP = 3;

    /** 左侧星期标签占用的宽度。 */
    private static final int LABEL_LEFT = 38;

    /** 顶部月份标签占用的高度。 */
    private static final int LABEL_TOP = 22;

    /** 底部图例占用的高度。 */
    private static final int LEGEND_HEIGHT = 30;

    /** 顶部留白。 */
    private static final int PADDING_TOP = 6;

    /** 右侧留白。 */
    private static final int PADDING_RIGHT = 14;

    /** 星期名称，与行号一一对应。 */
    private static final String[] WEEKDAY_TEXT = {
            "周一", "周二", "周三", "周四", "周五", "周六", "周日"
    };

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("M月");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LocalDate from;
    private final LocalDate to;
    private final Map<LocalDate, Integer> dailyMinutes;
    private final LocalDate firstMonday;
    private final int weekCount;

    private int cellSize = CELL_MAX;
    private int gridX;
    private int gridY;

    /** 当前被点击选中的日期，未选中时为 null。 */
    private LocalDate selectedDate;

    /** 点击后弹出的详情浮层，复用以避免同时出现多个。 */
    private JPopupMenu detailPopup;

    /**
     * 创建热力图面板。
     *
     * @param from         区间开始日期
     * @param to           区间结束日期
     * @param dailyMinutes 日期 → 当天学习分钟数
     */
    public HeatMapPanel(LocalDate from, LocalDate to, Map<LocalDate, Integer> dailyMinutes) {
        this.from = from;
        this.to = to;
        this.dailyMinutes = dailyMinutes;

        LocalDate monday = from.with(DayOfWeek.MONDAY);
        if (monday.isAfter(from)) {
            monday = monday.minusWeeks(1);
        }
        this.firstMonday = monday;
        this.weekCount = (int) ((to.toEpochDay() - monday.toEpochDay()) / 7) + 1;

        setOpaque(false);
        setFocusable(false);
        setFont(AppearanceManager.uiFont(Font.PLAIN, -3));
        installMouseHandling();
    }

    /**
     * 首选尺寸。
     * <p>
     * 宽度按"方块取最小边长"计算：面板放得下时会被滚动面板拉伸到视口宽度，
     * 方块随之放大；放不下时保持这个宽度，由滚动条提供横向浏览。
     * 高度按最大方块计算，保证图例与星期标签始终有位置。
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(requiredWidth(),
                PADDING_TOP + LABEL_TOP + 7 * (CELL_MAX + CELL_GAP) + LEGEND_HEIGHT);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    /** 方块取最小边长时，完整画下整个网格所需的宽度。 */
    private int requiredWidth() {
        return LABEL_LEFT + weekCount * (CELL_MIN + CELL_GAP) - CELL_GAP + PADDING_RIGHT;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return cellSize + CELL_GAP;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(cellSize + CELL_GAP, (orientation == javax.swing.SwingConstants.HORIZONTAL
                ? visibleRect.width : visibleRect.height) - cellSize - CELL_GAP);
    }

    /** 视口足够宽时铺满视口，宽度不够时保持所需宽度以触发横向滚动。 */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        Container parent = getParent();
        return parent == null || parent.getWidth() <= 0 || requiredWidth() <= parent.getWidth();
    }

    /** 始终跟随视口高度，使网格在垂直方向居中。 */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return true;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int available = getWidth() - LABEL_LEFT - PADDING_RIGHT;
        int raw = (available - (weekCount - 1) * CELL_GAP) / Math.max(1, weekCount);
        cellSize = Math.max(CELL_MIN, Math.min(CELL_MAX, raw));

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(getFont());

            int step = cellSize + CELL_GAP;
            int gridWidth = weekCount * step - CELL_GAP;
            int gridHeight = 7 * step - CELL_GAP;
            int blockHeight = LABEL_TOP + gridHeight + LEGEND_HEIGHT;
            gridX = Math.max(LABEL_LEFT, (getWidth() - gridWidth) / 2);
            gridY = Math.max(PADDING_TOP, (getHeight() - blockHeight) / 2) + LABEL_TOP;

            paintMonthLabels(g2, step);
            paintWeekdayLabels(g2, step);
            paintCells(g2, step);
            paintLegend(g2, gridHeight);
        } finally {
            g2.dispose();
        }
    }

    /**
     * 顶部月份标签，仅在月份发生变化的列上绘制。
     * <p>
     * 月份取该列中第一个落在统计区间内的日期。若直接用该列周一所在的月份，
     * 区间起点位于月中时，第一列会被标成上一个月（例如 9 月的第一列标成 8 月）。
     */
    private void paintMonthLabels(Graphics2D g2, int step) {
        g2.setColor(TEXT_COLOR);
        int lastYear = -1;
        int lastMonth = -1;
        for (int week = 0; week < weekCount; week++) {
            LocalDate first = columnFirstDate(week);
            if (first == null) {
                continue;
            }
            if (first.getYear() != lastYear || first.getMonthValue() != lastMonth) {
                g2.drawString(first.format(MONTH_FORMAT), gridX + week * step, gridY - 7);
                lastYear = first.getYear();
                lastMonth = first.getMonthValue();
            }
        }
    }

    /**
     * 返回某一列中第一个落在统计区间内的日期。
     *
     * @param week 列序号（0 表示第一列）
     * @return 该列的起始有效日期；整列都在区间外时返回 null
     */
    private LocalDate columnFirstDate(int week) {
        LocalDate start = firstMonday.plusWeeks(week);
        LocalDate end = start.plusDays(6);
        if (end.isBefore(from) || start.isAfter(to)) {
            return null;
        }
        return start.isBefore(from) ? from : start;
    }

    /** 左侧星期标签，只标注周一、周三、周五，避免拥挤。 */
    private void paintWeekdayLabels(Graphics2D g2, int step) {
        g2.setColor(TEXT_COLOR);
        FontMetrics fm = g2.getFontMetrics();
        for (int row = 0; row < 7; row += 2) {
            String text = WEEKDAY_TEXT[row];
            int x = gridX - 8 - fm.stringWidth(text);
            int y = gridY + row * step + (cellSize + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(text, x, y);
        }
    }

    /**
     * 逐格绘制方块。
     * <p>
     * 区间内的日期按色阶填充；被选中的方块额外描边；区间之外的日期只画虚线边框。
     */
    private void paintCells(Graphics2D g2, int step) {
        Stroke originalStroke = g2.getStroke();
        for (int week = 0; week < weekCount; week++) {
            for (int row = 0; row < 7; row++) {
                LocalDate date = firstMonday.plusDays((long) week * 7 + row);
                int x = gridX + week * step;
                int y = gridY + row * step;

                if (date.isBefore(from) || date.isAfter(to)) {
                    g2.setColor(OUT_OF_RANGE_COLOR);
                    g2.setStroke(OUTLINE_DASH);
                    g2.drawRoundRect(x, y, cellSize - 1, cellSize - 1, 4, 4);
                    g2.setStroke(originalStroke);
                    continue;
                }

                int minutes = dailyMinutes.getOrDefault(date, 0);
                g2.setColor(LEVEL_COLORS[levelOf(minutes)]);
                g2.fillRoundRect(x, y, cellSize, cellSize, 4, 4);

                if (date.equals(selectedDate)) {
                    g2.setColor(SELECT_COLOR);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(x + 1, y + 1, cellSize - 3, cellSize - 3, 4, 4);
                    g2.setStroke(originalStroke);
                }
            }
        }
        g2.setStroke(originalStroke);
    }

    /** 底部图例：写明每一档颜色对应的学习时长区间，并说明区间外格子的样式。 */
    private void paintLegend(Graphics2D g2, int gridHeight) {
        FontMetrics fm = g2.getFontMetrics();
        int box = Math.max(10, Math.min(cellSize, 13));
        int textGap = 4;
        int itemGap = 14;
        String caption = "学习时长（分钟）";
        int captionWidth = fm.stringWidth(caption) + itemGap;

        int totalWidth = captionWidth;
        for (int i = 0; i < LEVEL_COLORS.length; i++) {
            totalWidth += box + textGap + fm.stringWidth(LEVEL_LABELS[i]) + itemGap;
        }
        totalWidth += box + textGap + fm.stringWidth(OUT_OF_RANGE_LABEL);

        int contentWidth = getWidth() - LABEL_LEFT - PADDING_RIGHT;
        int startX = LABEL_LEFT + Math.max(0, (contentWidth - totalWidth) / 2);
        int startY = gridY + gridHeight + 14;

        g2.setColor(TEXT_COLOR);
        g2.drawString(caption, startX, startY + box - 2);

        int x = startX + captionWidth;
        for (int i = 0; i < LEVEL_COLORS.length; i++) {
            g2.setColor(LEVEL_COLORS[i]);
            g2.fillRoundRect(x, startY, box, box, 3, 3);
            g2.setColor(TEXT_COLOR);
            g2.drawString(LEVEL_LABELS[i], x + box + textGap, startY + box - 2);
            x += box + textGap + fm.stringWidth(LEVEL_LABELS[i]) + itemGap;
        }

        Stroke originalStroke = g2.getStroke();
        g2.setColor(OUT_OF_RANGE_COLOR);
        g2.setStroke(OUTLINE_DASH);
        g2.drawRoundRect(x, startY, box - 1, box - 1, 3, 3);
        g2.setStroke(originalStroke);
        g2.setColor(TEXT_COLOR);
        g2.drawString(OUT_OF_RANGE_LABEL, x + box + textGap, startY + box - 2);
    }

    /**
     * 把当天学习分钟数映射到色阶档位。
     * <p>
     * 分档阈值：0 分钟为第 0 档，1～29 分钟为第 1 档，30～59 分钟为第 2 档，
     * 60～119 分钟为第 3 档，120 分钟及以上为第 4 档。
     */
    private static int levelOf(int minutes) {
        if (minutes <= 0) {
            return 0;
        }
        if (minutes < 30) {
            return 1;
        }
        if (minutes < 60) {
            return 2;
        }
        if (minutes < 120) {
            return 3;
        }
        return 4;
    }

    /** 注册悬停提示与点击查看详情。 */
    private void installMouseHandling() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                LocalDate date = dateAt(e.getX(), e.getY());
                setToolTipText(date == null ? null : detailHtml(date));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setToolTipText(null);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                select(dateAt(e.getX(), e.getY()), e.getX(), e.getY());
            }
        };
        addMouseMotionListener(adapter);
        addMouseListener(adapter);
        setToolTipText("");
    }

    /** 选中某个日期并弹出详情；传入 null 表示取消选中。 */
    private void select(LocalDate date, int px, int py) {
        if (detailPopup != null) {
            detailPopup.setVisible(false);
        }
        selectedDate = date;
        repaint();

        if (date == null || !isShowing()) {
            return;
        }

        JLabel label = new JLabel(detailHtml(date));
        label.setFont(AppearanceManager.uiFont(Font.PLAIN, -2));
        label.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        detailPopup = new JPopupMenu();
        detailPopup.setBorder(BorderFactory.createLineBorder(new Color(0xD0D7DE)));
        detailPopup.add(label);
        detailPopup.show(this, px + 12, py + 12);
    }

    /** 把鼠标坐标换算成对应的日期，落在空白处时返回 null。 */
    private LocalDate dateAt(int px, int py) {
        int step = cellSize + CELL_GAP;
        int offsetX = px - gridX;
        int offsetY = py - gridY;
        if (offsetX < 0 || offsetY < 0) {
            return null;
        }

        int week = offsetX / step;
        int row = offsetY / step;
        if (week >= weekCount || row >= 7
                || offsetX % step >= cellSize || offsetY % step >= cellSize) {
            return null;
        }

        LocalDate date = firstMonday.plusDays((long) week * 7 + row);
        if (date.isBefore(from) || date.isAfter(to)) {
            return null;
        }
        return date;
    }

    /** 生成某一天的详情文本，悬停与点击共用。 */
    private String detailHtml(LocalDate date) {
        int minutes = dailyMinutes.getOrDefault(date, 0);
        int row = date.getDayOfWeek().getValue() - 1;
        String band = minutes <= 0 ? "未学习" : LEVEL_LABELS[levelOf(minutes)] + " 分钟";
        return "<html><b>" + date.format(DATE_FORMAT) + "</b> " + WEEKDAY_TEXT[row]
                + "<br>学习时长：" + minutes + " 分钟"
                + "<br>对应色阶：" + band + "</html>";
    }
}
