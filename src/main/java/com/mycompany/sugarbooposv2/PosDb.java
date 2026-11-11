/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author mathe
 */

package com.mycompany.sugarbooposv2;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** MongoDB access for the POS: reads the shared products, saves orders and takes sold items out of stock. */
public class PosDb implements AutoCloseable {

    /** One line of an order. */
    public static class SaleLine {
        public final String sku, name;
        public final double unitPrice;
        public final int quantity;

        public SaleLine(String sku, String name, double unitPrice, int quantity) {
            this.sku = sku;
            this.name = name;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
        }
    }

    /** Thrown when an item does not have enough stock for the sale. */
    public static class OutOfStockException extends Exception {
        private final String itemName;

        public OutOfStockException(String itemName) {
            super("Not enough stock for " + itemName);
            this.itemName = itemName;
        }

        public String getItemName() {
            return itemName;
        }
    }

    private final MongoClient client;
    private final MongoCollection<Document> products;
    private final MongoCollection<Document> movements;
    private final MongoCollection<Document> orders;
    private final MongoCollection<Document> users;

    public PosDb(String uri, String dbName) {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .applyToClusterSettings(b -> b.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .build();
        client = MongoClients.create(settings);
        MongoDatabase database = client.getDatabase(dbName);
        products = database.getCollection("products");
        movements = database.getCollection("stock_movements");
        orders = database.getCollection("orders");
        users = database.getCollection("users");
        orders.createIndex(Indexes.ascending("orderNo"), new IndexOptions().unique(true));
        orders.createIndex(Indexes.descending("paidAt"));
    }

    public List<Document> findProducts() {
        return products.find().sort(Sorts.ascending("name")).into(new ArrayList<>());
    }

    /**
     * Saves a paid order and takes its items out of stock. Each stock update only matches if enough
     * stock exists, so quantity can never go below zero. If any item is short (or saving fails),
     * the stock already taken is put back and nothing is saved.
     *
     * @param cashier username of the logged-in cashier (saved on the order and on each stock movement)
     * @return the new order number
     */
    public String recordSale(List<SaleLine> lines, double total, double cash, double change,
                             String cashier, String cashierName) throws OutOfStockException {
        Date now = new Date();
        String orderNo = "ORD-" + new SimpleDateFormat("yyyyMMdd-HHmmssSSS").format(now);
        List<SaleLine> taken = new ArrayList<>();
        try {
            List<Document> items = new ArrayList<>();
            for (SaleLine l : lines) {
                long modified = products.updateOne(
                        Filters.and(Filters.eq("sku", l.sku), Filters.gte("quantity", l.quantity)),
                        Updates.combine(Updates.inc("quantity", -l.quantity),
                                Updates.set("updatedAt", now))).getModifiedCount();
                if (modified != 1) {
                    throw new OutOfStockException(l.name);
                }
                taken.add(l);
                items.add(new Document("sku", l.sku)
                        .append("name", l.name)
                        .append("unitPrice", l.unitPrice)
                        .append("quantity", l.quantity)
                        .append("subtotal", l.unitPrice * l.quantity));
            }
            orders.insertOne(new Document("orderNo", orderNo)
                    .append("items", items)
                    .append("totalAmount", total)
                    .append("cashReceived", cash)
                    .append("change", change)
                    .append("status", "paid")
                    .append("cashier", cashier)
                    .append("cashierName", cashierName)
                    .append("paidAt", now));
        } catch (OutOfStockException | RuntimeException ex) {
            for (SaleLine l : taken) {          // put the stock back
                products.updateOne(Filters.eq("sku", l.sku), Updates.inc("quantity", l.quantity));
            }
            throw ex;
        }
        // The stock history is secondary: a failure here must not undo a valid sale.
        try {
            for (SaleLine l : lines) {
                movements.insertOne(new Document("sku", l.sku)
                        .append("type", "SALE")
                        .append("quantity", l.quantity)
                        .append("note", "Order " + orderNo)
                        .append("by", cashier)
                        .append("timestamp", now));
            }
        } catch (RuntimeException ex) {
            ex.printStackTrace();
        }
        return orderNo;
    }

    // ---- users (created and managed in the Inventory app; the POS only reads them) ----

    public Document findUser(String username) {
        return users.find(Filters.eq("username", username.trim().toLowerCase())).first();
    }

    public void touchLogin(String username) {
        users.updateOne(Filters.eq("username", username), Updates.set("lastLoginAt", new Date()));
    }

    // ---- saved orders ----

    /** Paid orders with from <= paidAt < to, newest first. cashier = null means everybody's orders. */
    public List<Document> findOrders(Date from, Date to, String cashier) {
        Bson filter = Filters.and(Filters.gte("paidAt", from), Filters.lt("paidAt", to));
        if (cashier != null) filter = Filters.and(filter, Filters.eq("cashier", cashier));
        return orders.find(filter).sort(Sorts.descending("paidAt")).into(new ArrayList<>());
    }

    @Override
    public void close() {
        client.close();
    }
}