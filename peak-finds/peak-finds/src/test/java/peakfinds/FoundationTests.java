package peakfinds;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import peakfinds.auth.PasswordUtil;
import peakfinds.auth.PrivilegeSys;
import peakfinds.auth.Validator;
import peakfinds.auth.ValidationException;
import peakfinds.database.Database;
import peakfinds.database.DatabaseInitializer;
import peakfinds.database.SqlScript;

/**
 * Tests for the shared foundation. Plain Java, no test framework needed.
 * The database is replaced by an in-memory stand-in that answers only the login query, so these run
 * anywhere. (The real SQLite engine is exercised when the app runs; schema.sql is checked separately.)
 * Run: mvn test-compile exec:java@tests
 */
public final class FoundationTests {
    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    /** A database stand-in that only knows the login lookup. */
    private static final class FakeDatabase extends Database {
        final Map<String, Map<String, Object>> usersByEmail = new HashMap<>();

        FakeDatabase() {
            super("jdbc:none");
        }

        void addUser(long id, String email, String hash, String role, Long customerId, String customerName,
                     Long bearerId, String bearerName) {
            Map<String, Object> row = new HashMap<>();
            row.put("user_id", id);
            row.put("email", email);
            row.put("password_hash", hash);
            row.put("role", role);
            row.put("is_active", 1);
            row.put("customer_id", customerId);
            row.put("customer_name", customerName);
            row.put("customer_active", customerId == null ? null : 1);
            row.put("bearer_id", bearerId);
            row.put("bearer_name", bearerName);
            row.put("bearer_active", bearerId == null ? null : 1);
            usersByEmail.put(email, row);
        }

        @Override
        public List<Map<String, Object>> query(String sql, Object... params) {
            if (sql.contains("FROM users u")) {
                Map<String, Object> row = usersByEmail.get((String) params[0]);
                return row == null ? List.of() : List.of(row);
            }
            throw new UnsupportedOperationException(sql);
        }
    }

