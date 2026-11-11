package com.mycompany.sugarbooposv2;

import java.awt.*;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import org.bson.Document;

/**
 * The cashier login screen. States: ready, checking, wrong password, disabled account,
 * role without POS access, and a 30-second pause after 5 wrong attempts.
 */
public class LoginPanel extends JPanel {

    private static final int MAX_ATTEMPTS = 5, LOCK_SECONDS = 30;

    private final PosDb db;
    private final Consumer<Auth.User> onSuccess;
    private final Ui.RoundField username = new Ui.RoundField(Color.WHITE, Theme.LINE, Theme.INK, Theme.body(Font.PLAIN, 14f));
    private final Ui.RoundPassword password = new Ui.RoundPassword(Theme.body(Font.PLAIN, 14f));
    private final JLabel message = new JLabel(" ", SwingConstants.CENTER);
    private final Ui.PillButton signIn = new Ui.PillButton("Sign in", Ui.PillButton.Kind.FILLED).depth(4);
    private int failures, lockLeft;
    private javax.swing.Timer lockTimer;

    public LoginPanel(PosDb db, Consumer<Auth.User> onSuccess) {
        this.db = db;
        this.onSuccess = onSuccess;
        setLayout(new GridBagLayout());
        setBackground(Theme.PAGE);
        setPreferredSize(new Dimension(520, 600));

        Ui.RoundPanel card = new Ui.RoundPanel(Color.WHITE, Theme.LINE, 28);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(30, 36, 28, 36));
        card.setPreferredSize(new Dimension(400, 470));

        card.add(centered("Sugar Boo", new Font("Brush Script MT", Font.BOLD | Font.ITALIC, 40), Theme.RASPBERRY));
        card.add(Box.createVerticalStrut(2));
        card.add(centered("Cashiering / Point of Sale", Theme.body(Font.BOLD, 13f), Theme.INK));
        card.add(Box.createVerticalStrut(4));
        card.add(centered("Sign in to start your shift", Theme.body(Font.PLAIN, 12f), Theme.MUTED));
        card.add(Box.createVerticalStrut(22));

        card.add(label("Username"));
        username.setPreferredSize(new Dimension(100, 40));
        username.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        username.setHorizontalAlignment(SwingConstants.LEFT);
        username.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(username);
        card.add(Box.createVerticalStrut(12));
        card.add(label("Password"));
        password.setPreferredSize(new Dimension(100, 40));
        password.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        password.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(password);
        card.add(Box.createVerticalStrut(10));

        message.setFont(Theme.body(Font.BOLD, 12f));
        message.setForeground(Color.RED.darker());
        message.setAlignmentX(Component.LEFT_ALIGNMENT);
        message.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        card.add(message);
        card.add(Box.createVerticalStrut(8));

        signIn.setFont(Theme.display(18f));
        signIn.setPreferredSize(new Dimension(100, 50));
        signIn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        signIn.setAlignmentX(Component.LEFT_ALIGNMENT);
        signIn.addActionListener(e -> attempt());
        card.add(signIn);
        card.add(Box.createVerticalGlue());
        card.add(centered("Forgot your password? Ask an Admin to reset it.", Theme.body(Font.PLAIN, 11f), Theme.MUTED));
        add(card);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (getRootPane() != null) getRootPane().setDefaultButton(signIn);       // Enter = Sign in
        SwingUtilities.invokeLater(username::requestFocusInWindow);
    }

    private static JLabel centered(String text, Font f, Color c) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(f);
        l.setForeground(c);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, l.getPreferredSize().height + 4));
        return l;
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.body(Font.BOLD, 12f));
        l.setForeground(Theme.INK);
        l.setBorder(new EmptyBorder(0, 4, 5, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private void attempt() {
        if (lockLeft > 0) return;
        final String name = username.getText().trim();
        final char[] pw = password.getPassword();
        if (name.isEmpty() || pw.length == 0) {
            show("Enter your username and password.", Color.RED.darker());
            return;
        }
        signIn.setEnabled(false);
        show("Checking...", Theme.MUTED);
        new SwingWorker<Auth.Outcome, Void>() {
            @Override protected Auth.Outcome doInBackground() {
                Document doc = db.findUser(name);
                Auth.Outcome o = Auth.login(doc, pw);
                if (o.result == Auth.Result.OK) db.touchLogin(o.user.username);
                return o;
            }

            @Override protected void done() {
                Arrays.fill(pw, '\0');
                signIn.setEnabled(true);
                try {
                    handle(get());
                } catch (Exception ex) {
                    show("Could not reach the database. Check your internet and try again.", Color.RED.darker());
                }
            }
        }.execute();
    }

    private void handle(Auth.Outcome o) {
        switch (o.result) {
            case OK:
                if (lockTimer != null) lockTimer.stop();
                Window w = SwingUtilities.getWindowAncestor(this);
                if (w != null) w.dispose();
                onSuccess.accept(o.user);
                return;
            case DISABLED:
                show("This account is disabled. Ask an Admin.", Color.RED.darker());
                password.setText("");
                return;
            case NO_ACCESS:
                show("The " + o.user.role.label + " role cannot use the POS.", new Color(200, 120, 0));
                password.setText("");
                return;
            default:
                failures++;
                password.setText("");
                if (failures >= MAX_ATTEMPTS) startLock();
                else show("Wrong username or password. (" + (MAX_ATTEMPTS - failures) + " tries left)", Color.RED.darker());
        }
    }

    private void startLock() {
        failures = 0;
        lockLeft = LOCK_SECONDS;
        signIn.setEnabled(false);
        show("Too many attempts. Try again in " + lockLeft + "s.", Color.RED.darker());
        lockTimer = new javax.swing.Timer(1000, e -> {
            lockLeft--;
            if (lockLeft <= 0) {
                lockTimer.stop();
                signIn.setEnabled(true);
                show(" ", Color.RED);
            } else {
                show("Too many attempts. Try again in " + lockLeft + "s.", Color.RED.darker());
            }
        });
        lockTimer.start();
    }

    private void show(String text, Color c) {
        message.setText("<html><div style='text-align:center'>" + text + "</div></html>");
        message.setForeground(c);
    }
}
