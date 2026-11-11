package com.mycompany.sugarbooposv2;

import java.util.function.Consumer;
import javax.swing.JFrame;

/** The cashier login window (the content is in LoginPanel). */
public class LoginFrame extends JFrame {

    public LoginFrame(PosDb db, Consumer<Auth.User> onSuccess) {
        super("Sugar Boo POS - Sign in");
        setContentPane(new LoginPanel(db, onSuccess));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        pack();
        setLocationRelativeTo(null);
    }
}
