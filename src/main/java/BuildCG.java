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
import java.util.Optional;
import sootup.java.core.JavaSootMethod;


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


// import sootup.core.jimple.common.stmt.Stmt;
// import sootup.core.jimple.common.expr.AbstractInvokeExpr;
// import sootup.core.jimple.common.stmt.JInvokeStmt;


public class BuildCG {
    private static Map<MethodSignature, SootMethod> methodMap = new HashMap<>();
    private static Set<MethodSignature> callers = new HashSet<>();

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
            
            // System.out.println("========================================");
            // System.out.println("Building Call Graph for Project");
            // System.out.println("========================================");
            // System.out.println("Project path: " + projectPath);
            // System.out.println();
            
            AnalysisInputLocation inputLocation = 
                new JavaClassPathAnalysisInputLocation(projectPath);

            JavaView view = new JavaView(Collections.singletonList(inputLocation));
            
            List<JavaSootClass> allClasses = view.getClasses().collect(Collectors.toList());
            //System.out.println("Found " + allClasses.size() + " classes in the project\n");
            
            //print names of all classes
            // System.out.println("========================================");
            // System.out.println("Classes Found:");
            // System.out.println("========================================");
            int classCount = 1;
            // for (JavaSootClass sootClass : allClasses) {
            //     String className = sootClass.getType().toString();
            //     System.out.println(classCount + ". " + className);
            //     classCount++;
            // }
            
            List<MethodSignature> entryPoints = new ArrayList<>();
            int totalMethods = 0;
            
            for (JavaSootClass sootClass : allClasses) {
                String className = sootClass.getType().toString();
                                
                System.out.println("\nClass: " + className);
                int methodCount = 0;
                for (SootMethod method : sootClass.getMethods()) {
                    entryPoints.add(method.getSignature());
                    methodMap.put(method.getSignature(), method);
                    totalMethods++;
                    methodCount++;
                }
                //System.out.println("  Total methods: " + methodCount);
            }
            
            // System.out.println("\n========================================");
            // System.out.println("Building Call Graph...");
            // System.out.println("========================================");
            // System.out.println("Total entry points (methods): " + totalMethods);
            // System.out.println();
            

            CallGraphAlgorithm cha = new ClassHierarchyAnalysisAlgorithm(view);
            CallGraph cg = cha.initialize(entryPoints);
            String methodname = "methodA";
            
            // System.out.println("\n========================================");
            // System.out.println("Call Graph Results");
            // System.out.println("========================================");
            // System.out.println("Total call edges: " + cg.callCount());
            // System.out.println();
            
