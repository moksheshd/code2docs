package com.mokshesh.code2docs;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ImportAnalyzer is responsible for analyzing and storing import statements
 * found in Java source files within a project.
 */
public class ImportAnalyzer {
    /** Database manager instance for database operations */
    private final DatabaseManager dbManager;

    /** The root path of the project being analyzed */
    private final Path projectPath;

    /**
     * Constructs an ImportAnalyzer.
     *
     * @param projectPath The root path of the project to be analyzed
     */
    public ImportAnalyzer(Path projectPath) {
        this.dbManager = DatabaseManager.getInstance();
        this.projectPath = projectPath;
    }

    /**
     * Initiates the analysis process for all Java files in the project.
     * This method walks through the project directory and its subdirectories,
     * identifying and analyzing imports in each Java file encountered.
     */
    public void analyze() {
        System.out.println("Starting import analysis...");

        try (Stream<Path> paths = Files.walk(projectPath)) {
            // Filter for Java files and analyze each one
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(this::analyzeFile);
        } catch (IOException e) {
            System.err.println("Error walking through project directory: " + e.getMessage());
        }

        System.out.println("Import analysis completed.");
    }

    /**
     * Analyzes a single Java file, extracting and storing import information.
     *
     * @param filePath The path to the Java file to be analyzed
     */
    private void analyzeFile(Path filePath) {
        System.out.println("Analyzing imports in file: " + filePath);

        try {
            // Read the entire file content
            String content = Files.readString(filePath);

            // Parse the Java file
            JavaParser javaParser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = javaParser.parse(content);

            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();

                // Extract necessary information
                String packageName = extractPackageName(cu);
                String className = extractClassName(cu);
                String fileName = filePath.getFileName().toString();

                // Analyze imports
                analyzeImports(cu, packageName, className, fileName, filePath);
            } else {
                System.err.println("Failed to parse file: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error reading file " + filePath + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }

    /**
     * Analyzes import statements in a given CompilationUnit and stores the details in the database.
     *
     * @param cu The CompilationUnit representing the parsed Java file
     * @param packageName The name of the package containing the class
     * @param className The name of the class containing the imports
     * @param fileName The name of the file
     * @param filePath The path of the file being analyzed
     * @throws SQLException If there's an error executing the database operations
     */
    private void analyzeImports(CompilationUnit cu, String packageName,
                                String className, String fileName, Path filePath) throws SQLException {
        // Retrieve all import declarations from the CompilationUnit
        List<ImportDeclaration> imports = cu.getImports();

        // Process each import declaration
        for (ImportDeclaration importDecl : imports) {
            try {
                // Insert the details of each import into the database
                insertImportDetails(packageName, className, fileName, filePath, importDecl);
            } catch (SQLException e) {
                System.err.println("Error inserting import details for " + importDecl.getNameAsString() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Extracts the package name from the CompilationUnit.
     *
     * @param cu The CompilationUnit
     * @return The package name, or "default" if no package is specified
     */
    private String extractPackageName(CompilationUnit cu) {
        Optional<PackageDeclaration> packageDecl = cu.getPackageDeclaration();
        return packageDecl.map(pd -> pd.getNameAsString()).orElse("default");
    }

    /**
     * Extracts the class name from the CompilationUnit.
     *
     * @param cu The CompilationUnit
     * @return The name of the first class or interface found, or "Unknown" if none found
     */
    private String extractClassName(CompilationUnit cu) {
        return cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .map(ClassOrInterfaceDeclaration::getNameAsString)
                .collect(Collectors.joining(", "));
    }

    /**
     * Inserts the details of a single import declaration into the database.
     *
     * @param packageName The name of the package containing the class
     * @param className The name of the class containing the import
     * @param fileName The name of the file containing the class
     * @param filePath The path of the file containing the import
     * @param importDecl The ImportDeclaration object containing import details
     * @throws SQLException If there's an error executing the database insert operation
     */
    private void insertImportDetails(String packageName, String className,
                                     String fileName, Path filePath, ImportDeclaration importDecl) throws SQLException {
        // SQL query to insert import details with a subquery to get class_details_id
        String insertSql = "INSERT INTO class_import_details (class_details_id, package_name, class_name, file_name, file_path, import_name, is_static, is_asterisk) " +
                "VALUES ((SELECT id FROM class_details WHERE package_name = ? AND class_name = ?), ?, ?, ?, ?, ?, ?, ?)";

        // Execute the insert operation using the database manager
        long rowsAffected = dbManager.insert(insertSql,
                packageName, className,
                packageName,
                className,
                fileName,
                filePath.toString(),
                importDecl.getNameAsString(),  // Get the full name of the import
                importDecl.isStatic(),         // Check if it's a static import
                importDecl.isAsterisk()        // Check if it's a wildcard import (ends with *)
        );

        // Check if the insert was successful
        if (rowsAffected == 0) {
            System.err.println("Failed to insert import details for package: " + packageName + ", class: " + className + ", import: " + importDecl.getNameAsString());
        }
    }
}