package com.mycompany.sugarbooposv2;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import org.bson.Document;

/**
 * The Sales Inventory window: the paid orders of one day (read from the database, so they are still
 * there after the program is closed) and a sales report for the same day.
 * A Cashier sees only their own orders; an Admin sees everybody's, with a total per cashier.
 */
public class OrdersPanel extends JPanel {

    private final PosDb db;
    private final Auth.User user;
    private final List<Document> orders = new ArrayList<>();
    private LocalDate day = LocalDate.now();
    private boolean showingReport;

    private final JLabel dayLabel = new JLabel();
    private final JLabel summary = new JLabel(" ");
    private final JTextArea receipt = text(), report = text();
    private final CardLayout pages = new CardLayout();
    private final JPanel pageHolder = new JPanel(pages);
    private final Ui.PillButton ordersTab = new Ui.PillButton("Today's Orders", Ui.PillButton.Kind.OUTLINE);
    private final Ui.PillButton reportTab = new Ui.PillButton("Sales Report", Ui.PillButton.Kind.OUTLINE);
    private final Ui.PillButton nextDay = new Ui.PillButton(">", Ui.PillButton.Kind.OUTLINE);
    private JTable table;

    private final AbstractTableModel model = new AbstractTableModel() {
        final String[] cols = {"Time", "Order No", "Cashier", "Items", "Total"};
        @Override public int getRowCount() { return orders.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int r, int c) {
            Document d = orders.get(r);
            switch (c) {
                case 0: return time(d);
                case 1: return str(d, "orderNo");
                case 2: return cashierOf(d);
                case 3: return String.valueOf(units(d));
                default: return Theme.peso(num(d, "totalAmount"));
            }
        }
    };

    public OrdersPanel(PosDb db, Auth.User user, boolean startOnReport, Runnable close) {
        this.db = db;
        this.user = user;
        setLayout(new BorderLayout(0, 12));
        setBackground(Theme.PAGE);
        setBorder(new EmptyBorder(16, 18, 16, 18));
        setPreferredSize(new Dimension(1000, 640));

        add(buildHeader(), BorderLayout.NORTH);

        pageHolder.setOpaque(false);
        pageHolder.add(buildOrdersPage(), "orders");
        pageHolder.add(buildReportPage(), "report");
        add(pageHolder, BorderLayout.CENTER);

        add(buildFooter(close), BorderLayout.SOUTH);
        showTab(startOnReport);
        load();
    }

    private static JTextArea text() {
        JTextArea a = new JTextArea();
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        a.setEditable(false);
        a.setForeground(Theme.INK);
        a.setBorder(new EmptyBorder(12, 16, 12, 16));
        return a;
    }

