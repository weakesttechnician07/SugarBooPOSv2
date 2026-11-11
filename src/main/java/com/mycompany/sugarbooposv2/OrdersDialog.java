package com.mycompany.sugarbooposv2;

import java.awt.Frame;
import javax.swing.JDialog;

/** The Sales Inventory window (the content is in OrdersPanel). */
public class OrdersDialog extends JDialog {

    public OrdersDialog(Frame owner, PosDb db, Auth.User user, boolean startOnReport) {
        super(owner, "Sales Inventory", true);
        setContentPane(new OrdersPanel(db, user, startOnReport, this::dispose));
        pack();
        setLocationRelativeTo(owner);
    }
}
