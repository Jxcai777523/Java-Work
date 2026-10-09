package com.study.tracker.view;

import com.study.tracker.config.AppConfig;
import com.study.tracker.config.AppearanceManager;
import com.study.tracker.config.UserSession;
import com.study.tracker.model.User;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 主窗口。
 * <p>
 * 使用 JTabbedPane 划分功能页签：学习计时、待办任务、学习记录、学习目标、效率分析、系统设置。
 * "系统设置"内部再用子页签组织科目管理、外观设置与数据管理。
 * <p>
 * 窗口大小与位置由 {@link AppearanceManager} 记忆：退出时保存，启动时恢复；
 * 若上次是最大化状态，则启动后直接最大化。
 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final TimerPanel timerPanel = new TimerPanel();
    private final TaskPanel taskPanel = new TaskPanel();
    private final RecordPanel recordPanel = new RecordPanel();
    private final GoalPanel goalPanel = new GoalPanel();
    private final AnalysisPanel analysisPanel = new AnalysisPanel();

    public MainFrame() {
        setTitle(AppConfig.APP_TITLE);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(960, 640));
        restoreWindowBounds();

        // 内容放在自绘背景面板上，背景图由它负责绘制
        BackgroundPanel backgroundPanel = new BackgroundPanel();
        backgroundPanel.setLayout(new BorderLayout());
        backgroundPanel.add(createTabbedPane(), BorderLayout.CENTER);
        backgroundPanel.add(createStatusBar(), BorderLayout.SOUTH);
        setContentPane(backgroundPanel);

        // 表格行高由外观设置统一控制，界面构建完成后应用一次
        AppearanceManager.applyTableRowHeight(getContentPane());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApplication();
            }
        });

        if (AppearanceManager.isWindowMaximized()) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
    }

    /** 恢复上次退出时的窗口位置；没有记录或记录已不在可见屏幕上时居中显示。 */
    private void restoreWindowBounds() {
        Rectangle saved = AppearanceManager.loadWindowBounds();
        if (saved != null) {
            setBounds(saved);
        } else {
            setSize(1120, 720);
            setLocationRelativeTo(null);
        }
    }

    /** 保存窗口状态后退出程序。 */
    private void exitApplication() {
        boolean maximized = (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        AppearanceManager.saveWindowState(maximized ? null : getBounds(), maximized);
        dispose();
        System.exit(0);
    }

    /** 创建中部页签容器。 */
    private JTabbedPane createTabbedPane() {
        JTabbedPane tabbedPane = new JTabbedPane();

        tabbedPane.addTab("学习计时", timerPanel);
        tabbedPane.addTab("待办任务", taskPanel);
        tabbedPane.addTab("学习记录", recordPanel);
        tabbedPane.addTab("学习目标", goalPanel);
        tabbedPane.addTab("效率分析", analysisPanel);
        tabbedPane.addTab("系统设置", createSettingsPanel());

        // 切换页签时刷新数据，保证在其它页面产生的变更能立即反映出来
        tabbedPane.addChangeListener(e -> {
            Object selected = tabbedPane.getSelectedComponent();
            if (selected == timerPanel) {
                timerPanel.reloadSubjects();
            } else if (selected == taskPanel) {
                taskPanel.reload();
            } else if (selected == recordPanel) {
                recordPanel.reloadSubjects();
            } else if (selected == goalPanel) {
                goalPanel.reload();
            } else if (selected == analysisPanel) {
                analysisPanel.reload();
            }
        });

        return tabbedPane;
    }

    /** 系统设置页签：内部再用子页签分组。 */
    private JPanel createSettingsPanel() {
        JTabbedPane settingsTabs = new JTabbedPane();
        settingsTabs.addTab("科目管理", new SubjectPanel());
        settingsTabs.addTab("外观设置", new AppearancePanel(analysisPanel::reload));
        settingsTabs.addTab("数据管理", new DataPanel());
        settingsTabs.addTab("我的账户", new AccountPanel());
        if (UserSession.isAdmin()) {
            settingsTabs.addTab("用户管理", new UserPanel());
        }

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(settingsTabs, BorderLayout.CENTER);
        return wrapper;
    }

    /** 创建底部状态栏。 */
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));

        User user = UserSession.current();
        String text = user == null
                ? "就绪"
                : "当前账户：" + user.getUsername() + "（" + user.getRole().getDisplayName() + "）";
        JLabel hint = new JLabel(text);
        statusBar.add(hint, BorderLayout.WEST);
        return statusBar;
    }
}
