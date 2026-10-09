package com.study.tracker.view;

import com.study.tracker.config.AppConfig;
import com.study.tracker.config.UserSession;
import com.study.tracker.model.User;
import com.study.tracker.service.UserService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * 我的账户面板。
 * <p>
 * 显示当前登录账户的信息，并提供修改自己密码的功能。
 * 修改密码需要先输入原密码，成功后会清空输入框。
 */
public class AccountPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final UserService userService = new UserService();

    private final JPasswordField oldPasswordField = new JPasswordField(18);
    private final JPasswordField newPasswordField = new JPasswordField(18);
    private final JPasswordField confirmPasswordField = new JPasswordField(18);
    private final JLabel messageLabel = new JLabel(" ");

    public AccountPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(createProfilePanel());
        content.add(Box.createVerticalStrut(14));
        content.add(createPasswordPanel());
        content.add(Box.createVerticalGlue());
        add(content, BorderLayout.CENTER);
    }

    /** 当前账户信息。 */
    private JPanel createProfilePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("当前账户"));

        User user = UserSession.current();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 10, 6, 10);
        gbc.anchor = GridBagConstraints.WEST;

        addInfo(panel, gbc, 0, "用户名", user == null ? "-" : user.getUsername());
        addInfo(panel, gbc, 1, "角色", user == null ? "-" : user.getRole().getDisplayName());
        addInfo(panel, gbc, 2, "最近登录", user == null || user.getLastLoginAt() == null
                ? "-" : user.getLastLoginAt().format(AppConfig.DATE_TIME_FORMATTER));
        return panel;
    }

    private void addInfo(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        panel.add(new JLabel(value), gbc);
    }

    /** 修改密码表单。 */
    private JPanel createPasswordPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("修改密码"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;

        addRow(panel, gbc, 0, "原密码", oldPasswordField);
        addRow(panel, gbc, 1, "新密码", newPasswordField);
        addRow(panel, gbc, 2, "确认新密码", confirmPasswordField);
        addRow(panel, gbc, 3, " ", PasswordVisibility.createToggle(
                oldPasswordField, newPasswordField, confirmPasswordField));

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        messageLabel.setForeground(new Color(0xC0392B));
        panel.add(messageLabel, gbc);

        JButton saveButton = new JButton("保存新密码");
        saveButton.setPreferredSize(new Dimension(130, 32));
        saveButton.addActionListener(e -> savePassword());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.add(saveButton);
        gbc.gridy = 5;
        panel.add(buttons, gbc);

        return panel;
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        panel.add(field, gbc);
    }

    /** 校验并保存新密码。 */
    private void savePassword() {
        User user = UserSession.current();
        try {
            userService.changePassword(user,
                    new String(oldPasswordField.getPassword()),
                    new String(newPasswordField.getPassword()),
                    new String(confirmPasswordField.getPassword()));
            clearFields();
            messageLabel.setForeground(new Color(0x1F7A33));
            messageLabel.setText("密码已修改，下次登录请使用新密码。");
        } catch (IllegalArgumentException e) {
            messageLabel.setForeground(new Color(0xC0392B));
            messageLabel.setText(e.getMessage());
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "修改密码失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearFields() {
        oldPasswordField.setText("");
        newPasswordField.setText("");
        confirmPasswordField.setText("");
    }
}
