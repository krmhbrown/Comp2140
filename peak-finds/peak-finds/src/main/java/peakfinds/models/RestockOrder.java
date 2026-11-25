//Author: Karimah Brown
//Date of Creation: 20/11/2026
//Date of last modification: 25/11/2026

/* RestockOrder class that represents a restock order in the system.
It contains information about the order details, including the supplier,
products, quantities and date. The class provides methods to save restock
orders in the database, list them from newest to oldest, cancel them and 
mark them as received, which adds the quantities to each product's stock.*/

package peakfinds.models;

import java.util.ArrayList;
import java.util.Map;
import peakfinds.database.Database;
import peakfinds.database.Rows;

public class RestockOrder {
    private int id;
    private int supplierId;
    private String supplierName;
    private ArrayList<Integer> productsOrdered;
    private ArrayList<Integer> quantitiesOrdered;
    private String dateOrdered;
    private boolean isReceived;

    //Constructor to create instance of a restock order.
    public RestockOrder(int supplierId) {
        this.supplierId = supplierId;
        this.productsOrdered = new ArrayList<>();
        this.quantitiesOrdered = new ArrayList<>();
        this.isReceived = false; // Default to not received
    }

    //Adds one product and the quantity ordered.
    public void addProduct(int productId, int quantity) {
        productsOrdered.add(productId);
        quantitiesOrdered.add(quantity);
    }

    //Order is only valid if it has at least one product and every quantity is at least 1.
    public boolean validateOrder() {
        if (productsOrdered.isEmpty()) {
            return false;
        }
        for (int i = 0; i < quantitiesOrdered.size(); i++) {
            if (quantitiesOrdered.get(i) < 1) {
                return false;
            }
        }
        return true;
    }

    //Saves a new restock order. Returns false and saves nothing if it is not valid.
    public boolean createRestockOrder(Database db) {
        if (!validateOrder()) {
            return false;
        }
        Supplier supplier = Supplier.findById(db, supplierId);
        if (supplier == null || !supplier.isActive()) {
            return false;
        }
        //The database fills in today's date and gives the order the next ID
        long newId = db.insert("INSERT INTO restock_orders (supplier_id, status) VALUES (?, 'Ordered')", supplierId);
        id = (int) newId;

        for (int i = 0; i < productsOrdered.size(); i++) {
            db.insert("INSERT INTO restock_order_items (restock_id, product_id, quantity) VALUES (?, ?, ?)",
                    id, productsOrdered.get(i), quantitiesOrdered.get(i));
        }
        return true;
    }

    //Marks the order as received and adds the quantities to each product's stock.
    //Returns false if it was already received, so stock is only added once.
    public boolean markAsReceived(Database db) {
        if (isReceived) {
            return false;
        }
        //Only changes the order if it is still "Ordered"
        int changed = db.update("UPDATE restock_orders SET status = 'Received', received_date = datetime('now', 'localtime') "
                + "WHERE restock_id = ? AND status = 'Ordered'", id);
        if (changed == 0) {
            return false;
        }
        for (int i = 0; i < productsOrdered.size(); i++) {
            db.update("UPDATE products SET stock_quantity = stock_quantity + ? WHERE product_id = ?",
                    quantitiesOrdered.get(i), productsOrdered.get(i));
        }
        isReceived = true;
        return true;
    }

    //Cancels the order by removing it from the database. Returns false if it was already received.
    public boolean cancelOrder(Database db) {
        if (isReceived) {
            return false;
        }
        db.update("DELETE FROM restock_order_items WHERE restock_id IN " + "(SELECT restock_id FROM restock_orders WHERE restock_id = ? AND status = 'Ordered')", id);
        int deleted = db.update("DELETE FROM restock_orders WHERE restock_id = ? AND status = 'Ordered'", id);
        if (deleted == 0) {
            return false;
        }
        return true;
    }

    //Finds one restock order by ID. Returns null if there is no order with that ID.
    public static RestockOrder findById(Database db, int restockId) {
        for (Map<String, Object> row : db.query("SELECT r.*, s.name AS supplier_name FROM restock_orders r "
                + "JOIN suppliers s ON s.supplier_id = r.supplier_id WHERE r.restock_id = ?", restockId)) {
            return makeOrder(db, row);
        }
        return null;
    }

    //All restock orders from newest to oldest.
    public static ArrayList<RestockOrder> findAll(Database db) {
        ArrayList<RestockOrder> orders = new ArrayList<>();
        for (Map<String, Object> row : db.query("SELECT r.*, s.name AS supplier_name FROM restock_orders r "
                + "JOIN suppliers s ON s.supplier_id = r.supplier_id ORDER BY r.order_date DESC, r.restock_id DESC")) {
            orders.add(makeOrder(db, row));
        }
        return orders;
    }

    //Turns one row from the restock_orders table (and its products) into a RestockOrder.
    private static RestockOrder makeOrder(Database db, Map<String, Object> row) {
        RestockOrder order = new RestockOrder(Rows.intOf(row, "supplier_id"));
        order.id = Rows.intOf(row, "restock_id");
        order.supplierName = Rows.str(row, "supplier_name");
        order.dateOrdered = Rows.str(row, "order_date");
        order.isReceived = Rows.str(row, "status").equals("Received");

        for (Map<String, Object> item : db.query("SELECT product_id, quantity FROM restock_order_items "
                + "WHERE restock_id = ? ORDER BY product_id", order.id)) {
            order.addProduct(Rows.intOf(item, "product_id"), Rows.intOf(item, "quantity"));
        }
        return order;
    }

    public String getStatus() {
        if (isReceived) {
            return "Received";
        }
        return "Ordered";
    }

    //Getters
    public int getId() {
        return id;
    }

    public String getOrderID() {
        return "RST" + String.format("%04d", id);
    }

    public int getSupplierId() {
        return supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public ArrayList<Integer> getProductsOrdered() {
        return productsOrdered;
    }

    public ArrayList<Integer> getQuantitiesOrdered() {
        return quantitiesOrdered;
    }

    public String getDateOrdered() {
        return dateOrdered;
    }

    public boolean isReceived() {
        return isReceived;
    }
}