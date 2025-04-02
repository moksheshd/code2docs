package com.mokshesh.code2docs;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * FileAnalyzer is responsible for analyzing Java source files within a project.
 * It extracts various metrics and details from each file and stores them in the database.
 */
public class ClassAnalyzer {
    private final Path projectPath;
    private final DatabaseManager dbManager;

    /**
     * Constructs a FileAnalyzer with the specified project path.
     *
     * @param projectPath The root path of the project to be analyzed.
     */
    public ClassAnalyzer(Path projectPath) {
        this.projectPath = projectPath;
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Initiates the analysis process for all Java files in the project.
     * This method walks through the project directory and its subdirectories,
     * identifying and analyzing each Java file encountered.
     */
    public void analyze() {
        System.out.println("Starting file analysis...");

        try (Stream<Path> paths = Files.walk(projectPath)) {
            // Filter for Java files and analyze each one
            paths.filter(path -> path.toString().endsWith(".java")).forEach(this::analyzeFile);
        } catch (IOException e) {
            System.err.println("Error walking through project directory: " + e.getMessage());
        }

        System.out.println("File analysis completed.");
    }

    /**
     * Analyzes a single Java file, extracting various metrics and storing them in the database.
     *
     * @param filePath The path to the Java file to be analyzed.
     */
    private void analyzeFile(Path filePath) {
        System.out.println("Analyzing file: " + filePath);

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

                // Extract file-level metrics
                String packageName = extractPackageName(cu);
                String classNames = extractClassNames(cu);
                int loc = calculateLOC(content);
                int commentCount = countComments(cu);
                int methodCount = countMethods(cu);
                int classCount = countClasses(cu);
                double commentDensity = calculateCommentDensity(loc, commentCount);
                String importList = extractImports(cu);

                // Insert file details into the database
                long fileId = insertFileDetails(fileName, packageName, classNames, filePathStr, loc, commentCount,
                        methodCount, classCount, commentDensity, importList);

                // Log the analysis results
                logAnalysisResults(fileId, fileName, packageName, classNames, loc, commentCount,
                        methodCount, classCount, commentDensity, importList);
            } else {
                System.err.println("Failed to parse file: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error reading file " + filePath + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error inserting file details into database: " + e.getMessage());
        }
    }

    /**
     * Inserts the file details into the database.
     *
     * @param fileName       The name of the file.
     * @param packageName    The package name of the file.
     * @param classNames     The names of the classes in the file.
     * @param filePath       The path of the file relative to the project root.
     * @param loc            The lines of code in the file.
     * @param commentCount   The number of comments in the file.
     * @param methodCount    The number of methods in the file.
     * @param classCount     The number of classes and interfaces in the file.
     * @param commentDensity The comment density of the file.
     * @param importList     The list of imports in the file.
     * @return The generated ID of the inserted record.
     * @throws SQLException If a database error occurs.
     */
    private long insertFileDetails(String fileName, String packageName, String classNames, String filePath, int loc,
                                   int commentCount, int methodCount, int classCount,
                                   double commentDensity, String importList) throws SQLException {
        String sql = "INSERT INTO class_details (file_name, package_name, class_name, file_path, loc, comment_count, " +
                "method_count, class_count, comment_density, import_count, imports) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int importCount = importList.isEmpty() ? 0 : importList.split(",").length;

        return dbManager.insert(sql, fileName, packageName, classNames, filePath, loc, commentCount,
                methodCount, classCount, commentDensity, importCount, importList);
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
     * Extracts the names of all classes and interfaces in the CompilationUnit.
     *
     * @param cu The CompilationUnit representing the parsed Java file.
     * @return A comma-separated string of all class and interface names.
     */
    private String extractClassNames(CompilationUnit cu) {
        return cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .map(ClassOrInterfaceDeclaration::getNameAsString)
                .collect(Collectors.joining(", "));
    }

    /**
     * Calculates the number of lines of code in the file.
     *
     * @param content The content of the file as a string.
     * @return The number of lines in the file.
     */
    private int calculateLOC(String content) {
        return content.split("\r\n|\r|\n").length;
    }

    /**
     * Counts the number of comments in the CompilationUnit.
     *
     * @param cu The CompilationUnit representing the parsed Java file.
     * @return The total number of comments in the file.
     */
    private int countComments(CompilationUnit cu) {
        return cu.getAllContainedComments().size();
    }

    /**
     * Counts the number of methods in the CompilationUnit.
     *
     * @param cu The CompilationUnit representing the parsed Java file.
     * @return The total number of methods in the file.
     */
    private int countMethods(CompilationUnit cu) {
        return cu.findAll(MethodDeclaration.class).size();
    }

    /**
     * Counts the number of classes and interfaces in the CompilationUnit.
     *
     * @param cu The CompilationUnit representing the parsed Java file.
     * @return The total number of classes and interfaces in the file.
     */
    private int countClasses(CompilationUnit cu) {
        return cu.findAll(TypeDeclaration.class).size();
    }

    /**
     * Calculates the comment density (ratio of comments to total lines of code).
     *
     * @param loc          The total lines of code.
     * @param commentCount The total number of comments.
     * @return The comment density as a double.
     */
    private double calculateCommentDensity(int loc, int commentCount) {
        return (double) commentCount / loc;
    }

    /**
     * Extracts all import statements from the CompilationUnit.
     *
     * @param cu The CompilationUnit representing the parsed Java file.
     * @return A comma-separated string of all import statements.
     */
    private String extractImports(CompilationUnit cu) {
        List<String> imports = cu.getImports().stream()
                .map(im -> im.getNameAsString())
                .toList();
        return String.join(", ", imports);
    }

    /**
     * Logs the analysis results for a file.
     *
     * @param fileId         The database ID of the analyzed file.
     * @param fileName       The name of the analyzed file.
     * @param packageName    The package name of the file.
     * @param classNames     The names of the classes in the file.
     * @param loc            The lines of code in the file.
     * @param commentCount   The number of comments in the file.
     * @param methodCount    The number of methods in the file.
     * @param classCount     The number of classes and interfaces in the file.
     * @param commentDensity The comment density of the file.
     * @param importList     The list of imports in the file.
     */
    private void logAnalysisResults(long fileId, String fileName, String packageName, String classNames, int loc, int commentCount,
                                    int methodCount, int classCount, double commentDensity, String importList) {
        System.out.println("File analysis complete for: " + fileName + " (ID: " + fileId + ")");
        System.out.println("Package: " + packageName);
        System.out.println("File name: " + fileName);
        System.out.println("Classes: " + classNames);
        System.out.println("LOC: " + loc);
        System.out.println("Comment Count: " + commentCount);
        System.out.println("Method Count: " + methodCount);
        System.out.println("Class Count: " + classCount);
        System.out.println("Comment Density: " + String.format("%.2f", commentDensity));
        System.out.println("Number of Imports: " + importList.split(",").length);
        System.out.println("Imports: " + importList);
    }
}