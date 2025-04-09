package com.mokshesh.code2docs;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * EndpointAnalyzer is responsible for analyzing web endpoints within Java source files in a project.
 * It identifies methods annotated with Spring Web annotations and extracts relevant information.
 * This class uses JavaParser to parse Java source files and extract endpoint information,
 * which is then stored in a database for further analysis or reporting.
 */
public class EndpointAnalyzer {
    // The root path of the project to be analyzed
    private final Path projectPath;

    // Database manager for storing endpoint information
    private final DatabaseManager dbManager;

    // List of Spring Web annotations to look for when identifying endpoints
    private static final List<String> WEB_ANNOTATIONS = List.of(
            "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping"
    );

    /**
     * Constructs an EndpointAnalyzer with the specified project path.
     *
     * @param projectPath The root path of the project to be analyzed.
     */
    public EndpointAnalyzer(Path projectPath) {
        this.projectPath = projectPath;
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Initiates the analysis process for all web endpoints in Java files within the project.
     * This method walks through the project directory and its subdirectories,
     * identifying Java files and analyzing endpoints within them.
     */
    public void analyze() {
        System.out.println("Starting endpoint analysis...");

        try (Stream<Path> paths = Files.walk(projectPath)) {
            // Filter for Java files and analyze endpoints in each one
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(this::analyzeFile);
        } catch (IOException e) {
            System.err.println("Error walking through project directory: " + e.getMessage());
        }

        System.out.println("Endpoint analysis completed.");
    }

    /**
     * Analyzes a single Java file for web endpoints.
     * This method reads the file content, parses it using JavaParser,
     * and analyzes all classes and interfaces within the file for endpoints.
     *
     * @param filePath The path to the Java file to be analyzed.
     */
    private void analyzeFile(Path filePath) {
        System.out.println("Analyzing endpoints in file: " + filePath);

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
                String packageName = extractPackageName(cu);

                // Find and analyze all classes and interfaces in the file
                List<ClassOrInterfaceDeclaration> declarations = cu.findAll(ClassOrInterfaceDeclaration.class);
                for (ClassOrInterfaceDeclaration declaration : declarations) {
                    analyzeClassOrInterface(declaration, fileName, filePathStr, packageName);
                }
            } else {
                System.err.println("Failed to parse file: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error reading file " + filePath + ": " + e.getMessage());
        }
    }

    /**
     * Analyzes a single class or interface for web endpoints.
     * This method extracts the class-level mapping (if any) and analyzes all methods within the class.
     *
     * @param declaration The ClassOrInterfaceDeclaration to be analyzed.
     * @param fileName The name of the file containing the class or interface.
     * @param filePath The path of the file relative to the project root.
     * @param packageName The package name of the file.
     */
    private void analyzeClassOrInterface(ClassOrInterfaceDeclaration declaration, String fileName, String filePath, String packageName) {
        String className = declaration.getNameAsString();
        String classLevelMapping = getClassLevelMapping(declaration);

        // Find and analyze all methods in the class or interface
        List<MethodDeclaration> methods = declaration.findAll(MethodDeclaration.class);
        for (MethodDeclaration method : methods) {
            analyzeMethod(method, className, classLevelMapping, fileName, filePath, packageName);
        }
    }

