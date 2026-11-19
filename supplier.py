# Supplier class

#From Feature 3: Supplier management
#1. SR-1: The system shall allow the authorised user to add suppliers, storing their name, phone number, email address and location.
#2. SR-2: The system shall assign a unique supplier ID to each supplier.
#3. SR-3: The system shall allow the authorised user to edit existing supplier information.
#4. SR-4: The system shall allow the authorised user to remove suppliers

#From Feature 4: Supplier product management
#1. SR-1: The system shall link each product to one or more suppliers. 
#2. SR-2: The system shall display all products linked to a selected supplier.
#3. SR-3: The system shall include the name and phone number of each supplier linked to a product in low-stock notifications.
#4. SR-4: The system shall store restock orders with the supplier, products ordered, quantity of each product and the date ordered.
#5. SR-5: The system shall display all restock orders placed from newest to oldest.
#6. SR-6: The system shall allow an authorised user to mark a restock order as received.
#7. SR-7: The system shall increase a product's quantity by the quantity received when a restock order is marked as received.



class Supplier:
    def __init__(self, name, phone, email, location):
        self.name = name
        self.phone = phone
        self.email = email
        self.location = location


    def add_supplier(self):
        # Code to add supplier to the database
        pass

    def edit_supplier(self, name=None, phone=None, email=None, location=None):
        # Code to edit supplier details in the database
        if name:
            self.name = name
        if phone:
            self.phone = phone
        if email:
            self.email = email
        if location:
            self.location = location

    def remove_supplier(self):
        # Code to delete supplier from the database
        pass

    def get_supplier_details(self):
        # Code to retrieve supplier details from the database
        pass

    def list_suppliers(self):
        # Code to list all suppliers from the database
        pass

    def link_product(self, supplier_id, product_id):
        # Code to link a product to the supplier
        pass

    def get_linked_products(self, supplier_id):
        # Code to retrieve all products linked to the supplier
        pass
