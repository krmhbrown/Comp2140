
package peakfinds;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;
import peakfinds.auth.AdminAuth;
import peakfinds.auth.PrivilegeSys;
import peakfinds.auth.SessionManager;
import peakfinds.database.Database;
import peakfinds.database.DatabaseInitializer;
import peakfinds.routes.AuthRoutes;
import peakfinds.routes.CustomerAccountRoutes;
import peakfinds.routes.CustomerRoutes;
import peakfinds.routes.DeliveryRoutes;
import peakfinds.routes.OwnerRoutes;
import peakfinds.routes.CustomerManagementRoutes;
import peakfinds.routes.CartRoutes;
import peakfinds.web.Router;

/**
 * Starts Peak Finds (Jabari owns this file; ask him before editing).
 * Run with: mvn compile exec:java
 * Then open http://localhost:8080
 */
public final class App {
    private App() {}

    public static void main(String[] args) throws IOException {
        Config config = Config.fromEnv();
        System.out.println("Starting with " + config);

        Database db = new Database(config.dbUrl());

        if (!db.connect()) {
            throw new IllegalStateException(
                "Cannot open the database at " + config.dbPath()
                + ". Is the sqlite-jdbc dependency installed?"
            );
        }

        DatabaseInitializer.initialise(db, config.dbPath());

        AppContext ctx = createContext(db, config);

        // Feature 07: Start the delivery email worker.
        new peakfinds.services.DeliveryEmailWorker(
            db,
            peakfinds.services.DeliveryEmailWorker.smtp(config)
        ).start();

        HttpServer server = HttpServer.create(
            new InetSocketAddress(config.port()), 0
        );

        server.createContext("/", ctx.router());
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        System.out.println(
            "Peak Finds is running at http://localhost:" + config.port()
        );
    }

    /** Builds the shared objects and registers every page. */
    public static AppContext createContext(Database db, Config config) {
        SessionManager sessions = new SessionManager(Duration.ofHours(2));
        PrivilegeSys privileges = new PrivilegeSys();
        AdminAuth auth = new AdminAuth(db);
        Router router = new Router(sessions, privileges);

        AppContext ctx = new AppContext(
            db, auth, sessions, privileges, router, config
        );

        // Shared setup
        AuthRoutes.register(ctx);
        OwnerRoutes.register(ctx);

        // Feature 07: Delivery service
        var deliveryService =
            new peakfinds.services.DeliveryService(db);

        DeliveryRoutes.register(ctx, deliveryService);

        // Customer Management and Shopping Cart
        CustomerRoutes.register(ctx);
        CustomerAccountRoutes.register(ctx);
        CustomerManagementRoutes.register(ctx);

        // Register updated customer routes AFTER account routes.
        // This ensures the new /account page takes priority.

        CartRoutes.register(ctx);

        // Product Management
        peakfinds.routes.ProductRoutes.register(ctx);

        // Order Management and Checkout
        peakfinds.routes.OrderRoutes.register(ctx);

        // Supplier Management
        peakfinds.routes.SupplierRoutes.register(ctx);

        // Delivery Management
        peakfinds.routes.DeliveryAdminRoutes.register(
            ctx, deliveryService
        );

        peakfinds.routes.BearerAdminRoutes.register(ctx);

        return ctx;
    }
}
