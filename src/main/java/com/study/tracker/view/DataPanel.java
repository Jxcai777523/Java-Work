package com.study.tracker.view;

import com.study.tracker.service.DataService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 数据管理面板。
 * <p>
 * 提供三项功能：把数据库备份到指定位置、从备份恢复数据库、把学习记录导出为 CSV。
 * 恢复会覆盖当前数据，因此界面上先说明影响并要求二次确认；
 * 恢复完成后程序会正常退出，由用户重新打开以载入新数据。
 */
public class DataPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** 备份文件名中的时间戳格式。 */
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DataService dataService = new DataService();
    private final JLabel databaseLabel = new JLabel();

    public DataPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(createInfoPanel());
        content.add(Box.createVerticalStrut(14));
        content.add(createBackupPanel());
        content.add(Box.createVerticalStrut(14));
        content.add(createExportPanel());
        content.add(Box.createVerticalGlue());
        add(content, BorderLayout.CENTER);

        refreshInfo();
    }

    /** 当前数据库信息。 */
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("当前数据"));
        refreshInfo();
        panel.add(databaseLabel, BorderLayout.WEST);
        return panel;
    }

    /** 备份与恢复。 */
    private JPanel createBackupPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createTitledBorder("备份与恢复"));

        JLabel hint = new JLabel("<html>备份会生成一个独立的数据库副本，程序运行中也可以安全备份；"
                + "<br>恢复会用备份覆盖当前的全部数据，执行前会自动另存一份当前数据。</html>");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton backupButton = new JButton("备份数据库…");
        backupButton.addActionListener(e -> doBackup());
        JButton restoreButton = new JButton("从备份恢复…");
        restoreButton.addActionListener(e -> doRestore());
        buttons.add(backupButton);
        buttons.add(restoreButton);

        JLabel warning = new JLabel("恢复会替换现有数据，操作后需要重新打开程序。");
        warning.setForeground(new Color(0xC0392B));

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(hint, BorderLayout.NORTH);
        top.add(buttons, BorderLayout.CENTER);
        top.add(warning, BorderLayout.SOUTH);

        panel.add(top, BorderLayout.WEST);
        return panel;
    }

    /** 导出区。 */
    private JPanel createExportPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createTitledBorder("导出"));

        JLabel hint = new JLabel("把全部学习记录导出为 CSV 文件，带 UTF-8 BOM，可用 Excel 直接打开。");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton exportButton = new JButton("导出学习记录 CSV…");
        exportButton.addActionListener(e -> doExport());
        buttons.add(exportButton);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(hint, BorderLayout.NORTH);
        top.add(buttons, BorderLayout.CENTER);

        panel.add(top, BorderLayout.WEST);
        return panel;
    }

    /** 执行备份。 */
    private void doBackup() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择备份保存位置");
        chooser.setSelectedFile(new File("StudyTimeTracker-backup-"
                + LocalDateTime.now().format(STAMP) + ".db"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File target = chooser.getSelectedFile();
        try {
            dataService.backup(target);
            JOptionPane.showMessageDialog(this,
                    "备份完成：\n" + target.getAbsolutePath(),
                    "备份成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException e) {
            showError("备份失败", e);
        }
    }

    /** 执行恢复。 */
    private void doRestore() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择要恢复的备份文件");
        chooser.setFileFilter(new FileNameExtensionFilter("数据库备份 (*.db, *.sqlite)", "db", "sqlite", "sqlite3"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File source = chooser.getSelectedFile();
        int answer = JOptionPane.showConfirmDialog(this,
                "将用下面的备份覆盖当前的全部学习数据：\n" + source.getAbsolutePath()
                        + "\n\n当前数据会自动另存一份到数据目录；恢复完成后程序将退出，需要重新打开。\n确定继续吗？",
                "确认恢复数据", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            Path safety = dataService.restore(source);
            JOptionPane.showMessageDialog(this,
                    "恢复完成。\n当前数据已另存为：\n" + safety
                            + "\n\n程序将退出，请重新打开以载入恢复后的数据。",
                    "恢复成功", JOptionPane.INFORMATION_MESSAGE);
            closeApplication();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "无法恢复", JOptionPane.WARNING_MESSAGE);
        } catch (RuntimeException e) {
            showError("恢复失败", e);
        }
    }

    /** 执行 CSV 导出。 */
    private void doExport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择导出位置");
        chooser.setSelectedFile(new File("学习记录-" + LocalDateTime.now().format(STAMP) + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File target = chooser.getSelectedFile();
        try {
            int count = dataService.exportRecords(target);
            JOptionPane.showMessageDialog(this,
                    "已导出 " + count + " 条学习记录：\n" + target.getAbsolutePath(),
                    "导出成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException e) {
            showError("导出失败", e);
        }
    }

    /** 刷新数据库信息显示。 */
    private void refreshInfo() {
        long size = dataService.databaseSize();
        databaseLabel.setText("<html>数据库文件：" + dataService.databasePath()
                + "<br>当前大小：" + size + " 字节（约 " + Math.max(1, size / 1024) + " KB）</html>");
    }

    /** 关闭主窗口，走与点关闭按钮相同的保存流程。 */
    private void closeApplication() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
        }
    }

    private void showError(String title, RuntimeException e) {
        JOptionPane.showMessageDialog(this, e.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }
}