    // ------------------------------------------------------------------ layout

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel(user.role.canSeeAllOrders() ? "Sales Inventory - all cashiers" : "Sales Inventory - my orders");
        title.setFont(Theme.display(24f));
        title.setForeground(Theme.RASPBERRY);
        header.add(title, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        Ui.PillButton prev = new Ui.PillButton("<", Ui.PillButton.Kind.OUTLINE);
        Ui.PillButton today = new Ui.PillButton("Today", Ui.PillButton.Kind.SOFT);
        prev.setPreferredSize(new Dimension(36, 32));
        nextDay.setPreferredSize(new Dimension(36, 32));
        today.setPreferredSize(new Dimension(today.getPreferredSize().width, 32));
        prev.addActionListener(e -> { day = day.minusDays(1); load(); });
        nextDay.addActionListener(e -> { day = day.plusDays(1); load(); });
        today.addActionListener(e -> { day = LocalDate.now(); load(); });
        dayLabel.setFont(Theme.body(Font.BOLD, 13f));
        dayLabel.setForeground(Theme.INK);
        right.add(prev);
        right.add(dayLabel);
        right.add(nextDay);
        right.add(today);
        right.add(Box.createHorizontalStrut(14));
        ordersTab.setFont(Theme.body(Font.BOLD, 12f));
        reportTab.setFont(Theme.body(Font.BOLD, 12f));
        ordersTab.setPreferredSize(new Dimension(ordersTab.getPreferredSize().width, 32));
        reportTab.setPreferredSize(new Dimension(reportTab.getPreferredSize().width, 32));
        ordersTab.addActionListener(e -> showTab(false));
        reportTab.addActionListener(e -> showTab(true));
        right.add(ordersTab);
        right.add(reportTab);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JComponent buildOrdersPage() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setOpaque(false);

        Ui.RoundPanel left = new Ui.RoundPanel(Color.WHITE, Theme.LINE, 22);
        left.setLayout(new BorderLayout());
        table = new JTable(model);
        table.setOpaque(false);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setRowHeight(34);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, false, false, r, c);
                l.setOpaque(true);
                l.setBackground(sel ? Theme.ROW_SELECTED : r % 2 == 1 ? Theme.ROW_ALT : Color.WHITE);
                l.setForeground(Theme.INK);
                l.setFont(Theme.safe(Theme.body(c == 4 ? Font.BOLD : Font.PLAIN, 12f)));
                l.setBorder(new EmptyBorder(0, 12, 0, 12));
                l.setHorizontalAlignment(c == 3 ? SwingConstants.CENTER : c == 4 ? SwingConstants.RIGHT : SwingConstants.LEFT);
                return l;
            }
        });
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, false, false, r, c);
                l.setOpaque(true);
                l.setBackground(Theme.PINK_FILL);
                l.setForeground(Theme.INK);
                l.setFont(Theme.body(Font.BOLD, 11f));
                l.setBorder(new EmptyBorder(0, 12, 0, 12));
                l.setHorizontalAlignment(c == 3 ? SwingConstants.CENTER : c == 4 ? SwingConstants.RIGHT : SwingConstants.LEFT);
                return l;
            }
        });
        table.getTableHeader().setPreferredSize(new Dimension(10, 32));
        table.getTableHeader().setReorderingAllowed(false);
        int[] w = {80, 150, 110, 62, 90};
        for (int i = 0; i < w.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showReceipt();
        });
        JScrollPane sp = new JScrollPane(table);
        Ui.slim(sp);
        sp.setColumnHeaderView(table.getTableHeader());
        sp.getColumnHeader().setOpaque(false);
        left.add(sp, BorderLayout.CENTER);

        Ui.RoundPanel right = new Ui.RoundPanel(Color.WHITE, Theme.LINE, 22);
        right.setLayout(new BorderLayout());
        receipt.setOpaque(false);
        right.add(receipt, BorderLayout.CENTER);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridx = 0;
        c.weightx = 0.58;
        c.insets = new Insets(0, 0, 0, 12);
        page.add(left, c);
        c.gridx = 1;
        c.weightx = 0.42;
        c.insets = new Insets(0, 0, 0, 0);
        page.add(right, c);
        return page;
    }

    private JComponent buildReportPage() {
        Ui.RoundPanel panel = new Ui.RoundPanel(Color.WHITE, Theme.LINE, 22);
        panel.setLayout(new BorderLayout());
        report.setOpaque(false);
        JScrollPane sp = new JScrollPane(report);
        Ui.slim(sp);
        panel.add(sp, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildFooter(Runnable close) {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        summary.setFont(Theme.safe(Theme.body(Font.BOLD, 13f)));
        summary.setForeground(Theme.RASPBERRY);
        footer.add(summary, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        Ui.PillButton print = new Ui.PillButton("Print", Ui.PillButton.Kind.SOFT);
        Ui.PillButton done = new Ui.PillButton("Close", Ui.PillButton.Kind.FILLED);
        print.setPreferredSize(new Dimension(print.getPreferredSize().width + 10, 36));
        done.setPreferredSize(new Dimension(done.getPreferredSize().width + 10, 36));
        print.addActionListener(e -> print());
        done.addActionListener(e -> close.run());
        buttons.add(print);
        buttons.add(done);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private void showTab(boolean reportTabSelected) {
        showingReport = reportTabSelected;
        pages.show(pageHolder, reportTabSelected ? "report" : "orders");
        ordersTab.setOn(!reportTabSelected);
        reportTab.setOn(reportTabSelected);
    }

    // ------------------------------------------------------------------ loading

    private void load() {
        dayLabel.setText((day.equals(LocalDate.now()) ? "Today, " : "") + day.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)));
        nextDay.setEnabled(day.isBefore(LocalDate.now()));
        if (db == null) {
            summary.setText("The sales records are in the database, which is not connected.");
            return;
        }
        summary.setText("Loading...");
        ZoneId zone = ZoneId.systemDefault();
        final Date from = Date.from(day.atStartOfDay(zone).toInstant());
        final Date to = Date.from(day.plusDays(1).atStartOfDay(zone).toInstant());
        final String who = user.role.canSeeAllOrders() ? null : user.username;
        new SwingWorker<List<Document>, Void>() {
            @Override protected List<Document> doInBackground() { return db.findOrders(from, to, who); }

            @Override protected void done() {
                try {
                    setOrders(get());
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    summary.setText("Could not load the orders.");
                    JOptionPane.showMessageDialog(OrdersPanel.this, "Could not load the orders:\n" + c.getMessage());
                }
            }
        }.execute();
    }

    /** Also used by the preview. */
    void setOrders(List<Document> list) {
        orders.clear();
        orders.addAll(list);
        model.fireTableDataChanged();
        double total = 0;
        for (Document d : orders) total += num(d, "totalAmount");
        summary.setText(orders.size() + (orders.size() == 1 ? " order" : " orders") + "   |   Total sales: " + Theme.peso(total));
        report.setText(reportText(orders, day, user));
        report.setCaretPosition(0);
        if (!orders.isEmpty()) table.setRowSelectionInterval(0, 0);
        showReceipt();
    }

    private void showReceipt() {
        int row = table == null ? -1 : table.getSelectedRow();
        if (row < 0 || row >= orders.size()) {
            receipt.setText(orders.isEmpty() ? "\n  No orders on this day." : "\n  Select an order to see its receipt.");
            return;
        }
        receipt.setText(receiptText(orders.get(row)));
        receipt.setCaretPosition(0);
    }

    private void print() {
        JTextArea area = showingReport ? report : receipt;
        if (!showingReport && table.getSelectedRow() < 0) {
            JOptionPane.showMessageDialog(this, "Select an order first.");
            return;
        }
        try {
            area.print();
        } catch (java.awt.print.PrinterException ex) {
            JOptionPane.showMessageDialog(this, "Could not print:\n" + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ reading order documents

    private static String str(Document d, String key) {
        Object v = d.get(key);
        return v == null ? "" : v.toString();
    }

    private static double num(Map<?, ?> d, String key) {
        Object v = d.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : 0;
    }

    private static String time(Document d) {
        Object t = d.get("paidAt");
        return t instanceof Date ? new SimpleDateFormat("hh:mm a", Locale.US).format((Date) t) : "";
    }

    private static String cashierOf(Document d) {
        String n = str(d, "cashierName");
        return n.isEmpty() ? (str(d, "cashier").isEmpty() ? "-" : str(d, "cashier")) : n;
    }

    private static List<Map<?, ?>> itemsOf(Document d) {
        List<Map<?, ?>> out = new ArrayList<>();
        Object items = d.get("items");
        if (items instanceof List) for (Object o : (List<?>) items) if (o instanceof Map) out.add((Map<?, ?>) o);
        return out;
    }

    private static int units(Document d) {
        int n = 0;
        for (Map<?, ?> i : itemsOf(d)) n += (int) num(i, "quantity");
        return n;
    }

    // ------------------------------------------------------------------ texts

    static final String LINE = "----------------------------------------\n";

    private static String right(String left, String right, int width) {
        int gap = Math.max(1, width - left.length() - right.length());
        return left + " ".repeat(gap) + right + "\n";
    }

    /** The receipt of a saved order. */
    static String receiptText(Document d) {
        Object when = d.get("paidAt");
        List<Object[]> items = new ArrayList<>();
        for (Map<?, ?> i : itemsOf(d)) {
            items.add(new Object[]{String.valueOf(i.get("name")), (int) num(i, "quantity"), num(i, "unitPrice"), num(i, "subtotal")});
        }
        return receiptText(str(d, "orderNo"), when instanceof Date ? (Date) when : null, cashierOf(d), items,
                num(d, "totalAmount"), num(d, "cashReceived"), num(d, "change"));
    }

    /** items: {name, quantity, unitPrice, subtotal}. Used for saved orders and for the receipt shown right after paying. */
    static String receiptText(String orderNo, Date when, String cashier, List<Object[]> items,
                              double total, double cash, double change) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n          SUGAR BOO\n    Cashiering / Point of Sale\n").append(LINE);
        sb.append("Order:   ").append(orderNo).append("\n");
        sb.append("Date:    ").append(when == null ? "" : new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US).format(when)).append("\n");
        sb.append("Cashier: ").append(cashier).append("\n").append(LINE);
        for (Object[] i : items) {
            sb.append(i[0]).append("\n");
            sb.append(right(String.format(Locale.US, "   %d x %,.2f", (Integer) i[1], (Double) i[2]),
                    String.format(Locale.US, "%,.2f", (Double) i[3]), 40));
        }
        sb.append(LINE);
        sb.append(right("TOTAL", Theme.peso(total), 40));
        sb.append(right("Cash", Theme.peso(cash), 40));
        sb.append(right("Change", Theme.peso(change), 40));
        sb.append(LINE).append("      Thank you! Come again.\n");
        return sb.toString();
    }

    /** Sales report of the given orders: by item, and (for an Admin) by cashier. */
    static String reportText(List<Document> orders, LocalDate day, Auth.User viewer) {
        TreeMap<String, double[]> byItem = new TreeMap<>();
        TreeMap<String, double[]> byCashier = new TreeMap<>();
        double total = 0;
        for (Document d : orders) {
            total += num(d, "totalAmount");
            double[] c = byCashier.computeIfAbsent(cashierOf(d), k -> new double[2]);
            c[0]++;
            c[1] += num(d, "totalAmount");
            for (Map<?, ?> i : itemsOf(d)) {
                double[] s = byItem.computeIfAbsent(String.valueOf(i.get("name")), k -> new double[2]);
                s[0] += num(i, "quantity");
                s[1] += num(i, "subtotal");
            }
        }
        String bar = "=".repeat(62) + "\n";
        StringBuilder sb = new StringBuilder();
        sb.append("  SALES REPORT\n");
        sb.append("  Date:  ").append(day.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy", Locale.US))).append("\n");
        sb.append("  For:   ").append(viewer.role.canSeeAllOrders() ? "all cashiers" : viewer.fullName).append("\n");
        sb.append("  Orders: ").append(orders.size()).append("     Total sales: ").append(Theme.peso(total)).append("\n\n");
        if (orders.isEmpty()) return sb.append("  No sales on this day.\n").toString();
        sb.append(bar).append(String.format("  %-34s %6s %16s\n", "Item", "Units", "Sales")).append(bar);
        for (Map.Entry<String, double[]> e : byItem.entrySet()) {
            sb.append(String.format("  %-34s %6d %16s\n", e.getKey(), (long) e.getValue()[0], Theme.peso(e.getValue()[1])));
        }
        sb.append(bar).append(String.format("  %-34s %6s %16s\n", "TOTAL", "", Theme.peso(total)));
        if (viewer.role.canSeeAllOrders()) {
            sb.append("\n").append(bar).append(String.format("  %-34s %6s %16s\n", "Cashier", "Orders", "Sales")).append(bar);
            for (Map.Entry<String, double[]> e : byCashier.entrySet()) {
                sb.append(String.format("  %-34s %6d %16s\n", e.getKey(), (long) e.getValue()[0], Theme.peso(e.getValue()[1])));
            }
        }
        return sb.toString();
    }
}
