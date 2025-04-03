package com.mokshesh.code2docs;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * MethodAnalyzer is responsible for analyzing methods within Java source files in a project.
 * It extracts various metrics and details from each method and stores them in the database.
 */
public class MethodAnalyzer {
    private final Path projectPath;
    private final DatabaseManager dbManager;

    /**
     * Constructs a MethodAnalyzer with the specified project path.
     *
     * @param projectPath The root path of the project to be analyzed.
     */
    public MethodAnalyzer(Path projectPath) {
        this.projectPath = projectPath;
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Initiates the analysis process for all methods in Java files within the project.
     * This method walks through the project directory and its subdirectories,
     * identifying Java files and analyzing methods within them.
     */
    public void analyze() {
        System.out.println("Starting method analysis...");

        try (Stream<Path> paths = Files.walk(projectPath)) {
            // Filter for Java files and analyze methods in each one
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(this::analyzeFile);
        } catch (IOException e) {
            System.err.println("Error walking through project directory: " + e.getMessage());
        }

        System.out.println("Method analysis completed.");
    }

    /**
     * Analyzes all methods within a single Java file.
     *
     * @param filePath The path to the Java file to be analyzed.
     */
    private void analyzeFile(Path filePath) {
        System.out.println("Analyzing methods in file: " + filePath);

        try {
            // Read the entire file content
            String content = Files.readString(filePath);

            // Parse the Java file
            JavaParser javaParser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = javaParser.parse(content);

            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                String fileName = filePath.getFileName().toString();
                String filePathStr = projectPath.relativize(filePath).toString();

                // Find and analyze all methods in the file
                List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
                for (MethodDeclaration method : methods) {
                    analyzeMethod(method, fileName, filePathStr, cu);
                }
            } else {
                System.err.println("Failed to parse file: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error reading file " + filePath + ": " + e.getMessage());
        }
    }

    /**
     * Analyzes a single method, extracting various metrics and storing them in the database.
     *
     * @param method   The MethodDeclaration to be analyzed.
     * @param fileName The name of the file containing the method.
     * @param filePath The path of the file relative to the project root.
     * @param cu       The CompilationUnit containing the method.
     */
    private void analyzeMethod(MethodDeclaration method, String fileName, String filePath, CompilationUnit cu) {
        String methodName = method.getNameAsString();
        String className = method.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                .map(NodeWithSimpleName::getNameAsString)
                .orElse("Unknown");
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("default");
        int lineCount = calculateMethodLineCount(method);
        List<Parameter> parameters = method.getParameters();
        int parameterCount = parameters.size();
        int complexityScore = calculateComplexity(method);
        String returnType = method.getType().asString();
        String accessModifier = method.getAccessSpecifier().asString();
        boolean isStatic = method.isStatic();
        String parameterDetails = extractParameterDetails(parameters);

        try {
            // Insert method details into the database
            long methodId = insertMethodDetails(methodName, className, packageName, fileName, filePath, lineCount,
                    parameterCount, complexityScore, returnType,
                    accessModifier, isStatic, parameterDetails);

            // Insert parameter details for each parameter
            for (int i = 0; i < parameters.size(); i++) {
                Parameter parameter = parameters.get(i);
                insertMethodParameterDetails(packageName, className, fileName, filePath, methodName, parameter, i);
            }

            // Log the analysis results
            logAnalysisResults(methodId, methodName, className, packageName, fileName, lineCount, parameterCount,
                    complexityScore, returnType, accessModifier, isStatic, parameterDetails);
        } catch (SQLException e) {
            System.err.println("Error inserting method or parameter details into database: " + e.getMessage());
        }
    }

    /**
     * Inserts the method details into the database.
     *
     * @param methodName       The name of the method.
     * @param className        The name of the class containing the method.
     * @param packageName      The name of the package containing the class.
     * @param fileName         The name of the file containing the method.
     * @param filePath         The path of the file relative to the project root.
     * @param lineCount        The number of lines in the method.
     * @param parameterCount   The number of parameters in the method.
     * @param complexityScore  The calculated complexity score of the method.
     * @param returnType       The return type of the method.
     * @param accessModifier   The access modifier of the method.
     * @param isStatic         Whether the method is static.
     * @param parameterDetails A string representation of the method's parameters.
     * @return The generated ID of the inserted record.
     * @throws SQLException If a database error occurs.
     */
    private long insertMethodDetails(String methodName, String className, String packageName, String fileName, String filePath,
                                     int lineCount, int parameterCount, int complexityScore, String returnType,
                                     String accessModifier, boolean isStatic, String parameterDetails) throws SQLException {
        String sql = "INSERT INTO method_details (class_details_id, method_name, class_name, package_name, file_name, file_path, " +
                "line_count, parameter_count, complexity_score, return_type, access_modifier, " +
                "is_static, parameter_details) " +
                "VALUES ((SELECT id FROM class_details WHERE package_name = ? AND class_name = ?), " +
                "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        return dbManager.insert(sql,
                packageName, className,
                methodName,
                className,
                packageName,
                fileName,
                filePath,
                lineCount,
                parameterCount,
                complexityScore,
                returnType,
                accessModifier,
                isStatic,
                parameterDetails
        );
    }

