
//Author: Karimah Brown
//Date of Creation: 20/11/2026
//Date of last modification: 24/11/2026
 
/*Supplier class that represents a supplier in the system. It contains
information about the supplier's name, contact details and location. The
class provides methods to add, edit and remove suppliers in the database,
to link suppliers to the products they supply, and to find the suppliers of
a product for the low-stock alert. */
 
package peakfinds.models;
 
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import peakfinds.database.Database;
import peakfinds.database.Rows;
 
public class Supplier {
    private int id;
    private String name;
    private String phone;
    private String email;
    private String location;
    private boolean active;
    private static int nextID = 1;
 
    //Constructor to create instance of a new supplier.
    public Supplier(String name, String phone, String email, String location) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.location = location;
        this.active = true; // Default to active
 
        if (location == null) {
            this.location = ""; // Location is optional
        }
    }
 
    //Constructor to create instance of a supplier that is already saved.
    public Supplier(int id, String name, String phone, String email, String location, boolean active) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.location = location;
        this.active = active;
    }
 
    //Checks that name, phone and email were entered. Location is optional.
    public boolean validateSupplier() {
        return isFilled(name) && isFilled(phone) && isFilled(email);
    }
 
    //Gives the supplier the next ID. Only called once the supplier is valid.
    public void generateSupplierID() {
        id = nextID;
        nextID++;
    }
 
    //Saves a new supplier. Returns false and saves nothing if a required field is missing.
    public boolean addSupplier(Database db) {
        if (!validateSupplier()) {
            return false;
        }
        //Start the counter after the highest ID already saved, so IDs carry on after a restart
        nextID = getHighestID(db) + 1;
        generateSupplierID();
 
        db.insert("INSERT INTO suppliers (supplier_id, name, phone_number, email, location) VALUES (?, ?, ?, ?, ?)",
                id, name, phone, email, location);
        return true;
    }
 
    //Changes the supplier's details. Nothing is changed if name, phone or email is left empty.
    public boolean editSupplierInfo(Database db, String name, String phone, String email, String location) {
        if (!isFilled(name) || !isFilled(phone) || !isFilled(email)) {
            return false;
        }
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.location = location;
 
        if (location == null) {
            this.location = "";
        }
        db.update("UPDATE suppliers SET name = ?, phone_number = ?, email = ?, location = ? WHERE supplier_id = ?",
                this.name, this.phone, this.email, this.location, id);
        return true;
    }
 
    //A supplier can only be removed if none of its products would be left with no supplier.
    public boolean canRemove(Database db) {
        ArrayList<Product> products = getProducts(db);
        for (int i = 0; i < products.size(); i++) {
            if (getSuppliersForProduct(db, products.get(i)).size() == 1) {
                return false;
            }
        }
        return true;
    }
 
    //Removes the supplier. It is only marked inactive, so old restock orders still show its name.
    public boolean removeSupplier(Database db) {
        if (!active || !canRemove(db)) {
            return false;
        }
        active = false;
        db.update("UPDATE suppliers SET is_active = 0 WHERE supplier_id = ?", id);
        return true;
    }
 
    //Links a product to this supplier. A product can have many suppliers, and a supplier many products.
    public boolean linkProduct(Database db, Product product) {
        if (!active) {
            return false;
        }
        //OR IGNORE means linking the same product twice does nothing
        db.update("INSERT OR IGNORE INTO product_suppliers (product_id, supplier_id) VALUES (?, ?)",
                product.getProductId(), id);
        product.addSupplierId(id);
        return true;
    }
 
    //All products linked to this supplier.
    public ArrayList<Product> getProducts(Database db) {
        ArrayList<Product> products = new ArrayList<>();
        List<Product> allProducts = Product.findActive(db);
        for (int i = 0; i < allProducts.size(); i++) {
            if (allProducts.get(i).getSupplierIds().contains((long) id)) {
                products.add(allProducts.get(i));
            }
        }
        return products;
    }
 
    //Products not linked to this supplier yet (for the "Link a product" list).
    public ArrayList<Product> getUnlinkedProducts(Database db) {
        ArrayList<Product> products = new ArrayList<>();
        List<Product> allProducts = Product.findActive(db);
        for (int i = 0; i < allProducts.size(); i++) {
            if (!allProducts.get(i).getSupplierIds().contains((long) id)) {
                products.add(allProducts.get(i));
            }
        }
        return products;
    }
 
    //Finds one supplier by ID. Returns null if there is no supplier with that ID.
    public static Supplier findById(Database db, int supplierId) {
        for (Map<String, Object> row : db.query("SELECT * FROM suppliers WHERE supplier_id = ?", supplierId)) {
            return makeSupplier(row);
        }
        return null;
    }
 
    //All suppliers that have not been removed.
    public static ArrayList<Supplier> findAll(Database db) {
        ArrayList<Supplier> suppliers = new ArrayList<>();
        for (Map<String, Object> row : db.query("SELECT * FROM suppliers WHERE is_active = 1 ORDER BY supplier_id")) {
            suppliers.add(makeSupplier(row));
        }
        return suppliers;
    }
 
    //All active suppliers of one product. The low-stock alert uses this to show who to reorder from.
    public static ArrayList<Supplier> getSuppliersForProduct(Database db, Product product) {
        ArrayList<Supplier> productSuppliers = new ArrayList<>();
        ArrayList<Supplier> suppliers = findAll(db);
        for (int i = 0; i < suppliers.size(); i++) {
            if (product.getSupplierIds().contains((long) suppliers.get(i).getId())) {
                productSuppliers.add(suppliers.get(i));
            }
        }
        return productSuppliers;
    }
 
    //The highest supplier ID saved so far (0 if there are none).
    private static int getHighestID(Database db) {
        int highest = 0;
        for (Map<String, Object> row : db.query("SELECT MAX(supplier_id) AS highest FROM suppliers")) {
            highest = Rows.intOf(row, "highest");
        }
        return highest;
    }
 
    //Turns one row from the suppliers table into a Supplier.
    private static Supplier makeSupplier(Map<String, Object> row) {
        return new Supplier(Rows.intOf(row, "supplier_id"), Rows.str(row, "name"), Rows.str(row, "phone_number"),
                Rows.str(row, "email"), Rows.str(row, "location"), Rows.bool(row, "is_active"));
    }
 
    //Checks that a field is not empty.
    private boolean isFilled(String text) {
        return text != null && !text.trim().isEmpty();
    }
 
    //Getters
    public int getId() {
        return id;
    }
 
    public String getSupplierID() {
        return "S" + String.format("%03d", id);
    }
 
    public String getName() {
        return name;
    }
 
    public String getPhone() {
        return phone;
    }
 
    public String getEmail() {
        return email;
    }
 
    public String getLocation() {
        return location;
    }
 
    public boolean isActive() {
        return active;
    }
 
    public String toString() {
        return "Supplier ID: " + getSupplierID() + ", Name: " + name + ", Phone: " + phone + ", Email: " + email + ", Location: " + location;
    }
}