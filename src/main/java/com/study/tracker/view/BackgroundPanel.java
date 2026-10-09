package com.study.tracker.view;

import com.study.tracker.config.AppearanceManager;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * 主窗口背景面板。
 * <p>
 * 先铺一层当前主题的面板底色，再按设置的填充方式画出背景图：
 * 拉伸铺满会按窗口尺寸缩放并缓存缩放结果，平铺与居中直接使用原图。
 * 未设置背景图时，它就是一个普通的主题色面板。
 */
public class BackgroundPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** 缩放结果缓存，避免每次重绘都重新缩放。 */
    private BufferedImage cachedScaled;
    private BufferedImage cachedSource;
    private int cachedWidth;
    private int cachedHeight;

    public BackgroundPanel() {
        setOpaque(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());

            BufferedImage image = AppearanceManager.getBackgroundImage();
            if (image == null || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }

            float alpha = Math.max(0.1f, Math.min(1f, AppearanceManager.getBackgroundOpacity() / 100f));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            switch (AppearanceManager.getBackgroundMode()) {
                case AppearanceManager.MODE_TILE -> {
                    TexturePaint paint = new TexturePaint(image,
                            new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight()));
                    g2.setPaint(paint);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                case AppearanceManager.MODE_CENTER -> {
                    int x = (getWidth() - image.getWidth()) / 2;
                    int y = (getHeight() - image.getHeight()) / 2;
                    g2.drawImage(image, x, y, null);
                }
                default -> g2.drawImage(scaledImage(image), 0, 0, null);
            }
        } finally {
            g2.dispose();
        }
    }

    /** 按当前面板尺寸缩放图片，尺寸未变化时复用缓存。 */
    private BufferedImage scaledImage(BufferedImage source) {
        if (cachedScaled != null && cachedSource == source
                && cachedWidth == getWidth() && cachedHeight == getHeight()) {
            return cachedScaled;
        }

        BufferedImage target = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = target.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(source, 0, 0, getWidth(), getHeight(), null);
        } finally {
            g2.dispose();
        }

        cachedScaled = target;
        cachedSource = source;
        cachedWidth = getWidth();
        cachedHeight = getHeight();
        return target;
    }
}
