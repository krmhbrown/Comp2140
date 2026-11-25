
package peakfinds.routes;

import peakfinds.AppContext;
import peakfinds.Constants.Action;
import peakfinds.auth.ValidationException;
import peakfinds.services.CustomerService;
import peakfinds.services.CustomerService.CustomerDetails;
import peakfinds.ui.Layout;
import peakfinds.web.Html;
import peakfinds.web.Request;
import peakfinds.web.Response;

public final class CustomerAccountRoutes {

    private CustomerAccountRoutes() {
    }

    public static void register(AppContext ctx) {

        CustomerService service =
                new CustomerService(ctx.db(), ctx.auth());

        // Customer registration
        ctx.router().get("/signup", req -> {
            if (req.session() != null) {
                return Response.redirect("/customer");
            }

            return Response.html(
                    Layout.page(
                            req.session(),
                            "Sign up",
                            registrationForm()
                    )
            );
        });

        ctx.router().post("/signup", req ->
                registerCustomer(req, service));

        // Customer account management
        ctx.router().get(
                "/account",
                Action.MANAGE_OWN_ACCOUNT,
                req -> showAccount(req, service)
        );

        ctx.router().post(
                "/account",
                Action.MANAGE_OWN_ACCOUNT,
                req -> saveAccount(req, service)
        );
    }

    // ---------------- REGISTRATION ----------------

    private static Response registerCustomer(
            Request req, CustomerService service) {

        try {
            service.registerCustomer(
                    req.form("name"),
                    req.form("email"),
                    req.form("password"),
                    req.form("phone"),
                    req.form("address")
            );

            return Response.redirect("/login");

        } catch (ValidationException | IllegalArgumentException e) {

            System.out.println(
                    "Registration validation failed: " + e.getMessage()
            );

            String error = """
                    <div class="notice">
                        Registration unsuccessful.
                        Please check your information and try again.
                    </div>
                    """;

            return Response.html(
                    400,
                    Layout.page(
                            req.session(),
                            "Sign up",
                            error + registrationForm()
                    )
            );
        }
    }

    private static String registrationForm() {

        return """
                <section class="narrow" style="position:relative;">

                    <span aria-hidden="true"
                          style="position:absolute;
                                 top:5px;
                                 right:5px;
                                 font-size:45px;
                                 opacity:0.25;
                                 pointer-events:none;">
                        👜
                    </span>

                    <h1>Create Customer Account</h1>

                    <p style="line-height:1.7;
                              margin-bottom:28px;
                              padding-right:35px;">
                        The best finds don't stay hidden for long.
                        Lucky for you, Peak Finds knows where to look.
                    </p>

                    <form action="/signup"
                          method="post"
                          class="form">

                        <label for="name">Full Name</label>
                        <input id="name"
                               name="name"
                               type="text"
                               placeholder="Enter your full name"
                               autocomplete="name"
                               required>

                        <label for="email">Email Address</label>
                        <input id="email"
                               name="email"
                               type="email"
                               placeholder="Enter your email address"
                               autocomplete="email"
                               required>

                        <label for="phone">Phone Number</label>
                        <input id="phone"
                               name="phone"
                               type="tel"
                               placeholder="Enter your phone number"
                               autocomplete="tel"
                               required>

                        <label for="address">Delivery Address</label>
                        <input id="address"
                               name="address"
                               type="text"
                               placeholder="Enter your delivery address"
                               autocomplete="street-address"
                               required>

                        <label for="password">Password</label>
                        <input id="password"
                               name="password"
                               type="password"
                               placeholder="Create a password"
                               autocomplete="new-password"
                               required>

                        <button type="submit" class="button">
                            Create Account
                        </button>

                    </form>

                    <p style="margin-top:25px;
                              text-align:center;">
                        Already have an account?
                        <a href="/login">Log in</a>
                    </p>

                </section>
                """;
    }

    // ---------------- MY ACCOUNT ----------------

    private static Response showAccount(
            Request req, CustomerService service) {

        if (req.session() == null ||
                req.session().customerId() == null) {
            return Response.redirect("/login");
        }

        CustomerDetails customer =
                service.getCustomer(req.session().customerId());

        boolean updated =
                "1".equals(req.query("updated"));

        return Response.html(
                Layout.page(
                        req.session(),
                        "My account",
                        accountForm(req, customer, updated)
                )
        );
    }

