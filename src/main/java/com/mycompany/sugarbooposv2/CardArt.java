/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author mathe
 */

package com.mycompany.sugarbooposv2;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** Draws a Sugar Boo product card (131 x 150): photo in a dashed pink frame, then name and price. */
public final class CardArt {

    public static final int W = 131, H = 150;
    private static final String FONT_FILE = "/fonts/BakerieRough-Black.otf";   // your downloaded font (optional)
    private static final String FALLBACK_FONT = "Comic Sans MS";  // used if the file above is missing
    private static final float NAME_SIZE = 13f, PRICE_SIZE = 13f;
    private static final int TEXT_X = 20;           // left edge of the text (the photo frame starts at 20)
    private static final boolean CENTER_TEXT = false; // true = centre name and price under the photo
    private static Font baseFont;
    private static final Color PINK = new Color(0xFB, 0x6F, 0x92);
    private static final Color INK = new Color(30, 30, 30);

    private CardArt() { }

    private static synchronized Font font(int style, float size) {
        if (baseFont == null) {
            try (java.io.InputStream in = CardArt.class.getResourceAsStream(FONT_FILE)) {
                if (in != null) baseFont = Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Exception ex) {
                // fall through to the fallback font
            }
            if (baseFont == null) baseFont = new Font(FALLBACK_FONT, Font.PLAIN, 1);
        }
        return baseFont.deriveFont(style, size);
    }

    /**
     * photo may be null (a placeholder is drawn). A photo that is exactly 131x150 is treated as
     * ready-made card art; any other size is a plain photo and gets placed in the dashed frame.
     */
    public static BufferedImage render(BufferedImage photo, String name, double price, boolean soldOut) {
        BufferedImage card;
        if (photo != null && photo.getWidth() == W && photo.getHeight() == H) {
            card = copy(photo);
        } else {
            card = blankCard();
            Graphics2D g = card.createGraphics();
            hints(g);
            RoundRectangle2D frame = new RoundRectangle2D.Float(20, 31, 91, 88, 18, 18);
            if (photo != null) {
                Shape old = g.getClip();
                g.setClip(frame);
                double s = Math.max(91.0 / photo.getWidth(), 88.0 / photo.getHeight());
                int w = (int) Math.round(photo.getWidth() * s);
                int h = (int) Math.round(photo.getHeight() * s);
                g.drawImage(photo, 20 + (91 - w) / 2, 31 + (88 - h) / 2, w, h, null);
                g.setClip(old);
            } else {
                g.setColor(new Color(150, 150, 150));
                g.setFont(font(Font.PLAIN, 11f));
                String t = "No photo";
                g.drawString(t, 20 + (91 - g.getFontMetrics().stringWidth(t)) / 2, 31 + 48);
            }
            g.setColor(PINK);
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                    10f, new float[]{4f, 3f}, 0f));
            g.draw(frame);
            g.dispose();
        }
        Graphics2D g = card.createGraphics();
        hints(g);
        g.setColor(INK);
        float size = NAME_SIZE;
        Font f = font(Font.PLAIN, size);
        while (size > 8f && g.getFontMetrics(f).stringWidth(name) > W - TEXT_X - 6) {
            f = font(Font.PLAIN, size -= 0.5f);
        }
        g.setFont(f);
        g.drawString(name, textX(g.getFontMetrics().stringWidth(name)), 133);
        drawPrice(g, price, 146);
        if (soldOut) {
            g.setColor(new Color(255, 255, 255, 170));
            g.fillRect(0, 0, W, H);
            g.setColor(new Color(200, 40, 70));
            g.setFont(font(Font.BOLD, 15f));
            String t = "SOLD OUT";
            g.drawString(t, (W - g.getFontMetrics().stringWidth(t)) / 2, 80);
        }
        g.dispose();
        return card;
    }

    private static int textX(int textWidth) {
        return CENTER_TEXT ? (W - textWidth) / 2 : TEXT_X;
    }

    /** Draws the peso sign with a system font if the custom font has no peso glyph, then the amount. */
    private static void drawPrice(Graphics2D g, double price, int y) {
        Font numberFont = font(Font.PLAIN, PRICE_SIZE);
        Font signFont = numberFont.canDisplay('\u20B1') ? numberFont
                : new Font(Font.DIALOG, Font.PLAIN, Math.round(PRICE_SIZE));
        String sign = "\u20B1";
        String amount = String.format(java.util.Locale.US, "%,.2f", price);
        int signW = g.getFontMetrics(signFont).stringWidth(sign);
        int amountW = g.getFontMetrics(numberFont).stringWidth(amount);
        int x = textX(signW + 1 + amountW);
        g.setFont(signFont);
        g.drawString(sign, x, y);
        g.setFont(numberFont);
        g.drawString(amount, x + signW + 1, y);
    }

    private static BufferedImage blankCard() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.dispose();
        return img;
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return img;
    }

    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }
}