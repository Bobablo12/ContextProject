import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaSootClass;
import sootup.java.core.views.JavaView;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.callgraph.CallGraph;
import sootup.callgraph.CallGraphAlgorithm;
import sootup.callgraph.ClassHierarchyAnalysisAlgorithm;
import sootup.callgraph.RapidTypeAnalysisAlgorithm;


import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;


// import sootup.core.jimple.basic.Stmt;
// import sootup.core.jimple.common.expr.AbstractInvokeExpr;
// import sootup.core.jimple.common.stmt.JInvokeStmt;
// import sootup.core.graph.BasicBlock;

public class BuildCG {
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
                    totalMethods++;
                    methodCount++;
                    //System.out.println("  " + methodCount + ". " + method.getName() + "()");
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

                System.out.println("DEBUG: entering file write section, classes = " + allClasses.size());

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                FileWriter writer = new FileWriter("output.txt");

                // Track all visited methods globally to avoid infinite recursion
                System.out.println("Traversing all reachable methods...");

                for (JavaSootClass sootClass : allClasses) {
                    String className = sootClass.getType().toString();

                    for (SootMethod method : sootClass.getMethods()) {
                        MethodSignature methodSig = method.getSignature();

                        // Skip methods that don't appear in the call graph
                        //if (!cg.containsMethod(methodSig)) continue;

                        // Start traversal for this method
                        writer.write("<" + className + ": " + method.getName() + ">\n");
                        System.out.println("CALL GRAPH for <" + className + ": " + method.getName() + ">");

                        // Perform a depth-first traversal
                        traverseCallGraph(methodSig, cg, writer, 1, new ArrayList<>());
                        writer.write("\n");
                    }
                }
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


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

                // Body body = method.getBody();
                // for (Stmt stmt : body.getStmts()) {
                //     if (stmt.containsInvokeExpr()) {
                //         InvokeExpr invokeExpr = stmt.getInvokeExpr();
                //         MethodSignature target = invokeExpr.getMethodSignature();
                //         int line = stmt.getJavaSourceStartLineNumber();

                //         System.out.println(methodSig + " may call:");
                //         System.out.println("\t" + target + " (line " + line + ")");
                //     }
                // }

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

        for (MethodSignature callee : cg.callTargetsFrom(methodSig)) {
            String callOutput = indent + "-> <" + callee.getDeclClassType() + ": " + callee.getType() + " " + callee.getName() + ">\n";
            writer.write(callOutput);
            System.out.print(callOutput);

            // Recursive call for deeper methods
            traverseCallGraph(callee, cg, writer, depth + 1, visited);
        }
    }
}
