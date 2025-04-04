package com.mokshesh.code2docs;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * FieldAnalyzer is responsible for analyzing and storing field (class variable)
 * information found in Java source files within a project.
 */
public class FieldAnalyzer {
    /** Database manager instance for database operations */
    private final DatabaseManager dbManager;

    /** The root path of the project being analyzed */
    private final Path projectPath;

    /**
     * Constructs a FieldAnalyzer.
     *
     * @param projectPath The root path of the project to be analyzed
     */
    public FieldAnalyzer(Path projectPath) {
        this.dbManager = DatabaseManager.getInstance();
        this.projectPath = projectPath;
    }

    /**
     * Initiates the analysis process for all Java files in the project.
     * This method walks through the project directory and its subdirectories,
     * identifying and analyzing fields in each Java file encountered.
     */
    public void analyze() {
        System.out.println("Starting field analysis...");

        try (Stream<Path> paths = Files.walk(projectPath)) {
            // Filter for Java files and analyze each one
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(this::analyzeFile);
        } catch (IOException e) {
            System.err.println("Error walking through project directory: " + e.getMessage());
        }

        System.out.println("Field analysis completed.");
    }

    /**
     * Analyzes a single Java file, extracting and storing field information.
     *
     * @param filePath The path to the Java file to be analyzed
     */
    private void analyzeFile(Path filePath) {
        System.out.println("Analyzing fields in file: " + filePath);

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
                String fileName = filePath.getFileName().toString();

                // Analyze fields for each class in the file
                cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                    try {
                        analyzeFields(packageName, classDecl, fileName, filePath);
                    } catch (SQLException e) {
                        System.err.println("Database error while processing class " + classDecl.getNameAsString() + ": " + e.getMessage());
                    }
                });
            } else {
                System.err.println("Failed to parse file: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error reading file " + filePath + ": " + e.getMessage());
        }
    }

    /**
     * Analyzes fields in a given ClassOrInterfaceDeclaration and stores the details in the database.
     *
     * @param packageName The name of the package containing the class
     * @param classDecl The ClassOrInterfaceDeclaration representing the class being analyzed
     * @param fileName The name of the file
     * @param filePath The path of the file being analyzed
     * @throws SQLException If there's an error executing the database operations
     */
    private void analyzeFields(String packageName, ClassOrInterfaceDeclaration classDecl,
                               String fileName, Path filePath) throws SQLException {
        String className = classDecl.getNameAsString();

        // Retrieve all field declarations from the class
        List<FieldDeclaration> fields = classDecl.getFields();

        // Process each field declaration
        for (FieldDeclaration field : fields) {
            for (VariableDeclarator variable : field.getVariables()) {
                insertFieldDetails(packageName, className, fileName, filePath, field, variable);
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
     * Inserts the details of a single field into the database.
     *
     * @param packageName The name of the package containing the class
     * @param className The name of the class containing the field
     * @param fileName The name of the file containing the class
     * @param filePath The path of the file containing the field
     * @param field The FieldDeclaration object containing field details
     * @param variable The VariableDeclarator object for the specific field variable
     * @throws SQLException If there's an error executing the database insert operation
     */
    private void insertFieldDetails(String packageName, String className, String fileName,
                                    Path filePath, FieldDeclaration field, VariableDeclarator variable) throws SQLException {
        // SQL query to insert field details with a subquery to get file_details_id
        String insertSql = "INSERT INTO class_field_details (class_details_id, package_name, class_name, file_name, file_path, " +
                "field_name, type, is_static, is_final, access_modifier) " +
                "VALUES ((SELECT id FROM class_details WHERE package_name = ? AND class_name = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // Execute the insert operation using the database manager
        long rowsAffected = dbManager.insert(insertSql,
                packageName,
                className,
                packageName,
                className,
                fileName,
                filePath.toString(),
                variable.getNameAsString(),
                variable.getType().asString(),
                field.isStatic(),
                field.isFinal(),
                field.getAccessSpecifier().asString()
        );

        // Check if the insert was successful
        if (rowsAffected == 0) {
            System.err.println("Failed to insert field details for package: " + packageName +
                    ", class: " + className + ", field: " + variable.getNameAsString());
        }
    }
}