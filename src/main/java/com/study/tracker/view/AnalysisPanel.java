package com.study.tracker.view;

import com.study.tracker.chart.ChartHelper;
import com.study.tracker.config.UserSession;
import com.study.tracker.config.AppearanceManager;
import com.study.tracker.dao.StudyRecordDao;
import com.study.tracker.service.StatService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 效率分析面板。
 * <p>
 * 结构分为三部分：
 * <ol>
 *   <li>顶部：统计时间范围选择；</li>
 *   <li>中部：效率指标卡片（总时长、日均、计划完成率、综合效率指数等）；</li>
 *   <li>下部：图表区，用子页签切换柱状图、饼图、折线图、热力图与高效时段分析。</li>
 * </ol>
 * 图表采用懒加载：只有当某个子页签被选中时才查询数据并渲染，避免一次性执行多次聚合查询。
 */
public class AnalysisPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** 可选的时间范围。 */
    private static final String[] RANGE_OPTIONS = {"近 7 天", "近 30 天", "近 90 天", "本周", "本月", "今年"};

    /** 指标卡片标题。 */
    private static final String[] CARD_TITLES = {
            "总学习时长", "日均学习", "平均单次专注", "平均中断次数",
            "计划完成率", "番茄钟完成率", "连续学习天数", "综合效率指数"
    };

    /** 图表子页签标题。 */
    private static final String[] CHART_TITLES = {
            "时长分布", "科目占比", "时长趋势", "学习热力图", "高效时段"
    };

    private final StatService statService = new StatService();
    private final StudyRecordDao recordDao = new StudyRecordDao();

    private final JComboBox<String> rangeCombo = new JComboBox<>(RANGE_OPTIONS);
    private final JLabel[] cardValues = new JLabel[CARD_TITLES.length];
    private final JTabbedPane chartTabs = new JTabbedPane();

    /** 当前统计区间。 */
    private LocalDate currentFrom;
    private LocalDate currentTo;

    /** 已渲染过的图表页签索引。 */
    private final Set<Integer> loadedTabs = new HashSet<>();

    public AnalysisPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(createTopPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);

        reload();
    }

    /** 顶部：时间范围选择。 */
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel left = new JPanel();
        left.add(new JLabel("统计范围："));
        rangeCombo.setPreferredSize(new Dimension(120, 28));
        rangeCombo.addActionListener(e -> reload());
        left.add(rangeCombo);

        JButton refreshButton = new JButton("刷新");
        refreshButton.addActionListener(e -> reload());
        left.add(refreshButton);

        panel.add(left, BorderLayout.WEST);
        return panel;
    }

    /** 中部：指标卡片 + 图表区。 */
    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(createCardPanel(), BorderLayout.NORTH);
        panel.add(createChartPanel(), BorderLayout.CENTER);
        return panel;
    }

    /** 指标卡片区。 */
    private JPanel createCardPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 4, 8, 8));

        for (int i = 0; i < CARD_TITLES.length; i++) {
            panel.add(createCard(i));
        }
        return panel;
    }

    /** 单个指标卡片。 */
    private JPanel createCard(int index) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor()),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JLabel title = new JLabel(CARD_TITLES[index], SwingConstants.CENTER);
        title.setFont(AppearanceManager.uiFont(Font.PLAIN, -2));
        title.setForeground(new Color(0x666666));

        JLabel value = new JLabel("--", SwingConstants.CENTER);
        value.setFont(AppearanceManager.uiFont(Font.BOLD, 6));
        value.setForeground(accentColor());
        cardValues[index] = value;

        card.add(title, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    /** 图表区。 */
    private JPanel createChartPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("数据图表"));

        chartTabs.setFont(AppearanceManager.uiFont(Font.PLAIN, -1));
        for (String title : CHART_TITLES) {
            chartTabs.addTab(title, ChartHelper.createEmptyPanel("加载中……"));
        }

        // 懒加载：仅渲染当前选中的图表
        chartTabs.addChangeListener(e -> loadChart(chartTabs.getSelectedIndex()));

        panel.add(chartTabs, BorderLayout.CENTER);
        return panel;
    }

    /** 重新计算指标并刷新图表。 */
    public void reload() {
        LocalDate[] range = resolveRange();
        currentFrom = range[0];
        currentTo = range[1];

        StatService.Metrics metrics = statService.computeMetrics(currentFrom, currentTo);
        updateCards(metrics);

        // 时间范围变化后，已渲染的图表全部作废
        loadedTabs.clear();
        for (int i = 0; i < CHART_TITLES.length; i++) {
            chartTabs.setComponentAt(i, ChartHelper.createEmptyPanel("加载中……"));
        }
        loadChart(chartTabs.getSelectedIndex());
    }

    /** 根据下拉选项解析出起止日期。 */
    private LocalDate[] resolveRange() {
        LocalDate today = LocalDate.now();
        String option = (String) rangeCombo.getSelectedItem();
        if (option == null) {
            return new LocalDate[]{today.minusDays(6), today};
        }
        return switch (option) {
            case "近 30 天" -> new LocalDate[]{today.minusDays(29), today};
            case "近 90 天" -> new LocalDate[]{today.minusDays(89), today};
            case "本周" -> new LocalDate[]{
                    today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)), today};
            case "本月" -> new LocalDate[]{today.withDayOfMonth(1), today};
            case "今年" -> new LocalDate[]{today.withDayOfYear(1), today};
            default -> new LocalDate[]{today.minusDays(6), today};
        };
    }

    /** 刷新指标卡片。 */
    private void updateCards(StatService.Metrics m) {
        cardValues[0].setText(formatMinutes(m.totalMinutes()));
        cardValues[1].setText(Math.round(m.avgDailyMinutes()) + " 分钟");
        cardValues[2].setText(Math.round(m.avgSessionMinutes()) + " 分钟");
        cardValues[3].setText(String.format("%.1f 次", m.interruptionPerSession()));
        cardValues[4].setText(Math.round(m.taskCompletionRate() * 100) + "%");
        cardValues[5].setText(Math.round(m.pomodoroCompletionRate() * 100) + "%");
        cardValues[6].setText(m.streakDays() + " 天");

        int score = m.efficiencyScore();
        cardValues[7].setText(score + " 分");
        cardValues[7].setForeground(scoreColor(score));
        cardValues[7].setToolTipText("综合效率指数 = 计划完成率 30% + 番茄钟完成率 25% + 目标达成率 20% + 专注度 15% + 出勤率 10%");
    }

    /** 效率指数配色：>=80 绿、>=60 蓝、>=40 橙、其余红。 */
    private Color scoreColor(int score) {
        if (score >= 80) {
            return new Color(0x27866F);
        }
        if (score >= 60) {
            return new Color(0x2D7FF9);
        }
        if (score >= 40) {
            return new Color(0xD68910);
        }
        return new Color(0xC0392B);
    }

    /** 卡片边框颜色跟随当前主题，取不到时用浅灰兜底。 */
    private static Color borderColor() {
        Color color = UIManager.getColor("Component.borderColor");
        return color != null ? color : new Color(0xDDDDDD);
    }

    /** 指标数值颜色跟随强调色设置，取不到时用默认蓝兜底。 */
    private static Color accentColor() {
        Color color = UIManager.getColor("Component.accentColor");
        return color != null ? color : new Color(0x2D7FF9);
    }

    /** 按需渲染指定索引的图表。 */
    private void loadChart(int index) {
        if (index < 0 || index >= CHART_TITLES.length) {
            return;
        }
        if (loadedTabs.contains(index)) {
            return;
        }

        JPanel chart;
        try {
            chart = buildChart(index);
        } catch (RuntimeException ex) {
            chart = ChartHelper.createEmptyPanel("图表加载失败：" + ex.getMessage());
        }

        chartTabs.setComponentAt(index, chart);
        loadedTabs.add(index);
    }

    /** 构建指定索引的图表。 */
    private JPanel buildChart(int index) {
        Map<LocalDate, Integer> daily = recordDao.sumDurationByDay(
                UserSession.currentUserId(), currentFrom, currentTo);

        if (daily.isEmpty()) {
            return ChartHelper.createEmptyPanel("该时间段暂无学习记录，先去计时或补录一条吧～");
        }

        return switch (index) {
            case 0 -> {
                Granularity granularity = Granularity.of(currentFrom, currentTo);
                yield ChartHelper.createBarChart(granularity.barTitle(), granularity.axisTitle(),
                        aggregate(granularity, daily));
            }
            case 1 -> {
                Map<String, Integer> bySubject = recordDao.sumDurationBySubject(
                        UserSession.currentUserId(), currentFrom, currentTo);
                yield ChartHelper.createSubjectPieChart(bySubject);
            }
            case 2 -> {
                Granularity granularity = Granularity.of(currentFrom, currentTo);
                yield ChartHelper.createLineChart(granularity.lineTitle(), granularity.axisTitle(),
                        aggregate(granularity, daily));
            }
            case 3 -> ChartHelper.createHeatMapChart(currentFrom, currentTo, fillMissingDays(daily));
            case 4 -> {
                List<StatService.SlotStat> slots = statService.analyzeTimeSlots(currentFrom, currentTo);
                List<String> labels = new ArrayList<>();
                List<Number> values = new ArrayList<>();
                for (StatService.SlotStat slot : slots) {
                    labels.add(slot.label());
                    values.add(slot.minutes());
                }
                yield ChartHelper.createSlotBarChart(labels, values);
            }
            default -> ChartHelper.createEmptyPanel("未知图表");
        };
    }

    /** 按天聚合时的标签格式。 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    /** 按月聚合时的标签格式。 */
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 统计粒度。
     * <p>
     * 区间较短时按天展示，中等长度按周聚合，跨度较大时按月聚合，
     * 避免柱状图与折线图在长区间下挤成一片。
     */
    private enum Granularity {

        /** 按天。 */
        DAY("每日学习时长", "学习时长趋势", "日期"),

        /** 按周。 */
        WEEK("每周学习时长", "学习时长趋势（按周）", "周起始日"),

        /** 按月。 */
        MONTH("每月学习时长", "学习时长趋势（按月）", "月份");

        private final String barTitle;
        private final String lineTitle;
        private final String axisTitle;

        Granularity(String barTitle, String lineTitle, String axisTitle) {
            this.barTitle = barTitle;
            this.lineTitle = lineTitle;
            this.axisTitle = axisTitle;
        }

        String barTitle() {
            return barTitle;
        }

        String lineTitle() {
            return lineTitle;
        }

        String axisTitle() {
            return axisTitle;
        }

        /** 31 天以内按天，120 天以内按周，再长按月。 */
        static Granularity of(LocalDate from, LocalDate to) {
            long days = ChronoUnit.DAYS.between(from, to) + 1;
            if (days <= 31) {
                return DAY;
            }
            return days <= 120 ? WEEK : MONTH;
        }
    }

    /**
     * 按指定粒度把每日学习时长聚合成"标签 → 分钟数"。
     *
     * @param granularity 统计粒度
     * @param daily       每日分钟数
     * @return 保持时间顺序的聚合结果
     */
    private Map<String, Integer> aggregate(Granularity granularity, Map<LocalDate, Integer> daily) {
        Map<String, Integer> result = new LinkedHashMap<>();
        LocalDate cursor = currentFrom;
        while (!cursor.isAfter(currentTo)) {
            String key = switch (granularity) {
                case DAY -> cursor.format(DAY_LABEL);
                case WEEK -> cursor.with(DayOfWeek.MONDAY).format(DAY_LABEL);
                case MONTH -> cursor.format(MONTH_LABEL);
            };
            result.merge(key, daily.getOrDefault(cursor, 0), Integer::sum);
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    /** 补全区间内缺失的日期（数值为 0），使折线图与热力图的横轴连续。 */
    private Map<LocalDate, Integer> fillMissingDays(Map<LocalDate, Integer> source) {
        Map<LocalDate, Integer> filled = new LinkedHashMap<>();
        LocalDate cursor = currentFrom;
        while (!cursor.isAfter(currentTo)) {
            filled.put(cursor, source.getOrDefault(cursor, 0));
            cursor = cursor.plusDays(1);
        }
        return filled;
    }

    /** 把分钟数格式化为便于阅读的形式。 */
    private String formatMinutes(int minutes) {
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        return rest == 0 ? hours + " 小时" : hours + " 时 " + rest + " 分";
    }
}