    /**
     * Analyzes a single method for web endpoint annotations.
     * This method checks if the method is annotated with any of the recognized web annotations,
     * extracts relevant information, and stores it in the database.
     *
     * @param method The MethodDeclaration to be analyzed.
     * @param className The name of the class or interface containing the method.
     * @param classLevelMapping The class-level request mapping, if any.
     * @param fileName The name of the file containing the method.
     * @param filePath The path of the file relative to the project root.
     * @param packageName The package name of the file.
     */
    private void analyzeMethod(MethodDeclaration method, String className, String classLevelMapping, String fileName, String filePath, String packageName) {
        Optional<AnnotationExpr> endpointAnnotation = method.getAnnotations().stream()
                .filter(a -> WEB_ANNOTATIONS.contains(a.getNameAsString()))
                .findFirst();

        if (endpointAnnotation.isPresent()) {
            String methodName = method.getNameAsString();
            String httpMethod = getHttpMethod(endpointAnnotation.get());
            String endpoint = constructEndpoint(classLevelMapping, getEndpointPath(endpointAnnotation.get()));

            try {
                // Insert endpoint details into the database
                long endpointId = insertEndpointDetails(packageName, methodName, className, fileName, filePath, httpMethod, endpoint);

                // Log the analysis results
                logAnalysisResults(endpointId, packageName, methodName, className, fileName, httpMethod, endpoint);
            } catch (SQLException e) {
                System.err.println("Error inserting endpoint details into database: " + e.getMessage());
            }
        }
    }

    /**
     * Inserts the endpoint details into the database.
     *
     * @param packageName The name of the package containing the class.
     * @param methodName The name of the method.
     * @param className The name of the class containing the method.
     * @param fileName The name of the file containing the method.
     * @param filePath The path of the file relative to the project root.
     * @param httpMethod The HTTP method of the endpoint.
     * @param endpoint The full endpoint path.
     * @return The generated ID of the inserted record.
     * @throws SQLException If a database error occurs.
     */
    private long insertEndpointDetails(String packageName, String methodName, String className, String fileName, String filePath,
                                       String httpMethod, String endpoint) throws SQLException {
        String sql = "INSERT INTO endpoint_details (class_details_id, method_details_id, package_name, method_name, class_name, file_name, file_path, " +
                "http_method, endpoint) VALUES ((SELECT id FROM class_details WHERE package_name = ? AND class_name = ?),(SELECT id FROM method_details WHERE package_name = ? AND class_name = ? AND method_name = ?), ?, ?, ?, ?, ?, ?, ?)";

        return dbManager.insert(sql,
                packageName, className,
                packageName, className, methodName,
                packageName,
                methodName,
                className, fileName, filePath, httpMethod, endpoint);
    }

    /**
     * Gets the class-level request mapping, if any.
     * This method handles various ways the @RequestMapping annotation can be used:
     * 1. @RequestMapping("/path")
     * 2. @RequestMapping(value = "/path")
     * 3. @RequestMapping(path = "/path")
     * 4. @RequestMapping(value = {"/path1", "/path2"})
     *
     * @param declaration The ClassOrInterfaceDeclaration to analyze.
     * @return The class-level mapping or an empty string if none exists.
     */
    private String getClassLevelMapping(ClassOrInterfaceDeclaration declaration) {
        return declaration.getAnnotationByName("RequestMapping")
                .flatMap(a -> {
                    // Case 1: @RequestMapping("/path")
                    if (a.isSingleMemberAnnotationExpr()) {
                        return Optional.of(extractStringValue(a.asSingleMemberAnnotationExpr().getMemberValue()));
                    }

                    // Cases 2, 3, and 4
                    if (a.isNormalAnnotationExpr()) {
                        return a.asNormalAnnotationExpr().getPairs().stream()
                                .filter(p -> p.getNameAsString().equals("value") || p.getNameAsString().equals("path"))
                                .findFirst()
                                .map(p -> extractStringValue(p.getValue()));
                    }

                    return Optional.empty();
                })
                .orElse("");
    }

    /**
     * Extracts a string value from an expression, handling both single string literals
     * and arrays of string literals.
     *
     * @param expr The expression to extract the string value from.
     * @return The extracted string value, or an empty string if extraction fails.
     */
    private String extractStringValue(com.github.javaparser.ast.expr.Expression expr) {
        if (expr.isStringLiteralExpr()) {
            return expr.asStringLiteralExpr().getValue();
        } else if (expr.isArrayInitializerExpr()) {
            return expr.asArrayInitializerExpr().getValues().stream()
                    .filter(com.github.javaparser.ast.expr.Expression::isStringLiteralExpr)
                    .map(e -> e.asStringLiteralExpr().getValue())
                    .findFirst()
                    .orElse("");
        }
        return "";
    }

