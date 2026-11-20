package com.smartProjectPlanner.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;

/**
 * Manages JDBC connection lifecycle to MySQL.
 * Includes automatic database/table initialization from schema.sql.
 */
public class DatabaseConnector {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/smart_project_planner?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "tnvi00@BA";

    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;

    static {
        // Load configuration from Environment Variables or System Properties
        String envUrl = System.getenv("DB_URL");
        dbUrl = (envUrl != null && !envUrl.trim().isEmpty()) ? envUrl : System.getProperty("db.url", DEFAULT_URL);

        String envUser = System.getenv("DB_USER");
        dbUser = (envUser != null && !envUser.trim().isEmpty()) ? envUser : System.getProperty("db.user", DEFAULT_USER);

        String envPass = System.getenv("DB_PASSWORD");
        dbPassword = (envPass != null) ? envPass : System.getProperty("db.password", DEFAULT_PASSWORD);

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (ClassNotFoundException ex) {
                System.err.println("[DatabaseConnector] WARNING: MySQL JDBC Driver not found in classpath.");
            }
        }
    }

    /**
     * Obtains a new database connection.
     * @return Connection instance
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Exception ignored) {}
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Programmatic override for connection parameters.
     */
    public static void configure(String url, String user, String password) {
        dbUrl = url;
        dbUser = user;
        dbPassword = password;
    }

    /**
     * Tests connection and automatically initializes tables from schema.sql if needed.
     * @return true if database is connected and ready
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            boolean valid = conn != null && !conn.isClosed() && conn.isValid(3);
            if (valid) {
                initializeDatabaseIfEmpty(conn);
            }
            return valid;
        } catch (SQLException e) {
            System.err.println("--------------------------------------------------------------------------------");
            System.err.println("[DatabaseConnector] MySQL Connection Failed: " + e.getMessage());
            System.err.println("  Configured URL : " + dbUrl);
            System.err.println("  Configured User: " + dbUser);
            System.err.println("  Error Code     : " + e.getErrorCode() + " (SQLState: " + e.getSQLState() + ")");
            if (e.getErrorCode() == 1045) {
                System.err.println("  >> DIAGNOSIS: Access denied. MySQL Password may be incorrect!");
                System.err.println("     Check password in DatabaseConnector.java or set DB_PASSWORD environment variable.");
            } else if (e.getMessage().contains("Communications link failure") || e.getErrorCode() == 0) {
                System.err.println("  >> DIAGNOSIS: Cannot reach MySQL server on port 3306!");
                System.err.println("     Make sure MySQL service or XAMPP/WAMP MySQL is started.");
            }
            System.err.println("--------------------------------------------------------------------------------");
            return false;
        } catch (Exception e) {
            System.err.println("[DatabaseConnector] Unexpected error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Auto-runs schema.sql if the 'users' table is not yet present.
     */
    private static void initializeDatabaseIfEmpty(Connection conn) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, "users", new String[]{"TABLE"})) {
                if (rs.next()) {
                    // Tables already exist
                    return;
                }
            }

            System.out.println("[DatabaseConnector] Initializing database tables from database/schema.sql...");
            Path schemaPath = Paths.get("database", "schema.sql");
            if (!Files.exists(schemaPath)) {
                schemaPath = Paths.get("..", "database", "schema.sql");
            }

            if (Files.exists(schemaPath)) {
                String sqlContent = Files.readString(schemaPath, StandardCharsets.UTF_8);
                executeSqlScript(conn, sqlContent);
                System.out.println("[DatabaseConnector] Database schema & seed data initialized successfully!");
            } else {
                System.err.println("[DatabaseConnector] Warning: database/schema.sql file not found at " + schemaPath);
            }
        } catch (Exception e) {
            System.err.println("[DatabaseConnector] Notice during auto-init: " + e.getMessage());
        }
    }

    private static void executeSqlScript(Connection conn, String script) throws SQLException {
        // Strip comments and split by semicolon
        String[] statements = script.split(";");
        try (Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                String trimmed = sql.trim();
                // Skip empty lines or pure comments
                if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("/*")) {
                    continue;
                }
                try {
                    stmt.execute(trimmed);
                } catch (SQLException ex) {
                    // Ignore duplicate key or existing index warnings
                    if (ex.getErrorCode() != 1050 && ex.getErrorCode() != 1061) {
                        System.err.println("[DatabaseConnector] SQL warning on statement: " + ex.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Safely closes any AutoCloseable without throwing exceptions.
     */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {}
        }
    }

    public static String getDbUrl() { return dbUrl; }
    public static String getDbUser() { return dbUser; }
    public static String getDbPassword() { return dbPassword; }

    /**
     * Standalone main method to test DB connection from command line:
     * java -cp "backend/bin;backend/lib/*" com.smartProjectPlanner.config.DatabaseConnector
     */
    public static void main(String[] args) {
        System.out.println("Testing MySQL Connection...");
        System.out.println("URL: " + dbUrl);
        System.out.println("User: " + dbUser);
        boolean success = testConnection();
        if (success) {
            System.out.println(">>> SUCCESS: Connected to MySQL database successfully! <<<");
        } else {
            System.out.println(">>> FAILED: Could not connect to MySQL. See error diagnosis above. <<<");
        }
    }
}