package com.study.tracker.view;

import com.study.tracker.model.Subject;
import com.study.tracker.model.Task;
import com.study.tracker.model.TaskPriority;
import com.study.tracker.model.TaskStatus;
import com.study.tracker.service.SubjectService;
import com.study.tracker.service.TaskService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 待办任务管理面板。
 * <p>
 * 任务按"未完成优先、截止日期升序"排列，优先级以颜色区分。
 * 任务的完成情况会被效率分析模块用于计算"计划完成率"。
 */
public class TaskPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS = {
            "ID", "任务标题", "科目", "优先级", "截止日期", "预计时长(分钟)", "状态"
    };

    /** 下拉框中代表"无"的选项文本。 */
    private static final String NONE_TEXT = "（不关联）";

    private final TaskService taskService = new TaskService();
    private final SubjectService subjectService = new SubjectService();

    private final JCheckBox onlyTodoCheck = new JCheckBox("只显示未完成");

    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final List<Task> currentTasks = new ArrayList<>();

    public TaskPanel() {
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

        JButton addButton = new JButton("新增任务");
        addButton.addActionListener(e -> onAdd());

        JButton editButton = new JButton("编辑");
        editButton.addActionListener(e -> onEdit());

        JButton toggleButton = new JButton("完成/取消完成");
        toggleButton.addActionListener(e -> onToggleStatus());

        JButton deleteButton = new JButton("删除");
        deleteButton.addActionListener(e -> onDelete());

        toolBar.add(addButton);
        toolBar.add(editButton);
        toolBar.add(toggleButton);
        toolBar.add(deleteButton);
        toolBar.addSeparator();

        onlyTodoCheck.addActionListener(e -> reload());
        toolBar.add(onlyTodoCheck);

        return toolBar;
    }

    private void configureTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(3).setMaxWidth(80);
        table.getColumnModel().getColumn(5).setMaxWidth(120);
        table.getColumnModel().getColumn(6).setMaxWidth(90);

        // 优先级列着色：高=红、中=橙、低=灰
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component component = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    String text = String.valueOf(value);
                    if ("高".equals(text)) {
                        component.setForeground(new Color(0xC0392B));
                    } else if ("中".equals(text)) {
                        component.setForeground(new Color(0xD68910));
                    } else {
                        component.setForeground(new Color(0x7F8C8D));
                    }
                }
                return component;
            }
        });
    }

    /** 重新加载任务列表。 */
    public void reload() {
        List<Task> tasks = taskService.list(onlyTodoCheck.isSelected());

        currentTasks.clear();
        currentTasks.addAll(tasks);

        tableModel.setRowCount(0);
        for (Task task : tasks) {
            tableModel.addRow(new Object[]{
                    task.getId(),
                    task.getTitle(),
                    task.getSubjectName() == null ? NONE_TEXT : task.getSubjectName(),
                    task.getPriority().getDisplayName(),
                    task.getDueDate() == null ? "" : task.getDueDate().toString(),
                    task.getEstimatedMinutes() == 0 ? "" : task.getEstimatedMinutes(),
                    task.getStatus().getDisplayName()
            });
        }
    }

    /** 新增任务。 */
    private void onAdd() {
        Task draft = new Task();
        draft.setPriority(TaskPriority.MEDIUM);
        draft.setDueDate(LocalDate.now().plusDays(7));
        if (!showEditDialog(draft, "新增任务")) {
            return;
        }
        try {
            taskService.create(draft.getTitle(), draft.getSubjectId(), draft.getPriority(),
                    draft.getDueDate(), draft.getEstimatedMinutes());
            reload();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /** 编辑选中任务。 */
    private void onEdit() {
        Task selected = getSelectedTask();
        if (selected == null) {
            showInfo("请先在列表中选择一个任务");
            return;
        }
        Task draft = copyOf(selected);
        if (!showEditDialog(draft, "编辑任务")) {
            return;
        }
        try {
            taskService.update(selected, draft.getTitle(), draft.getSubjectId(), draft.getPriority(),
                    draft.getDueDate(), draft.getEstimatedMinutes());
            reload();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /** 切换完成状态。 */
    private void onToggleStatus() {
        Task selected = getSelectedTask();
        if (selected == null) {
            showInfo("请先在列表中选择一个任务");
            return;
        }
        try {
            TaskStatus target = selected.getStatus() == TaskStatus.DONE ? TaskStatus.TODO : TaskStatus.DONE;
            taskService.setStatus(selected, target);
            reload();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /** 删除选中任务。 */
    private void onDelete() {
        Task selected = getSelectedTask();
        if (selected == null) {
            showInfo("请先在列表中选择一个任务");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "确定要删除任务「" + selected.getTitle() + "」吗？",
                "删除确认", JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            taskService.delete(selected.getId());
            reload();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /**
     * 弹出任务编辑对话框。
     *
     * @return 用户是否确认保存
     */
    private boolean showEditDialog(Task draft, String title) {
        JTextField titleField = new JTextField(draft.getTitle() == null ? "" : draft.getTitle(), 22);

        JComboBox<Object> subjectCombo = new JComboBox<>();
        subjectCombo.addItem(NONE_TEXT);
        for (Subject subject : subjectService.list(false)) {
            subjectCombo.addItem(subject);
            if (draft.getSubjectId() != null && subject.getId() == draft.getSubjectId()) {
                subjectCombo.setSelectedItem(subject);
            }
        }

        JComboBox<TaskPriority> priorityCombo = new JComboBox<>(TaskPriority.values());
        priorityCombo.setSelectedItem(draft.getPriority() == null ? TaskPriority.MEDIUM : draft.getPriority());

        JCheckBox dueCheck = new JCheckBox("设置截止日期");
        JSpinner dueSpinner = new JSpinner(new SpinnerDateModel());
        dueSpinner.setEditor(new JSpinner.DateEditor(dueSpinner, "yyyy-MM-dd"));
        dueSpinner.setPreferredSize(new Dimension(140, 28));
        dueCheck.setSelected(draft.getDueDate() != null);
        dueSpinner.setEnabled(dueCheck.isSelected());
        if (draft.getDueDate() != null) {
            dueSpinner.setValue(toDate(draft.getDueDate().atStartOfDay()));
        }
        dueCheck.addActionListener(e -> dueSpinner.setEnabled(dueCheck.isSelected()));

        JSpinner estimateSpinner = new JSpinner(
                new SpinnerNumberModel(draft.getEstimatedMinutes(), 0, 10080, 15));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("任务标题："), gbc);
        gbc.gridx = 1;
        form.add(titleField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("关联科目："), gbc);
        gbc.gridx = 1;
        form.add(subjectCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("优先级："), gbc);
        gbc.gridx = 1;
        form.add(priorityCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        form.add(dueCheck, gbc);
        gbc.gridx = 1;
        form.add(dueSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        form.add(new JLabel("预计时长(分钟)："), gbc);
        gbc.gridx = 1;
        form.add(estimateSpinner, gbc);

        int result = JOptionPane.showConfirmDialog(this, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        draft.setTitle(titleField.getText());
        Object subjectItem = subjectCombo.getSelectedItem();
        draft.setSubjectId(subjectItem instanceof Subject subject ? subject.getId() : null);
        draft.setPriority((TaskPriority) priorityCombo.getSelectedItem());
        draft.setDueDate(dueCheck.isSelected()
                ? toLocalDate((Date) dueSpinner.getValue()).toLocalDate() : null);
        draft.setEstimatedMinutes((Integer) estimateSpinner.getValue());
        return true;
    }

    private Task getSelectedTask() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= currentTasks.size()) {
            return null;
        }
        return currentTasks.get(row);
    }

    private Task copyOf(Task source) {
        Task copy = new Task();
        copy.setId(source.getId());
        copy.setUserId(source.getUserId());
        copy.setSubjectId(source.getSubjectId());
        copy.setSubjectName(source.getSubjectName());
        copy.setTitle(source.getTitle());
        copy.setPriority(source.getPriority());
        copy.setDueDate(source.getDueDate());
        copy.setEstimatedMinutes(source.getEstimatedMinutes());
        copy.setStatus(source.getStatus());
        copy.setCompletedAt(source.getCompletedAt());
        return copy;
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    private LocalDateTime toLocalDate(Date value) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(value.getTime()), ZoneId.systemDefault());
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "操作失败", JOptionPane.ERROR_MESSAGE);
    }
}