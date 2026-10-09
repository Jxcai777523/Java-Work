package com.study.tracker.view;

import com.study.tracker.config.AppConfig;
import com.study.tracker.model.StudyRecord;
import com.study.tracker.model.Subject;
import com.study.tracker.service.RecordService;
import com.study.tracker.service.SubjectService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 学习记录管理面板。
 * <p>
 * 支持按日期区间与科目筛选记录，并提供手动补录、编辑与删除功能。
 * 自动计时产生的记录同样会出现在此列表中。
 */
public class RecordPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS = {
            "ID", "日期", "科目", "开始时间", "结束时间", "时长(分钟)", "中断次数", "来源", "备注"
    };

    /** 时间显示格式。 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 对话框中的日期时间格式。 */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";

    /** 下拉框中代表"全部科目"的选项。 */
    private static final String ALL_SUBJECTS = "全部科目";

    private final RecordService recordService = new RecordService();
    private final SubjectService subjectService = new SubjectService();

    private final JSpinner fromSpinner = new JSpinner(new SpinnerDateModel());
    private final JSpinner toSpinner = new JSpinner(new SpinnerDateModel());
    private final JComboBox<Object> subjectFilter = new JComboBox<>();

    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final List<StudyRecord> currentRecords = new ArrayList<>();

    public RecordPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(createFilterPanel(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);

        configureTable();
        reloadSubjects();
        resetFilter();
    }

    /** 顶部筛选栏。 */
    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));

        fromSpinner.setEditor(new JSpinner.DateEditor(fromSpinner, "yyyy-MM-dd"));
        toSpinner.setEditor(new JSpinner.DateEditor(toSpinner, "yyyy-MM-dd"));
        fromSpinner.setPreferredSize(new java.awt.Dimension(120, 28));
        toSpinner.setPreferredSize(new java.awt.Dimension(120, 28));

        subjectFilter.setPreferredSize(new java.awt.Dimension(140, 28));

        JButton searchButton = new JButton("查询");
        searchButton.addActionListener(e -> reload());

        JButton resetButton = new JButton("重置");
        resetButton.addActionListener(e -> resetFilter());

        panel.add(new JLabel("从："));
        panel.add(fromSpinner);
        panel.add(new JLabel("到："));
        panel.add(toSpinner);
        panel.add(new JLabel("科目："));
        panel.add(subjectFilter);
        panel.add(searchButton);
        panel.add(resetButton);

        return panel;
    }

    /** 底部操作按钮。 */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));

        JButton addButton = new JButton("手动补录");
        addButton.addActionListener(e -> onAddManual());

        JButton editButton = new JButton("编辑");
        editButton.addActionListener(e -> onEdit());

        JButton deleteButton = new JButton("删除");
        deleteButton.addActionListener(e -> onDelete());

        panel.add(addButton);
        panel.add(editButton);
        panel.add(deleteButton);

        return panel;
    }

    private void configureTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(5).setMaxWidth(100);
        table.getColumnModel().getColumn(6).setMaxWidth(90);
        table.getColumnModel().getColumn(7).setMaxWidth(90);
    }

    /** 重新加载科目筛选下拉框。 */
    public void reloadSubjects() {
        Object selected = subjectFilter.getSelectedItem();
        subjectFilter.removeAllItems();
        subjectFilter.addItem(ALL_SUBJECTS);
        for (Subject subject : subjectService.list(false)) {
            subjectFilter.addItem(subject);
        }
        if (selected instanceof Subject ) {
            for (int i = 0; i < subjectFilter.getItemCount(); i++) {
                Object item = subjectFilter.getItemAt(i);
                if (item instanceof Subject s && s.getId() == ((Subject) selected).getId()) {
                    subjectFilter.setSelectedIndex(i);
                    break;
                }
            }
        }
        reload();
    }

    /** 重置筛选条件为"最近 7 天 + 全部科目"。 */
    private void resetFilter() {
        LocalDate today = LocalDate.now();
        fromSpinner.setValue(toDate(today.minusDays(6).atStartOfDay()));
        toSpinner.setValue(toDate(today.atStartOfDay()));
        subjectFilter.setSelectedIndex(0);
        reload();
    }

    /** 按当前筛选条件查询并刷新表格。 */
    public void reload() {
        LocalDate from = toLocalDateTime((Date) fromSpinner.getValue()).toLocalDate();
        LocalDate to = toLocalDateTime((Date) toSpinner.getValue()).toLocalDate();
        Integer subjectId = null;
        Object selected = subjectFilter.getSelectedItem();
        if (selected instanceof Subject subject) {
            subjectId = subject.getId();
        }

        List<StudyRecord> records = recordService.search(from, to, subjectId);

        currentRecords.clear();
        currentRecords.addAll(records);

        tableModel.setRowCount(0);
        for (StudyRecord record : records) {
            tableModel.addRow(new Object[]{
                    record.getId(),
                    record.getStartTime() == null ? "" : record.getStartTime().toLocalDate().toString(),
                    record.getSubjectName() == null ? "未分类" : record.getSubjectName(),
                    record.getStartTime() == null ? "" : record.getStartTime().format(TIME_FORMATTER),
                    record.getEndTime() == null ? "" : record.getEndTime().format(TIME_FORMATTER),
                    record.getDurationMinutes(),
                    record.getPauseCount(),
                    record.getSource().getDisplayName(),
                    record.getRemark() == null ? "" : record.getRemark()
            });
        }
    }

    /** 手动补录。 */
    private void onAddManual() {
        StudyRecord draft = new StudyRecord();
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        draft.setStartTime(now.minusMinutes(30));
        draft.setEndTime(now);
        Subject first = firstSubject();
        if (first != null) {
            draft.setSubjectId(first.getId());
        }

        if (!showEditDialog(draft, "手动补录学习记录")) {
            return;
        }
        try {
            recordService.addManual(draft.getSubjectId(), draft.getStartTime(),
                    draft.getEndTime(), draft.getRemark());
            reload();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /** 编辑选中记录。 */
    private void onEdit() {
        StudyRecord selected = getSelectedRecord();
        if (selected == null) {
            showInfo("请先在列表中选择一条记录");
            return;
        }
        StudyRecord draft = copyOf(selected);
        if (!showEditDialog(draft, "编辑学习记录")) {
            return;
        }
        try {
            recordService.update(selected, draft.getSubjectId(), draft.getStartTime(),
                    draft.getEndTime(), draft.getRemark());
            reload();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /** 删除选中记录。 */
    private void onDelete() {
        StudyRecord selected = getSelectedRecord();
        if (selected == null) {
            showInfo("请先在列表中选择一条记录");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "确定要删除这条学习记录吗？（" + selected.getStartTime() + "，"
                        + selected.getDurationMinutes() + " 分钟）",
                "删除确认", JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            recordService.delete(selected.getId());
            reload();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /**
     * 弹出记录编辑对话框。
     *
     * @return 用户是否确认保存
     */
    private boolean showEditDialog(StudyRecord draft, String title) {
        JComboBox<Subject> subjectCombo = new JComboBox<>();
        for (Subject subject : subjectService.list(false)) {
            subjectCombo.addItem(subject);
            if (draft.getSubjectId() != 0 && subject.getId() == draft.getSubjectId()) {
                subjectCombo.setSelectedItem(subject);
            }
        }

        JSpinner startSpinner = createDateTimeSpinner(draft.getStartTime());
        JSpinner endSpinner = createDateTimeSpinner(draft.getEndTime());

        JTextField remarkField = new JTextField(draft.getRemark() == null ? "" : draft.getRemark(), 22);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("科目："), gbc);
        gbc.gridx = 1;
        form.add(subjectCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("开始时间："), gbc);
        gbc.gridx = 1;
        form.add(startSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("结束时间："), gbc);
        gbc.gridx = 1;
        form.add(endSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        form.add(new JLabel("备注："), gbc);
        gbc.gridx = 1;
        form.add(remarkField, gbc);

        int result = JOptionPane.showConfirmDialog(this, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        Subject subject = (Subject) subjectCombo.getSelectedItem();
        if (subject == null) {
            showError("请先添加科目");
            return false;
        }

        draft.setSubjectId(subject.getId());
        draft.setStartTime(toLocalDateTime((Date) startSpinner.getValue()));
        draft.setEndTime(toLocalDateTime((Date) endSpinner.getValue()));
        draft.setRemark(remarkField.getText().isBlank() ? null : remarkField.getText().trim());
        return true;
    }

    /** 创建日期时间选择器。 */
    private JSpinner createDateTimeSpinner(LocalDateTime value) {
        JSpinner spinner = new JSpinner(new SpinnerDateModel());
        spinner.setEditor(new JSpinner.DateEditor(spinner, DATE_TIME_PATTERN));
        spinner.setPreferredSize(new java.awt.Dimension(170, 28));
        if (value != null) {
            spinner.setValue(toDate(value));
        }
        return spinner;
    }

    /** 取得当前选中的记录对象。 */
    private StudyRecord getSelectedRecord() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= currentRecords.size()) {
            return null;
        }
        return currentRecords.get(row);
    }

    private Subject firstSubject() {
        List<Subject> subjects = subjectService.list(false);
        return subjects.isEmpty() ? null : subjects.get(0);
    }

    private StudyRecord copyOf(StudyRecord source) {
        StudyRecord copy = new StudyRecord();
        copy.setId(source.getId());
        copy.setUserId(source.getUserId());
        copy.setSubjectId(source.getSubjectId());
        copy.setSubjectName(source.getSubjectName());
        copy.setStartTime(source.getStartTime());
        copy.setEndTime(source.getEndTime());
        copy.setDurationMinutes(source.getDurationMinutes());
        copy.setPausedSeconds(source.getPausedSeconds());
        copy.setPauseCount(source.getPauseCount());
        copy.setMode(source.getMode());
        copy.setCompleted(source.isCompleted());
        copy.setSource(source.getSource());
        copy.setRemark(source.getRemark());
        return copy;
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    private LocalDateTime toLocalDateTime(Date value) {
        return LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "操作失败", JOptionPane.ERROR_MESSAGE);
    }
}