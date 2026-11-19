#Restock Order class


class RestockOrder:
    def __init__(self, order_id, supplier_id, products, order_date, status):
        self.order_id = order_id
        self.supplier_id = supplier_id
        self.products = products  # List of product IDs and quantities
        self.order_date = order_date
        self.status = status  # e.g., 'pending', 'completed', 'cancelled'

    def create_restock_order(self):
        # Code to create a new restock order in the database
        pass

    def mark_order_as_received(self):
        # Code to mark the restock order as received in the database
        pass

    