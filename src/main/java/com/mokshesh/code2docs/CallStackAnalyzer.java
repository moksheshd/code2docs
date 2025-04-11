package com.mokshesh.code2docs;

import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.types.ClassType;
import sootup.java.core.JavaProject;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.language.JavaLanguage;
import sootup.java.core.views.JavaView;
import sootup.core.model.SootMethod;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.jimple.common.stmt.InvokeStmt;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;

import java.nio.file.Paths;
import java.util.*;

public class CallStackAnalyzer {

    public static void main(String[] args) {
        String projectPath = "D:\\Workspace\\leucine\\projects\\streem\\streem-backend\\backend\\build\\classes\\java\\main";
        String className = "com.mokshesh.streem.controller.AuthController";
        String methodName = "login";

        analyzeCallStack(projectPath, className, methodName);
    }

    public static void analyzeCallStack(String projectPath, String className, String methodName) {
        // Initialize SootUp project
        AnalysisInputLocation inputLocation = AnalysisInputLocation.create(Paths.get(projectPath), null);
        JavaProject project = JavaProject.builder(new JavaLanguage(8))
                .addInputLocation(inputLocation)
                .build();

        // Create a view of the project
        JavaView view = project.createView();

        // Get the class
        ClassType classType = project.getIdentifierFactory().getClassType(className);
        Optional<JavaSootClass> classOpt = view.getClass(classType);

        if (classOpt.isPresent()) {
            JavaSootClass sootClass = classOpt.get();

            // Find the method
            Optional<? extends SootMethod> methodOpt = sootClass.getMethods().stream()
                    .filter(m -> m.getName().equals(methodName))
                    .findFirst();

            if (methodOpt.isPresent()) {
                JavaSootMethod method = (JavaSootMethod) methodOpt.get();
                Set<String> visited = new HashSet<>();
                printCallStack(method, view, 0, visited);
            } else {
                System.out.println("Method not found: " + methodName);
            }
        } else {
            System.out.println("Class not found: " + className);
        }
    }

    private static void printCallStack(JavaSootMethod method, JavaView view, int depth, Set<String> visited) {
        String indentation = "  ".repeat(depth);
        System.out.println(indentation + method.getDeclaringClass().getName() + "." + method.getName());

        if (visited.contains(method.getSignature())) {
            System.out.println(indentation + "  (recursive call, stopping here)");
            return;
        }
        visited.add(method.getSignature());

        for (Stmt stmt : method.getBody().getStmts()) {
            if (stmt instanceof InvokeStmt) {
                AbstractInvokeExpr invokeExpr = ((InvokeStmt) stmt).getInvokeExpr();
                String calledMethodSignature = invokeExpr.getMethodSignature();
                Optional<? extends SootMethod> calledMethodOpt = view.getMethod(calledMethodSignature);

                if (calledMethodOpt.isPresent()) {
                    JavaSootMethod calledMethod = (JavaSootMethod) calledMethodOpt.get();
                    printCallStack(calledMethod, view, depth + 1, new HashSet<>(visited));
                } else {
                    System.out.println(indentation + "  " + calledMethodSignature + " (external or unresolved)");
                }
            }
        }
    }
}