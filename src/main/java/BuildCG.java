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
import java.nio.file.Paths;

// TODO BEFORE NEXT MEETING:
// Parse the input file (stack.java) directly to get body source 
// Use the Queue implementation as input for callgraph - Queue implementation already in Example to test folder 
// *********Remove the library calls from the call graph output (if start line is 0, don't print it)
// *********Correct accuracy of line numbers when there is a comment right under method definition
// Store the javadoc from the og source code file 
// Ayush should work on generating the whole callgraph for the project 
// Make a data structure that holds method name, caller list, callee list, source code, javadoc, start line, and end line.
// Check what line numbers to use, the call ones or the definition ones?
// UPDATE ON FRIDAY

// TODO: 
// Add comments to explain the code
// How is extractMethodCG handling duplicate methods names like overloads? (e.g., methodA(int a), methodA(String b)) - can we maybe include line numbers as input to identify which method needs it or maybe parameter types?
// Figure out why the methods are not being printed in order in the stack and how to fix it

// REQUIRED OUTPUTS:
// Caller source code
// Caller java doc 
// Callee source code 
// Callee java doc 
// Call graph without utility methods
// List of utility methods called

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

            String inFile = "CallGraphExample2.java";
            
            List<JavaSootClass> allClasses = view.getClasses().collect(Collectors.toList());

            int classCount = 1;
            
            List<MethodSignature> entryPoints = new ArrayList<>();
            int totalMethods = 0;
            
            for (JavaSootClass sootClass : allClasses) {
                String className = sootClass.getType().toString();
                                
                //System.out.println("\nClass: " + className);
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
                // FileWriter writerCHA = new FileWriter("Output_CallGraph_CHA.txt");
                // FileWriter writerRTA = new FileWriter("Output_CallGraph_RTA.txt");
                FileWriter writer = new FileWriter("Output_CallGraph.txt");
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
                            extractMethodCG(view, sootClass, method, method.getSignature(), cg, className, writer2);
                            //targetStack(methodSig, cg);
                        }

                        int defLine = -1; //CHECK HERE FOR LINE NUMBER ACCURACY
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
                    System.out.println("Method " + methodname + " not found in the project classes.");
                }

                CallGraphAlgorithm rta = 
                        new RapidTypeAnalysisAlgorithm(view);

                CallGraph cgrta = 
                        rta.initialize(entryPoints);

                // writerCHA.write(cg.toString());
                // writerRTA.write(cgrta.toString());
                // writerCHA.close();
                // writerRTA.close();
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

        FileWriter writer = new FileWriter("Output_Single_Method_CallGraph.txt");
    
        writer.write("<" 
                + sootClass.getType() + ": "
                + method.getName() + "()> (line " + (method.getBody().getPosition().getFirstLine() - 1) + ")\n");
        ArrayList<MethodSignature> methodsList = printCallsRecursive(methodSig, cg, writer, 1, new HashSet<>(), new ArrayList<MethodSignature>());
        
        ArrayList<MethodSignature> methodList2 = new ArrayList<>(methodsList);
        int startLineNumbers[] = new int[methodList2.size() + 1];
        for (MethodSignature calleeSig : methodList2) {
            Optional<JavaSootMethod> calleeOpt = view.getMethod(calleeSig);
            if (calleeOpt.isPresent()) {
                JavaSootMethod calleeMethod = calleeOpt.get();
                Body calleeBody = calleeMethod.getBody();
                int line = (calleeBody.getPosition().getFirstLine() - 1);
                writer.write("to <" 
                    + sootClass.getType() + ": "
                    + calleeSig.getName() + "()> (line " + line + ")\n");
                startLineNumbers[methodList2.indexOf(calleeSig) + 1] = line;
                printCallsRecursive(calleeSig, cg, writer, 1, callers, methodsList);
            }
        }
        
        for (CallGraph.Call call : cg.callsTo(methodSig)) {
            MethodSignature callerSig = call.sourceMethodSignature();
            int line = call.getLineNumber();
            // if (!methodList2.contains(callerSig)) {
            //     methodList2.add(callerSig);
            //     startLineNumbers[methodList2.indexOf(callerSig)] = line;
            // }
            writer.write("from <" 
                    + callerSig.getDeclClassType() + ": " 
                    + callerSig.getType() + " "
                    + callerSig.getName() + "> (line " + line + ")\n");
            printCallsRecursive(callerSig, cg, writer, 1, callers, methodsList);
        }
        writer.close();
        String [] methodsNames = new String[methodList2.size() + 1];
        int endLineNumbers[] = new int[methodList2.size() + 1];
        methodsNames[0] = method.getName();
        startLineNumbers[0] = method.getBody().getPosition().getFirstLine() - 1;
        endLineNumbers[0] = method.getBody().getPosition().getLastLine() ;
         
        for (int i = 0; i < methodList2.size(); i++) {
            MethodSignature msig = methodList2.get(i);
            methodsNames[i+1] = methodList2.get(i).getName();
            Optional<JavaSootMethod> calleeOpt = view.getMethod(msig);
            if (calleeOpt.isPresent()) {
                JavaSootMethod calleeMethod = calleeOpt.get();
                Body calleeBody = calleeMethod.getBody();
                endLineNumbers[i+1] = calleeBody.getPosition().getLastLine();
            }            
        }
        printMethodBody(System.getProperty("user.dir") + "\\CurrentAnalyzing\\CallGraphExample.java",
                methodsNames,
                startLineNumbers,
                endLineNumbers);
    }

    private static String[] printMethodBody(
        String fileName,
        String[] methodNames,
        int[] startLineNumbers,
        int[] endLineNumbers
    ) {
        String[] javadocs = new String[methodNames.length];
        java.util.Arrays.fill(javadocs, "");

        try {
            List<String> allLines = java.nio.file.Files.readAllLines(Paths.get(fileName));
            int lineCount = allLines.size();
            FileWriter writer = new FileWriter("Output_Single_Method_BodySourceCode.txt");

            Map<Integer, String> javadocForAll = new HashMap<>();

            int i = 0;
            while (i < lineCount) {
                String line = allLines.get(i).trim();

                if (line.startsWith("/**")) {
                    StringBuilder jd = new StringBuilder();
                    int j = i;
                    jd.append(allLines.get(j)).append("\n");
                    j++;

                    while (j < lineCount) {
                        String l = allLines.get(j);
                        jd.append(l).append("\n");
                        if (l.trim().endsWith("*/")) {
                            j++;
                            break;
                        }
                        j++;
                    }

                    int methodLineIndex = -1; 

                    while (j < lineCount) {
                        String sigLine = allLines.get(j).trim();

                        if (sigLine.isEmpty() || sigLine.startsWith("//")) {
                            j++;
                            continue;
                        }
                        if (sigLine.startsWith("@")) {
                            j++;
                            continue;
                        }

                        int parenIdx = sigLine.indexOf('(');
                        if (parenIdx != -1) {
                            methodLineIndex = j; 
                        }
                        break;  
                    }

                    if (methodLineIndex != -1) {
                        javadocForAll.put(methodLineIndex, jd.toString());
                    }

                    i = j; 
                } else {
                    i++;
                }
            }

            for (int idx = 0; idx < methodNames.length; idx++) {
                int start = startLineNumbers[idx];
                int end = endLineNumbers[idx];

                if (start < 1 || end <= start) {
                    continue;
                }

                if (start > lineCount) {
                    System.err.println("Start line " + start + " for method " +
                        methodNames[idx] + " is beyond file length " + lineCount);
                    continue;
                }
                if (end > lineCount) {
                    end = lineCount;
                }

                int startIndex0 = start - 1;

                String jd = javadocForAll.getOrDefault(startIndex0, "");
                javadocs[idx] = jd; 

                writer.write(methodNames[idx] + ":\n");

                for (int k = start - 1; k < end && k < lineCount; k++) {
                    writer.write(allLines.get(k) + "\n");
                }
                writer.write("\n");
            }

            writer.close();
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n\n\n\n*******************************************JAVADOCS for methods in file " + fileName + ":******************************************* \n");
        for (int k = 0; k < javadocs.length; k++) {
            System.out.println("Javadoc for method starting at line " + startLineNumbers[k]
                    + " (" + methodNames[k] + ")");
            if (javadocs[k] == null || javadocs[k].isEmpty()) {
                System.out.println("No javadoc found\n");
                continue;
            }
            System.out.println(javadocs[k]); 
        }

        return javadocs;
    }
}