    /**
     * Calculates the number of lines in the method.
     *
     * @param method The MethodDeclaration to analyze.
     * @return The number of lines in the method.
     */
    private int calculateMethodLineCount(MethodDeclaration method) {
        return method.getEnd().get().line - method.getBegin().get().line + 1;
    }

    /**
     * Calculates a simple complexity score for the method.
     * This is a basic implementation and could be expanded for more sophisticated analysis.
     *
     * @param method The MethodDeclaration to analyze.
     * @return A complexity score for the method.
     */
    private int calculateComplexity(MethodDeclaration method) {
        // Start with a base complexity of 1
        int complexity = 1;

        // Increase complexity for each control flow statement
        complexity += (int) method.findAll(Statement.class).stream()
                .filter(stmt -> stmt.isIfStmt() || stmt.isWhileStmt() || stmt.isForStmt() ||
                        stmt.isForEachStmt() || stmt.isSwitchStmt() || stmt.isTryStmt())
                .count();

        return complexity;
    }

    /**
     * Extracts and formats the detailed information of the method's parameters.
     *
     * @param parameters The list of Parameters to analyze.
     * @return A string representation of the method's parameters with their types.
     */
    private String extractParameterDetails(List<Parameter> parameters) {
        return parameters.stream()
                .map(param -> param.getType().asString() + " " + param.getNameAsString())
                .collect(Collectors.joining(", "));
    }

    /**
     * Logs the analysis results for a method.
     *
     * @param methodId         The database ID of the analyzed method.
     * @param methodName       The name of the method.
     * @param className        The name of the class containing the method.
     * @param packageName      The name of the package containing the class.
     * @param fileName         The name of the file containing the method.
     * @param lineCount        The number of lines in the method.
     * @param parameterCount   The number of parameters in the method.
     * @param complexityScore  The calculated complexity score of the method.
     * @param returnType       The return type of the method.
     * @param accessModifier   The access modifier of the method.
     * @param isStatic         Whether the method is static.
     * @param parameterDetails A string representation of the method's parameters with their types.
     */
    private void logAnalysisResults(long methodId, String methodName, String className, String packageName, String fileName,
                                    int lineCount, int parameterCount, int complexityScore, String returnType,
                                    String accessModifier, boolean isStatic, String parameterDetails) {
        System.out.println("Method analysis complete for: " + methodName + " (ID: " + methodId + ")");
        System.out.println("Method Name: " + methodName);
        System.out.println("Class: " + className);
        System.out.println("Package: " + packageName);
        System.out.println("File: " + fileName);
        System.out.println("Line Count: " + lineCount);
        System.out.println("Parameter Count: " + parameterCount);
        System.out.println("Complexity Score: " + complexityScore);
        System.out.println("Return Type: " + returnType);
        System.out.println("Access Modifier: " + accessModifier);
        System.out.println("Is Static: " + isStatic);
        System.out.println("Parameters: " + parameterDetails);
    }

    /**
     * Inserts the details of a single method parameter into the database.
     *
     * @param packageName The name of the package containing the class
     * @param className The name of the class containing the method
     * @param fileName The name of the file containing the class
     * @param filePath The path of the file containing the method
     * @param methodName The name of the method containing the parameter
     * @param parameter The Parameter object containing parameter details
     * @param parameterIndex The index of the parameter in the method's parameter list
     * @throws SQLException If there's an error executing the database insert operation
     */
    private void insertMethodParameterDetails(String packageName, String className, String fileName,
                                              String filePath, String methodName, Parameter parameter, int parameterIndex) throws SQLException {
        String insertSql = "INSERT INTO method_parameter_details (method_details_id, class_details_id, package_name, class_name, file_name, file_path, " +
                "method_name, parameter_name, parameter_type, parameter_index) " +
                "VALUES ((SELECT id FROM method_details WHERE package_name = ? AND class_name = ? AND method_name = ?), " +
                "(SELECT id FROM class_details WHERE package_name = ? AND class_name = ?), " +
                "?, ?, ?, ?, ?, ?, ?, ?)";

        long rowsAffected = dbManager.insert(insertSql,
                packageName, className, methodName,
                packageName, className,
                packageName, className, fileName, filePath,
                methodName,
                parameter.getNameAsString(),
                parameter.getType().asString(),
                parameterIndex
        );

        if (rowsAffected == 0) {
            System.err.println("Failed to insert method parameter details for package: " + packageName +
                    ", class: " + className + ", method: " + methodName +
                    ", parameter: " + parameter.getNameAsString());
        }
    }
}