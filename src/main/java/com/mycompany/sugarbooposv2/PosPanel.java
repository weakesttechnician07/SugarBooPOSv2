package com.mycompany.sugarbooposv2;

import com.mycompany.sugarbooposv2.Ui.PillButton;
import com.mycompany.sugarbooposv2.Ui.RoundField;
import com.mycompany.sugarbooposv2.Ui.RoundPanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import org.bson.Document;

/**
 * The Sugar Boo POS screen (rounded design). Built in code, not in the NetBeans designer.
 * Menu and stock come from MongoDB Atlas through PosDb; sales are saved there too.
 */
public class PosPanel extends JPanel {

    static final String DB_NAME = "sugarboo_inventory";
    private static final String[] TAB_CATS = {"Pastry", "Drink", "Dessert"};
    private static final String[] TAB_LABELS = {"Pastry", "Drinks", "Desserts"};

    // ---------- data ----------

    /** One size of a product (a drink can have Small / Large / Jumbo). label is null for single-price items. */
    static final class Size {
        final String label;
        final double price;

        Size(String label, double price) {
            this.label = label;
            this.price = price;
        }
    }

    /** A product on the menu. Stock is one number per product, shared by all its sizes. */
    static final class Item {
        String sku, name, category;
        int stock, photoHash;
        BufferedImage photo;                      // may be null
        final List<Size> sizes = new ArrayList<>();
    }

    /** One line of the current order. Name, size and price are copied so later menu changes don't alter it. */
    private static final class Line {
        final String sku, name, size;
        final double price;
        int qty;

        Line(String sku, String name, String size, double price) {
            this.sku = sku;
            this.name = name;
            this.size = size;
            this.price = price;
            this.qty = 1;
        }

        double subtotal() { return price * qty; }
        String fullName() { return size == null ? name : name + " (" + size + ")"; }
    }

    private PosDb db;
    private String menuSignature = "";
    private String currentCategory = "Pastry";
    private boolean offlineNoticeShown;
    private final Map<String, Item> itemsBySku = new HashMap<>();
    private final Map<String, Integer> chosenSize = new HashMap<>();
    private final List<Line> lines = new ArrayList<>();
    private final Auth.User user;
    private final Runnable onLogout;
    private javax.swing.Timer refreshTimer;
    /** true while a paid order is shown for review (the order is locked until "Next Customer"). */
    private boolean reviewing;
    private String lastOrderNo = "";

    // ---------- widgets ----------
    private final JPanel cardMenu = new JPanel(new CardLayout());
    private final PillButton[] tabs = new PillButton[3];
    private JTable table;
    private JLabel footer;
    private RoundField totalField, cashField, changeField;
    private PillButton payBtn;
    private boolean settingCash;

    private final AbstractTableModel model = new AbstractTableModel() {
        @Override public int getRowCount() { return lines.size(); }
        @Override public int getColumnCount() { return 4; }
        @Override public String getColumnName(int c) { return new String[]{"Item", "Qty", "Price", "Subtotal"}[c]; }
        @Override public Object getValueAt(int r, int c) { return lines.get(r); }
    };

    /** The POS screen for the logged-in user. db is the shared database connection. */
    public PosPanel(PosDb db, Auth.User user, Runnable onLogout) {
        this(db, user, onLogout, true);
    }

    /** load = false is for previews: no menu is read, the caller supplies it with showMenu(). */
    PosPanel(PosDb db, Auth.User user, Runnable onLogout, boolean load) {
        super(new BorderLayout());
        this.db = db;
        this.user = user;
        this.onLogout = onLogout;
        setBackground(Theme.PAGE);
        add(buildHeader(), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(14, 14, 14, 14));
        body.add(buildMenuArea(), BorderLayout.CENTER);
        body.add(buildRightColumn(), BorderLayout.EAST);
        add(body, BorderLayout.CENTER);
        showCategory("Pastry");
        refreshOrderUi(-1);
        if (load && db != null) {
            refreshMenu();
            refreshTimer = new javax.swing.Timer(10000, e -> refreshMenu());   // pick up Inventory changes every 10 seconds
            refreshTimer.start();
        }
    }

