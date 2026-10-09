package com.study.tracker.view;

import com.study.tracker.model.Subject;
import com.study.tracker.service.SubjectService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * 科目管理面板。
 * <p>
 * 提供科目的新增、修改、归档与删除操作。删除受业务规则限制：
 * 科目下若已有学习记录，只能归档、不能删除。
 */
public class SubjectPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS = {"ID", "科目名称", "颜色", "每周目标(分钟)", "状态"};

    /** 新增科目时的默认颜色。 */
    private static final Color DEFAULT_COLOR = new Color(0x4A90D9);

    private final SubjectService subjectService = new SubjectService();

    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(tableModel);

    private final JCheckBox showArchivedCheck = new JCheckBox("显示已归档科目");

    /** 当前表格对应的科目对象列表，索引与表格行一一对应。 */
    private final List<Subject> currentSubjects = new ArrayList<>();

    public SubjectPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(createToolBar(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        configureTable();
        reload();
    }

    /** 构建顶部工具栏。 */
    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton addButton = new JButton("新增科目");
        addButton.addActionListener(e -> onAdd());

        JButton editButton = new JButton("修改");
        editButton.addActionListener(e -> onEdit());

        JButton archiveButton = new JButton("归档/恢复");
        archiveButton.addActionListener(e -> onToggleArchive());

        JButton deleteButton = new JButton("删除");
        deleteButton.addActionListener(e -> onDelete());

        toolBar.add(addButton);
        toolBar.add(editButton);
        toolBar.add(archiveButton);
        toolBar.add(deleteButton);
        toolBar.addSeparator();

        showArchivedCheck.addActionListener(e -> reload());
        toolBar.add(showArchivedCheck);

        return toolBar;
    }

    /** 表格外观与渲染设置。 */
    private void configureTable() {
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);

        // "颜色"列渲染为色块
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(t, "", isSelected, hasFocus, row, column);
                label.setText("");
                label.setOpaque(true);
                try {
                    label.setBackground(Color.decode(String.valueOf(value)));
                } catch (NumberFormatException ex) {
                    label.setBackground(Color.LIGHT_GRAY);
                }
                return label;
            }
        });
    }

    /** 从数据库重新加载科目列表并刷新表格。 */
    public void reload() {
        boolean includeArchived = showArchivedCheck.isSelected();
        List<Subject> subjects = subjectService.list(includeArchived);

        currentSubjects.clear();
        currentSubjects.addAll(subjects);

        tableModel.setRowCount(0);
        for (Subject subject : subjects) {
            tableModel.addRow(new Object[]{
                    subject.getId(),
                    subject.getName(),
                    subject.getColor(),
                    subject.getWeeklyTargetMinutes(),
                    subject.isArchived() ? "已归档" : "使用中"
            });
        }
    }

    /** 新增科目。 */
    private void onAdd() {
        Subject draft = new Subject("", toHex(DEFAULT_COLOR), 120);
        if (!showEditDialog(draft, "新增科目")) {
            return;
        }
        try {
            subjectService.create(draft.getName(), draft.getColor(), draft.getWeeklyTargetMinutes());
            reload();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /** 修改选中科目。 */
    private void onEdit() {
        Subject selected = getSelectedSubject();
        if (selected == null) {
            showInfo("请先在列表中选择一个科目");
            return;
        }
        Subject draft = copyOf(selected);
        if (!showEditDialog(draft, "修改科目")) {
            return;
        }
        try {
            subjectService.update(selected, draft.getName(), draft.getColor(), draft.getWeeklyTargetMinutes());
            reload();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /** 归档或恢复选中科目。 */
    private void onToggleArchive() {
        Subject selected = getSelectedSubject();
        if (selected == null) {
            showInfo("请先在列表中选择一个科目");
            return;
        }
        String action = selected.isArchived() ? "恢复" : "归档";
        int choice = JOptionPane.showConfirmDialog(this,
                "确定要" + action + "科目「" + selected.getName() + "」吗？",
                action + "确认", JOptionPane.OK_CANCEL_OPTION);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            subjectService.setArchived(selected.getId(), !selected.isArchived());
            reload();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /** 删除选中科目。 */
    private void onDelete() {
        Subject selected = getSelectedSubject();
        if (selected == null) {
            showInfo("请先在列表中选择一个科目");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "确定要删除科目「" + selected.getName() + "」吗？此操作不可撤销。",
                "删除确认", JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            subjectService.delete(selected.getId());
            reload();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /**
     * 弹出科目编辑对话框。
     *
     * @param draft 待编辑的数据（新增时为空科目，修改时为副本）
     * @param title 对话框标题
     * @return 用户是否确认
     */
    private boolean showEditDialog(Subject draft, String title) {
        JTextField nameField = new JTextField(draft.getName(), 16);
        JSpinner targetSpinner = new JSpinner(
                new SpinnerNumberModel(draft.getWeeklyTargetMinutes(), 0, 10080, 30));

        JButton colorButton = new JButton();
        colorButton.setPreferredSize(new java.awt.Dimension(72, 26));
        colorButton.setBackground(decodeColor(draft.getColor()));
        colorButton.setOpaque(true);
        colorButton.addActionListener(e -> {
            Color picked = JColorChooser.showDialog(this, "选择科目颜色", colorButton.getBackground());
            if (picked != null) {
                colorButton.setBackground(picked);
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("科目名称："), gbc);
        gbc.gridx = 1;
        form.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("每周目标："), gbc);
        gbc.gridx = 1;
        form.add(targetSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("颜色标记："), gbc);
        gbc.gridx = 1;
        form.add(colorButton, gbc);

        int result = JOptionPane.showConfirmDialog(this, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        draft.setName(nameField.getText());
        draft.setWeeklyTargetMinutes((Integer) targetSpinner.getValue());
        draft.setColor(toHex(colorButton.getBackground()));
        return true;
    }

    /** 取得当前选中的科目对象。 */
    private Subject getSelectedSubject() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= currentSubjects.size()) {
            return null;
        }
        return currentSubjects.get(row);
    }

    private Subject copyOf(Subject source) {
        Subject copy = new Subject();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setColor(source.getColor());
        copy.setWeeklyTargetMinutes(source.getWeeklyTargetMinutes());
        copy.setArchived(source.isArchived());
        return copy;
    }

    private Color decodeColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return DEFAULT_COLOR;
        }
    }

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "操作失败", JOptionPane.ERROR_MESSAGE);
    }
}