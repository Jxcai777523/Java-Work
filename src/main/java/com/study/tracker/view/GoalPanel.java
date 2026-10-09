package com.study.tracker.view;

import com.study.tracker.model.Goal;
import com.study.tracker.model.GoalPeriod;
import com.study.tracker.model.Subject;
import com.study.tracker.service.GoalService;
import com.study.tracker.service.SubjectService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * 学习目标管理面板。
 * <p>
 * 以表格展示各周期的目标完成情况，进度列用进度条呈现，
 * 便于直观看出"每日 2 小时""每周 10 小时"这类目标是否达成。
 */
public class GoalPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS = {"ID", "周期", "科目", "目标时长", "已完成", "完成率", "进度"};

    /** 下拉框中代表"全部科目"的选项文本。 */
    private static final String ALL_SUBJECTS = "全部科目";

    private final GoalService goalService = new GoalService();
    private final SubjectService subjectService = new SubjectService();

    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final List<Goal> currentGoals = new ArrayList<>();

    public GoalPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(createToolBar(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        configureTable();
        reload();
    }

    /** 顶部工具栏。 */
    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton addButton = new JButton("新增目标");
        addButton.addActionListener(e -> onAdd());

        JButton editButton = new JButton("修改");
        editButton.addActionListener(e -> onEdit());

        JButton deleteButton = new JButton("删除");
        deleteButton.addActionListener(e -> onDelete());

        JButton refreshButton = new JButton("刷新进度");
        refreshButton.addActionListener(e -> reload());

        toolBar.add(addButton);
        toolBar.add(editButton);
        toolBar.add(deleteButton);
        toolBar.addSeparator();
        toolBar.add(refreshButton);

        return toolBar;
    }

    private void configureTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setMaxWidth(80);
        table.getColumnModel().getColumn(3).setMaxWidth(110);
        table.getColumnModel().getColumn(4).setMaxWidth(110);
        table.getColumnModel().getColumn(5).setMaxWidth(90);

        // 进度列用进度条渲染
        table.getColumnModel().getColumn(6).setCellRenderer(new TableCellRenderer() {
            private final JProgressBar bar = new JProgressBar(0, 100);

            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                int percent = value instanceof Integer i ? i : 0;
                bar.setValue(percent);
                bar.setString(percent + "%");
                bar.setStringPainted(true);
                return bar;
            }
        });
    }

    /** 重新计算并刷新目标进度。 */
    public void reload() {
        List<Goal> goals = goalService.list();

        currentGoals.clear();
        currentGoals.addAll(goals);

        tableModel.setRowCount(0);
        for (Goal goal : goals) {
            GoalService.Progress progress = goalService.progressOf(goal);
            tableModel.addRow(new Object[]{
                    goal.getId(),
                    goal.getPeriodType().getDisplayName(),
                    goal.getSubjectName() == null ? ALL_SUBJECTS : goal.getSubjectName(),
                    formatMinutes(goal.getTargetMinutes()),
                    formatMinutes(progress.achievedMinutes()),
                    progress.percentRaw() + "%",
                    progress.percentCapped()
            });
        }
    }

    /** 新增目标。 */
    private void onAdd() {
        Goal draft = new Goal();
        draft.setPeriodType(GoalPeriod.DAILY);
        draft.setTargetMinutes(120);
        if (!showEditDialog(draft, "新增学习目标")) {
            return;
        }
        try {
            goalService.create(draft.getPeriodType(), draft.getSubjectId(), draft.getTargetMinutes());
            reload();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /** 修改选中目标。 */
    private void onEdit() {
        Goal selected = getSelectedGoal();
        if (selected == null) {
            showInfo("请先在列表中选择一个目标");
            return;
        }
        Goal draft = copyOf(selected);
        if (!showEditDialog(draft, "修改学习目标")) {
            return;
        }
        try {
            goalService.update(selected, draft.getPeriodType(), draft.getSubjectId(), draft.getTargetMinutes());
            reload();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /** 删除选中目标。 */
    private void onDelete() {
        Goal selected = getSelectedGoal();
        if (selected == null) {
            showInfo("请先在列表中选择一个目标");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "确定要删除这个目标吗？（" + selected.getPeriodType().getDisplayName() + "，"
                        + formatMinutes(selected.getTargetMinutes()) + "）",
                "删除确认", JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            goalService.delete(selected.getId());
            reload();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /**
     * 目标编辑对话框。
     *
     * @return 用户是否确认保存
     */
    private boolean showEditDialog(Goal draft, String title) {
        JComboBox<GoalPeriod> periodCombo = new JComboBox<>(GoalPeriod.values());
        periodCombo.setSelectedItem(draft.getPeriodType() == null ? GoalPeriod.DAILY : draft.getPeriodType());

        JComboBox<Object> subjectCombo = new JComboBox<>();
        subjectCombo.addItem(ALL_SUBJECTS);
        for (Subject subject : subjectService.list(false)) {
            subjectCombo.addItem(subject);
            if (draft.getSubjectId() != null && subject.getId() == draft.getSubjectId()) {
                subjectCombo.setSelectedItem(subject);
            }
        }

        JSpinner targetSpinner = new JSpinner(
                new SpinnerNumberModel(Math.max(draft.getTargetMinutes(), 30), 10, 10080, 30));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("目标周期："), gbc);
        gbc.gridx = 1;
        form.add(periodCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("关联科目："), gbc);
        gbc.gridx = 1;
        form.add(subjectCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("目标时长(分钟)："), gbc);
        gbc.gridx = 1;
        form.add(targetSpinner, gbc);

        int result = JOptionPane.showConfirmDialog(this, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        draft.setPeriodType((GoalPeriod) periodCombo.getSelectedItem());
        Object subjectItem = subjectCombo.getSelectedItem();
        draft.setSubjectId(subjectItem instanceof Subject subject ? subject.getId() : null);
        draft.setTargetMinutes((Integer) targetSpinner.getValue());
        return true;
    }

    private Goal getSelectedGoal() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= currentGoals.size()) {
            return null;
        }
        return currentGoals.get(row);
    }

    private Goal copyOf(Goal source) {
        Goal copy = new Goal();
        copy.setId(source.getId());
        copy.setUserId(source.getUserId());
        copy.setSubjectId(source.getSubjectId());
        copy.setSubjectName(source.getSubjectName());
        copy.setPeriodType(source.getPeriodType());
        copy.setTargetMinutes(source.getTargetMinutes());
        copy.setStartDate(source.getStartDate());
        copy.setEndDate(source.getEndDate());
        return copy;
    }

    /** 把分钟数格式化为"X 小时 Y 分钟"。 */
    private String formatMinutes(int minutes) {
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        return rest == 0 ? hours + " 小时" : hours + " 小时 " + rest + " 分";
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "操作失败", JOptionPane.ERROR_MESSAGE);
    }
}