    /**
     * Extracts the package name from the CompilationUnit.
     *
     * @param cu The CompilationUnit representing the parsed Java file.
     * @return The package name, or "default" if no package is specified.
     */
    private String extractPackageName(CompilationUnit cu) {
        return cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("default");
    }

    /**
     * Determines the HTTP method based on the annotation.
     *
     * @param annotation The AnnotationExpr to analyze.
     * @return The HTTP method as a string.
     */
    private String getHttpMethod(AnnotationExpr annotation) {
        String annotationName = annotation.getNameAsString();
        return switch (annotationName) {
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "DeleteMapping" -> "DELETE";
            case "PatchMapping" -> "PATCH";
            case "RequestMapping" -> getRequestMappingMethod(annotation);
            default -> "UNKNOWN";
        };
    }

    /**
     * Extracts the HTTP method from a RequestMapping annotation.
     *
     * @param annotation The RequestMapping annotation to analyze.
     * @return The HTTP method specified in the annotation, or "GET" if not specified.
     */
    private String getRequestMappingMethod(AnnotationExpr annotation) {
        return annotation.getChildNodes().stream()
                .filter(n -> n instanceof MemberValuePair && ((MemberValuePair) n).getNameAsString().equals("method"))
                .findFirst()
                .map(n -> ((MemberValuePair) n).getValue().toString())
                .map(s -> s.replace("RequestMethod.", ""))
                .orElse("GET");  // Default to GET if method is not specified
    }

    /**
     * Extracts the endpoint path from an annotation.
     * This method handles various ways the endpoint annotations can be used:
     * 1. @GetMapping("/path")
     * 2. @PostMapping(value = "/path")
     * 3. @RequestMapping(path = "/path")
     * 4. @PutMapping(value = {"/path1", "/path2"})
     * 5. @DeleteMapping
     *
     * @param annotation The AnnotationExpr to analyze.
     * @return The endpoint path specified in the annotation, or an empty string if not specified.
     */
    private String getEndpointPath(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            return extractStringValue(annotation.asSingleMemberAnnotationExpr().getMemberValue());
        }

        if (annotation.isNormalAnnotationExpr()) {
            return annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value") || p.getNameAsString().equals("path"))
                    .findFirst()
                    .map(p -> extractStringValue(p.getValue()))
                    .orElse("");
        }

        // For marker annotations like @GetMapping without arguments
        return "";
    }

    /**
     * Constructs the full endpoint by combining class-level and method-level mappings.
     *
     * @param classMapping The class-level mapping.
     * @param methodMapping The method-level mapping.
     * @return The full endpoint path.
     */
    private String constructEndpoint(String classMapping, String methodMapping) {
        ArrayList<String> parts = new ArrayList<>();
        if (!classMapping.isEmpty()) parts.add(classMapping);
        if (!methodMapping.isEmpty()) parts.add(methodMapping);
        return "/" + String.join("/", parts).replaceAll("^/|/$", "").replaceAll("//", "/");
    }

    /**
     * Logs the analysis results for an endpoint.
     *
     * @param endpointId The database ID of the analyzed endpoint.
     * @param packageName The name of the package containing the class.
     * @param methodName The name of the method.
     * @param className The name of the class containing the method.
     * @param fileName The name of the file containing the method.
     * @param httpMethod The HTTP method of the endpoint.
     * @param endpoint The full endpoint path.
     */
    private void logAnalysisResults(long endpointId, String packageName, String methodName, String className, String fileName,
                                    String httpMethod, String endpoint) {
        System.out.println("Endpoint analysis complete for: " + methodName + " (ID: " + endpointId + ")");
        System.out.println("Package: " + packageName);
        System.out.println("Class: " + className);
        System.out.println("Method Name: " + methodName);
        System.out.println("File: " + fileName);
        System.out.println("HTTP Method: " + httpMethod);
        System.out.println("Endpoint: " + endpoint);
        System.out.println("");
    }
}