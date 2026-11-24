# Peak Finds: shared foundation (Jabari's steps, in Java)

Java 17+, a built-in web server (`com.sun.net.httpserver`), SQLite through JDBC, plain HTML/CSS/JS. The only
third-party library is the `sqlite-jdbc` driver.

## Run it

```
mvn compile exec:java                 # then open http://localhost:8080
mvn test-compile exec:java@tests      # run the foundation tests
```

Test logins (password for all: `Password123!`):
`owner@peakfinds.test`, `customer1@example.test`, `customer2@example.test`, `bearer1@peakfinds.test`, `bearer2@peakfinds.test`.

To reset the data, stop the app and delete the `data/` folder; the next start rebuilds it from `schema.sql` and `seed.sql`.

Settings come from environment variables: `PF_PORT` (8080), `PF_DB_PATH` (data/peakfinds.db),
`PF_SECURE_COOKIES` (set `true` once served over HTTPS), `PF_SMTP_HOST`, `PF_SMTP_PORT`, `PF_SMTP_USER`,
`PF_SMTP_PASSWORD`, `PF_MAIL_FROM`. Never commit the email password.

## Where things live (plan folder -> Java folder)

| Plan | Here |
|---|---|
| `app.py`, `config.py`, `constants.py` | `peakfinds/App.java`, `Config.java`, `Constants.java` |
| `database/schema.sql`, `seed.sql`, `db.py` | `src/main/resources/database/*.sql`, `database/Database.java` |
| `auth/` | `auth/` (LoginUI, AdminAuth, PrivilegeSys, plus Session, Validator, PasswordUtil) |
| `models/`, `services/` | `models/`, `services/` (empty, for feature owners) |
| `routes/` | `routes/` (one class per area) and `web/Router.java` |
| `templates/`, `static/` | `src/main/resources/templates`, `src/main/resources/static` |
| `tests/` | `src/test/java` |

## How each piece works

**Constants** holds every role, status, payment label and limit, with the exact SRS wording. `schema.sql` repeats
the same text in its CHECK rules, and a test fails if the two ever differ.

**Database** is the only class that uses JDBC. `query`, `queryOne`, `insert` and `update` take SQL with `?`
placeholders and a list of values, so user input is never glued into SQL (this stops SQL injection).
`transaction(() -> { ... })` makes several calls succeed or fail together, which the SDS requires for an order
plus its stock deduction. Read result values with `Rows.longOf(row, "col")` and friends, because SQLite may return
numbers as Integer or Long.

**schema.sql** merges the SDS class attributes into tables, with database-level guards: one owner only, at most 5
active bearers, at most 5 images per product, at most 100 active products, no negative stock, and a delivery is
either a bearer's or Knutsford's. Passwords and logins live in one `users` table (the SDS put email/password on
Customer and email on Bearer; sharing one table lets one login form serve all three roles).
Orders and order items store a name/price snapshot so deleted products and customers still read correctly.

**Passwords** are stored as salted PBKDF2-SHA256 hashes (600,000 rounds), never plain text.

**Login** (`LoginUI` + `AdminAuth`): the form posts email and password. `AdminAuth` looks up the user, checks the
hash, and refuses deactivated accounts. Wrong email and wrong password give the same message and take the same
time, so nobody can discover which emails exist. After 5 failures an email is locked for up to 10 minutes. A
successful login creates a session with a fresh random token, stored in an HttpOnly, SameSite cookie.

**Roles** (`PrivilegeSys`): each role has a list of allowed actions (names in `Constants.Action`). The `Router`
checks the action before running any protected page: not logged in -> redirect to /login, wrong role -> 403.
For data owned by one person, feature code must also call `ctx.privileges().canAccessCustomerRecord(session, id)`
or `canAccessBearerRecord(session, id)`. This is what stops a customer opening another customer's order.

**Router** maps URLs to handlers, supports `/owner/orders/{id}` patterns (`req.pathLong("id")`), turns validation
errors into a 400 page and unexpected errors into a 500 page, and adds security headers to every response. Every
POST from a logged-in user must carry the session's anti-forgery token, or it is refused (403).

**Pages**: `base.html` is the frame, `Layout.page(session, title, html)` fills it and builds the menu for the
user's role. Escape any user text with `Html.esc(...)`. The mobile menu toggle is in `static/js/app.js`.

**OwnerDashboard** is the owner's home page: one card per entry in its `MENU` list. Each card links to a
placeholder page until its feature owner replaces it.

## For teammates: adding your feature

1. Create your routes class, for example `routes/ProductRoutes.java`, with `register(AppContext ctx)`.
2. Register pages with the right action:
   `ctx.router().get("/owner/products", Action.MANAGE_PRODUCTS, req -> ...)` and
   `ctx.router().post("/owner/products/add", Action.MANAGE_PRODUCTS, req -> ...)`.
   Using the same URL as a placeholder replaces the placeholder.
3. Ask Jabari to add one line to `App.createContext` (the commented examples are there).
4. Every `<form method="post">` needs `Layout.csrfField(req.session())` inside it. For file uploads put the token
   in the URL instead: `action="/owner/products/image?_csrf=" + token`.
5. Keep SQL inside your classes under `models/`, call them from the routes, and use `Constants` for status text.
6. Validate every field with `Validator` before saving.
7. Creating a login (customer sign-up, new bearer): `ctx.auth().createUser(email, password, Constants.ROLE_CUSTOMER)`
   inside `ctx.db().transaction(...)` together with your own row.

## What has and has not been verified

- Verified: all Java compiles; `schema.sql` and `seed.sql` run on real SQLite (idempotent, all guards fire);
  72 checks pass for hashing, validation, permissions, SQL splitting, and the full HTTP login, role, session,
  logout and lock-out flow.
- Not yet run: the live app against SQLite through the `sqlite-jdbc` driver (it could not be downloaded in the
  build environment, so the tests use a stand-in database for the login query). Run `mvn compile exec:java` once and
  log in as each seeded user to confirm. Email sending (`services/Notification`) is not part of this foundation.
