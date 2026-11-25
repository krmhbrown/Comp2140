//Author: Karimah Brown
//Date of Creation: 20/11/2026
//Date of last modification: 23/11/2026

/*Supplier class that represents a supplier in the system. It contains 
information about the supplier's name, contact details, and the products 
they supply. The class provides methods to retrieve and update supplier 
information, as well as to manage the list of products supplied by the 
supplier. */

package supplier;

import java.util.ArrayList;
import product.Product;

public class Supplier {
    private String supplierID;
    private String name;
    private String phone;
    private String email;
    private String location;
    private boolean active;
    private ArrayList<Product> productsSupplied;
    private static int nextID = 1;

    //Constructor to create instance of a supplier.
    public Supplier(String name, String phone, String email, String location) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.location = location;
        this.active = true; // Default to active
        this.productsSupplied = new ArrayList<>();

        if (location == null) {
            this.location = ""; // Location is optional
        }
    }

    //Checks that name, phone and email were entered. Location is optional.
    public boolean validateSupplier() {
        return isFilled(name) && isFilled(phone) && isFilled(email);
    }

    //Gives the supplier the next ID, e.g. S001. Only called once the supplier is valid.
    public void generateSupplierID() {
        supplierID = "S" + String.format("%03d", nextID);
        nextID++;
    }

    //Changes the supplier's details. Nothing is changed if name, phone or email is left empty.
    public boolean editSupplierInfo(String name, String phone, String email, String location) {
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
        return true;
    }

    //Marks the supplier as removed. It is kept so old restock orders still show it.
    public void removeSupplier() {
        this.active = false;
    }

    public void addProduct(Product product) {
        if (!productsSupplied.contains(product)) {
            productsSupplied.add(product);
        }
    }

    public void removeProduct(Product product) {
        productsSupplied.remove(product);
    }

    //Checks that a field is not empty.
    private boolean isFilled(String text) {
        return text != null && !text.trim().isEmpty();
    }

    //Getters
    public String getSupplierID() {
        return supplierID;
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
        if (location.isEmpty()) {
            return "Not provided";
        }
        return location;
    }

    public ArrayList<Product> getProductsSupplied() {
        return productsSupplied;
    }

    public boolean isActive() {
        return active;
    }

    public void displaySupplierInfo() {
        if (!active) {
            System.out.println("Supplier is inactive.");
            return;
        }
        System.out.println("Supplier ID: " + supplierID);
        System.out.println("Name: " + name);
        System.out.println("Phone: " + phone);
        System.out.println("Email: " + email);
        System.out.println("Location: " + getLocation());
    }

    public String toString() {
        return "Supplier ID: " + supplierID + ", Name: " + name + ", Phone: " + phone + ", Email: " + email + ", Location: " + getLocation();
    }
}