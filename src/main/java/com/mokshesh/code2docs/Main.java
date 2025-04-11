package com.yourcompany;

import com.mokshesh.code2docs.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

/**
 * Main class for the Java Code Analyzer.
 * This class serves as the entry point for the application and orchestrates the analysis process.
 */
public class Main {

    /**
     * The main method that initiates the code analysis process.
     *
     * @param args Command line arguments. The first argument should be the path to the project to be analyzed.
     */
    public static void main(String[] args) {
        // Validate command line arguments
//        if (args.length < 1) {
//            System.err.println("Error: Please provide the path to the project as a command line argument.");
//            System.exit(1);
//        }
//
//        // Get the project path from command line arguments
//        String projectPathStr = args[0];
        String projectPathStr = "D:\\Workspace\\leucine\\projects\\streem\\streem-backend\\backend\\src\\main\\java\\com\\leucine\\streem\\controller";
        Path projectPath = Paths.get(projectPathStr).toAbsolutePath();

        // Validate if the provided path exists
        if (!projectPath.toFile().exists()) {
            System.err.println("Error: The provided project path does not exist: " + projectPath);
            System.exit(1);
        }

        System.out.println("Starting analysis for project: " + projectPath);

        try {
            DatabaseManager dbManager = DatabaseManager.getInstance();
//            dbManager.dropTableCascade("class_details");
//            dbManager.dropTableCascade("class_import_details");
//            dbManager.dropTableCascade("class_field_details");
//            dbManager.dropTableCascade("method_details");
//            dbManager.dropTableCascade("method_parameter_details");
//            dbManager.dropTableCascade("endpoint_details");

            // Create tables
            createTables(dbManager);

            // Initialize and run file analysis
//            ClassAnalyzer classAnalyzer = new ClassAnalyzer(projectPath);
//            classAnalyzer.analyze();
//            System.out.println("File analysis completed.");
//
//            // Initialize and run import analysis
//            ImportAnalyzer importAnalyzer = new ImportAnalyzer(projectPath);
//            importAnalyzer.analyze();
//            System.out.println("Import analysis completed.");
//
//            // Initialize and run field analysis
//            FieldAnalyzer fieldAnalyzer = new FieldAnalyzer(projectPath);
//            fieldAnalyzer.analyze();
//            System.out.println("Field analysis completed.");
//
//            // Initialize and run method analysis
//            MethodAnalyzer methodAnalyzer = new MethodAnalyzer(projectPath);
//            methodAnalyzer.analyze();
//            System.out.println("Method analysis completed.");
//
//            // Initialize and run endpoint analysis
//            EndpointAnalyzer endpointAnalyzer = new EndpointAnalyzer(projectPath);
//            endpointAnalyzer.analyze();
//            System.out.println("Endpoint analysis completed.");

            // Initialize and run call stack analysis
            CallStackAnalyzer callStackAnalyzer = new CallStackAnalyzer(projectPath);
            callStackAnalyzer.analyze();
            System.out.println("Call stack analysis completed.");

            System.out.println("Analysis completed successfully.");
        } catch (Exception e) {
            System.err.println("An error occurred during analysis: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Ensure database connection is closed
            try {
                DatabaseManager.getInstance().closeConnection();
                System.out.println("Database connection closed.");
            } catch (Exception e) {
                System.err.println("Error closing database connection: " + e.getMessage());
            }
        }
    }

    private static void createTables(DatabaseManager dbManager) throws SQLException {
        // Create class_details table
        dbManager.executeDDL("""
                    CREATE TABLE IF NOT EXISTS class_details (
                        id BIGSERIAL PRIMARY KEY,
                        package_name VARCHAR(512) NOT NULL,
                        class_name VARCHAR(512) NOT NULL,
                        file_name VARCHAR(512) NOT NULL,
                        file_path TEXT NOT NULL,
                        loc INTEGER NOT NULL,
                        comment_count INTEGER NOT NULL,
                        method_count INTEGER NOT NULL,
                        class_count INTEGER NOT NULL,
                        comment_density NUMERIC NOT NULL,
                        import_count INTEGER NOT NULL,
                        imports TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);

        // Create class_import_details table
        dbManager.executeDDL("""
                    CREATE TABLE IF NOT EXISTS class_import_details (
                        id BIGSERIAL PRIMARY KEY,
                        class_details_id BIGINT NOT NULL,
                        package_name VARCHAR(512) NOT NULL,
                        class_name VARCHAR(512) NOT NULL,
                        file_name VARCHAR(512) NOT NULL,
                        file_path TEXT NOT NULL,
                        import_name TEXT NOT NULL,
                        is_static BOOLEAN NOT NULL,
                        is_asterisk BOOLEAN NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (class_details_id) REFERENCES class_details(id)
                    )
                """);

        // Create class_field_details table
        dbManager.executeDDL("""
                    CREATE TABLE IF NOT EXISTS class_field_details (
                        id BIGSERIAL PRIMARY KEY,
                        class_details_id BIGINT NOT NULL,
                        package_name VARCHAR(512) NOT NULL,
                        class_name VARCHAR(512) NOT NULL,
                        file_name VARCHAR(512) NOT NULL,
                        file_path TEXT NOT NULL,
                        field_name VARCHAR(512) NOT NULL,
                        type VARCHAR(512) NOT NULL,
                        is_static BOOLEAN NOT NULL,
                        is_final BOOLEAN NOT NULL,
                        access_modifier VARCHAR(50) NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (class_details_id) REFERENCES class_details(id)
                    )
                """);

        // Create method_details table
        dbManager.executeDDL("""
                    CREATE TABLE IF NOT EXISTS method_details (
                        id BIGSERIAL PRIMARY KEY,
                        class_details_id BIGINT NOT NULL,
                        package_name VARCHAR(512) NOT NULL,
                        class_name VARCHAR(512) NOT NULL,
                        file_name VARCHAR(512) NOT NULL,
                        file_path TEXT NOT NULL,
                        method_name VARCHAR(512) NOT NULL,
                        line_count INTEGER NOT NULL,
                        parameter_count INTEGER NOT NULL,
                        complexity_score INTEGER NOT NULL,
                        return_type VARCHAR(512) NOT NULL,
                        access_modifier VARCHAR(150) NOT NULL,
                        is_static BOOLEAN NOT NULL,
                        parameter_details TEXT,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (class_details_id) REFERENCES class_details(id)
                    )
                """);

        // Create method_parameter_details table
        dbManager.executeDDL("""
                    CREATE TABLE IF NOT EXISTS IF NOT EXISTS method_parameter_details (
                        id BIGSERIAL PRIMARY KEY,
                        method_details_id BIGINT NOT NULL,
                        class_details_id BIGINT NOT NULL,
                        package_name VARCHAR(512) NOT NULL,
                        class_name VARCHAR(512) NOT NULL,
                        file_name VARCHAR(512) NOT NULL,
                        file_path TEXT NOT NULL,
                        method_name VARCHAR(512) NOT NULL,
                        parameter_name VARCHAR(512) NOT NULL,
                        parameter_type VARCHAR(512) NOT NULL,
                        parameter_index INTEGER NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (method_details_id) REFERENCES method_details(id),
                        FOREIGN KEY (class_details_id) REFERENCES class_details(id)
                    )
                """);

        // Create endpoint_details table
        dbManager.executeDDL("""
                    CREATE TABLE IF NOT EXISTS endpoint_details (
                        id BIGSERIAL PRIMARY KEY,
                        class_details_id BIGINT NOT NULL,
                        method_details_id BIGINT NOT NULL,
                        package_name VARCHAR(512),
                        class_name VARCHAR(512) NOT NULL,
                        method_name VARCHAR(512) NOT NULL,
                        file_name VARCHAR(512) NOT NULL,
                        file_path TEXT NOT NULL,
                        http_method VARCHAR(45) NOT NULL,
                        endpoint TEXT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (class_details_id) REFERENCES class_details(id),
                        FOREIGN KEY (method_details_id) REFERENCES method_details(id)
                    )
                """);
    }
}