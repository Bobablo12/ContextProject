import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaSootClass;
import sootup.java.core.views.JavaView;
// (removed unused imports LinePosition and Position)
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.callgraph.CallGraph;
import sootup.callgraph.CallGraphAlgorithm;
import sootup.callgraph.ClassHierarchyAnalysisAlgorithm;
import sootup.callgraph.RapidTypeAnalysisAlgorithm;


import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;

import java.io.FileWriter;
import java.io.IOException;


import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.stmt.JInvokeStmt;


public class LineNumbers {
    private static Map<MethodSignature, SootMethod> methodMap = new HashMap<>();
    public static void main(String[] args) {
        try {
            //given path
            String projectPath;
            if (args.length > 0) {
                projectPath = args[0];
            } else {
                //current path
                projectPath = System.getProperty("user.dir") + "/CurrentAnalyzingCompiled";
            }
            
            System.out.println("========================================");
            System.out.println("Building Call Graph for Project");
            System.out.println("========================================");
            System.out.println("Project path: " + projectPath);
            System.out.println();
            
            //input location
            AnalysisInputLocation inputLocation = 
                new JavaClassPathAnalysisInputLocation(projectPath);

            JavaView view = new JavaView(Collections.singletonList(inputLocation));
            
            List<JavaSootClass> allClasses = view.getClasses().collect(Collectors.toList());
            System.out.println("Found " + allClasses.size() + " classes in the project\n");
            
            //print names of all classes
            System.out.println("========================================");
            System.out.println("Classes Found:");
            System.out.println("========================================");
            int classCount = 1;
            for (JavaSootClass sootClass : allClasses) {
                String className = sootClass.getType().toString();
                System.out.println(classCount + ". " + className);
                classCount++;
            }
            
            //methods of all classes entry points
            List<MethodSignature> entryPoints = new ArrayList<>();
            int totalMethods = 0;
            
            for (JavaSootClass sootClass : allClasses) {
                String className = sootClass.getType().toString();
                                
                System.out.println("\nClass: " + className);
                int methodCount = 0;
                //get all the methods in each class
                for (SootMethod method : sootClass.getMethods()) {
                    entryPoints.add(method.getSignature());
                    methodMap.put(method.getSignature(), method);
                    totalMethods++;
                    methodCount++;
                }
                System.out.println("  Total methods: " + methodCount);
            }
            
            System.out.println("\n========================================");
            System.out.println("Building Call Graph...");
            System.out.println("========================================");
            System.out.println("Total entry points (methods): " + totalMethods);
            System.out.println();
            

            //build hierarchical and call graph
            CallGraphAlgorithm cha = new ClassHierarchyAnalysisAlgorithm(view);
            CallGraph cg = cha.initialize(entryPoints);
            
            System.out.println("\n========================================");
            System.out.println("Call Graph Results");
            System.out.println("========================================");
            System.out.println("Total call edges: " + cg.callCount());
            System.out.println();
            
            //print filtered call graph to file and console
            try {
                FileWriter writerCHA = new FileWriter("output_CHA.txt");
                FileWriter writerRTA = new FileWriter("output_RTA.txt");
                FileWriter writer = new FileWriter("output1.txt");

                // Track all visited methods globally to avoid infinite recursion
                System.out.println("Traversing all reachable methods...");

                for (JavaSootClass sootClass : allClasses) {
                    String className = sootClass.getType().toString();

                    for (SootMethod method : sootClass.getMethods()) {
                        MethodSignature methodSig = method.getSignature();

                        writer.write("<" + className + ": " + method.getName() + ">\n");
                        System.out.println("CALL GRAPH for <" + className + ": " + method.getName() + ">");
                         traverseCallGraph(methodSig, cg, writer, 1, new ArrayList<>());
                        // for (SootMethod target : cg.get(methodSig)) {
                        //     System.out.println("    from <" 
                        //         + target.getDeclClassType() + ": " 
                        //         + target.getType() + " " 
                        //         + target.getName() + ">");
                        // }
                        writer.write("\n");
                    }
                }

                // Also build RTA call graph 
                CallGraphAlgorithm rta = 
                        new RapidTypeAnalysisAlgorithm(view);

                CallGraph cgrta = 
                        rta.initialize(entryPoints);

                writerCHA.write(cg.toString());
                writerRTA.write(cgrta.toString());
                writerCHA.close();
                writerRTA.close();
                writer.close();
                System.out.println("\nSuccessfully wrote filtered call graph to output.txt");
            } catch (IOException e) {
                System.out.println("An error occurred: " + e.getMessage());
            }
            
            // Summary
            System.out.println("\n========================================");
            System.out.println("Summary Statistics");
            System.out.println("========================================");
            System.out.println("Classes analyzed: " + allClasses.size());
            System.out.println("Methods analyzed: " + totalMethods);
            System.out.println("Total call edges: " + cg.callCount());
 
        } catch (Exception e) {
            System.err.println("Error building call graph:");
            e.printStackTrace();
        }
    }
    
