package com.study.tracker.view;

import com.study.tracker.config.AppConfig;
import com.study.tracker.config.UserSession;
import com.study.tracker.model.User;
import com.study.tracker.model.UserRole;
import com.study.tracker.service.UserService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户管理面板（仅管理员可见）。
 * <p>
 * 提供三项操作：新增账户、重置指定账户的密码、启用或停用账户。
 * 出于数据完整性考虑不提供删除：学习记录、任务、目标都通过外键引用用户，
 * 直接删除会让这些数据失去归属，需要停止使用时改用"停用"。
 */
public class UserPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS = {"用户名", "角色", "状态", "创建时间", "最近登录"};

    private final UserService userService = new UserService();

    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(tableModel);
    private final List<User> users = new ArrayList<>();

    public UserPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        add(createToolbar(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(createHint(), BorderLayout.SOUTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        reload();
    }

    private JPanel createToolbar() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0));
        JButton createButton = new JButton("新增用户…");
        createButton.addActionListener(e -> createUser());
        JButton resetButton = new JButton("重置密码…");
        resetButton.addActionListener(e -> resetPassword());
        JButton toggleButton = new JButton("启用 / 停用");
        toggleButton.addActionListener(e -> toggleEnabled());
        JButton refreshButton = new JButton("刷新");
        refreshButton.addActionListener(e -> reload());

        buttons.add(createButton);
        buttons.add(resetButton);
        buttons.add(toggleButton);
        buttons.add(refreshButton);

        panel.add(buttons, BorderLayout.WEST);
        return panel;
    }

    private JPanel createHint() {
        JPanel panel = new JPanel(new BorderLayout());
        String name = UserSession.current() == null ? "-" : UserSession.current().getUsername();
        panel.add(new JLabel("当前账户：" + name + "　|　账户只能停用，不能删除，避免历史数据失去归属。"),
                BorderLayout.WEST);
        return panel;
    }

    /** 重新载入用户列表。 */
    public void reload() {
        users.clear();
        users.addAll(userService.listUsers());

        tableModel.setRowCount(0);
        for (User user : users) {
            tableModel.addRow(new Object[]{
                    user.getUsername(),
                    user.getRole().getDisplayName(),
                    user.isEnabled() ? "正常" : "已停用",
                    user.getCreatedAt() == null ? "-" : user.getCreatedAt().format(AppConfig.DATE_TIME_FORMATTER),
                    user.getLastLoginAt() == null ? "从未登录" : user.getLastLoginAt().format(AppConfig.DATE_TIME_FORMATTER)
            });
        }
    }

    /** 新增账户。 */
    private void createUser() {
        JTextField usernameField = new JTextField(16);
        JPasswordField passwordField = new JPasswordField(16);
        JPasswordField confirmField = new JPasswordField(16);
        JComboBox<UserRole> roleCombo = new JComboBox<>(UserRole.values());

        JPanel form = new JPanel(new GridLayout(5, 2, 8, 8));
        form.add(new JLabel("用户名"));
        form.add(usernameField);
        form.add(new JLabel("密码"));
        form.add(passwordField);
        form.add(new JLabel("确认密码"));
        form.add(confirmField);
        form.add(new JLabel("角色"));
        form.add(roleCombo);
        form.add(new JLabel(" "));
        form.add(PasswordVisibility.createToggle(passwordField, confirmField));

        int result = JOptionPane.showConfirmDialog(this, form, "新增用户",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            userService.createUser(UserSession.current(), usernameField.getText(),
                    new String(passwordField.getPassword()), new String(confirmField.getPassword()),
                    (UserRole) roleCombo.getSelectedItem());
            reload();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "无法新增用户", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** 重置选中账户的密码。 */
    private void resetPassword() {
        User selected = selectedUser();
        if (selected == null) {
            return;
        }

        String[] input = askNewPassword("重置密码 - " + selected.getUsername());
        if (input == null) {
            return;
        }

        try {
            userService.resetPassword(UserSession.current(), selected.getId(), input[0], input[1]);
            JOptionPane.showMessageDialog(this, "已重置 " + selected.getUsername() + " 的密码。",
                    "重置成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "无法重置密码", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** 启用或停用选中账户。 */
    private void toggleEnabled() {
        User selected = selectedUser();
        if (selected == null) {
            return;
        }

        boolean target = !selected.isEnabled();
        String action = target ? "启用" : "停用";
        int answer = JOptionPane.showConfirmDialog(this,
                "确定要" + action + "账户 " + selected.getUsername() + " 吗？",
                action + "账户", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            userService.setEnabled(UserSession.current(), selected.getId(), target);
            reload();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "无法" + action, JOptionPane.WARNING_MESSAGE);
        }
    }

    /** 取当前选中的用户，未选中时提示并返回 null。 */
    private User selectedUser() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= users.size()) {
            JOptionPane.showMessageDialog(this, "请先在列表中选择一个账户。",
                    "未选择账户", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return users.get(row);
    }

    /** 弹出"新密码 + 确认密码"输入框，取消时返回 null。 */
    private String[] askNewPassword(String title) {
        JPasswordField first = new JPasswordField(16);
        JPasswordField second = new JPasswordField(16);

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.add(new JLabel("新密码"));
        form.add(first);
        form.add(new JLabel("确认密码"));
        form.add(second);
        form.add(new JLabel(" "));
        form.add(PasswordVisibility.createToggle(first, second));

        int result = JOptionPane.showConfirmDialog(this, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        return new String[]{new String(first.getPassword()), new String(second.getPassword())};
    }
}
