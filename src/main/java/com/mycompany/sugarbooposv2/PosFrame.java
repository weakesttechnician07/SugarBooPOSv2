package com.mycompany.sugarbooposv2;

import java.awt.Dimension;
import java.awt.EventQueue;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

/**
 * The Sugar Boo POS: run this file (it has main).
 * Flow: connect to Atlas -> cashier login -> POS window. Logging out comes back to the login window.
 * Connection string: env var MONGODB_URI (or the first program argument), otherwise the program asks.
 */
public class PosFrame extends JFrame {

    private static PosDb db;
    private final PosPanel panel;

    public PosFrame(PosDb db, Auth.User user, Runnable onLogout) {
        super("Sugar Boo POS - " + user.fullName + " (" + user.role.label + ")");
        panel = new PosPanel(db, user, onLogout);
        setContentPane(panel);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(920, 640));
        setSize(1080, 740);
        setLocationRelativeTo(null);
    }

    @Override
    public void dispose() {
        panel.stop();
        super.dispose();
    }

    public static void main(String[] args) {
        String uri = args.length > 0 ? args[0] : System.getenv("MONGODB_URI");
        if (uri == null || uri.trim().isEmpty()) {
            uri = JOptionPane.showInputDialog(null, "Paste your MongoDB Atlas connection string:",
                    "Connect to Atlas", JOptionPane.QUESTION_MESSAGE);
        }
        if (uri == null || uri.trim().isEmpty()) System.exit(0);
        final String connection = uri.trim();
        EventQueue.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // the default look is fine too
            }
            Theme.applyDialogDefaults();
            try {
                db = new PosDb(connection, PosPanel.DB_NAME);
                showLogin();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "Could not connect to MongoDB Atlas:\n" + ex.getMessage()
                        + "\n\nCheck your connection string and that your IP is allowed in Atlas Network Access.",
                        "Connection Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    /** The login window; after a successful login the POS opens, and logging out comes back here. */
    static void showLogin() {
        new LoginFrame(db, user -> new PosFrame(db, user, PosFrame::showLogin).setVisible(true)).setVisible(true);
    }
}
