package peakfinds.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * The Data Management layer's single doorway to SQLite (SDS class "Database").
 * Only this class touches JDBC. Every value is passed as a "?" parameter, never glued into the SQL,
 * which is what blocks SQL injection.
 *
 * Mapping from the SDS method names: saveData = insert, retrieveData = query/queryOne,
 * updateData = update, deleteData = update with a DELETE statement.
 */
public class Database {

    /** Unchecked wrapper so callers are not forced to catch SQLException everywhere. */
    public static class DatabaseException extends RuntimeException {
        public DatabaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    private final String url;
    /** Holds the open connection while a transaction is running on this thread. */
    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();

    public Database(String url) {
        this.url = url;
    }

    /** Checks that a connection can be opened. */
    public boolean connect() {
        try (Connection c = open()) {
            return c.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /** Opens a connection with foreign keys enforced (SQLite leaves them off by default). */
    protected Connection open() throws SQLException {
        Connection c = java.sql.DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("PRAGMA busy_timeout = 5000");
        }
        return c;
    }

    /** Runs a SELECT and returns every row as a column-name-to-value map. */
    public List<Map<String, Object>> query(String sql, Object... params) {
        return run(c -> {
            try (PreparedStatement ps = prepare(c, sql, params, false); ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        });
    }

    /** Runs a SELECT and returns the first row, or empty if nothing matched. */
    public Optional<Map<String, Object>> queryOne(String sql, Object... params) {
        List<Map<String, Object>> rows = query(sql, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Runs an UPDATE or DELETE (or any non-SELECT) and returns how many rows changed. */
    public int update(String sql, Object... params) {
        return run(c -> {
            try (PreparedStatement ps = prepare(c, sql, params, false)) {
                return ps.executeUpdate();
            }
        });
    }

    /** Runs an INSERT and returns the new row's generated ID. */
    public long insert(String sql, Object... params) {
        return run(c -> {
            try (PreparedStatement ps = prepare(c, sql, params, true)) {
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1L;
                }
            }
        });
    }

    /**
     * Runs several database calls as one all-or-nothing unit (for example an order and its stock
     * deduction). If the work throws, everything is rolled back. Nested calls join the outer transaction.
     */
    public <T> T transaction(Callable<T> work) {
        if (transactionConnection.get() != null) {
            return call(work);
        }
        try (Connection c = open()) {
            c.setAutoCommit(false);
            transactionConnection.set(c);
            try {
                T result = call(work);
                c.commit();
                return result;
            } catch (RuntimeException e) {
                rollbackQuietly(c);
                throw e;
            } finally {
                transactionConnection.remove();
            }
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e);
        }
    }

    /** Runs a whole .sql script (many statements). */
    public void executeScript(String script) {
        for (String statement : SqlScript.split(script)) {
            run(c -> {
                try (Statement st = c.createStatement()) {
                    st.execute(statement);
                }
                return null;
            });
        }
    }

    // ---- internals ----

    private <T> T run(SqlWork<T> work) {
        Connection inTransaction = transactionConnection.get();
        try {
            if (inTransaction != null) {
                return work.apply(inTransaction);
            }
            try (Connection c = open()) {
                return work.apply(c);
            }
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e);
        }
    }

    private static <T> T call(Callable<T> work) {
        try {
            return work.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new DatabaseException("Transaction failed: " + e.getMessage(), e);
        }
    }

    private static void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignored) {
            // nothing more we can do; the connection is closed right after
        }
    }

    private static PreparedStatement prepare(Connection c, String sql, Object[] params, boolean returnKeys)
            throws SQLException {
        PreparedStatement ps = returnKeys
                ? c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                : c.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps;
    }
}