    /** Stops the 10-second menu refresh (when the window closes or the user logs out). */
    void stop() {
        if (refreshTimer != null) refreshTimer.stop();
    }

    // =====================================================================
    //  Layout
    // =====================================================================

    private JComponent buildHeader() {
        Ui.GradientPanel header = new Ui.GradientPanel();
        header.setLayout(new BorderLayout());
        header.setPreferredSize(new Dimension(10, 66));
        header.setBorder(new EmptyBorder(0, 22, 0, 18));
        header.add(new LogoComponent(), BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 14));
        buttons.setOpaque(false);
        PillButton print = new PillButton("Print Sales", PillButton.Kind.SOFT);
        PillButton logout = new PillButton("Log out", PillButton.Kind.OUTLINE_DARK);
        for (PillButton b : new PillButton[]{print, logout}) b.setPreferredSize(new Dimension(b.getPreferredSize().width + 6, 32));
        print.addActionListener(e -> openSales(true));
        logout.addActionListener(e -> logout());
        buttons.add(print);
        buttons.add(userChip());
        buttons.add(logout);
        header.add(buttons, BorderLayout.EAST);
        return header;
    }

    /** Who is logged in: initial in a circle, name and role. */
    private JComponent userChip() {
        RoundPanel chip = new RoundPanel(Color.WHITE, Theme.LINE, 36);
        chip.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 3));
        JComponent avatar = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.smooth(g);
                g2.setColor(Theme.RASPBERRY);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.setColor(Color.WHITE);
                g2.setFont(Theme.body(Font.BOLD, 14f));
                String letter = user.fullName.substring(0, 1).toUpperCase();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(letter, (getWidth() - fm.stringWidth(letter)) / 2f, (getHeight() - fm.getHeight()) / 2f + fm.getAscent());
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(26, 26));
        JLabel who = new JLabel("<html><b>" + user.fullName.replace("<", "&lt;") + "</b><br><span style='font-size:9px;color:#8F7B86'>"
                + user.role.label + "</span></html>");
        who.setFont(Theme.body(Font.PLAIN, 12f));
        who.setForeground(Theme.INK);
        chip.add(avatar);
        chip.add(who);
        chip.add(Box.createHorizontalStrut(4));
        return chip;
    }

    private void logout() {
        if (!lines.isEmpty() && !reviewing) {
            int ok = JOptionPane.showConfirmDialog(this, "There is an unpaid order. Log out and discard it?", "Log out",
                    JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
        } else {
            int ok = JOptionPane.showConfirmDialog(this, "Log out of " + user.fullName + "?", "Log out", JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
        }
        stop();
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w != null) w.dispose();
        onLogout.run();
    }

    private void openSales(boolean report) {
        new OrdersDialog((Frame) SwingUtilities.getWindowAncestor(this), db, user, report).setVisible(true);
    }

    private JComponent buildMenuArea() {
        RoundPanel menu = new RoundPanel(Theme.CREAM, Theme.LINE, 26);
        menu.setLayout(new BorderLayout());

        JPanel tabRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tabRow.setOpaque(false);
        tabRow.setBorder(new EmptyBorder(14, 14, 10, 14));
        for (int i = 0; i < 3; i++) {
            final String cat = TAB_CATS[i];
            tabs[i] = new PillButton(TAB_LABELS[i], PillButton.Kind.OUTLINE);
            tabs[i].setFont(Theme.display(14f));
            tabs[i].setPreferredSize(new Dimension(tabs[i].getPreferredSize().width + 4, 30));
            tabs[i].addActionListener(e -> showCategory(cat));
            tabRow.add(tabs[i]);
        }
        menu.add(tabRow, BorderLayout.NORTH);
        cardMenu.setOpaque(false);
        menu.add(cardMenu, BorderLayout.CENTER);
        return menu;
    }

    private JComponent buildRightColumn() {
        JPanel right = new JPanel(new BorderLayout(0, 10));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(390, 10));
        right.add(buildOrderCard(), BorderLayout.CENTER);

        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(buildQtyRow());
        south.add(Box.createVerticalStrut(10));
        south.add(buildTotals());
        south.add(Box.createVerticalStrut(10));
        south.add(buildBottomRow());
        right.add(south, BorderLayout.SOUTH);
        return right;
    }

    private JComponent buildOrderCard() {
        RoundPanel card = new RoundPanel(Color.WHITE, Theme.LINE, 22);
        card.setLayout(new BorderLayout());

        table = new JTable(model);
        table.setOpaque(false);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setRowHeight(42);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new RowRenderer());
        table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setResizingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(10, 32));
        int[] widths = {170, 40, 70, 80};
        for (int i = 0; i < 4; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        JScrollPane sp = new JScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        Ui.slim(sp);
        sp.setColumnHeaderView(table.getTableHeader());
        sp.getColumnHeader().setOpaque(false);
        card.add(sp, BorderLayout.CENTER);

        footer = new JLabel("No items yet");
        footer.setFont(Theme.body(Font.PLAIN, 11f));
        footer.setForeground(Theme.MUTED);
        footer.setBorder(new EmptyBorder(8, 14, 10, 14));
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildQtyRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        row.setOpaque(false);
        PillButton minus = PillButton.iconButton(PillButton.ICON_MINUS, "One less of the selected item");
        PillButton plus = PillButton.iconButton(PillButton.ICON_PLUS, "One more of the selected item");
        PillButton remove = PillButton.iconButton(PillButton.ICON_CROSS, "Remove the selected item (or clear the order if none is selected)");
        minus.addActionListener(e -> changeQty(-1));
        plus.addActionListener(e -> changeQty(+1));
        remove.addActionListener(e -> removeSelected());
        row.add(minus);
        row.add(plus);
        row.add(remove);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private JComponent buildTotals() {
        RoundPanel box = new RoundPanel(Color.WHITE, Theme.LINE, 22);
        box.setLayout(new GridBagLayout());
        box.setBorder(new EmptyBorder(12, 16, 12, 16));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);

        Font moneyFont = Theme.safe(Theme.body(Font.BOLD, 15f));
        totalField = new RoundField(Theme.PINK_SOFT, Theme.LINE, Theme.RASPBERRY, Theme.safe(Theme.body(Font.BOLD, 18f)));
        totalField.setEditable(false);
        cashField = new RoundField(Color.WHITE, Theme.LINE, Theme.INK, moneyFont);
        cashField.setPlaceholder("\u20B10.00");
        changeField = new RoundField(Theme.GREEN_FILL, Theme.GREEN_LINE, Theme.GREEN_TEXT, moneyFont);
        changeField.setEditable(false);

        addRow(box, 0, "Total", Theme.display(21f), totalField, 38);
        addRow(box, 1, "Cash", Theme.body(Font.BOLD, 14f), cashField, 32);
        addRow(box, 2, "Change", Theme.body(Font.BOLD, 14f), changeField, 32);

        cashField.addActionListener(e -> payAction());
        cashField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { if (!settingCash) updateChange(); }
            @Override public void removeUpdate(DocumentEvent e) { if (!settingCash) updateChange(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        cashField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                double v = parseMoney(cashField.getText());
                if (!Double.isNaN(v)) setCash(v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v));
                SwingUtilities.invokeLater(cashField::selectAll);
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                double v = parseMoney(cashField.getText());
                if (!Double.isNaN(v)) setCash(Theme.peso(v));
            }
        });
        return box;
    }

    private void addRow(JPanel box, int row, String label, Font font, JComponent field, int height) {
        JLabel l = new JLabel(label);
        l.setFont(font);
        l.setForeground(Theme.INK);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(row == 0 ? 0 : 5, 0, 5, 10);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.weightx = 0.35;
        box.add(l, c);
        c.gridx = 1;
        c.weightx = 0.65;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(row == 0 ? 0 : 5, 0, 5, 0);
        field.setPreferredSize(new Dimension(180, height));
        box.add(field, c);
    }

    private JComponent buildBottomRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        PillButton inv = new PillButton("Sales Inventory", PillButton.Kind.OUTLINE);
        inv.setFont(Theme.body(Font.BOLD, 12f));
        inv.addActionListener(e -> openSales(false));
        payBtn = new PillButton("Pay", PillButton.Kind.FILLED).depth(4);
        payBtn.setFont(Theme.display(20f));
        payBtn.addActionListener(e -> payAction());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 0.36;
        c.insets = new Insets(0, 0, 4, 10);
        inv.setPreferredSize(new Dimension(100, 50));
        row.add(inv, c);
        c.gridx = 1;
        c.weightx = 0.64;
        c.insets = new Insets(0, 0, 0, 0);
        payBtn.setPreferredSize(new Dimension(180, 54));
        row.add(payBtn, c);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        return row;
    }

    // ---------- header logo ----------

    /** The header logo: /images/logo/logo.png if present, otherwise the words "Sugar Boo". */
    private static final class LogoComponent extends JComponent {
        private final Image img;
        private final Font script;

        LogoComponent() {
            Image i = null;
            if (Theme.USE_LOGO_IMAGE) {
                java.net.URL url = PosPanel.class.getResource("/images/logo/logo.png");
                if (url != null) i = new ImageIcon(url).getImage();
            }
            img = i;
            Font f = null;
            for (String fam : new String[]{"Pacifico", "Brush Script MT", "Segoe Script", "Lucida Handwriting"}) {
                Font t = new Font(fam, Font.BOLD, 34);
                if (t.getFamily().equalsIgnoreCase(fam)) { f = t; break; }
            }
            script = f != null ? f : new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 34);
        }

        private int imgW() {
            int ih = img.getHeight(null);
            return ih <= 0 ? 0 : Math.round(img.getWidth(null) * (46f / ih));
        }

        @Override
        public Dimension getPreferredSize() {
            if (img != null) return new Dimension(imgW(), 60);
            return new Dimension(getFontMetrics(script).stringWidth("Sugar Boo") + 12, 60);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.smooth(g);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            if (img != null) {
                g2.drawImage(img, 0, (getHeight() - 46) / 2, imgW(), 46, null);
            } else {
                g2.setFont(script);
                g2.setColor(Theme.RASPBERRY);
                g2.drawString("Sugar Boo", 0, (getHeight() + g2.getFontMetrics().getAscent()) / 2 - 4);
            }
            g2.dispose();
        }
    }

    // ---------- table renderers ----------

    private final class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, model.getColumnName(col), false, false, row, col);
            l.setOpaque(true);
            l.setBackground(Theme.PINK_FILL);
            l.setForeground(Theme.INK);
            l.setFont(Theme.body(Font.BOLD, 11f));
            l.setBorder(new EmptyBorder(0, 12, 0, 12));
            l.setHorizontalAlignment(col == 0 ? SwingConstants.LEFT : col == 1 ? SwingConstants.CENTER : SwingConstants.RIGHT);
            return l;
        }
    }

    /** The Item cell: name, and the size (if any) smaller and grey underneath. Drawn by hand. */
    private static final class ItemCell extends JComponent {
        String name = "", size;
        Color bg = Color.WHITE;

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.smooth(g);
            g2.setColor(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());
            Font nf = Theme.body(Font.PLAIN, 12f), sf = Theme.body(Font.PLAIN, 10f);
            FontMetrics nm = g2.getFontMetrics(nf), sm = g2.getFontMetrics(sf);
            int maxW = getWidth() - 12 - 6;
            String n = name;
            while (n.length() > 1 && nm.stringWidth(n) > maxW) n = n.substring(0, n.length() - 1);
            if (!n.equals(name)) n = n.trim() + "...";
            int blockH = nm.getHeight() + (size != null ? sm.getHeight() : 0);
            int y = (getHeight() - blockH) / 2 + nm.getAscent();
            g2.setFont(nf);
            g2.setColor(Theme.INK);
            g2.drawString(n, 12, y);
            if (size != null) {
                g2.setFont(sf);
                g2.setColor(Theme.MUTED);
                g2.drawString(size, 12, y + nm.getDescent() + sm.getAscent());
            }
            g2.dispose();
        }
    }

    private final class RowRenderer extends DefaultTableCellRenderer {
        private final ItemCell itemCell = new ItemCell();

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, "", false, false, row, col);
            Line ln = row < lines.size() ? lines.get(row) : null;
            Color bg = sel ? Theme.ROW_SELECTED : row % 2 == 1 ? Theme.ROW_ALT : Color.WHITE;
            l.setOpaque(true);
            l.setBackground(bg);
            l.setForeground(Theme.INK);
            l.setBorder(new EmptyBorder(0, 12, 0, 12));
            if (ln == null) return l;
            if (col == 0) {
                itemCell.bg = bg;
                itemCell.name = ln.name;
                itemCell.size = ln.size;
                return itemCell;
            }
            l.setFont(Theme.body(col == 3 ? Font.BOLD : Font.PLAIN, 12f));
            l.setHorizontalAlignment(col == 1 ? SwingConstants.CENTER : SwingConstants.RIGHT);
            switch (col) {
                case 1: l.setText(String.valueOf(ln.qty)); break;
                case 2: l.setText(String.format(java.util.Locale.US, "%,.2f", ln.price)); break;
                default: l.setText(String.format(java.util.Locale.US, "%,.2f", ln.subtotal())); break;
            }
            return l;
        }
    }

    // =====================================================================
    //  Order handling
    // =====================================================================

    private int unitsInOrder(String sku) {
        int n = 0;
        for (Line l : lines) if (l.sku.equals(sku)) n += l.qty;
        return n;
    }

    /** Adds one of the item (in the given size) to the order, if there is stock for it. */
    void addToOrder(Item it, Size size) {
        if (lockedMessage()) return;
        if (unitsInOrder(it.sku) + 1 > it.stock) {
            JOptionPane.showMessageDialog(this, "Only " + it.stock + " left in stock for " + it.name + ".");
            return;
        }
        String sizeLabel = it.sizes.size() > 1 ? size.label : null;
        int row = -1;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            if (l.sku.equals(it.sku) && java.util.Objects.equals(l.size, sizeLabel)) { row = i; break; }
        }
        if (row < 0) {
            lines.add(new Line(it.sku, it.name, sizeLabel, size.price));
            row = lines.size() - 1;
        } else {
            lines.get(row).qty++;
        }
        refreshOrderUi(row);
    }

    private void changeQty(int delta) {
        if (lockedMessage()) return;
        int row = table.getSelectedRow();
        if (row < 0 || row >= lines.size()) {
            JOptionPane.showMessageDialog(this, "Please select an item in the order first.");
            return;
        }
        Line l = lines.get(row);
        if (delta > 0) {
            Item it = itemsBySku.get(l.sku);
            if (it != null && unitsInOrder(l.sku) + 1 > it.stock) {
                JOptionPane.showMessageDialog(this, "Only " + it.stock + " left in stock for " + l.name + ".");
                return;
            }
            l.qty++;
        } else {
            l.qty--;
            if (l.qty <= 0) {
                lines.remove(row);
                refreshOrderUi(Math.min(row, lines.size() - 1));
                return;
            }
        }
        refreshOrderUi(row);
    }

    private void removeSelected() {
        if (lockedMessage()) return;
        int row = table.getSelectedRow();
        if (row >= 0 && row < lines.size()) {
            lines.remove(row);
            refreshOrderUi(Math.min(row, lines.size() - 1));
        } else if (!lines.isEmpty()) {
            int ok = JOptionPane.showConfirmDialog(this, "Clear the whole order?", "Clear order", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) clearOrder();
        }
    }

    /** While a paid order is on screen for review it cannot be changed. Returns true (after a hint) if locked. */
    private boolean lockedMessage() {
        if (!reviewing) return false;
        JOptionPane.showMessageDialog(this, "This order is already paid.\nPress \"Next Customer\" to start a new order.");
        return true;
    }

    private void clearOrder() {
        reviewing = false;
        lines.clear();
        setCash("");
        refreshOrderUi(-1);
    }

    private double total() {
        double t = 0;
        for (Line l : lines) t += l.subtotal();
        return t;
    }

    private void refreshOrderUi(int selectRow) {
        model.fireTableDataChanged();
        if (selectRow >= 0 && selectRow < lines.size()) table.setRowSelectionInterval(selectRow, selectRow);
        double t = total();
        int n = 0;
        for (Line l : lines) n += l.qty;
        totalField.setText(Theme.peso(t));
        footer.setText(reviewing ? "PAID  -  " + lastOrderNo
                : n == 0 ? "No items yet" : n + (n == 1 ? " item in order" : " items in order"));
        footer.setForeground(reviewing ? Theme.GREEN_TEXT : Theme.MUTED);
        cashField.setEditable(!reviewing);
        payBtn.setEnabled(!lines.isEmpty());
        payBtn.setText(reviewing ? "Next Customer" : lines.isEmpty() ? "Pay" : "Pay " + Theme.peso(t));
        updateChange();
    }

    private void setCash(String text) {
        settingCash = true;
        cashField.setText(text);
        settingCash = false;
        updateChange();
    }

    private void updateChange() {
        double cash = parseMoney(cashField.getText());
        double t = total();
        changeField.setText(Theme.peso(!Double.isNaN(cash) && cash >= t ? cash - t : 0));
    }

    /** "₱1,250.50" or "1250.5" -> number; NaN if empty or not a number. */
    private static double parseMoney(String s) {
        String clean = s == null ? "" : s.replaceAll("[^0-9.]", "");
        if (clean.isEmpty() || clean.equals(".")) return Double.NaN;
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private void showCategory(String cat) {
        currentCategory = cat;
        ((CardLayout) cardMenu.getLayout()).show(cardMenu, cat);
        for (int i = 0; i < 3; i++) tabs[i].setOn(TAB_CATS[i].equals(cat));
    }


    // =====================================================================
    //  Payment
    // =====================================================================

    private void payAction() {
        if (reviewing) clearOrder();            // the button says "Next Customer" while a paid order is shown
        else processPayment();
    }

    private void processPayment() {
        if (lines.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No items in the order! Please add items first.");
            return;
        }
        double total = total();
        double cash = parseMoney(cashField.getText());
        if (Double.isNaN(cash) || cash <= 0) {
            JOptionPane.showMessageDialog(this, "Please input cash.");
            cashField.requestFocusInWindow();
            return;
        }
        if (cash < total) {
            JOptionPane.showMessageDialog(this, "Insufficient cash!");
            cashField.requestFocusInWindow();
            return;
        }
        double change = cash - total;
        String orderNo = saveSale(total, cash, change);
        if (orderNo == null) return;                     // not saved: the message was already shown

        changeField.setText(Theme.peso(change));
        List<Object[]> items = new ArrayList<>();
        for (Line l : lines) items.add(new Object[]{l.fullName(), l.qty, l.price, l.subtotal()});
        JTextArea receipt = new JTextArea(OrdersPanel.receiptText(orderNo, new java.util.Date(), user.fullName,
                items, total, cash, change));
        receipt.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        receipt.setEditable(false);
        receipt.setOpaque(false);
        JScrollPane sp = new JScrollPane(receipt);
        sp.setPreferredSize(new Dimension(380, Math.min(420, 150 + 36 * items.size())));
        Object[] choices = {"Next Customer", "Review Order"};
        int pick = JOptionPane.showOptionDialog(this, sp, "Payment successful", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]);
        if (pick == 1) {                                 // Review Order: keep it on screen, locked
            reviewing = true;
            lastOrderNo = orderNo;
            refreshOrderUi(-1);
        } else {
            clearOrder();                                // Next Customer (or the window was closed)
        }
    }

    /** Saves the paid order (with the cashier) in MongoDB and takes the items out of stock. Returns the order number, or null if the sale must not go through. */
    private String saveSale(double total, double cash, double change) {
        if (db == null) return "PREVIEW-ORDER";           // previews only: nothing to save to
        List<PosDb.SaleLine> out = new ArrayList<>();
        for (Line l : lines) {
            if (itemsBySku.get(l.sku) == null) {
                JOptionPane.showMessageDialog(this,
                        "\"" + l.name + "\" is no longer on the menu. Please remove it from the order.");
                return null;
            }
            out.add(new PosDb.SaleLine(l.sku, l.fullName(), l.price, l.qty));
        }
        try {
            String orderNo = db.recordSale(out, total, cash, change, user.username, user.fullName);
            refreshMenu();                               // show the new stock levels right away
            return orderNo;
        } catch (PosDb.OutOfStockException ex) {
            JOptionPane.showMessageDialog(this, "Not enough stock for " + ex.getItemName()
                    + ".\nLower its quantity or remove it, then try again.");
            refreshMenu();
            return null;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save the sale:\n" + ex.getMessage());
            return null;
        }
    }

    // =====================================================================
    //  Menu from MongoDB
    // =====================================================================

    private void refreshMenu() {
        if (db == null) return;
        new SwingWorker<List<Item>, Void>() {
            @Override protected List<Item> doInBackground() {
                List<Item> list = new ArrayList<>();
                for (Document d : db.findProducts()) list.add(toItem(d));
                return list;
            }
            @Override protected void done() {
                try {
                    showMenu(get());
                } catch (Exception ex) {
                    if (!offlineNoticeShown) {
                        offlineNoticeShown = true;
                        JOptionPane.showMessageDialog(PosPanel.this, "Could not load products from MongoDB:\n"
                                + ex.getMessage() + "\n\nUsing the built-in menu for now.");
                        showOfflineMenu();
                    }
                }
            }
        }.execute();
    }

    /** A product document -> menu item. A "sizes" list ([{label, price}, ...]) is used if present, else the single price. */
    private Item toItem(Document d) {
        Item m = new Item();
        m.sku = d.getString("sku");
        m.name = d.getString("name");
        m.category = d.getString("category");
        Number qty = d.get("quantity", Number.class);
        m.stock = qty == null ? 0 : qty.intValue();
        Number price = d.get("price", Number.class);
        Object s = d.get("sizes");
        if (s instanceof List) {
            for (Object o : (List<?>) s) {
                if (o instanceof Map) {
                    Object label = ((Map<?, ?>) o).get("label"), p = ((Map<?, ?>) o).get("price");
                    if (label != null && p instanceof Number) m.sizes.add(new Size(label.toString(), ((Number) p).doubleValue()));
                }
            }
        }
        if (m.sizes.isEmpty()) m.sizes.add(new Size(null, price == null ? 0 : price.doubleValue()));
        byte[] bytes = null;
        Object p = d.get("photo");
        if (p instanceof org.bson.types.Binary) bytes = ((org.bson.types.Binary) p).getData();
        m.photoHash = java.util.Arrays.hashCode(bytes);
        m.photo = loadPhoto(m.sku, bytes);
        return m;
    }

    /** Photo from the database, else /images/photos/<sku>.png, else null. Old 131x150 card art is cut down to its photo. */
    private BufferedImage loadPhoto(String sku, byte[] bytes) {
        try {
            BufferedImage img = null;
            if (bytes != null && bytes.length > 0) {
                img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            } else {
                java.net.URL url = getClass().getResource("/images/photos/" + sku + ".png");
                if (url != null) img = javax.imageio.ImageIO.read(url);
            }
            if (img != null && img.getWidth() == CardArt.W && img.getHeight() == CardArt.H) {
                img = img.getSubimage(24, 35, 83, 80);       // the photo area inside the old card art
            }
            return img;
        } catch (java.io.IOException ex) {
            return null;
        }
    }

    /** Built-in menu, only used when MongoDB can't be reached. */
    private void showOfflineMenu() {
        Object[][] backup = {
            {"FT-001", "Fruit Tart", "Pastry", 120.00}, {"SC-002", "Strawberry Crepe", "Pastry", 150.00},
            {"BP-003", "Banana Parfait", "Dessert", 130.00}, {"CN-004", "Canele", "Pastry", 65.00},
            {"EC-005", "Eclair", "Pastry", 90.00}, {"MD-006", "Madeleine", "Pastry", 50.00},
            {"SM-007", "Strawberry Milk", "Drink", 130.00}, {"CC-008", "Cotton Candy Cappuccino", "Drink", 125.00},
            {"PV-009", "Pink Velvet Hot Choco", "Drink", 120.00},
        };
        List<Item> list = new ArrayList<>();
        for (Object[] r : backup) {
            Item m = new Item();
            m.sku = (String) r[0];
            m.name = (String) r[1];
            m.category = (String) r[2];
            m.sizes.add(new Size(null, (double) r[3]));
            m.stock = 999;
            m.photo = loadPhoto(m.sku, null);
            list.add(m);
        }
        showMenu(list);
    }

    /** Rebuilds the three tab pages, but only if something actually changed. */
    void showMenu(List<Item> items) {
        itemsBySku.clear();
        StringBuilder sig = new StringBuilder();
        for (Item m : items) {
            itemsBySku.put(m.sku, m);
            sig.append(m.sku).append('|').append(m.name).append('|').append(m.category).append('|')
               .append(m.stock).append('|').append(m.photoHash);
            for (Size s : m.sizes) sig.append('|').append(s.label).append('=').append(s.price);
            sig.append(';');
        }
        if (sig.toString().equals(menuSignature)) return;
        menuSignature = sig.toString();
        cardMenu.removeAll();
        for (String cat : TAB_CATS) cardMenu.add(buildPage(cat, items), cat);
        showCategory(currentCategory);
        cardMenu.revalidate();
        cardMenu.repaint();
    }

    private JComponent buildPage(String cat, List<Item> items) {
        ProductCard.Listener listener = new ProductCard.Listener() {
            @Override public void add(Item item, Size size) { addToOrder(item, size); }
            @Override public void sizeChosen(String sku, int index) { chosenSize.put(sku, index); }
        };
        JPanel grid = new JPanel(new GridLayout(0, 3, 12, 12));
        grid.setOpaque(false);
        for (Item m : items) {
            if (m.category != null && m.category.trim().equalsIgnoreCase(cat)) {
                grid.add(new ProductCard(m, chosenSize.getOrDefault(m.sku, 0), listener));
            }
        }
        if (grid.getComponentCount() == 0) {
            JLabel none = new JLabel("Nothing here yet", SwingConstants.CENTER);
            none.setFont(Theme.body(Font.PLAIN, 13f));
            none.setForeground(Theme.MUTED);
            JPanel p = new JPanel(new BorderLayout());
            p.setOpaque(false);
            p.add(none, BorderLayout.CENTER);
            return p;
        }
        WidthTrackingPanel holder = new WidthTrackingPanel();
        holder.setBorder(new EmptyBorder(2, 14, 14, 14));
        holder.add(grid, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        Ui.slim(sp);
        return sp;
    }

    /** A panel that is always as wide as its scroll pane, so the cards stretch to fit and only scroll up/down. */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {
        WidthTrackingPanel() {
            super(new BorderLayout());
            setOpaque(false);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(24, r.height - 24); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
