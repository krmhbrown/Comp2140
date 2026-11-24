package peakfinds.database;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a .sql file into single statements, because JDBC runs one statement at a time.
 * A plain split on ";" would break CREATE TRIGGER blocks, which contain ";" before their final "END;".
 */
public final class SqlScript {
    private SqlScript() {}

    /** Returns each statement in the script, without comments. */
    public static List<String> split(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inTrigger = false;

        for (String raw : script.split("\\R")) {
            String line = stripComment(raw).stripTrailing();
            if (line.isBlank()) {
                continue;
            }
            if (current.length() == 0) {
                inTrigger = line.stripLeading().toUpperCase().startsWith("CREATE TRIGGER");
            }
            current.append(line).append('\n');

            String trimmed = line.strip();
            boolean finished = inTrigger ? trimmed.equalsIgnoreCase("END;") : trimmed.endsWith(";");
            if (finished) {
                statements.add(current.toString().strip());
                current.setLength(0);
                inTrigger = false;
            }
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString().strip());
        }
        return statements;
    }

    /** Removes a trailing "-- comment" unless the dashes are inside a quoted string. */
    private static String stripComment(String line) {
        boolean inQuote = false;
        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);
            if (c == '\'') {
                inQuote = !inQuote;
            } else if (!inQuote && c == '-' && line.charAt(i + 1) == '-') {
                return line.substring(0, i);
            }
        }
        return line;
    }
}
