package peakfinds;

import peakfinds.auth.AdminAuth;
import peakfinds.auth.PrivilegeSys;
import peakfinds.auth.SessionManager;
import peakfinds.database.Database;
import peakfinds.web.Router;

/**
 * The shared objects every feature needs. Each feature's routes class receives this one object:
 * ctx.db() for data, ctx.router() to register pages, ctx.privileges() for ownership checks,
 * ctx.auth() to create login accounts.
 */
public record AppContext(
        Database db,
        AdminAuth auth,
        SessionManager sessions,
        PrivilegeSys privileges,
        Router router,
        Config config) {}
