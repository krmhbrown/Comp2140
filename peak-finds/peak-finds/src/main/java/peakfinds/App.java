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
 * Run with:  mvn compile exec:java     then open http://localhost:8080
 */
public final class App {
    private App() {}

    public static void main(String[] args) throws IOException {
        Config config = Config.fromEnv();
        System.out.println("Starting with " + config);

        Database db = new Database(config.dbUrl());
        if (!db.connect()) {
            throw new IllegalStateException("Cannot open the database at " + config.dbPath()
                    + ". Is the sqlite-jdbc dependency installed?");
        }
        DatabaseInitializer.initialise(db, config.dbPath());

        AppContext ctx = createContext(db, config);

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/", ctx.router());
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();
        System.out.println("Peak Finds is running at http://localhost:" + config.port());
    }

    /** Builds the shared objects and registers every page. Tests call this with a stand-in database. */
    public static AppContext createContext(Database db, Config config) {
        SessionManager sessions = new SessionManager(Duration.ofHours(2));
        PrivilegeSys privileges = new PrivilegeSys();
        AdminAuth auth = new AdminAuth(db);
        Router router = new Router(sessions, privileges);
        AppContext ctx = new AppContext(db, auth, sessions, privileges, router, config);

        // Shared setup (Jabari)
        AuthRoutes.register(ctx);
        OwnerRoutes.register(ctx);      // owner dashboard + placeholder pages
        CustomerRoutes.register(ctx);   // customer home + placeholder pages
        DeliveryRoutes.register(ctx);   // bearer home + placeholder pages


        // Customer Management and Cart
        CustomerAccountRoutes.register(ctx);
        CustomerManagementRoutes.register(ctx);
        CartRoutes.register(ctx);

        
        // Feature routes: each owner adds ONE line below once their routes class exists.
        // Registering the same URL as a placeholder replaces it. Example:
         peakfinds.routes.ProductRoutes.register(ctx);
             peakfinds.routes.OrderRoutes.register(ctx);        // orders (feature/orders)
        //   peakfinds.routes.CustomerAccountRoutes.register(ctx); // sign-up, cart, account (feature/customers)
        //   peakfinds.routes.SupplierRoutes.register(ctx);     // suppliers and restock (feature/suppliers)
        //   peakfinds.routes.DeliveryAdminRoutes.register(ctx);// bearers and deliveries (feature/delivery)

        return ctx;
    }
}
