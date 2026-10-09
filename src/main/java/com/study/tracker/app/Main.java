package com.study.tracker.app;

import com.study.tracker.config.AppearanceManager;
import com.study.tracker.config.UserSession;
import com.study.tracker.dao.DBUtil;
import com.study.tracker.model.User;
import com.study.tracker.view.LoginDialog;
import com.study.tracker.view.MainFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * 程序入口。
 * <p>
 * 启动顺序：初始化数据库 → 读取外观偏好 → 安装主题 → 登录/注册 → 打开主窗口。
 * 主题必须早于任何 Swing 组件的创建，否则界面不会应用新外观。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // 1. 初始化数据库，外观偏好与账户都存放在其中
            try {
                DBUtil.initialize();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "数据库初始化失败：\n" + e.getMessage(),
                        "启动错误", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }

            // 2. 读取并安装外观，必须早于窗口创建
            AppearanceManager.load();
            AppearanceManager.apply();

            // 3. 登录：首次使用会引导创建管理员账号
            LoginDialog loginDialog = new LoginDialog(null);
            loginDialog.setVisible(true);
            User user = loginDialog.getLoggedInUser();
            if (user == null) {
                System.exit(0);
            }
            UserSession.login(user);

            // 4. 打开主窗口
            new MainFrame().setVisible(true);
        });
    }
}
