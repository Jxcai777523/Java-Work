package com.study.tracker.chart;

import com.study.tracker.config.AppearanceManager;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.CategorySeries;
import org.knowm.xchart.PieChart;
import org.knowm.xchart.PieChartBuilder;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.internal.chartpart.Chart;
import org.knowm.xchart.style.PieStyler;
import org.knowm.xchart.style.Styler;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 图表工厂。
 * <p>
 * 统一封装 XChart 的创建过程，对外只暴露"传数据、拿面板"的简单接口，
 * 把图表库的 API 细节限制在本类内部。
 * <p>
 * 注意：XChart 默认字体不含中文字形，若不显式设置字体，
 * 标题与坐标轴上的中文会显示为方框，因此每个图表都会调用
 * {@link #applyChineseFont(Chart)} 统一处理。
 */
public final class ChartHelper {

    /** 图表宽度。 */
    private static final int CHART_WIDTH = 860;

    /** 图表高度。 */
    private static final int CHART_HEIGHT = 400;

    /** 柱体在所属分类区间内占用的比例，值越小柱子越细（XChart 默认 0.9）。 */
    private static final double BAR_WIDTH_FILL = 0.4;

    private ChartHelper() {
    }

    /**
     * 创建按时段聚合的柱状图。
     * <p>
     * 横轴标签由调用方给出，可以是按天、按周或按月的聚合结果。
     *
     * @param title      图表标题
     * @param xAxisTitle 横轴标题
     * @param points     标签 → 分钟数（保持传入顺序）
     */
    public static JPanel createBarChart(String title, String xAxisTitle, Map<String, Integer> points) {
        CategoryChart chart = new CategoryChartBuilder()
                .width(CHART_WIDTH).height(CHART_HEIGHT)
                .title(title)
                .xAxisTitle(xAxisTitle).yAxisTitle("分钟")
                .build();
        applyChineseFont(chart);
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setXAxisLabelRotation(45);
        chart.getStyler().setAvailableSpaceFill(BAR_WIDTH_FILL);
        chart.addSeries("学习时长", new ArrayList<>(points.keySet()), new ArrayList<>(points.values()));

        return wrap(chart);
    }

    /**
     * 创建按时段聚合的折线图。
     *
     * @param title      图表标题
     * @param xAxisTitle 横轴标题
     * @param points     标签 → 分钟数（保持传入顺序）
     */
    public static JPanel createLineChart(String title, String xAxisTitle, Map<String, Integer> points) {
        CategoryChart chart = new CategoryChartBuilder()
                .width(CHART_WIDTH).height(CHART_HEIGHT)
                .title(title)
                .xAxisTitle(xAxisTitle).yAxisTitle("分钟")
                .build();
        applyChineseFont(chart);
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setXAxisLabelRotation(45);
        chart.getStyler().setDefaultSeriesRenderStyle(CategorySeries.CategorySeriesRenderStyle.Line);
        chart.addSeries("学习时长", new ArrayList<>(points.keySet()), new ArrayList<>(points.values()));

        return wrap(chart);
    }

    /**
     * 创建"科目时间占比"饼图。
     *
     * @param subjectMinutes 科目名称 → 分钟数
     */
    public static JPanel createSubjectPieChart(Map<String, Integer> subjectMinutes) {
        PieChart chart = new PieChartBuilder()
                .width(CHART_WIDTH).height(CHART_HEIGHT)
                .title("各科目时间占比")
                .build();
        applyChineseFont(chart);

        PieStyler styler = chart.getStyler();
        styler.setLegendPosition(Styler.LegendPosition.OutsideE);
        styler.setLabelsVisible(true);
        styler.setLabelsFont(AppearanceManager.uiFont(Font.PLAIN, -3));
        styler.setLabelsFontColorAutomaticEnabled(true);

        subjectMinutes.forEach((name, minutes) -> {
            if (minutes > 0) {
                chart.addSeries(name, minutes);
            }
        });

        return wrap(chart);
    }

    /**
     * 创建"高效时段"柱状图。
     *
     * @param labels  时段名称
     * @param minutes 各时段分钟数
     */
    public static JPanel createSlotBarChart(List<String> labels, List<Number> minutes) {
        CategoryChart chart = new CategoryChartBuilder()
                .width(CHART_WIDTH).height(CHART_HEIGHT)
                .title("高效时段分析")
                .xAxisTitle("时段").yAxisTitle("分钟")
                .build();
        applyChineseFont(chart);
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setXAxisLabelRotation(20);
        chart.getStyler().setAvailableSpaceFill(BAR_WIDTH_FILL);
        chart.addSeries("学习时长", labels, minutes);

        return wrap(chart);
    }

    /**
     * 创建"学习热力图"。
     * <p>
     * 采用 GitHub 风格的方块矩阵：横轴每周一列，纵轴为周一至周日，
     * 颜色越深表示当天学习时间越长，绘制细节见 {@link HeatMapPanel}。
     *
     * @param from         区间开始日期
     * @param to           区间结束日期
     * @param dailyMinutes 日期 → 分钟数
     */
    public static JPanel createHeatMapChart(LocalDate from, LocalDate to, Map<LocalDate, Integer> dailyMinutes) {
        HeatMapPanel heatMap = new HeatMapPanel(from, to, dailyMinutes);

        // 区间很长时网格会超出一屏，用滚动面板承载；是否需要滚动条由 HeatMapPanel 决定
        JScrollPane scrollPane = new JScrollPane(heatMap,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(scrollPane, BorderLayout.CENTER);
        return wrapper;
    }

    /** 创建"暂无数据"占位面板。 */
    public static JPanel createEmptyPanel(String message) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setFont(AppearanceManager.uiFont(Font.PLAIN, 1));
        label.setForeground(Color.GRAY);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    /** 把 XChart 图表包装为可放入 Swing 容器的面板。 */
    private static JPanel wrap(Chart<?, ?> chart) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new XChartPanel<>(chart), BorderLayout.CENTER);
        return panel;
    }

    /**
     * 统一设置中文字体。
     * <p>
     * XChart 3.8 的坐标轴字体继承自基础字体，因此设置 baseFont 即可覆盖
     * 坐标轴刻度、图例等位置的文字。
     */
    private static void applyChineseFont(Chart<?, ?> chart) {
        Styler styler = chart.getStyler();
        Font base = AppearanceManager.uiFont(Font.PLAIN, -2);
        styler.setBaseFont(base);
        styler.setLegendFont(base);
        styler.setChartTitleFont(AppearanceManager.uiFont(Font.BOLD, 1));
    }
}