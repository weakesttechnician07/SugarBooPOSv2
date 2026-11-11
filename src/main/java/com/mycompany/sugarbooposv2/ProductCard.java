package com.mycompany.sugarbooposv2;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * One menu card: rounded white box with a photo, the name and the price.
 * Items with several sizes (drinks) show Small / Large / Jumbo chips instead of a single price:
 * tap a chip to choose the size, tap the rest of the card to add it to the order.
 */
final class ProductCard extends JComponent {

    interface Listener {
        void add(PosPanel.Item item, PosPanel.Size size);
        void sizeChosen(String sku, int index);
    }

    private static final int PAD = 8, CHIP_H = 34, CHIP_GAP = 5;

    private final PosPanel.Item item;
    private final Listener listener;
    private final boolean chips, soldOut;
    private final List<Rectangle> chipRects = new ArrayList<>();
    private int sel;
    private boolean hover;
    private BufferedImage photoCache;
    private int photoCacheW = -1, photoCacheH = -1;

    ProductCard(PosPanel.Item item, int selectedSize, Listener listener) {
        this.item = item;
        this.listener = listener;
        this.chips = item.sizes.size() > 1;
        this.soldOut = item.stock <= 0;
        this.sel = Math.max(0, Math.min(selectedSize, item.sizes.size() - 1));
        setCursor(Cursor.getPredefinedCursor(soldOut ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            @Override public void mouseReleased(MouseEvent e) {
                if (!contains(e.getPoint())) return;
                for (int i = 0; i < chipRects.size(); i++) {
                    if (chipRects.get(i).contains(e.getPoint())) {
                        if (!soldOut) {
                            sel = i;
                            listener.sizeChosen(item.sku, i);
                            repaint();
                        }
                        return;
                    }
                }
                if (!soldOut) listener.add(item, item.sizes.get(sel));
            }
        };
        addMouseListener(ma);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(100, chips ? 236 : 212);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Ui.smooth(g);
        int w = getWidth(), h = getHeight();
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 20, 20));

        // photo
        int photoH = chips ? 116 : 122;           // roughly 3:2, so square photos are not cut to a thin strip
        int photoW = w - 2 * PAD;
        g2.drawImage(photo(photoW, photoH), PAD, PAD, null);

        // name (up to 2 lines)
        Font nameFont = Theme.body(Font.BOLD, 12f);
        g2.setFont(nameFont);
        g2.setColor(Theme.INK);
        FontMetrics fm = g2.getFontMetrics();
        int y = PAD + photoH + 8 + fm.getAscent();
        for (String line : wrap(item.name, fm, w - 2 * PAD - 4, 2)) {
            g2.drawString(line, PAD + 2, y);
            y += fm.getHeight();
        }

