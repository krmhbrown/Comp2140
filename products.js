// Sample data for now. Later this comes from the database (Spring Boot).
// Names and prices are from the owner's list. Quantities are the "Qty on Hand" from SRS Appendix B1.
// Reorder levels are placeholders. Photos: put p-001.jpg, p-002.jpg ... in the images folder.
const products = [
  { id: "P-001", name: "Blush Pink Juicy Couture", colour: "Blush Pink", category: "Backpack", price: 7000, quantity: 0, reorderLevel: 1, image: "images/p-001.jpg", description: "TODO: ask owner for the description." },
  { id: "P-002", name: "Dark Pink Juicy C. Backpack", colour: "Dark Pink", category: "Backpack", price: 10500, quantity: 0, reorderLevel: 1, image: "images/p-002.jpg", description: "Dark pink Juicy backpack." },
  { id: "P-003", name: "Pink Juicy Shoulder Bag", colour: "Pink", category: "Shoulder Bag", price: 9500, quantity: 2, reorderLevel: 1, image: "images/p-003.jpg", description: "Pink Juicy shoulder bag." },
  { id: "P-004", name: "Jesica Simpson Backpack", colour: "White", category: "Backpack", price: 6500, quantity: 0, reorderLevel: 1, image: "images/p-004.jpg", description: "Jesica Simpson backpack." },
  { id: "P-005", name: "Basic Fashion Shoulder Bag", colour: "White", category: "Shoulder Bag", price: 6500, quantity: 1, reorderLevel: 1, image: "images/p-005.jpg", description: "Basic fashion shoulder bag." },
  { id: "P-006", name: "White Steve Madden Shoulder Bag + Coin Pouch & Adjustable Strap", colour: "White", category: "Shoulder Bag", price: 9000, quantity: 1, reorderLevel: 1, image: "images/p-006.jpg", description: "White Steve Madden shoulder bag that comes with a coin pouch and an adjustable strap." },
  { id: "P-007", name: "Karl Lagerfeld Paris Crossbody Bag", colour: "White", category: "Crossbody Bag", price: 13500, quantity: 1, reorderLevel: 1, image: "images/p-007.jpg", description: "Karl Lagerfeld Paris crossbody bag." },
  { id: "P-008", name: "DKNY Margot Crescent Crossbody Bag", colour: "White", category: "Crossbody Bag", price: 14500, quantity: 1, reorderLevel: 1, image: "images/p-008.jpg", description: "DKNY Margot crescent crossbody bag." },
  { id: "P-009", name: "Pink Juicy Crossbody Bag", colour: "Pink", category: "Crossbody Bag", price: 8500, quantity: 1, reorderLevel: 1, image: "images/p-009.jpg", description: "Pink Juicy crossbody bag." },
  { id: "P-010", name: "Sage Green Crossbody Bag + Card Holder", colour: "Sage Green", category: "Crossbody Bag", price: 8500, quantity: 2, reorderLevel: 1, image: "images/p-010.jpg", description: "Sage green crossbody bag that comes with a card holder." },
  { id: "P-011", name: "Black Juicy Crossbody Bag + Card Holder & Adjustable Strap", colour: "Black", category: "Crossbody Bag", price: 7500, quantity: 2, reorderLevel: 1, image: "images/p-011.jpg", description: "Black Juicy crossbody bag with a card holder and an adjustable strap." },
  { id: "P-012", name: "True Religion Denim Tote", colour: "Denim", category: "Tote Bag", price: 12500, quantity: 0, reorderLevel: 1, image: "images/p-012.jpg", description: "True Religion denim tote." }
];

function formatPrice(amount) {
  return "J$" + amount.toLocaleString("en-US");
}

// Temporary placeholder until the real supplier IDs exist (Supplier feature)
products.forEach(p => { p.supplierIds = p.supplierIds || ["S-001"]; });
