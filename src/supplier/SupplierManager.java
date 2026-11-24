//Author: Karimah Brown
//Date of Creation: 22/11/2026
//Date of last modification: 22/11/2026

package supplier;

import java.util.ArrayList;
import product.Product;

public class SupplierManager {
    private ArrayList<Supplier> suppliers;

    //Constructor to create instance of a supplier manager.
    public SupplierManager() {
        this.suppliers = new ArrayList<>();
    }

    //Adds a new supplier. Returns false and adds nothing if a required field is missing.
    public boolean addSupplier(Supplier supplier) {
        if (!supplier.validateSupplier()) {
            return false;
        }
        supplier.generateSupplierID();
        suppliers.add(supplier);
        return true;
    }

    //Finds a supplier by ID, e.g. "S001". Returns null if there is no supplier with that ID.
    public Supplier getSupplier(String supplierID) {
        for (int i = 0; i < suppliers.size(); i++) {
            if (suppliers.get(i).getSupplierID().equals(supplierID)) {
                return suppliers.get(i);
            }
        }
        return null;
    }

    //All suppliers that have not been removed.
    public ArrayList<Supplier> getAllSuppliers() {
        ArrayList<Supplier> activeSuppliers = new ArrayList<>();
        for (int i = 0; i < suppliers.size(); i++) {
            if (suppliers.get(i).isActive()) {
                activeSuppliers.add(suppliers.get(i));
            }
        }
        return activeSuppliers;
    }

    //A supplier can only be removed if none of its products would be left with no supplier.
    public boolean canRemove(Supplier supplier) {
        ArrayList<Product> products = supplier.getProductsSupplied();
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).isActive() && getSuppliersForProduct(products.get(i)).size() == 1) {
                return false;
            }
        }
        return true;
    }

    //Removes a supplier. It stays in the list as inactive, so old restock orders still show it.
    public boolean removeSupplier(Supplier supplier) {
        if (!supplier.isActive() || !canRemove(supplier)) {
            return false;
        }
        supplier.removeSupplier();
        return true;
    }

    //Links a product to a supplier. A product can have many suppliers, and a supplier many products.
    public boolean linkProduct(Supplier supplier, Product product) {
        if (!supplier.isActive()) {
            return false;
        }
        supplier.addProduct(product);
        product.addSupplierID(supplier.getSupplierID());
        return true;
    }

    //All products linked to a supplier.
    public ArrayList<Product> getProducts(Supplier supplier) {
        return supplier.getProductsSupplied();
    }

    //All active suppliers of one product.
    public ArrayList<Supplier> getSuppliersForProduct(Product product) {
        ArrayList<Supplier> productSuppliers = new ArrayList<>();
        for (int i = 0; i < suppliers.size(); i++) {
            Supplier s = suppliers.get(i);
            if (s.isActive() && product.getSupplierIDs().contains(s.getSupplierID())) {
                productSuppliers.add(s);
            }
        }
        return productSuppliers;
    }

    //The supplier names and phone numbers to show with a product's low-stock alert.
    public String getReorderContacts(Product product) {
        ArrayList<Supplier> productSuppliers = getSuppliersForProduct(product);
        String contacts = "Reorder " + product.getName() + " from: ";
        for (int i = 0; i < productSuppliers.size(); i++) {
            if (i > 0) {
                contacts += ", ";
            }
            contacts += productSuppliers.get(i).getName() + " (" + productSuppliers.get(i).getPhone() + ")";
        }
        return contacts;
    }
}