// Sample data for now. Later this comes from the database (Spring Boot).
// Names and prices are from the owner's list. Quantities are the "Qty on Hand" from SRS Appendix B1.
// Reorder levels are placeholders. Photos: put P001.jpeg, P002.jpeg ... in the images folder.
const products = [
  { id: "P001", name: "Blush Pink Juicy Couture", colour: "Blush Pink", category: "Backpack", price: 7000, quantity: 0, reorderLevel: 1, image: "images/P001.jpeg", description: "TODO: ask owner for the description." },
  { id: "P002", name: "Dark Pink Juicy C. Backpack", colour: "Dark Pink", category: "Backpack", price: 10500, quantity: 0, reorderLevel: 1, image: "images/P002.jpeg", description: "Dark pink Juicy backpack." },
  { id: "P003", name: "Pink Juicy Shoulder Bag", colour: "Pink", category: "Shoulder Bag", price: 9500, quantity: 2, reorderLevel: 1, image: "images/P003.jpeg", description: "Pink Juicy shoulder bag." },
  { id: "P004", name: "Jessica Simpson Backpack", colour: "White", category: "Backpack", price: 6500, quantity: 0, reorderLevel: 1, image: "images/P004.jpeg", description: "Jessica Simpson backpack." },
  { id: "P005", name: "Basic Fashion Shoulder Bag", colour: "White", category: "Shoulder Bag", price: 6500, quantity: 1, reorderLevel: 1, image: "images/P005.jpeg", description: "Basic fashion shoulder bag." },
  { id: "P006", name: "White Steve Madden Shoulder Bag + Coin Pouch & Adjustable Strap", colour: "White", category: "Shoulder Bag", price: 9000, quantity: 1, reorderLevel: 1, image: "images/P006.jpeg", description: "White Steve Madden shoulder bag that comes with a coin pouch and an adjustable strap." },
  { id: "P007", name: "Karl Lagerfeld Paris Crossbody Bag", colour: "White", category: "Crossbody Bag", price: 13500, quantity: 1, reorderLevel: 1, image: "images/P007.jpeg", description: "Karl Lagerfeld Paris crossbody bag." },
  { id: "P008", name: "DKNY Margot Crescent Crossbody Bag", colour: "White", category: "Crossbody Bag", price: 14500, quantity: 1, reorderLevel: 1, image: "images/P008.jpeg", description: "DKNY Margot crescent crossbody bag." },
  { id: "P009", name: "Pink Juicy Crossbody Bag", colour: "Pink", category: "Crossbody Bag", price: 8500, quantity: 1, reorderLevel: 1, image: "images/P009.jpeg", description: "Pink Juicy crossbody bag." },
  { id: "P010", name: "Kate Spade Crossbody Bag + Card Holder", colour: "Sage Green", category: "Crossbody Bag", price: 8500, quantity: 2, reorderLevel: 1, image: "images/P010.jpeg", description: "Sage green crossbody bag that comes with a card holder." },
  { id: "P011", name: "Black Juicy Crossbody Bag + Card Holder & Adjustable Strap", colour: "Black", category: "Crossbody Bag", price: 7500, quantity: 2, reorderLevel: 1, image: "images/P011.jpeg", description: "Black Juicy crossbody bag with a card holder and an adjustable strap." },
  { id: "P012", name: "True Religion Denim Tote", colour: "Denim", category: "Tote Bag", price: 12500, quantity: 0, reorderLevel: 1, image: "images/P012.jpeg", description: "True Religion denim tote." }
];

function formatPrice(amount) {
  return "J$" + amount.toLocaleString("en-US");
}

// Temporary placeholder until the real supplier IDs exist (Supplier feature)
products.forEach(p => { p.supplierIds = p.supplierIds || ["S-001"]; });
