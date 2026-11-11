package com.mycompany.sugarbooposv2;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Small custom-painted components that give the POS its rounded look.
 * Standard Swing components draw square boxes; these draw themselves with anti-aliasing instead.
 */
public final class Ui {

    private Ui() { }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }


    /** Width of text where characters missing from f (like the peso sign) are measured in a system font. */
    static int mixedWidth(Graphics2D g2, Font f, String s) {
        int w = 0;
        Font fb = new Font(Font.DIALOG, f.getStyle() == Font.PLAIN ? Font.PLAIN : Font.BOLD, 1).deriveFont(f.getSize2D());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += g2.getFontMetrics(f.canDisplay(c) ? f : fb).charWidth(c);
        }
        return w;
    }

    /** Draws text with a system font for any character the font f can not show (the peso sign). */
    static void drawMixed(Graphics2D g2, Font f, String s, int x, int y) {
        Font fb = new Font(Font.DIALOG, f.getStyle() == Font.PLAIN ? Font.PLAIN : Font.BOLD, 1).deriveFont(f.getSize2D());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            Font use = f.canDisplay(c) ? f : fb;
            g2.setFont(use);
            g2.drawString(String.valueOf(c), x, y);
            x += g2.getFontMetrics().charWidth(c);
        }
    }

    static Color mix(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** A panel with a rounded fill and a rounded border. Children are clipped to the rounded shape. */
    public static class RoundPanel extends JPanel {
        private final Color fill, line;
        private final int arc;
        private final float lineWidth;

        public RoundPanel(Color fill, Color line, int arc) {
            this(fill, line, arc, 1.5f);
        }

        public RoundPanel(Color fill, Color line, int arc, float lineWidth) {
            this.fill = fill;
            this.line = line;
            this.arc = arc;
            this.lineWidth = lineWidth;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            g2.dispose();
        }

        @Override
        protected void paintChildren(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.clip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            super.paintChildren(g2);
            g2.dispose();
            if (line != null) {
                Graphics2D b = smooth(g);
                b.setColor(line);
                b.setStroke(new BasicStroke(lineWidth));
                float o = lineWidth / 2f;
                b.draw(new RoundRectangle2D.Float(o, o, getWidth() - lineWidth, getHeight() - lineWidth, arc, arc));
                b.dispose();
            }
        }
    }

    /** Pink header band: a soft left-to-right gradient. */
    public static class GradientPanel extends JPanel {
        public GradientPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setPaint(new GradientPaint(0, 0, Theme.HEADER_LEFT, getWidth(), 0, Theme.HEADER_RIGHT));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    /** A pill-shaped button (or a small rounded square with a drawn -, + or X icon). */
    public static class PillButton extends JButton {
        public enum Kind { FILLED, SOFT, OUTLINE, OUTLINE_DARK }

        public static final int ICON_NONE = 0, ICON_MINUS = 1, ICON_PLUS = 2, ICON_CROSS = 3;

        private final Kind kind;
        private boolean on;          // "selected" look, used by the category tabs
        private int depth;           // thickness of the darker bottom edge (Pay button)
        private int icon = ICON_NONE;
        private int arc = -1;        // -1 = fully round

        public PillButton(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setMargin(new Insets(0, 0, 0, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Theme.body(Font.BOLD, 12f));
        }

        public static PillButton iconButton(int icon, String tip) {
            PillButton b = new PillButton("", Kind.OUTLINE);
            b.icon = icon;
            b.arc = 14;
            b.setToolTipText(tip);
            b.setPreferredSize(new Dimension(46, 34));
            return b;
        }

        public PillButton depth(int d) {
            depth = d;
            return this;
        }

        public void setOn(boolean value) {
            on = value;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight() - depth;
            boolean enabled = isEnabled();
            boolean hover = enabled && getModel().isRollover();
            boolean pressed = enabled && getModel().isPressed();

            Color fill, line, fg;
            Kind k = (on && kind == Kind.OUTLINE) ? Kind.FILLED : kind;
            switch (k) {
                case FILLED:
                    fill = Theme.RASPBERRY; line = Theme.RASPBERRY; fg = Color.WHITE; break;
                case SOFT:
                    fill = Theme.PINK_FILL; line = Theme.LINE; fg = Theme.RASPBERRY; break;
                case OUTLINE_DARK:
                    fill = Color.WHITE; line = Theme.RASPBERRY; fg = Theme.RASPBERRY; break;
                default:
                    fill = Color.WHITE; line = Theme.LINE; fg = Theme.RASPBERRY; break;
            }
            if (!enabled) {
                fill = Ui.mix(fill, Color.WHITE, 0.55f);
                line = Ui.mix(line, Color.WHITE, 0.55f);
                fg = k == Kind.FILLED ? Color.WHITE : Ui.mix(fg, Color.WHITE, 0.5f);
            } else if (pressed) {
                fill = Ui.mix(fill, Color.BLACK, 0.12f);
            } else if (hover) {
                fill = k == Kind.FILLED ? Ui.mix(fill, Color.WHITE, 0.12f) : Ui.mix(fill, Theme.PINK_FILL, 0.5f);
            }
            int a = arc < 0 ? h : arc;
            int dy = pressed ? Math.min(depth, 2) : 0;       // sinks a little when pressed
            if (depth > 0) {
                g2.setColor(enabled ? Theme.RASPBERRY_DARK : Ui.mix(Theme.RASPBERRY_DARK, Color.WHITE, 0.55f));
                g2.fill(new RoundRectangle2D.Float(0, depth - dy, w, h, a, a));
            }
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(0, dy, w, h, a, a));
            g2.setColor(line);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new RoundRectangle2D.Float(0.75f, dy + 0.75f, w - 1.5f, h - 1.5f, a, a));

            g2.setColor(fg);
            if (icon != ICON_NONE) {
                drawIcon(g2, w / 2f, dy + h / 2f);
            } else {
                FontMetrics fm = g2.getFontMetrics(getFont());
                String t = getText();
                int tx = (w - Ui.mixedWidth(g2, getFont(), t)) / 2;
                int ty = dy + (h - fm.getHeight()) / 2 + fm.getAscent();
                Ui.drawMixed(g2, getFont(), t, tx, ty);
            }
            g2.dispose();
        }

        private void drawIcon(Graphics2D g2, float cx, float cy) {
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float r = 5f;
            if (icon == ICON_MINUS || icon == ICON_PLUS) {
                g2.drawLine(Math.round(cx - r), Math.round(cy), Math.round(cx + r), Math.round(cy));
            }
            if (icon == ICON_PLUS) {
                g2.drawLine(Math.round(cx), Math.round(cy - r), Math.round(cx), Math.round(cy + r));
            }
            if (icon == ICON_CROSS) {
                g2.setColor(Ui.mix(Theme.RASPBERRY, Color.WHITE, 0.35f));
                g2.drawLine(Math.round(cx - r), Math.round(cy - r), Math.round(cx + r), Math.round(cy + r));
                g2.drawLine(Math.round(cx - r), Math.round(cy + r), Math.round(cx + r), Math.round(cy - r));
            }
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            if (icon != ICON_NONE) return d;
            Graphics2D g2 = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
            int tw = Ui.mixedWidth(g2, getFont(), getText());
            g2.dispose();
            return new Dimension(tw + 28, Math.max(d.height, 30) + depth);
        }
    }

    /** A text box with rounded corners. Money boxes are right-aligned. */
    public static class RoundField extends JTextField {
        private final Color fill, line;
        private String placeholder = "";

        public RoundField(Color fill, Color line, Color text, Font font) {
            this.fill = fill;
            this.line = line;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
            setHorizontalAlignment(SwingConstants.RIGHT);
            setForeground(text);
            setCaretColor(text);
            setSelectionColor(Theme.PINK_FILL);
            setSelectedTextColor(text);
            setFont(font);
        }

        public void setPlaceholder(String p) {
            placeholder = p;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
            g2.setColor(line);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new RoundRectangle2D.Float(0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f, 14, 14));
            g2.dispose();
            super.paintComponent(g);
            if (getText().isEmpty() && !isFocusOwner() && !placeholder.isEmpty()) {
                Graphics2D p = smooth(g);
                p.setFont(getFont());
                p.setColor(Theme.MUTED);
                FontMetrics fm = p.getFontMetrics();
                Insets in = getInsets();
                p.drawString(placeholder, getWidth() - in.right - fm.stringWidth(placeholder),
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                p.dispose();
            }
        }
    }

    /** A rounded password box. */
    public static class RoundPassword extends JPasswordField {
        public RoundPassword(Font font) {
            super(0);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
            setForeground(Theme.INK);
            setCaretColor(Theme.INK);
            setSelectionColor(Theme.PINK_FILL);
            setFont(font);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(Color.WHITE);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
            g2.setColor(isFocusOwner() ? Theme.RASPBERRY : Theme.LINE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new RoundRectangle2D.Float(0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f, 14, 14));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Thin pink scroll bar without arrow buttons. */
    public static class SlimScrollBarUI extends BasicScrollBarUI {
        private static JButton nothing() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override protected JButton createDecreaseButton(int orientation) { return nothing(); }
        @Override protected JButton createIncreaseButton(int orientation) { return nothing(); }
        @Override protected void configureScrollBarColors() { }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = smooth(g);
            g2.setColor(Theme.LINE);
            g2.fill(new RoundRectangle2D.Float(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6));
            g2.dispose();
        }
    }

    /** Gives a scroll pane the slim look and removes its border. */
    public static void slim(JScrollPane sp) {
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setViewportBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        sp.getVerticalScrollBar().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(24);
    }
}
