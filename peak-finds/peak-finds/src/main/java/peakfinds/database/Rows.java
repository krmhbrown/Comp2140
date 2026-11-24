package peakfinds.database;

import java.util.Map;

/**
 * Small helpers for reading values out of a query-result row (a Map of column name to value).
 * SQLite can return INTEGER columns as Integer or Long, so always read numbers through these.
 */
public final class Rows {
    private Rows() {}

    /** Reads a whole-number column as long (0 if NULL). */
    public static long longOf(Map<String, Object> row, String column) {
        Long v = longOrNull(row, column);
        return v == null ? 0L : v;
    }

    /** Reads a whole-number column as Long, or null if the column is NULL. */
    public static Long longOrNull(Map<String, Object> row, String column) {
        Object v = row.get(column);
        return v == null ? null : ((Number) v).longValue();
    }

    /** Reads a whole-number column as int (0 if NULL). */
    public static int intOf(Map<String, Object> row, String column) {
        return (int) longOf(row, column);
    }

    /** Reads a decimal column as double (0 if NULL). */
    public static double doubleOf(Map<String, Object> row, String column) {
        Object v = row.get(column);
        return v == null ? 0.0 : ((Number) v).doubleValue();
    }

    /** Reads a text column (null if NULL). */
    public static String str(Map<String, Object> row, String column) {
        Object v = row.get(column);
        return v == null ? null : v.toString();
    }

    /** Reads a 0/1 column as boolean. */
    public static boolean bool(Map<String, Object> row, String column) {
        return longOf(row, column) != 0L;
    }
}
