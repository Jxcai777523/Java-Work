package com.study.tracker.view;

import com.study.tracker.model.User;
import com.study.tracker.service.UserService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;

/**
 * 登录 / 注册对话框。
 * <p>
 * 两种形态：
 * <ul>
 *   <li><b>首次使用</b>：数据库里还没有设置过密码的账户，此时只提供"创建管理员账号"，
 *       避免用户注册出新账户后，原有的学习数据仍挂在旧账户名下无法访问；</li>
 *   <li><b>日常使用</b>：默认登录，可切换到注册新账户。</li>
 * </ul>
 * 对话框关闭时若 {@link #getLoggedInUser()} 为 null，表示用户取消了登录。
 */
public class LoginDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** 表单形态。 */
    private enum Mode {
        /** 首次使用，创建管理员。 */
        INITIAL,
        /** 登录已有账户。 */
        LOGIN,
        /** 注册新账户。 */
        REGISTER
    }

    private final UserService userService = new UserService();

    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JPasswordField confirmField = new JPasswordField(18);

    private final JLabel titleLabel = new JLabel();
    private final JLabel hintLabel = new JLabel();
    private final JLabel messageLabel = new JLabel(" ");
    private final JLabel confirmLabel = new JLabel("确认密码");

    private final JButton submitButton = new JButton();
    private final JButton switchButton = new JButton();
    private final JButton cancelButton = new JButton("退出");

    private Mode mode;
    private User loggedInUser;

    public LoginDialog(Frame owner) {
        super(owner, true);
        mode = userService.needsInitialAdmin() ? Mode.INITIAL : Mode.LOGIN;

        setTitle("StudyTimeTracker");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBorder(BorderFactory.createEmptyBorder(22, 26, 18, 26));
        content.add(createHeader(), BorderLayout.NORTH);
        content.add(createForm(), BorderLayout.CENTER);
        content.add(createButtons(), BorderLayout.SOUTH);
        setContentPane(content);

        applyMode();
        pack();
        setLocationRelativeTo(null);
    }

    /** 登录成功后的用户；用户取消时为 null。 */
    public User getLoggedInUser() {
        return loggedInUser;
    }

    private JPanel createHeader() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        titleLabel.setFont(titleLabel.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        hintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        hintLabel.setForeground(new Color(0x6A737D));

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(6));
        panel.add(hintLabel);
        return panel;
    }

    private JPanel createForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        addRow(form, gbc, 0, new JLabel("用户名"), usernameField);
        addRow(form, gbc, 1, new JLabel("密码"), passwordField);
        addRow(form, gbc, 2, confirmLabel, confirmField);
        addRow(form, gbc, 3, new JLabel(" "), PasswordVisibility.createToggle(passwordField, confirmField));

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        messageLabel.setForeground(new Color(0xC0392B));
        form.add(messageLabel, gbc);

        return form;
    }

    private void addRow(JPanel form, GridBagConstraints gbc, int row, JLabel label, Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        form.add(label, gbc);

        gbc.gridx = 1;
        form.add(field, gbc);
    }

    private JPanel createButtons() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        submitButton.setPreferredSize(new Dimension(130, 32));
        submitButton.addActionListener(this::onSubmit);

        switchButton.setPreferredSize(new Dimension(130, 32));
        switchButton.addActionListener(e -> {
            mode = mode == Mode.REGISTER ? Mode.LOGIN : Mode.REGISTER;
            messageLabel.setText(" ");
            applyMode();
            pack();
        });

        cancelButton.setPreferredSize(new Dimension(84, 32));
        cancelButton.addActionListener(e -> {
            loggedInUser = null;
            dispose();
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(switchButton);
        right.add(cancelButton);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(submitButton);

        panel.add(left, BorderLayout.WEST);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    /** 根据当前形态刷新标题、按钮文字与字段可见性。 */
    private void applyMode() {
        boolean needConfirm = mode != Mode.LOGIN;
        confirmLabel.setVisible(needConfirm);
        confirmField.setVisible(needConfirm);
        switch (mode) {
            case INITIAL -> {
                titleLabel.setText("首次使用，请创建管理员账号");
                hintLabel.setText("该账号将接管已有的学习数据，用户名和密码请自行设置。");
                submitButton.setText("创建并进入");
                switchButton.setVisible(false);
            }
            case REGISTER -> {
                titleLabel.setText("注册新账户");
                hintLabel.setText("新账户的学习记录与其它账户相互独立。");
                submitButton.setText("注册并进入");
                switchButton.setText("返回登录");
                switchButton.setVisible(true);
            }
            default -> {
                titleLabel.setText("登录");
                hintLabel.setText("请输入用户名和密码。");
                submitButton.setText("登录");
                switchButton.setText("注册新用户");
                switchButton.setVisible(true);
            }
        }

        passwordField.setText("");
        confirmField.setText("");
        messageLabel.setText(" ");
        getRootPane().setDefaultButton(submitButton);
    }

    private void onSubmit(ActionEvent event) {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());
        String confirm = new String(confirmField.getPassword());

        try {
            loggedInUser = switch (mode) {
                case INITIAL -> userService.initializeAdmin(username, password, confirm);
                case REGISTER -> userService.register(username, password, confirm);
                default -> userService.login(username, password);
            };
            dispose();
        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());
            confirmField.setText("");
        }
    }
}
