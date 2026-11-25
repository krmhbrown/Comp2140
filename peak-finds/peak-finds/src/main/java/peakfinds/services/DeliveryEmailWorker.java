package peakfinds.services;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import peakfinds.Config;
import peakfinds.database.Database;
import peakfinds.database.Rows;
import peakfinds.models.DeliveryModel;

/** Single-process worker: persists notification work before sending it outside the status transaction. */
public final class DeliveryEmailWorker {
    @FunctionalInterface
    public interface Sender {
        void send(String recipient, String subject, String body) throws Exception;
    }

    private final Database db;
    private final Sender sender;
    private final DeliveryModel model;

    public DeliveryEmailWorker(Database db, Sender sender) {
        this.db = db;
        this.sender = sender;
        this.model = new DeliveryModel(db);
    }

    public ScheduledExecutorService start() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "delivery-email");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(() -> {
            try { processPending(); }
            catch (RuntimeException e) { System.err.println("Delivery email worker: " + e.getClass().getSimpleName()); }
        }, 0, 2, TimeUnit.SECONDS);
        return executor;
    }

    /** Run once from tests using a fake sender. Only one worker should run per database. */
    public synchronized void processPending() {
        for (Map<String, Object> row : db.query("""
                SELECT * FROM delivery_email_outbox WHERE state = 'Pending' ORDER BY email_id LIMIT 20
                """)) {
            long id = Rows.longOf(row, "email_id");
            long orderId = Rows.longOf(row, "order_id");
            String recipient = Rows.str(row, "recipient");
            String subject = Rows.str(row, "subject");
            String error = null;
            try {
                sender.send(recipient, subject, Rows.str(row, "body"));
            } catch (Exception ex) {
                // Avoid logging credentials or raw SMTP messages to the dashboard.
                error = "Email transport failed (" + ex.getClass().getSimpleName() + ")";
            }
            final String failure = error;
            db.transaction(() -> {
                String state = failure == null ? "Sent" : "Failed";
                model.logEmail(recipient, subject, orderId, state, failure);
                if (failure != null) {
                    model.addOwnerNotification("email_failed", subject + ": " + failure, null);
                }
                db.update("UPDATE delivery_email_outbox SET state = ? WHERE email_id = ?", state, id);
                return null;
            });
        }
    }

    /** Real SMTP transport using the existing PF_SMTP_* configuration and STARTTLS. */
    public static Sender smtp(Config config) {
        return (recipient, subject, body) -> {
            if (config.smtpUser().isBlank() || config.smtpPassword().isBlank()) {
                throw new IllegalStateException("SMTP is not configured");
            }
            Properties props = new Properties();
            props.put("mail.smtp.host", config.smtpHost());
            props.put("mail.smtp.port", Integer.toString(config.smtpPort()));
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.protocols", "TLSv1.2");
            props.put("mail.smtp.ssl.checkserveridentity", "true");
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");
            props.put("mail.smtp.writetimeout", "5000");
            MimeMessage message = new MimeMessage(javax.mail.Session.getInstance(props));
            message.setFrom(new InternetAddress(config.mailFrom(), true));
            InternetAddress address = new InternetAddress(recipient, true);
            address.validate();
            message.setRecipient(Message.RecipientType.TO, address);
            message.setSubject(subject, "UTF-8");
            message.setText(body, "UTF-8");
            Transport.send(message, config.smtpUser(), config.smtpPassword());
        };
    }
}
