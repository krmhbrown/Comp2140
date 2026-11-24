package peakfinds.auth;

import static peakfinds.Constants.Action.*;
import static peakfinds.Constants.ROLE_BEARER;
import static peakfinds.Constants.ROLE_CUSTOMER;
import static peakfinds.Constants.ROLE_OWNER;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides what each role may do (SDS class "PrivilegeSys"). The Router calls checkPermission for every
 * protected page. For data that belongs to one person (an order, a delivery) feature code must also call
 * canAccessCustomerRecord / canAccessBearerRecord so a customer can never open another customer's order.
 */
public final class PrivilegeSys {
    private final Map<String, Set<String>> permissions = new ConcurrentHashMap<>();

    public PrivilegeSys() {
        grantAll(ROLE_OWNER, VIEW_OWNER_DASHBOARD, MANAGE_ORDERS, CONFIRM_PAYMENT, MANAGE_CUSTOMERS,
                MANAGE_PRODUCTS, VIEW_LOW_STOCK_ALERTS, MANAGE_SUPPLIERS, MANAGE_RESTOCK_ORDERS,
                MANAGE_DELIVERIES, MANAGE_BEARERS);
        grantAll(ROLE_CUSTOMER, VIEW_CUSTOMER_HOME, MANAGE_OWN_ACCOUNT, MANAGE_CART, PLACE_ORDER,
                VIEW_OWN_ORDERS, CANCEL_OWN_ORDER);
        grantAll(ROLE_BEARER, VIEW_DELIVERY_DASHBOARD, VIEW_ASSIGNED_DELIVERIES, UPDATE_DELIVERY_STATUS,
                RECORD_PAYMENT);
    }

    /** True if the role is allowed to perform the action. Unknown roles and actions are denied. */
    public boolean checkPermission(String role, String action) {
        Set<String> allowed = permissions.get(role);
        return allowed != null && allowed.contains(action);
    }

    /** Gives a role a permission. */
    public void grantPermission(String role, String action) {
        permissions.computeIfAbsent(role, r -> ConcurrentHashMap.newKeySet()).add(action);
    }

    /** Takes a permission away from a role. */
    public void revokePermission(String role, String action) {
        Set<String> allowed = permissions.get(role);
        if (allowed != null) {
            allowed.remove(action);
        }
    }

    /** True for the owner, or for a customer looking at their own record. */
    public boolean canAccessCustomerRecord(Session session, long customerId) {
        if (session == null) {
            return false;
        }
        if (ROLE_OWNER.equals(session.role())) {
            return true;
        }
        return ROLE_CUSTOMER.equals(session.role()) && session.customerId() != null
                && session.customerId() == customerId;
    }

    /** True for the owner, or for a bearer looking at a delivery assigned to them. */
    public boolean canAccessBearerRecord(Session session, long bearerId) {
        if (session == null) {
            return false;
        }
        if (ROLE_OWNER.equals(session.role())) {
            return true;
        }
        return ROLE_BEARER.equals(session.role()) && session.bearerId() != null
                && session.bearerId() == bearerId;
    }

    private void grantAll(String role, String... actions) {
        Set<String> set = ConcurrentHashMap.newKeySet();
        set.addAll(new HashSet<>(Set.of(actions)));
        permissions.put(role, set);
    }
}
