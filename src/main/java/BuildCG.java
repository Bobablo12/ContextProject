import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaSootClass;
import sootup.java.core.views.JavaView;
import sootup.core.model.Body;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.callgraph.CallGraph;
import sootup.callgraph.CallGraphAlgorithm;
import sootup.callgraph.ClassHierarchyAnalysisAlgorithm;
import sootup.callgraph.RapidTypeAnalysisAlgorithm;
import sootup.java.core.JavaSootMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;

import java.io.FileWriter;
import java.io.IOException;

//TODO: 
// Add comments to explain the code
// How is extractMethodCG handling duplicate methods names like overloads? (e.g., methodA(int a), methodA(String b)) - can we maybe include line numbers as input to identify which method needs it or maybe parameter types?
// Correct accuracy of line numbers when there is a comment right under method definition
// Isolate the stack functionality into a separate class for better readability (PRIORITY!!!)
// Convert the single_method_bs.txt(Jimple) to Java




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
            FileWriter writer2 = new FileWriter("single_method_bs.txt");
            AnalysisInputLocation inputLocation = 
                new JavaClassPathAnalysisInputLocation(projectPath);

            JavaView view = new JavaView(inputLocation);
            
            List<JavaSootClass> allClasses = view.getClasses().collect(Collectors.toList());

            int classCount = 1;
            
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
            }

            CallGraphAlgorithm cha = new ClassHierarchyAnalysisAlgorithm(view);
            CallGraph cg = cha.initialize(entryPoints);
            String methodname = "methodA";
            SootMethod sootMethod = null;
            
            try {
                FileWriter writerCHA = new FileWriter("output_CHA.txt");
                FileWriter writerRTA = new FileWriter("output_RTA.txt");
                FileWriter writer = new FileWriter("output.txt");
                boolean contains = false;
                for (JavaSootClass sootClass : allClasses) {
                    String className = sootClass.getType().toString();

                    for (SootMethod method : sootClass.getMethods()) {
                        MethodSignature methodSig = method.getSignature();

                        if (method.getName().equals(methodname)) {
                            
                            contains = true;
                            
                            Optional<JavaSootMethod> optMethod = view.getMethod(methodSig);
                            if (!optMethod.isPresent()) {
                                System.out.println("Method not found.");
                                return;
                            }
                            sootMethod = optMethod.get();
                            Body methodBody = sootMethod.getBody();
                            writer2.write(methodBody.getMethodSignature().getName()); 
                            writer2.write(methodBody.toString()); 
                            extractMethodCG(view, sootClass, method, method.getSignature(), cg, className,
                                    writer2);
                        }

                        int defLine = -1;
                        if (method.hasBody() && method.getBody().getPosition() != null) {
                            defLine = method.getBody().getPosition().getFirstLine() - 1;
                        }
                        writer.write("<" + className + ": " + method.getName() + "()> (line " + defLine + ")\n");

                        printCallsRecursive(methodSig, cg, writer, 1, new HashSet<>(), new ArrayList<MethodSignature>());


                        for (CallGraph.Call call : cg.callsTo(methodSig)) {
                            MethodSignature sig = call.sourceMethodSignature(); 
                            int line = call.getLineNumber();
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

                CallGraphAlgorithm rta = 
                        new RapidTypeAnalysisAlgorithm(view);

                CallGraph cgrta = 
                        rta.initialize(entryPoints);

                writerCHA.write(cg.toString());
                writerRTA.write(cgrta.toString());
                writerCHA.close();
                writerRTA.close();
                writer.close();
                writer2.close();
                System.out.println("\nSuccessfully wrote filtered call graph to output.txt");
            } catch (IOException e) {
                System.out.println("An error occurred: " + e.getMessage());
            }
 
        } catch (Exception e) {
            System.err.println("Error building call graph:");
            e.printStackTrace();
        }
    }
    
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

        recursionStack.add(methodSig);
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
        JavaView view,
        JavaSootClass sootClass,
        SootMethod method,
        MethodSignature methodSig,
        CallGraph cg,
        String className, 
        FileWriter writer2) throws IOException {

        FileWriter writer = new FileWriter("single_method_cg.txt");
        

        writer.write("<" 
                + sootClass.getType() + ": "
                + method.getName() + "()> (line " + (method.getBody().getPosition().getFirstLine() - 1) + ")\n");
        ArrayList<MethodSignature> methodsList = printCallsRecursive(methodSig, cg, writer, 1, new HashSet<>(), new ArrayList<MethodSignature>());
        
        ArrayList<MethodSignature> methodList2 = new ArrayList<>(methodsList);

        for (MethodSignature calleeSig : methodList2) {
            Optional<JavaSootMethod> calleeOpt = view.getMethod(calleeSig);
            if (calleeOpt.isPresent()) {
                JavaSootMethod calleeMethod = calleeOpt.get();
                Body calleeBody = calleeMethod.getBody();
                writer2.write(calleeBody.getMethodSignature().getName()); 
                writer2.write(calleeBody.toString());
                            
                writer.write("to <" 
                    + sootClass.getType() + ": "
                    + calleeSig.getName() + "()> (line " + (calleeBody.getPosition().getFirstLine() - 1) + ")\n");
                printCallsRecursive(calleeSig, cg, writer, 1, callers, methodsList);
            }
        }
        
        for (CallGraph.Call call : cg.callsTo(methodSig)) {

            MethodSignature callerSig = call.sourceMethodSignature();

            if (!methodList2.contains(callerSig)) {
                methodsList.add(callerSig);

                Optional<JavaSootMethod> callerOpt = view.getMethod(callerSig);
                if (callerOpt.isPresent()) {
                    JavaSootMethod callerMethod = callerOpt.get();
                    Body callerBody = callerMethod.getBody(); 
                    writer2.write(callerBody.getMethodSignature().getName()); 
                    writer2.write(callerBody.toString());
                }
            }
            int line = call.getLineNumber();
            writer.write("from <" 
                    + callerSig.getDeclClassType() + ": " 
                    + callerSig.getType() + " "
                    + callerSig.getName() + "> (line " + line + ")\n");
            printCallsRecursive(callerSig, cg, writer, 1, callers, methodsList);
        }
        writer.close();
    }

}