    private static Response saveAccount(
            Request req, CustomerService service) {

        if (req.session() == null ||
                req.session().customerId() == null) {
            return Response.redirect("/login");
        }

        try {
            service.updateCustomer(
                    req.session().customerId(),
                    req.form("name"),
                    req.form("email"),
                    req.form("phone"),
                    req.form("address")
            );

            // Display confirmation after successfully saving
            return Response.redirect("/account?updated=1");

        } catch (ValidationException | IllegalArgumentException e) {

            System.out.println(
                    "Account update validation failed: " + e.getMessage()
            );

            CustomerDetails customer =
                    service.getCustomer(req.session().customerId());

            String error = """
                    <div class="notice">
                        Your changes could not be saved.
                        Please check your information.
                    </div>
                    """;

            return Response.html(
                    400,
                    Layout.page(
                            req.session(),
                            "My account",
                            error + accountForm(req, customer, false)
                    )
            );
        }
    }

    private static String accountForm(
            Request req,
            CustomerDetails customer,
            boolean updated) {

        // Confirmation pop-up using style.css
        String successMessage = updated
                ? """
                  <div class="pf-popup-overlay">

                      <div class="pf-popup"
                           role="alertdialog"
                           aria-modal="true"
                           aria-labelledby="successTitle"
                           aria-describedby="successDescription">

                          <div class="pf-popup-check"
                               aria-hidden="true">
                              &#10003;
                          </div>

                          <h2 id="successTitle">
                              Changes Saved!
                          </h2>

                          <p id="successDescription">
                              Your account details have been
                              updated successfully.
                          </p>

                          <a href="/account"
                             class="pf-popup-button">
                              OK
                          </a>

                      </div>

                  </div>
                  """
                : "";

        String form = """
                <section class="narrow" style="position:relative;">

                    <span aria-hidden="true"
                          style="position:absolute;
                                 top:5px;
                                 right:5px;
                                 font-size:45px;
                                 opacity:0.25;
                                 pointer-events:none;">
                        👜
                    </span>

                    <h1>My Account</h1>

                    <p style="line-height:1.7;
                              margin-bottom:28px;
                              padding-right:35px;">
                        A little space for all your details.
                        Keep everything up to date for a
                        smoother shopping experience.
                    </p>

                    %s

                    <form action="/account"
                          method="post"
                          class="form">

                        <input type="hidden"
                               name="_csrf"
                               value="%s">

                        <label for="name">Full Name</label>
                        <input id="name"
                               name="name"
                               type="text"
                               value="%s"
                               autocomplete="name"
                               required>

                        <label for="email">Email Address</label>
                        <input id="email"
                               name="email"
                               type="email"
                               value="%s"
                               autocomplete="email"
                               required>

                        <label for="phone">Phone Number</label>
                        <input id="phone"
                               name="phone"
                               type="tel"
                               value="%s"
                               autocomplete="tel"
                               required>

                        <label for="address">Delivery Address</label>
                        <input id="address"
                               name="address"
                               type="text"
                               value="%s"
                               autocomplete="street-address">

                        <button type="submit" class="button">
                            Save Changes
                        </button>

                    </form>

<div style="display:flex; flex-direction:column; gap:16px; margin-top:24px; width:100%%;">

    <a href="/account/deactivate"
       style="display:block; box-sizing:border-box; width:100%%;
              padding:14px 20px; text-align:center;
              background:white; color:#b83280;
              border:2px solid #b83280; border-radius:10px;
              text-decoration:none; font-weight:600;">
        Deactivate My Account
    </a>

    <a href="/customer"
       style="display:block; text-align:center;
              color:#b83280; text-decoration:underline;">
        Back to Dashboard
    </a>

</div>
</section>
                """;

        return form.formatted(
                successMessage,
                Html.esc(req.session().csrfToken()),
                Html.esc(customer.name()),
                Html.esc(customer.email()),
                Html.esc(customer.phoneNumber()),
                Html.esc(customer.deliveryAddress())
        );
    }
}
