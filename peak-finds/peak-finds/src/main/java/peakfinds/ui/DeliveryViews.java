package peakfinds.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import peakfinds.Constants;
import peakfinds.auth.Session;
import peakfinds.database.Rows;
import peakfinds.web.Html;

/** Presentation helpers shared by owner and bearer delivery pages. */
public final class DeliveryViews {
    private DeliveryViews() {}

    public static String field(Map<String,Object> row, String key) {
        String value = Rows.str(row, key);
        return value == null || value.isBlank() ? "—" : Html.esc(value);
    }

    public static String status(String status) {
        String style = status == null ? "neutral" : switch (status) {
            case "Delivered", "Paid", "Active" -> "positive";
            case "Failed", "Inactive" -> "negative";
            case "Returned", "Pending" -> "warning";
            case "Out for Delivery" -> "progress";
            default -> "neutral";
        };
        return "<span class=\"dm-status dm-" + style + "\">" + Html.esc(status) + "</span>";
    }

    public static String stat(String label, long value) {
        return "<div class=\"card dm-stat\"><p>" + Html.esc(label) + "</p><strong>" + value + "</strong></div>";
    }

    public static String detail(String label, String escapedValue) {
        return "<p class=\"dm-detail\"><strong>" + Html.esc(label) + ":</strong> " + escapedValue + "</p>";
    }

    public static String empty(String message) {
        return "<div class=\"card dm-empty\"><p>" + Html.esc(message) + "</p></div>";
    }

    public static String form(Session session, String path, String key, String escapedId) {
        return "<form class=\"dm-form\" method=\"post\" action=\"" + path + "\">"
                + Layout.csrfField(session) + "<input type=\"hidden\" name=\"" + key + "\" value=\"" + escapedId + "\">";
    }

    public static String statusForm(Session session, String path, Map<String,Object> row, String label) {
        String current = Rows.str(row, "delivery_status");
        List<String> next = current == null ? List.of() : switch (current) {
            case Constants.DELIVERY_ASSIGNED -> List.of(Constants.DELIVERY_OUT_FOR_DELIVERY, Constants.DELIVERY_FAILED, Constants.DELIVERY_RETURNED);
            case Constants.DELIVERY_OUT_FOR_DELIVERY -> List.of(Constants.DELIVERY_DELIVERED, Constants.DELIVERY_FAILED, Constants.DELIVERY_RETURNED);
            case Constants.DELIVERY_FAILED -> List.of(Constants.DELIVERY_RETURNED);
            default -> List.of();
        };
        if (next.isEmpty()) return "<p class=\"dm-muted\">No further status updates available.</p>";
        StringBuilder html = new StringBuilder(form(session, path, "delivery_id", field(row, "delivery_id")));
        html.append("<label>New status<select name=\"status\" required><option value=\"\">Select status</option>");
        for (String option : next) html.append("<option>").append(Html.esc(option)).append("</option>");
        return html.append("</select></label><label>Reason (required for Failed or Returned)<textarea name=\"reason\" maxlength=\"500\" rows=\"2\"></textarea></label><button type=\"submit\" class=\"button\">")
                .append(Html.esc(label)).append("</button></form>").toString();
    }

    private static BigDecimal money(Map<String,Object> row, String key) {
        return BigDecimal.valueOf(Rows.doubleOf(row, key)).setScale(2, RoundingMode.HALF_UP);
    }
    public static BigDecimal outstanding(Map<String,Object> row) {
        return money(row,"total_amount").add(money(row,"delivery_fee")).subtract(money(row,"payment_collected")).max(BigDecimal.ZERO);
    }
    public static String collected(Map<String,Object> row) { return "J$" + money(row,"payment_collected").toPlainString(); }
    public static boolean pendingCod(Map<String,Object> row) {
        return Constants.PAYMENT_CASH_ON_DELIVERY.equals(Rows.str(row,"payment_method"))
                && Constants.PAYMENT_STATUS_PENDING.equals(Rows.str(row,"payment_status"));
    }
    public static boolean delivered(Map<String,Object> row) {
        return Constants.DELIVERY_DELIVERED.equals(Rows.str(row,"delivery_status"));
    }
    public static String paymentForm(Session session, Map<String,Object> row) {
        if (!pendingCod(row)) return "<p class=\"dm-muted\">No COD payment to record.</p>";
        if (!delivered(row)) return "<p class=\"dm-muted\">Cash can be recorded after delivery.</p>";
        BigDecimal due = outstanding(row);
        if (due.signum() == 0) return "<p class=\"notice\">Payment recorded. Awaiting owner confirmation.</p>";
        String amount = due.toPlainString();
        return form(session,"/delivery/record-payment","delivery_id",field(row,"delivery_id"))
                + "<label>Amount collected (JMD)<input type=\"number\" name=\"amount\" min=\"0.01\" step=\"0.01\" max=\"" + amount + "\" value=\"" + amount + "\" required></label>"
                + "<button type=\"submit\" class=\"button\">Record payment</button></form>";
    }
}