    private record Resp(int status, String body, Map<String, List<String>> headers) {
        String header(String name) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue().get(0);
                }
            }
            return null;
        }
    }

    private static HttpClient client;
    private static String base;

    private static Resp get(String path, String cookie) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        HttpResponse<String> r = client.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body(), r.headers().map());
    }

    private static Resp post(String path, Map<String, String> form, String cookie) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded");
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        HttpResponse<String> r = client.send(b.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body(), r.headers().map());
    }

    /** Logs in and returns the "PF_SESSION=..." cookie, or null if login failed. */
    private static String login(String email, String password) throws Exception {
        Resp r = post("/login", Map.of("email", email, "password", password), null);
        String setCookie = r.header("Set-Cookie");
        return (r.status() == 303 && setCookie != null) ? setCookie.split(";")[0] : null;
    }

    private static String csrfFrom(String html) {
        Matcher m = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        return m.find() ? m.group(1) : null;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Unit checks");
        passwordTests();
        validatorTests();
        privilegeTests();
        scriptTests();

        System.out.println("HTTP checks (login, roles, sessions)");
        httpTests();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void passwordTests() {
        String hash = PasswordUtil.hash("Password123!");
        check("hash is not the password and has the expected format",
                !hash.contains("Password123!") && hash.startsWith("pbkdf2_sha256$600000$"));
        check("correct password verifies", PasswordUtil.verify("Password123!", hash));
        check("wrong password is rejected", !PasswordUtil.verify("password123!", hash));
        check("same password gives different hashes (random salt)", !hash.equals(PasswordUtil.hash("Password123!")));
        check("malformed stored hash is rejected, not a crash", !PasswordUtil.verify("x", "garbage"));
        check("seed.sql hashes verify against Password123!", seedHashesVerify());
    }

    private static boolean seedHashesVerify() {
        String seed = DatabaseInitializer.readResource("/database/seed.sql");
        Matcher m = Pattern.compile("'(pbkdf2_sha256\\$[^']+)'").matcher(seed);
        int n = 0;
        while (m.find()) {
            n++;
            if (!PasswordUtil.verify("Password123!", m.group(1))) {
                return false;
            }
        }
        return n == 5;
    }

    private static void validatorTests() {
        check("email is trimmed and lower-cased", Validator.email("  A@B.com ").equals("a@b.com"));
        check("bad email is rejected", throwsValidation(() -> Validator.email("not-an-email")));
        check("empty required field is rejected", throwsValidation(() -> Validator.required("   ", "Name")));
        check("short password is rejected", throwsValidation(() -> Validator.newPassword("short")));
        check("phone with letters is rejected", throwsValidation(() -> Validator.phone("abc")));
        check("valid phone is accepted", Validator.phone("876-555-0101").equals("876-555-0101"));
        check("number out of range is rejected", throwsValidation(() -> Validator.intInRange("9", "Quantity", 1, 5)));
        check("status outside the allowed list is rejected",
                throwsValidation(() -> Validator.oneOf("Shipped", "status", Constants.ORDER_STATUSES)));
    }

    private static boolean throwsValidation(Runnable r) {
        try {
            r.run();
            return false;
        } catch (ValidationException e) {
            return true;
        }
    }

    private static void privilegeTests() {
        PrivilegeSys p = new PrivilegeSys();
        check("owner may manage products", p.checkPermission(Constants.ROLE_OWNER, Constants.Action.MANAGE_PRODUCTS));
        check("customer may not manage products",
                !p.checkPermission(Constants.ROLE_CUSTOMER, Constants.Action.MANAGE_PRODUCTS));
        check("bearer may update delivery status",
                p.checkPermission(Constants.ROLE_BEARER, Constants.Action.UPDATE_DELIVERY_STATUS));
        check("bearer may not place orders", !p.checkPermission(Constants.ROLE_BEARER, Constants.Action.PLACE_ORDER));
        check("unknown role is denied everything", !p.checkPermission("hacker", Constants.Action.VIEW_OWN_ORDERS));
        p.grantPermission(Constants.ROLE_CUSTOMER, Constants.Action.MANAGE_PRODUCTS);
        check("grantPermission works", p.checkPermission(Constants.ROLE_CUSTOMER, Constants.Action.MANAGE_PRODUCTS));
        p.revokePermission(Constants.ROLE_CUSTOMER, Constants.Action.MANAGE_PRODUCTS);
        check("revokePermission works", !p.checkPermission(Constants.ROLE_CUSTOMER, Constants.Action.MANAGE_PRODUCTS));

        var own = new peakfinds.auth.Session(2, "c@x.test", Constants.ROLE_CUSTOMER, "C", 7L, null, "t");
        check("customer can access their own record", p.canAccessCustomerRecord(own, 7));
        check("customer cannot access another customer's record", !p.canAccessCustomerRecord(own, 8));
        var owner = new peakfinds.auth.Session(1, "o@x.test", Constants.ROLE_OWNER, "Owner", null, null, "t");
        check("owner can access any customer record", p.canAccessCustomerRecord(owner, 8));
    }

    private static void scriptTests() {
        String schema = DatabaseInitializer.readResource("/database/schema.sql");
        List<String> statements = SqlScript.split(schema);
        long expected = schema.lines().filter(l -> l.startsWith("CREATE ")).count();
        check("schema splits into one statement per CREATE (" + statements.size() + ")", statements.size() == expected);
        check("every trigger statement is kept whole (ends with END;)", statements.stream()
                .filter(s -> s.startsWith("CREATE TRIGGER")).allMatch(s -> s.endsWith("END;")));
        check("no comments left in statements", statements.stream().noneMatch(s -> s.contains("--")));

        String seed = DatabaseInitializer.readResource("/database/seed.sql");
        long inserts = seed.lines().filter(l -> l.startsWith("INSERT")).count();
        check("seed splits into one statement per INSERT", SqlScript.split(seed).size() == inserts);

        check("schema uses the same status wording as Constants", allInSchema(schema,
                Constants.ORDER_STATUSES, Constants.DELIVERY_STATUSES, Constants.PAYMENT_METHODS,
                Constants.ROLES, List.of(Constants.METHOD_BEARER, Constants.METHOD_KNUTSFORD,
                        Constants.PAYMENT_STATUS_PENDING, Constants.PAYMENT_STATUS_PAID,
                        Constants.RESTOCK_ORDERED, Constants.RESTOCK_RECEIVED)));
    }

    @SafeVarargs
    private static boolean allInSchema(String schema, List<String>... groups) {
        for (List<String> group : groups) {
            for (String value : group) {
                if (!schema.contains("'" + value + "'")) {
                    System.out.println("    missing from schema.sql: " + value);
                    return false;
                }
            }
        }
        return true;
    }

    private static void httpTests() throws Exception {
        String hash = PasswordUtil.hash("Password123!");
        FakeDatabase db = new FakeDatabase();
        db.addUser(1, "owner@peakfinds.test", hash, Constants.ROLE_OWNER, null, null, null, null);
        db.addUser(2, "customer1@example.test", hash, Constants.ROLE_CUSTOMER, 1L, "Aaliyah Test", null, null);
        db.addUser(3, "customer2@example.test", hash, Constants.ROLE_CUSTOMER, 2L, "Brianna Test", null, null);
        db.addUser(4, "bearer1@peakfinds.test", hash, Constants.ROLE_BEARER, null, null, 1L, "Bearer One");

        AppContext ctx = App.createContext(db, Config.defaults());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ctx.router());
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

        try {
            // Visitors
            Resp r = get("/catalogue", null);
            check("visitor can open the public catalogue page", r.status() == 200 && r.body().contains("placeholder"));
            r = get("/owner", null);
            check("visitor opening /owner is sent to login and remembered",
                    r.status() == 303 && r.header("Location").equals("/login?next=%2Fowner"));
            r = get("/", null);
            check("home sends visitors to the catalogue", r.status() == 303 && "/catalogue".equals(r.header("Location")));
            check("login page renders", get("/login", null).body().contains("<form method=\"post\" action=\"/login\""));
            check("unknown page gives 404", get("/nope", null).status() == 404);
            check("wrong method gives 405", post("/catalogue", Map.of(), null).status() == 405);

            // Failed logins
            r = post("/login", Map.of("email", "owner@peakfinds.test", "password", "wrong"), null);
            String wrongPassword = r.body();
            check("wrong password gives 401 and no cookie", r.status() == 401 && r.header("Set-Cookie") == null);
            r = post("/login", Map.of("email", "nobody@example.test", "password", "Password123!"), null);
            check("unknown email gives the same 401 message (no account enumeration)",
                    r.status() == 401 && r.body().contains("Incorrect email or password.")
                            && wrongPassword.contains("Incorrect email or password."));
            r = post("/login", Map.of("email", "", "password", ""), null);
            check("blank login is rejected", r.status() == 400);
            r = post("/login", Map.of("email", "\"><script>alert(1)</script>", "password", "x"), null);
            check("script in the email field is escaped in the page",
                    !r.body().contains("<script>alert(1)") && r.body().contains("&lt;script&gt;"));

            // Customer
            r = post("/login", Map.of("email", "Customer1@Example.test", "password", "Password123!"), null);
            check("customer login redirects to /customer (email is case-insensitive)",
                    r.status() == 303 && "/customer".equals(r.header("Location")));
            String setCookie = r.header("Set-Cookie");
            check("session cookie is HttpOnly and SameSite", setCookie != null
                    && setCookie.contains("HttpOnly") && setCookie.contains("SameSite=Lax"));
            String customer = setCookie.split(";")[0];
            r = get("/customer", customer);
            check("customer can open the customer home", r.status() == 200 && r.body().contains("Aaliyah Test"));
            check("customer menu has no owner links", !r.body().contains("/owner"));
            check("customer can open /orders and /cart",
                    get("/orders", customer).status() == 200 && get("/cart", customer).status() == 200);
            check("customer is denied the owner dashboard (403)", get("/owner", customer).status() == 403);
            check("customer is denied owner products (403)", get("/owner/products", customer).status() == 403);
            check("customer is denied the bearer page (403)", get("/delivery", customer).status() == 403);
            check("logged-in user visiting /login is sent home", get("/login", customer).status() == 303);

            // Bearer
            String bearer = login("bearer1@peakfinds.test", "Password123!");
            check("bearer can log in", bearer != null);
            check("bearer can open the delivery page", get("/delivery", bearer).status() == 200);
            check("bearer is denied owner pages (403)", get("/owner/orders", bearer).status() == 403);
            check("bearer is denied customer pages (403)", get("/orders", bearer).status() == 403);

            // Owner
            String owner = login("owner@peakfinds.test", "Password123!");
            check("owner can log in", owner != null);
            r = get("/owner", owner);
            check("owner dashboard shows all 8 menu cards", r.status() == 200 && count(r.body(), "class=\"card\"") == 8);
            check("owner can open every placeholder owner page", allOwnerPagesOpen(owner));
            check("owner is denied bearer-only page (403)", get("/delivery", owner).status() == 403);

            // Safe redirects
            r = post("/login", Map.of("email", "customer2@example.test", "password", "Password123!",
                    "next", "//evil.example/steal"), null);
            check("login ignores an off-site next= link", "/customer".equals(r.header("Location")));
            r = post("/login", Map.of("email", "customer2@example.test", "password", "Password123!",
                    "next", "/owner"), null);
            check("login ignores a next= page the role cannot open", "/customer".equals(r.header("Location")));
            r = post("/login", Map.of("email", "owner@peakfinds.test", "password", "Password123!",
                    "next", "/owner/products"), null);
            check("login returns to the page the owner wanted", "/owner/products".equals(r.header("Location")));

            // Logout and anti-forgery token
            String token = csrfFrom(get("/customer", customer).body());
            check("pages carry an anti-forgery token", token != null && token.length() > 20);
            check("logout without the token is refused (403)", post("/logout", Map.of(), customer).status() == 403);
            check("logout with a wrong token is refused (403)",
                    post("/logout", Map.of("_csrf", "wrong"), customer).status() == 403);
            check("customer is still logged in after refused logouts", get("/customer", customer).status() == 200);
            r = post("/logout", Map.of("_csrf", token), customer);
            check("logout with the token redirects to login and clears the cookie",
                    r.status() == 303 && r.header("Set-Cookie").contains("Max-Age=0"));
            check("old cookie no longer works after logout", get("/customer", customer).status() == 303);
            check("forged cookie is not accepted", get("/customer", "PF_SESSION=forged").status() == 303);

            // Headers and static files
            r = get("/login", null);
            check("security headers are present", "nosniff".equals(r.header("X-Content-Type-Options"))
                    && "DENY".equals(r.header("X-Frame-Options")) && r.header("Content-Security-Policy") != null);
            r = get("/static/css/style.css", null);
            check("stylesheet is served", r.status() == 200 && r.body().contains("--accent"));
            check("static path traversal is blocked", get("/static/..%2Fdatabase%2Fschema.sql", null).status() == 404);
            check("static folders and unknown types are not served",
                    get("/static/css/", null).status() == 404 && get("/static/css/style.sql", null).status() == 404);

            // Lock-out after repeated failures (last, because it blocks this email)
            for (int i = 0; i < 5; i++) {
                post("/login", Map.of("email", "customer2@example.test", "password", "bad" + i), null);
            }
            r = post("/login", Map.of("email", "customer2@example.test", "password", "Password123!"), null);
            check("after 5 failed logins the account is temporarily locked (429)", r.status() == 429);
            check("other accounts are not affected by the lock", login("bearer1@peakfinds.test", "Password123!") != null);
        } finally {
            server.stop(0);
        }
    }

    private static boolean allOwnerPagesOpen(String ownerCookie) throws Exception {
        for (String path : List.of("/owner/orders", "/owner/customers", "/owner/products", "/owner/low-stock",
                "/owner/suppliers", "/owner/restock-orders", "/owner/deliveries", "/owner/bearers")) {
            if (get(path, ownerCookie).status() != 200) {
                System.out.println("    could not open " + path);
                return false;
            }
        }
        return true;
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }
}