    private static void traverseCallGraph(
            MethodSignature methodSig,
            CallGraph cg,
            FileWriter writer,
            int depth,
            List<MethodSignature> visited) throws IOException {

        // Prevent infinite loops
        if (visited.contains(methodSig)) {
            return;
        }
        visited.add(methodSig);

        // Indentation for readability
        String indent = "    ".repeat(depth);

        // Look up the SootMethod for this signature so we can inspect its statements
        SootMethod method = methodMap.get(methodSig);

        // Gather all call targets from the call graph (may be unordered)
        List<MethodSignature> cgTargets = new ArrayList<>();
        for (MethodSignature t : cg.callTargetsFrom(methodSig)) {
            cgTargets.add(t);
        }

        // We'll print targets in source-order by scanning the caller's statements and
        // emitting any invocation we find that matches a cg target. Track which
        // targets we've already emitted so we can emit the remainder afterwards.
        Set<MethodSignature> emitted = new HashSet<>();

        if (method != null && method.hasBody()) {
            for (Stmt stmt : method.getBody().getStmts()) {
                if (stmt instanceof JInvokeStmt) {
                    java.util.Optional<AbstractInvokeExpr> invOpt = ((JInvokeStmt) stmt).getInvokeExpr();
                    if (invOpt != null && invOpt.isPresent()) {
                        MethodSignature calledSig = invOpt.get().getMethodSignature();
                        if (calledSig != null && cgTargets.contains(calledSig) && !emitted.contains(calledSig)) {
                            // Get the target method's definition line
                            int targetLine = -1;
                            SootMethod targetMethod = methodMap.get(calledSig);
                            if (targetMethod != null && targetMethod.hasBody()) {
                                try {
                                    if (targetMethod.getBody().getPosition() != null) {
                                        targetLine = targetMethod.getBody().getPosition().getFirstLine();
                                    }
                                } catch (Throwable ignore) {
                                }
                            }
                            String callOutput = indent + "to <" + calledSig.getDeclClassType() + ": " + calledSig.getType() + " " + calledSig.getName() + ">" + " (line " + targetLine + ")\n";
                            writer.write(callOutput);
                            System.out.print(callOutput);
                            emitted.add(calledSig);
                            // Recurse immediately to preserve depth-first source ordering
                            traverseCallGraph(calledSig, cg, writer, depth + 1, visited);
                        }
                    }
                }
            }
        }

        // Emit any remaining targets that weren't found in source order
        for (MethodSignature callee : cgTargets) {
            if (emitted.contains(callee)) continue;
            // Get the target method's definition line
            int line = -1;
            SootMethod targetMethod = methodMap.get(callee);
            if (targetMethod != null && targetMethod.hasBody()) {
                try {
                    if (targetMethod.getBody().getPosition() != null) {
                        line = targetMethod.getBody().getPosition().getFirstLine();
                    }
                } catch (Throwable ignore) {
                }
            }
            String callOutput = indent + "to <" + callee.getDeclClassType() + ": " + callee.getType() + " " + callee.getName() + ">" + " (line " + line + ")\n";
            writer.write(callOutput);
            System.out.print(callOutput);
            traverseCallGraph(callee, cg, writer, depth + 1, visited);
        }
    }
}
