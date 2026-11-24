-- Test data: 1 owner, 2 customers, 2 bearers, 5 products, 2 suppliers.
-- Every account's password is:  Password123!   (test data only, never use in production)
-- Product names and stock come from the owner's stock notes (SRS Appendix B1).
-- Only the Black Juicy price ($7500) is from the owner's Instagram post; the other prices are placeholders.

INSERT INTO users (user_id, email, password_hash, role) VALUES
 (1, 'owner@peakfinds.test',     'pbkdf2_sha256$600000$MakOgvo3GXx/TWTl8880gw==$wWQ7UiQ0kvmgclRVfIslU6WbjD/yoUTMng0htHgvbTY=', 'owner'),
 (2, 'customer1@example.test',   'pbkdf2_sha256$600000$SdQdSIWxYxlDeHzRlqKUhw==$9Udfa0J/gxv3yrcbYGDpj0Hhg4ub7fOgT7zOpQUlNHk=', 'customer'),
 (3, 'customer2@example.test',   'pbkdf2_sha256$600000$12Z7sguxpH7c1BIiAraOyg==$0/nLD9k5grxbsnA/gN14GdZZogN2NUeIstQ+n4eH410=', 'customer'),
 (4, 'bearer1@peakfinds.test',   'pbkdf2_sha256$600000$u7TtyybVaTSHm/2JM1np8A==$DIVuNIyj2jXRbwDOnnhG0Ayt1GZ5Tryq+Sl02VLRZIE=', 'bearer'),
 (5, 'bearer2@peakfinds.test',   'pbkdf2_sha256$600000$cSm70iN+RakQDJ5n4mp6Og==$d8gZkV8GUEsCF9CJe4CJvzfcBXqUC0mNm1ElukqRVjE=', 'bearer');

INSERT INTO customers (customer_id, user_id, name, phone_number, delivery_address) VALUES
 (1, 2, 'Aaliyah Test', '876-555-0101', '12 Hope Road, Kingston'),
 (2, 3, 'Brianna Test', '876-555-0102', '8 Waterford Drive, Portmore');

INSERT INTO bearers (bearer_id, user_id, name, phone_number) VALUES
 (1, 4, 'Bearer One', '876-555-0201'),
 (2, 5, 'Bearer Two', '876-555-0202');

INSERT INTO suppliers (supplier_id, name, phone_number, email, location) VALUES
 (1, 'Island Bags Wholesale', '876-555-0301', 'sales@islandbags.test', 'Kingston'),
 (2, 'Caribbean Fashion Imports', '876-555-0302', 'orders@cfi.test', 'Montego Bay');

INSERT INTO products (product_id, name, description, colour, category, price, stock_quantity, reorder_level) VALUES
 (1, 'Black Juicy Crossbody Bag + Card Holder & Adjustable Strap', 'Crossbody bag with card holder and adjustable strap.', 'Black', 'Crossbody', 7500, 2, 1),
 (2, 'Pink Juicy Shoulder Bag', 'Soft pink shoulder bag.', 'Pink', 'Shoulder Bag', 6500, 2, 1),
 (3, 'Sage Green Crossbody Bag + Card Holder', 'Sage green crossbody with card holder.', 'Sage Green', 'Crossbody', 5500, 2, 1),
 (4, 'Basic Fashion Shoulder Bag', 'Everyday shoulder bag.', 'Black', 'Shoulder Bag', 4000, 1, 1),
 (5, 'True Religion Denim Tote', 'Denim tote bag. Seeded at 0 stock to test Out of Stock.', 'Denim Blue', 'Tote', 6000, 0, 1);

INSERT INTO product_suppliers (product_id, supplier_id) VALUES
 (1, 1), (2, 1), (3, 2), (4, 2), (5, 1), (5, 2);