        chipRects.clear();
        if (chips) {
            int n = item.sizes.size();
            int cw = (w - 2 * PAD - (n - 1) * CHIP_GAP) / n;
            int cy = h - PAD - CHIP_H;
            Font cf = Theme.body(Font.BOLD, 10f);
            for (int i = 0; i < n; i++) {
                Rectangle r = new Rectangle(PAD + i * (cw + CHIP_GAP), cy, cw, CHIP_H);
                chipRects.add(r);
                boolean on = i == sel;
                RoundRectangle2D shape = new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, 12, 12);
                g2.setColor(on ? Theme.RASPBERRY : Color.WHITE);
                g2.fill(shape);
                g2.setColor(on ? Theme.RASPBERRY : Theme.LINE);
                g2.setStroke(new BasicStroke(1.3f));
                g2.draw(new RoundRectangle2D.Float(r.x + 0.65f, r.y + 0.65f, r.width - 1.3f, r.height - 1.3f, 12, 12));
                g2.setColor(on ? Color.WHITE : Theme.RASPBERRY);
                PosPanel.Size s = item.sizes.get(i);
                centered(g2, cf, s.label == null ? "" : s.label, r.x + r.width / 2, r.y + 14);
                centered(g2, cf, money0(s.price), r.x + r.width / 2, r.y + 27);
            }
        } else {
            g2.setColor(Theme.RASPBERRY);
            Font pf = Theme.body(Font.BOLD, 14f);
            Ui.drawMixed(g2, pf, Theme.peso(item.sizes.get(0).price), PAD + 2, h - PAD - 4);
        }

        if (soldOut) {
            g2.setColor(new Color(255, 255, 255, 190));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 20, 20));
            g2.setColor(Theme.RASPBERRY);
            centered(g2, Theme.body(Font.BOLD, 14f), "SOLD OUT", w / 2, h / 2);
        }

        // border last, so it sits on top
        g2.setColor(hover && !soldOut ? Theme.RASPBERRY : Theme.LINE);
        g2.setStroke(new BasicStroke(hover && !soldOut ? 2.2f : 1.5f));
        float o = hover && !soldOut ? 1.1f : 0.75f;
        g2.draw(new RoundRectangle2D.Float(o, o, w - 2 * o, h - 2 * o, 20, 20));
        g2.dispose();
    }

    private static void centered(Graphics2D g2, Font f, String s, int cx, int baseline) {
        g2.setFont(f);
        Ui.drawMixed(g2, f, s, cx - Ui.mixedWidth(g2, f, s) / 2, baseline);
    }

    private static String money0(double p) {
        return p == Math.rint(p) ? "\u20B1" + (long) p : Theme.peso(p);
    }

    /** The photo (or a pastel placeholder), scaled to cover the area and cut with rounded corners. */
    private BufferedImage photo(int w, int h) {
        if (photoCache != null && photoCacheW == w && photoCacheH == h) return photoCache;
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Ui.smooth(out.getGraphics());
        RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, 14, 14);
        g.setColor(Theme.TINTS[Math.abs(String.valueOf(item.sku).hashCode()) % Theme.TINTS.length]);
        g.fill(shape);
        if (item.photo != null) {
            g.setComposite(AlphaComposite.SrcAtop);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            double s = Math.max((double) w / item.photo.getWidth(), (double) h / item.photo.getHeight());
            int pw = (int) Math.round(item.photo.getWidth() * s), ph = (int) Math.round(item.photo.getHeight() * s);
            g.drawImage(item.photo, (w - pw) / 2, (h - ph) / 2, pw, ph, null);
        } else {
            g.setColor(Theme.MUTED);
            centered(g, Theme.body(Font.PLAIN, 10f), "No photo", w / 2, h / 2 + 4);
        }
        g.dispose();
        // cut the corners: keep only what is inside the rounded shape
        BufferedImage masked = new BufferedImage(out.getWidth(), out.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D m = Ui.smooth(masked.getGraphics());
        m.fill(shape);
        m.setComposite(AlphaComposite.SrcIn);
        m.drawImage(out, 0, 0, null);
        m.setComposite(AlphaComposite.SrcOver);
        m.setColor(new Color(0xFF, 0xD6, 0xE5));
        m.setStroke(new BasicStroke(1.2f));
        m.draw(new RoundRectangle2D.Float(0.6f, 0.6f, w - 1.2f, h - 1.2f, 14, 14));
        m.dispose();
        photoCache = masked;
        photoCacheW = w;
        photoCacheH = h;
        return masked;
    }

    private static List<String> wrap(String text, FontMetrics fm, int maxW, int maxLines) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String tryLine = cur.length() == 0 ? words[i] : cur + " " + words[i];
            if (fm.stringWidth(tryLine) <= maxW || cur.length() == 0) {
                cur = new StringBuilder(tryLine);
            } else {
                lines.add(cur.toString());
                cur = new StringBuilder(words[i]);
                if (lines.size() == maxLines - 1) {          // everything left goes on the last line
                    StringBuilder rest = new StringBuilder(words[i]);
                    for (int j = i + 1; j < words.length; j++) rest.append(' ').append(words[j]);
                    cur = rest;
                    break;
                }
            }
        }
        lines.add(cur.toString());
        String last = lines.get(lines.size() - 1);
        if (fm.stringWidth(last) > maxW) {
            while (last.length() > 1 && fm.stringWidth(last + "...") > maxW) last = last.substring(0, last.length() - 1);
            lines.set(lines.size() - 1, last + "...");
        }
        return lines;
    }
}
