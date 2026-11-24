/**
 * Business Logic layer: one class per SDS class (Order, Customer, Cart, Product, Supplier,
 * RestockOrder, Delivery, Bearer). Each feature owner adds their classes here.
 * Pages never touch the database; they call these classes, which call {@code Database}.
 */
package peakfinds.models;