            try {
                FileWriter writerCHA = new FileWriter("output_CHA.txt");
                FileWriter writerRTA = new FileWriter("output_RTA.txt");
                FileWriter writer = new FileWriter("output.txt");
                //System.out.println("Traversing all reachable methods...");
                boolean contains = false;
                for (JavaSootClass sootClass : allClasses) {
                    String className = sootClass.getType().toString();

                    for (SootMethod method : sootClass.getMethods()) {
                        MethodSignature methodSig = method.getSignature();

                        if (method.getName().equals(methodname)) {
                            //System.out.println("main=================================================================================================");
                            extractMethodCG(sootClass, method, method.getSignature(), cg, className,
                                    method.getBody().getPosition().getFirstLine() - 1);
                            contains = true;
                            //System.out.println("=============================================================================================");
                        }

                        int defLine = -1;
                        if (method.hasBody() && method.getBody().getPosition() != null) {
                            defLine = method.getBody().getPosition().getFirstLine() - 1;
                        }
                        //System.out.println("CALL GRAPH for <" + className + ": " + method.getName() + "()> (line " + defLine + ")");
                        writer.write("<" + className + ": " + method.getName() + "()> (line " + defLine + ")\n");

                        printCallsRecursive(methodSig, cg, writer, 1, new HashSet<>(), new ArrayList<MethodSignature>());


                        for (CallGraph.Call call : cg.callsTo(methodSig)) {
                            MethodSignature sig = call.sourceMethodSignature(); // or call.src(), depends on API
                            int line = call.getLineNumber();
                            // System.out.println("    from <"
                            //     + sig.getDeclClassType() + ": "
                            //     + sig.getType() + " "
                            //     + sig.getName() + ">" + " (line " + line + ")" );
                            writer.write("    from <"
                                + sig.getDeclClassType() + ": "
                                + sig.getType() + " "
                                + sig.getName() + ">" + " (line " + line + ")" );
                            writer.write("\n");
                        }
                        
                    }
                }
                if (!contains) {
                    System.out
                            .println("Method " + methodname + " not found in the project classes.");
                }

                //System.out.println("\n=======================================================================================================");

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
            // System.out.println("\n========================================");
            // System.out.println("Summary Statistics");
            // System.out.println("========================================");
            // System.out.println("Classes analyzed: " + allClasses.size());
            // System.out.println("Methods analyzed: " + totalMethods);
            // System.out.println("Total call edges: " + cg.callCount());
 
        } catch (Exception e) {
            System.err.println("Error building call graph:");
            e.printStackTrace();
        }
    }
    
    // private static void traverseCallGraph(
    //         MethodSignature methodSig,
    //         CallGraph cg,
    //         FileWriter writer,
    //         int depth,
    //         List<MethodSignature> visited) throws IOException {

    //     // Prevent infinite loops
    //     if (visited.contains(methodSig)) {
    //         return;
    //     }
    //     visited.add(methodSig);

    //     // Indentation for readability
    //     String indent = "    ".repeat(depth);

    //     // Look up the SootMethod for this signature so we can inspect its statements
    //     SootMethod method = methodMap.get(methodSig);

    //     // Gather all call targets from the call graph (may be unordered)
    //     List<MethodSignature> cgTargets = new ArrayList<>();
    //     for (MethodSignature t : cg.callTargetsFrom(methodSig)) {
    //         cgTargets.add(t);
    //     }

    //     // We'll print targets in source-order by scanning the caller's statements and
    //     // emitting any invocation we find that matches a cg target. Track which
    //     // targets we've already emitted so we can emit the remainder afterwards.
    //     Set<MethodSignature> emitted = new HashSet<>();

    //     if (method != null && method.hasBody()) {
    //         for (Stmt stmt : method.getBody().getStmts()) {
    //             if (stmt instanceof JInvokeStmt) {
    //                 java.util.Optional<AbstractInvokeExpr> invOpt = ((JInvokeStmt) stmt).getInvokeExpr();
    //                 if (invOpt != null && invOpt.isPresent()) {
    //                     MethodSignature calledSig = invOpt.get().getMethodSignature();
                        
    //                     if (calledSig != null && cgTargets.contains(calledSig) && !emitted.contains(calledSig)) {
    //                         int stmtLine = -1;
    //                         if (stmt.getPositionInfo() != null && stmt.getPositionInfo().getStmtPosition() != null) {
    //                             stmtLine = stmt.getPositionInfo().getStmtPosition().getFirstLine();
    //                         }
    //                         String callOutput = indent + "to <" + calledSig.getDeclClassType() + ": " + calledSig.getType() + " " + calledSig.getName() + ">" + " (line " + stmtLine + ")\n";
    //                         writer.write(callOutput);
    //                         System.out.print(callOutput);
    //                         emitted.add(calledSig);
    //                         // Recurse immediately to preserve depth-first source ordering
    //                         traverseCallGraph(calledSig, cg, writer, depth + 1, visited);
    //                     }
    //                 }
    //             }
    //         }
    //     }

    //     // Emit any remaining targets that weren't found in source order
    //     for (MethodSignature callee : cgTargets) {
    //         if (emitted.contains(callee)) continue;
    //         int line = -1;
    //         if (method != null && method.hasBody()) {
    //             try {
    //                 if (method.getBody().getPosition() != null) {
    //                     line = method.getBody().getPosition().getFirstLine();
    //                 }
    //             } catch (Throwable ignore) {
    //             }
    //         }
    //         String callOutput = indent + "to <" + callee.getDeclClassType() + ": " + callee.getType() + " " + callee.getName() + ">" + " (line " + line + ")\n";
    //         writer.write(callOutput);
    //         System.out.print(callOutput);
    //         traverseCallGraph(callee, cg, writer, depth + 1, visited);
    //     }
    // }
    // private static void reverseDFS(MethodSignature m, CallGraph cg) {
    //     if (!callers.add(m)) return;
    //     for (CallGraph.Call call : cg.callsFrom(m)) {
    //         MethodSignature caller = call.targetMethodSignature();
    //         reverseDFS(caller, cg);
    //     }
    // }
    private static ArrayList<MethodSignature> printCallsRecursive(
        MethodSignature methodSig,
        CallGraph cg,
        FileWriter writer,
        int depth,
        Set<MethodSignature> recursionStack, 
        ArrayList<MethodSignature> methodsList) throws IOException {
    
        String indent = "    ".repeat(depth);
        boolean isFirstLevel = (depth == 1);

        // Detect cycles
        if (recursionStack.contains(methodSig)) {
            writer.write(indent + "[cycle detected]\n");
            return methodsList;
        }

        // Mark current method
        recursionStack.add(methodSig);
        //System.out.println("depth: " + depth + " method: " + methodSig.getName());
        // Record method if first time seeing it
        if (!methodsList.contains(methodSig) && depth == 2) {
            methodsList.add(methodSig);
        }

        // Explore callees
        for (CallGraph.Call call : cg.callsFrom(methodSig)) {
            isFirstLevel = false;
            MethodSignature callee = call.targetMethodSignature();
            int line = call.getLineNumber();

            String out = indent + "to <"
                    + callee.getDeclClassType() + ": "
                    + callee.getType() + " "
                    + callee.getName() + "> (line " + line + ")\n";

            writer.write(out);

            // Recursive descent
            printCallsRecursive(callee, cg, writer, depth + 1, recursionStack, methodsList);
        }

        recursionStack.remove(methodSig);
        if (isFirstLevel) {
            writer.write(indent + "(no calls)\n");
        }
        return methodsList;
    }

    private static void extractMethodCG(
        JavaSootClass sootClass,
        SootMethod method,
        MethodSignature methodSig,
        CallGraph cg,
        String className, 
        int defLine) throws IOException {

        FileWriter writer = new FileWriter("single_method_cg.txt");
        FileWriter writer2 = new FileWriter("single_method_bs.txt");
        writer.write("<" 
                + sootClass.getType() + ": "
                + method.getName() + "()> (line " + (method.getBody().getPosition().getFirstLine() - 1) + ")\n");
                //////////////////////NEED TO FIX LINE NUMBER HERE//////////////////////
        ArrayList<MethodSignature> methodsList = printCallsRecursive(methodSig, cg, writer, 1, new HashSet<>(), new ArrayList<MethodSignature>());
        
        ArrayList<MethodSignature> methodList2 = new ArrayList<>(methodsList);

        for (MethodSignature calleeSig : methodList2) {
            writer.write("<" 
                + sootClass.getType() + ": "
                + calleeSig.getName() + "()> (line " + (method.getBody().getPosition().getFirstLine() - 1) + ")\n");
            printCallsRecursive(calleeSig, cg, writer, 1, callers, methodsList);

            // for (CallGraph.Call call : cg.callsFrom(calleeSig)) {
            //     int line = call.getLineNumber();
            //     // System.out.println("  from <" 
            //     //         + callerSig.getDeclClassType() + ": " 
            //     //         + callerSig.getType() + " "
            //     //         + callerSig.getName() + "> (line " + line + ")\n");
            //     writer.write("  from <" 
            //             + calleeSig.getDeclClassType() + ": " 
            //             + calleeSig.getType() + " "
            //             + calleeSig.getName() + "> (line " + line + ")\n");
            // }
        }
        

        for (CallGraph.Call call : cg.callsTo(methodSig)) {
            MethodSignature callerSig = call.sourceMethodSignature();
            if (!methodsList.contains(callerSig)) {
                methodsList.add(callerSig);
            }
            int line = call.getLineNumber();
            // System.out.println("  from <" 
            //         + callerSig.getDeclClassType() + ": " 
            //         + callerSig.getType() + " "
            //         + callerSig.getName() + "> (line " + line + ")\n");
            writer.write("  from <" 
                    + callerSig.getDeclClassType() + ": " 
                    + callerSig.getType() + " "
                    + callerSig.getName() + "> (line " + line + ")\n");
        }
        // for (MethodSignature msig : methodList2) {
        //     // System.out.println("Method in CG: <"
        //     //         + msig.getDeclClassType() + ": "
        //     //         + msig.getType() + " "
        //     //         + msig.getName() + ">\n");
        //     Optional<JavaSootMethod> opt = sootClass.getMethod(msig.getSubSignature());
        //     if (opt.isEmpty()) {
        //         continue;
        //     }

        //     //writer2.write("Method in CG: <" + opt.getBodySource() + ">\n");
        // }

        writer.close();
        writer2.close();
    }



}
