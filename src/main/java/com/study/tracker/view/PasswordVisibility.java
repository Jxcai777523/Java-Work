package com.study.tracker.view;

import javax.swing.JCheckBox;
import javax.swing.JPasswordField;

/**
 * 密码显示开关。
 * <p>
 * 勾选时把 {@link JPasswordField} 的回显字符设为 0，即直接显示明文；
 * 取消勾选时恢复该输入框原本的回显字符。
 * 恢复用的字符在勾选当下记录，因此切换界面主题后也不会退回错误的字符。
 */
final class PasswordVisibility {

    private PasswordVisibility() {
    }

    /**
     * 创建一个"显示密码"复选框。
     *
     * @param fields 需要联动的密码输入框，可以一次传多个（如密码与确认密码）
     * @return 复选框
     */
    static JCheckBox createToggle(JPasswordField... fields) {
        JCheckBox toggle = new JCheckBox("显示密码");
        char[] hiddenEchoChars = new char[fields.length];

        toggle.addActionListener(e -> {
            for (int i = 0; i < fields.length; i++) {
                JPasswordField field = fields[i];
                if (toggle.isSelected()) {
                    char current = field.getEchoChar();
                    if (current != 0) {
                        hiddenEchoChars[i] = current;
                    }
                    field.setEchoChar((char) 0);
                } else if (hiddenEchoChars[i] != 0) {
                    field.setEchoChar(hiddenEchoChars[i]);
                }
            }
        });
        return toggle;
    }
}
