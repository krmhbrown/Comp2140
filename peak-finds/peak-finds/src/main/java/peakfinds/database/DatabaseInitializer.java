package peakfinds.database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates the tables on first run and loads the test data when the database is empty. */
public final class DatabaseInitializer {
    private DatabaseInitializer() {}

    /** Makes sure the data folder exists, runs schema.sql, and runs seed.sql if there are no users yet. */
    public static void initialise(Database db, String dbPath) {
        createParentFolder(dbPath);
        db.executeScript(readResource("/database/schema.sql"));

        long users = Rows.longOf(db.queryOne("SELECT COUNT(*) AS n FROM users").orElseThrow(), "n");
        if (users == 0) {
            String seed = readResource("/database/seed.sql");
            db.transaction(() -> {
                db.executeScript(seed);
                return null;
            });
            System.out.println("Loaded seed data (1 owner, 2 customers, 2 bearers, 5 products, 2 suppliers).");
        }
    }

    private static void createParentFolder(String dbPath) {
        try {
            Path parent = Path.of(dbPath).toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create the database folder for " + dbPath, e);
        }
    }

    /** Reads a text file from the classpath (src/main/resources). */
    public static String readResource(String path) {
        try (InputStream in = DatabaseInitializer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read resource " + path, e);
        }
    }
}
