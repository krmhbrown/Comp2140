//Author: Karimah Brown
//Date of Creation: 23/11/2026
//Date of last modification: 23/11/2026

/*RestockOrderManager class that saves restock orders in the database, lists
them from newest to oldest, and marks them as received. When an order is
received, the quantities are added to each product's stock. */

package supplier;

import java.util.ArrayList;

public class RestockOrderManager {
    private ArrayList<RestockOrder> orders;

    //Constructor to create instance of a restock order manager.
    public RestockOrderManager() {
        this.orders = new ArrayList<>();
    }

    //Places and saves a new restock order. Returns false and saves nothing if it is not valid.
    public boolean createRestockOrder(RestockOrder order) {
        if (!order.placeOrder()) {
            return false;
        }
        orders.add(order);
        return true;
    }

    //All restock orders from newest to oldest. Orders are added in time order, so the list is read backwards.
    public ArrayList<RestockOrder> getHistory() {
        ArrayList<RestockOrder> history = new ArrayList<>();
        for (int i = orders.size() - 1; i >= 0; i--) {
            history.add(orders.get(i));
        }
        return history;
    }

    //One supplier's restock orders from newest to oldest.
    public ArrayList<RestockOrder> getHistory(Supplier supplier) {
        ArrayList<RestockOrder> history = new ArrayList<>();
        for (int i = orders.size() - 1; i >= 0; i--) {
            if (orders.get(i).getSupplier() == supplier) {
                history.add(orders.get(i));
            }
        }
        return history;
    }

    //Finds a restock order by ID, e.g. "RST0001". Returns null if there is no order with that ID.
    public RestockOrder getRestockOrder(String orderID) {
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).getOrderID().equals(orderID)) {
                return orders.get(i);
            }
        }
        return null;
    }

    //Marks a restock order as received, which adds the quantities to stock.
    public boolean markAsReceived(String orderID) {
        RestockOrder order = getRestockOrder(orderID);
        if (order == null) {
            return false;
        }
        return order.markAsReceived();
    }
}