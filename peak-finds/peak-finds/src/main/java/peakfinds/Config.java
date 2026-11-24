package peakfinds;

/**
 * App settings, read from environment variables so no passwords are ever committed to Git.
 * Every setting has a safe local default except the email login.
 */
public record Config(
        int port,
        String dbPath,
        boolean secureCookies,
        String smtpHost,
        int smtpPort,
        String smtpUser,
        String smtpPassword,
        String mailFrom) {

    /** Reads PF_* environment variables (see README for the list). */
    public static Config fromEnv() {
        return new Config(
                intEnv("PF_PORT", 8080),
                env("PF_DB_PATH", "data/peakfinds.db"),
                Boolean.parseBoolean(env("PF_SECURE_COOKIES", "false")),
                env("PF_SMTP_HOST", "smtp.gmail.com"),
                intEnv("PF_SMTP_PORT", 587),
                env("PF_SMTP_USER", ""),
                env("PF_SMTP_PASSWORD", ""),
                env("PF_MAIL_FROM", "Peak Finds <no-reply@peakfinds.test>"));
    }

    /** Defaults for tests and quick local runs. */
    public static Config defaults() {
        return new Config(8080, "data/peakfinds.db", false, "smtp.gmail.com", 587, "", "", "Peak Finds <no-reply@peakfinds.test>");
    }

    /** The JDBC URL for the SQLite file. */
    public String dbUrl() {
        return "jdbc:sqlite:" + dbPath;
    }

    /** Never prints the email password. */
    @Override
    public String toString() {
        return "Config[port=" + port + ", dbPath=" + dbPath + ", secureCookies=" + secureCookies
                + ", smtpHost=" + smtpHost + ", smtpPort=" + smtpPort + ", smtpUser=" + smtpUser + ", smtpPassword=***]";
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    private static int intEnv(String name, int fallback) {
        try {
            return Integer.parseInt(env(name, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
