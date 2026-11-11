package com.mycompany.sugarbooposv2;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

/**
 * Colors and fonts of the Sugar Boo POS, all in one place.
 * Only TWO fonts are used: DISPLAY (Bakerie Rough) for big/title text and BODY (Quicksand) for everything else.
 */
public final class Theme {

    // ---- switches you can change ----
    /** false = use the body font everywhere (in case Bakerie Rough looks too rough somewhere). */
    static boolean USE_BAKERIE = true;
    /** false = draw the words "Sugar Boo" instead of using /images/logo/logo.png in the header. */
    static boolean USE_LOGO_IMAGE = true;

    // ---- font files (put them in src/main/resources/fonts) ----
    private static final String DISPLAY_FILE = "/fonts/BakerieRough-Black.otf";
    private static final String BODY_REGULAR_FILE = "/fonts/Quicksand-Regular.ttf";
    private static final String BODY_BOLD_FILE = "/fonts/Quicksand-Bold.ttf";

    // ---- palette (sampled from the mockup) ----
    public static final Color PAGE = new Color(0xFF, 0xF0, 0xF5);        // window background
    public static final Color HEADER_LEFT = new Color(0xFF, 0xC3, 0xD9);
    public static final Color HEADER_RIGHT = new Color(0xFF, 0xE1, 0xEB);
    public static final Color CREAM = new Color(0xFF, 0xF8, 0xEF);       // menu panel
    public static final Color WHITE = Color.WHITE;
    public static final Color LINE = new Color(0xFF, 0xA6, 0xC9);        // soft pink borders
    public static final Color PINK_FILL = new Color(0xFF, 0xD6, 0xE5);   // table header
    public static final Color PINK_SOFT = new Color(0xFF, 0xF0, 0xF5);   // total box, alternate rows
    public static final Color ROW_ALT = new Color(0xFF, 0xF0, 0xF5);
    public static final Color ROW_SELECTED = new Color(0xFF, 0xD6, 0xE5);
    public static final Color RASPBERRY = new Color(0xC2, 0x18, 0x5B);   // main accent, Pay, selected tab
    public static final Color RASPBERRY_DARK = new Color(0xB6, 0x1D, 0x59);
    public static final Color INK = new Color(0x3A, 0x2A, 0x33);         // main text
    public static final Color MUTED = new Color(0x8F, 0x7B, 0x86);       // small grey text
    public static final Color GREEN_FILL = new Color(0xE5, 0xF6, 0xEC);
    public static final Color GREEN_LINE = new Color(0x8B, 0xD3, 0xA2);
    public static final Color GREEN_TEXT = new Color(0x1B, 0x7F, 0x3B);
    public static final Color[] TINTS = {
        new Color(0xFF, 0xE0, 0xEB), new Color(0xFF, 0xE8, 0xD6), new Color(0xFF, 0xF5, 0xC9),
        new Color(0xF7, 0xD9, 0xC8), new Color(0xE8, 0xD4, 0xCF), new Color(0xE9, 0xDD, 0xF6)};

    private static Font displayBase, bodyRegular, bodyBold;
    private static String bodyFamily;          // fallback family name when no file is found
    private static boolean loaded;

    private Theme() { }

    // ---------- fonts ----------

    /** Big text: Pay button, titles, Total. */
    public static Font display(float size) {
        load();
        if (USE_BAKERIE && displayBase != null) return displayBase.deriveFont(Font.PLAIN, size);
        return body(Font.BOLD, size);
    }

    /** Normal text. style = Font.PLAIN or Font.BOLD. */
    public static Font body(int style, float size) {
        load();
        Font f = style == Font.BOLD ? bodyBold : bodyRegular;
        if (f != null) return f.deriveFont(Font.PLAIN, size);
        return new Font(bodyFamily, style, 1).deriveFont(size);
    }

    /** Returns f, or a system font of the same size/style if f has no peso sign. Use for any text that shows money. */
    public static Font safe(Font f) {
        return f.canDisplay('\u20B1') ? f : new Font(Font.DIALOG, f.getStyle() == Font.PLAIN ? Font.PLAIN : Font.BOLD, 1).deriveFont(f.getSize2D());
    }

    /** Peso amount like 1,250.00 with the peso sign. */
    public static String peso(double v) {
        return "\u20B1" + String.format(Locale.US, "%,.2f", v);
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        displayBase = read(DISPLAY_FILE);
        bodyRegular = read(BODY_REGULAR_FILE);
        bodyBold = read(BODY_BOLD_FILE);
        if (bodyRegular == null) bodyRegular = bodyBold;
        if (bodyBold == null) bodyBold = bodyRegular;
        if (bodyFamily == null) {
            Set<String> fam = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            bodyFamily = fam.contains("Quicksand") ? "Quicksand"
                    : fam.contains("Segoe UI") ? "Segoe UI" : Font.SANS_SERIF;
        }
    }

    private static Font read(String path) {
        try (InputStream in = Theme.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return Font.createFont(Font.TRUETYPE_FONT, in);   // also reads .otf
        } catch (Exception ex) {
            return null;
        }
    }

    /** For previews only: pretend this installed family is the body font. */
    static void overrideBodyFamily(String family) {
        bodyFamily = family;
        bodyRegular = null;
        bodyBold = null;
        loaded = true;
    }

    /** Makes message boxes use the same fonts and soft background. Call once, before any window is made. */
    public static void applyDialogDefaults() {
        FontUIResource msg = new FontUIResource(safe(body(Font.PLAIN, 14f)));
        FontUIResource btn = new FontUIResource(body(Font.BOLD, 13f));
        UIManager.put("OptionPane.messageFont", msg);
        UIManager.put("OptionPane.buttonFont", btn);
        UIManager.put("OptionPane.messageForeground", INK);
        UIManager.put("OptionPane.background", CREAM);
        UIManager.put("OptionPane.border", javax.swing.BorderFactory.createEmptyBorder(14, 14, 10, 14));
        UIManager.put("Button.font", btn);
        UIManager.put("TextField.font", msg);
        UIManager.put("Label.font", msg);
    }
}
