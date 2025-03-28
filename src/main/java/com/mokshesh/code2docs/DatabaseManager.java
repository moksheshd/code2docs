package com.mokshesh.code2docs;

import java.sql.*;
import java.util.Properties;

/**
 * DatabaseManager is a singleton class that manages the database connection
 * and provides methods to interact with the PostgreSQL database.
 */
public class DatabaseManager {
    private static DatabaseManager instance;
    private Connection connection;

    // Database connection parameters
    private static final String DB_URL = "jdbc:postgresql://localhost:5433/code_analysis";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";

    /**
     * Private constructor to prevent instantiation.
     * Initializes the database connection.
     */
    private DatabaseManager() {
        try {
            Properties props = new Properties();
            props.setProperty("user", DB_USER);
            props.setProperty("password", DB_PASSWORD);
            props.setProperty("ssl", "false");  // Adjust based on your PostgreSQL setup

            connection = DriverManager.getConnection(DB_URL, props);
            System.out.println("Database connection established.");
        } catch (SQLException e) {
            System.err.println("Failed to establish database connection: " + e.getMessage());
            throw new RuntimeException("Database connection failed", e);
        }
    }

    /**
     * Returns the singleton instance of DatabaseManager.
     *
     * @return The DatabaseManager instance
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Executes an SQL insert statement with the provided parameters and returns the generated ID.
     *
     * @param sql The SQL insert statement to execute
     * @param params The parameters to be set in the prepared statement
     * @return The generated ID of the inserted row, or -1 if no ID was generated
     * @throws SQLException If a database access error occurs
     */
    public long insert(String sql, Object... params) throws SQLException {
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            // Set the parameters in the prepared statement
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            // Execute the insert statement
            int rowsAffected = pstmt.executeUpdate();
            System.out.println(rowsAffected + " row(s) inserted.");

            // Retrieve the generated ID
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long id = generatedKeys.getLong(1);
                    System.out.println("Generated ID: " + id);
                    return id;
                } else {
                    System.out.println("No ID generated.");
                    return -1;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error executing SQL insert: " + e.getMessage());
            throw e; // Re-throw the exception to allow caller to handle it
        }
    }

    /**
     * Drops a table with CASCADE option.
     *
     * @param tableName The name of the table to drop
     * @throws SQLException If a database access error occurs
     */
    public void dropTableCascade(String tableName) throws SQLException {
        String sql = "DROP TABLE IF EXISTS " + tableName + " CASCADE";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("Table " + tableName + " dropped successfully with CASCADE option.");
        } catch (SQLException e) {
            System.err.println("Error dropping table " + tableName + ": " + e.getMessage());
            throw e; // Re-throw the exception to allow caller to handle it
        }
    }

    /**
     * Executes a DDL (Data Definition Language) statement.
     *
     * @param ddlStatement The DDL statement to execute
     * @throws SQLException If a database access error occurs
     */
    public void executeDDL(String ddlStatement) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(ddlStatement);
            System.out.println("DDL statement executed successfully.");
        } catch (SQLException e) {
            System.err.println("Error executing DDL statement: " + e.getMessage());
            throw e; // Re-throw the exception to allow caller to handle it
        }
    }

    /**
     * Closes the database connection.
     */
    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("Database connection closed.");
            } catch (SQLException e) {
                System.err.println("Error closing database connection: " + e.getMessage());
            }
        }
    }
}