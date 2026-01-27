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
import java.nio.file.Path;
import java.nio.file.Paths;

// TODO:
//Correct accuracy of line numbers when there is a comment right under method definition
//Make sure the method body is being extracted by their method source file path
//Try intputing methods from the meta.csv file 


public class BuildCG {
    //private static Map<MethodSignature, SootMethod> methodMap = new HashMap<>();
    //private static Set<MethodSignature> callers = new HashSet<>();

    public static void main(String[] args) {
        try {
            //given path
            // String projectPath;
            // if (args.length > 0) {
            //     projectPath = args[0];
            // } else {
            //     //current path
            //     //projectPath = System.getProperty("user.dir") + "target/classes"; // + "CurrentAnalyzing";
            // //}
            // projectPath = Paths.get(System.getProperty("user.dir"), "target", "classes")
            //            .toString();
            // }

            // System.out.println("Using classpath: " + projectPath);
            FileWriter writer2 = new FileWriter("single_method_bs.txt");


            // String projectPath = Paths.get(
            //         "commons-math-4.0-beta1-src",
            //         "commons-math-legacy-core",
            //         "target",
            //         "commons-math4-legacy-core-4.0-beta1.jar"
            //     ).toString();

            String projectPath = Paths.get(
                    System.getProperty("user.dir"),
                    "joda-time",
                    "target",
                    "classes"
            ).toString();

            System.out.println("Using classpath: " + projectPath);



            AnalysisInputLocation inputLocation =
                new JavaClassPathAnalysisInputLocation(projectPath);

            JavaView view = new JavaView(inputLocation);
            
            List<JavaSootClass> allClasses = view.getClasses().collect(Collectors.toList());

            int classCount = 1;
            
            List<MethodSignature> entryPoints = new ArrayList<>();
            int totalMethods = 0;
            
            for (JavaSootClass sootClass : allClasses) {
                String className = sootClass.getType().toString();
                                
                int methodCount = 0;
                for (SootMethod method : sootClass.getMethods()) {

                    // Entry-point filter
                    if (!method.hasBody()) continue;        // skip abstract / native
                    if (!method.isConcrete()) continue;       // only API-level methods

                    entryPoints.add(method.getSignature());
                }

            }

            CallGraphAlgorithm cha = new ClassHierarchyAnalysisAlgorithm(view); //ClassHierarchyAnalysisAlgorithm(view);
            CallGraph cg = cha.initialize(entryPoints);
            String methodname = "withFields";
            int methodline = 776;
            String methodclassname = "org.joda.time.LocalTime";

            //can we provide this information?

            SootMethod sootMethod = null;
            
            try {
                FileWriter writerCHA = new FileWriter("Output_CallGraph_CHA.txt");
                writerCHA.write(cg.toString());
                writerCHA.close();
                // FileWriter writerRTA = new FileWriter("Output_CallGraph_RTA.txt");
                FileWriter writer = new FileWriter("Output_CallGraph.txt");
                boolean contains = false;  

                ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                for (JavaSootClass sootClass : allClasses) {
                    String className = sootClass.getType().toString();


                    for (SootMethod method : sootClass.getMethods()) {
                        MethodSignature methodSig = method.getSignature();
                        if (method.hasBody() && method.getName().equals(methodname) && method.getBody().getPosition().getFirstLine() - 1 == methodline && className.equals(methodclassname)) {

                            contains = true;
                            
                            Optional<JavaSootMethod> optMethod = view.getMethod(methodSig);
                            if (!optMethod.isPresent()) {
                                System.out.println("Method not found.");
                                return;
                            }
                            sootMethod = optMethod.get();
                            Body methodBody = null;
                            if (method.hasBody()) {
                                methodBody = sootMethod.getBody();
                            }
                            writer2.write(methodBody.getMethodSignature().getName()); 
                            writer2.write(methodBody.toString()); 
                            extractMethodCG(view, sootClass, method, method.getSignature(), cg, className, writer2);
                        }

                        int defLine = -1; //CHECK HERE FOR LINE NUMBER ACCURACY
                        if (method.hasBody() && method.getBody().getPosition() != null) {
                            defLine = method.getBody().getPosition().getFirstLine() - 1;
                        }

                        if (defLine <= 0) {
                            continue;
                        }
                        if (!entryPoints.contains(methodSig)) continue;
                        writer.write("<" + className + ": " + method.getName() + "()> (line " + defLine + ")\n");

                        boolean hasToCalls = cg.callsFrom(methodSig).iterator().hasNext();
                        boolean hasFromCalls = cg.callsTo(methodSig).iterator().hasNext();

                        // print TO calls
                        if (hasToCalls) {
                            printCallsRecursive(view, methodSig, cg, writer, 1, new HashSet<>(), false, new ArrayList<>());
                        }

                        // print FROM calls
                        if (hasFromCalls) {
                            for (CallGraph.Call call : cg.callsTo(methodSig)) {
                                MethodSignature sig = call.sourceMethodSignature();
                                int line = call.getLineNumber();

                                writer.write("    from <"
                                    + sig.getDeclClassType() + ": "
                                    + sig.getType() + " "
                                    + sig.getName() + "> (line " + line + ")\n");
                            }
                        }

                        // print NO CALLS only if neither exists
                        if (!hasToCalls && !hasFromCalls) {
                            writer.write("    (no calls)\n");
                        }


                        // writer.write("<" + className + ": " + method.getName() + "()> (line " + defLine + ")\n");
                        // printCallsRecursive(methodSig, cg, writer, 1, new HashSet<>(), new ArrayList<>());

                        // // for (CallGraph.Call call : cg.callsFrom(methodSig)) {
                        // //     MethodSignature callee = call.targetMethodSignature();
                        // //     int line = call.getLineNumber();

                        // //     writer.write("    to <"
                        // //         + callee.getDeclClassType() + ": "
                        // //         + callee.getType() + " "
                        // //         + callee.getName()
                        // //         + "> (line " + line + ")\n");
                        // // }



                        // for (CallGraph.Call call : cg.callsTo(methodSig)) {
                        //     MethodSignature sig = call.sourceMethodSignature(); 

                        //     int line = call.getLineNumber();

                        //     writer.write("    from <"
                        //         + sig.getDeclClassType() + ": "
                        //         + sig.getType() + " "
                        //         + sig.getName() + ">" + " (line " + line + ")" );
                        //     writer.write("\n");
                        // }
                        
                    }
                }
                ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                if (!contains) {
                    System.out.println("Method " + methodname + " not found in the project classes.");
                }

                // CallGraphAlgorithm rta = 
                //         new RapidTypeAnalysisAlgorithm(view);

                // CallGraph cgrta = 
                //         rta.initialize(entryPoints);

                
                // writerRTA.write(cgrta.toString());
                
                // writerRTA.close();
                writer.close();
                writer2.close();
                System.out.println("\nSuccessfully wrote filtered call graph to Output_CallGraph.txt");
            } catch (IOException e) {
                System.out.println("An error occurred: " + e.getMessage());
            }
 
        } catch (Exception e) {
            System.err.println("Error building call graph:");
            e.printStackTrace();
        }   
    }
    
    private static ArrayList<MethodSignature> printCallsRecursive(
        JavaView view,
        MethodSignature methodSig,
        CallGraph cg,
        FileWriter writer,
        int depth,
        Set<MethodSignature> recursionStack, 
        Boolean single,
        ArrayList<MethodSignature> methodsList) throws IOException {

        // if (!isProjectMethod(methodSig)) {
        //     return methodsList;
        // } //remove library calls
            
        String indent = "    ".repeat(depth);
        //boolean isFirstLevel = (depth == 1);

        // Detect cycles
        if (recursionStack.contains(methodSig)) {
            writer.write(indent + "[cycle detected]\n");
            return methodsList;
        }

        recursionStack.add(methodSig);
        if (depth == 1) {
            for (CallGraph.Call call : cg.callsFrom(methodSig)) {
                methodsList.add(call.targetMethodSignature());
            }
        }


        // Explore callees
        for (CallGraph.Call call : cg.callsFrom(methodSig)) {
            //isFirstLevel = false;
            MethodSignature callee = call.targetMethodSignature();
            int line = -1;
            //skip library callees
            // if (!isProjectMethod(callee)) {
            //     continue;
            // }
            Optional<JavaSootMethod> calleeOpt = view.getMethod(callee);
            if (calleeOpt.isPresent() ) {
                JavaSootMethod calleeMethod = calleeOpt.get();
                if (!calleeMethod.hasBody() || calleeMethod.getBody().getPosition() == null) {
                    continue;
                }
                Body calleeBody = calleeMethod.getBody();
                line = (calleeBody.getPosition().getFirstLine() - 1);
            }
            
            
            boolean project = isProjectMethod(callee);
            //String tag = project ? "" : " [LIB]";

            // writer.write(indent + "to <"
            //         + callee.getDeclClassType() + ": "
            //         + callee.getType() + " "
            //         + callee.getName() + ">"
            //         + " (line " + line + ")\n");

            // if (!project) {
            //     continue; // don't recurse, but DO print it
            // }

            // String out = indent + "to <"
            //         + callee.getDeclClassType() + ": "
            //         + callee.getType() + " "
            //         + callee.getName() + "> (line " + line + ")\n";

            // writer.write(out);

            // // Recursive descent
            // printCallsRecursive(callee, cg, writer, depth + 1, recursionStack, methodsList);

            if (!single) {
            writer.write(indent + "to <"
                    + callee.getDeclClassType() + ": "
                    + callee.getType() + " "
                    + callee.getName() + "> (line " + line + ")\n");
            }  
            if (project) {
                printCallsRecursive(view, callee, cg, writer, depth + 1, recursionStack, single, methodsList);
            }

        }

        recursionStack.remove(methodSig);
        // if (isFirstLevel) {
        //     writer.write(indent + "(no calls)\n");
        // }
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

        if (method.hasBody() && method.getBody().getPosition() != null) {
        writer.write("<" 
                + sootClass.getType() + ": "
                + method.getName() + "()> (line " + (method.getBody().getPosition().getFirstLine() - 1) + ")\n");
        }
        ArrayList<MethodSignature> methodsList = printCallsRecursive(view, methodSig, cg, writer, 1, new HashSet<>(), true, new ArrayList<MethodSignature>());
        
        ArrayList<MethodSignature> methodList2 = new ArrayList<>(methodsList);
        int startLineNumbers[] = new int[methodList2.size() + 1];

        for (MethodSignature calleeSig : methodList2) {
            Optional<JavaSootMethod> calleeOpt = view.getMethod(calleeSig);
            if (calleeOpt.isPresent() ) {
                JavaSootMethod calleeMethod = calleeOpt.get();

                if (!calleeMethod.hasBody() || calleeMethod.getBody().getPosition() == null) {
                    continue;
                }
                Body calleeBody = calleeMethod.getBody();
                System.out.println("Callee method: " + calleeSig.getName() + ", start line: " + calleeBody.getPosition().getFirstLine());
                int line = (calleeBody.getPosition().getFirstLine() - 1);
                writer.write("to <" 
                    + calleeMethod.getDeclClassType() + ": " + calleeSig.getType() + " "
                    + calleeSig.getName() + "> (line " + line + ")\n");
//CHECK HERE
                startLineNumbers[methodList2.indexOf(calleeSig) + 1] = line;
                // int line = calleeBody.getPosition().getFirstLine();   // keep 1-based
                // writer.write("to <" 
                //     + sootClass.getType() + ": "
                //     + calleeSig.getName() + "()> (line " + (line - 1) + ")\n");

                // startLineNumbers[methodList2.indexOf(calleeSig) + 1] = line;

                //printCallsRecursive(calleeSig, cg, writer, 2, new HashSet<>(), methodsList); //CALLERS
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
            printCallsRecursive(view, callerSig, cg, writer, 1, new HashSet<>(), false, methodsList); //CALLERS
        }
        writer.close();
        String [] methodsNames = new String[methodList2.size() + 1];
        int endLineNumbers[] = new int[methodList2.size() + 1];
        methodsNames[0] = method.getName();
        if (method.hasBody() && method.getBody().getPosition() != null) {
            startLineNumbers[0] = method.getBody().getPosition().getFirstLine(); //////////////////////////////////////////////////////////////////// -1
            endLineNumbers[0] = method.getBody().getPosition().getLastLine() ;
        }
         
        for (int i = 0; i < methodList2.size(); i++) {
            MethodSignature msig = methodList2.get(i);
            methodsNames[i+1] = methodList2.get(i).getName();
            Optional<JavaSootMethod> calleeOpt = view.getMethod(msig);
            if (calleeOpt.isPresent()) {
                JavaSootMethod calleeMethod = calleeOpt.get();
                if (!calleeMethod.hasBody() || calleeMethod.getBody().getPosition() == null) {
                    continue;
                }
                Body calleeBody = calleeMethod.getBody();
                //startLineNumbers[i + 1] = calleeBody.getPosition().getFirstLine(); /////////////////////////////////////////// new line
                endLineNumbers[i+1] = calleeBody.getPosition().getLastLine();
            }            
        }

        //Path sourceRoot = Paths.get(System.getProperty("user.dir"), "CurrentAnalyzing");
        //Path sourceRoot = Paths.get(System.getProperty("user.dir"), "joda-time", "src", "main", "java", "org", "joda", "time");
        Path sourceRoot = Paths.get(System.getProperty("user.dir"), "joda-time", "src", "main", "java", "org", "joda", "time");
        Path sourceFile = findSourceFile(method, sourceRoot);

        //DO SOMETHING HERE WITH THE SOURCE FILE PATH

        // System.out.println("Source file for method " + method.getName() + ": " + sourceFile);
        // for (int i: endLineNumbers){
        //     System.out.println(i);
        // }
        // System.out.println("-----");
        // for (int i: startLineNumbers){
        //     System.out.println(i);
        // }
        printMethodBody(sourceFile.toString(),
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
            List<String> allLines =
            java.nio.file.Files.readAllLines(Paths.get(fileName));
            int lineCount = allLines.size();
            FileWriter writer =
                    new FileWriter("Output_Single_Method_BodySourceCode.txt");

            for (int i : startLineNumbers){
                System.out.println(i);
            }

            for (int idx = 0; idx < startLineNumbers.length; idx++) {
                startLineNumbers[idx] =
                    normalizeSignatureLine(startLineNumbers[idx], allLines);
            }


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

                int openBraces = 0;
                for (int k = start - 1; k < end && k < lineCount; k++) {
                    String s = allLines.get(k);
                    for (char c : s.toCharArray()) {
                        if (c == '{') openBraces++;
                        if (c == '}') openBraces--;
                    }
                }
                int k2 = end - 1;
                while (openBraces > 0 && k2 + 1 < lineCount) {
                    k2++;
                    String s = allLines.get(k2);
                    for (char c : s.toCharArray()) {
                        if (c == '{') openBraces++;
                        if (c == '}') openBraces--;
                    }
                    end = k2 + 1;   
                }

                // System.out.println("Extracting method " + methodNames[idx] +
                //     " from lines " + start + " to " + end);

                int startIndex0 = start - 1;

                String jd = javadocForAll.getOrDefault(startIndex0, "");
                javadocs[idx] = jd; 

                writer.write(methodNames[idx] + ":\n");

                for (int k3 = start - 1; k3 < end && k3 < lineCount; k3++) {
                    writer.write(allLines.get(k3) + "\n");
                }
                writer.write("\n");
            }

            writer.close();
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
        }
        try {
            FileWriter writer = new FileWriter("Output_Single_Method_Javadocs.txt");
        //System.out.println("\n\n\n\n*******************************************JAVADOCS for methods in file " + fileName + ":******************************************* \n");
        for (int k = 0; k < javadocs.length; k++) {
            writer.write("Javadoc for method starting at line " + startLineNumbers[k]
                    + " (" + methodNames[k] + ")");
            if (javadocs[k] == null || javadocs[k].isEmpty()) {
                writer.write("\n\tNo javadoc found\n");
                continue;
            }
            writer.write(javadocs[k]); 
        }
        writer.close();
    } catch (IOException e) {
        System.err.println("Error writing javadocs: " + e.getMessage());   
        e.printStackTrace();
    }
        return javadocs;
    }


    static Path findSourceFile(SootMethod method, Path sourceRoot) {
        String className = method.getDeclaringClassType().getClassName();

        String relativePath = className.replace('.', '/') + ".java";
        return sourceRoot.resolve(relativePath);
    }

    static int normalizeSignatureLine(int sootLine, List<String> lines) {
        int idx = Math.max(0, sootLine - 1);

        if (idx < lines.size() && looksLikeMethodHeader(lines.get(idx))) {
            return sootLine;
        }

        for (int back = 1; back <= 3 && idx - back >= 0; back++) {
            if (looksLikeMethodHeader(lines.get(idx - back))) {
                return sootLine - back;
            }
        }

        return sootLine;  
    }

    static boolean looksLikeMethodHeader(String s) {
        s = s.trim();

        if (s.isEmpty()) return false;

        if (s.startsWith("if") ||
            s.startsWith("for") ||
            s.startsWith("while") ||
            s.startsWith("switch") ||
            s.startsWith("catch")) return false;

        if (s.startsWith("@")) return false;

        if (!s.contains("(")) return false;


        if (s.startsWith("new ")) return false;


        return (s.matches(".*\\b(public|protected|private|static|final|native|synchronized)\\b.*")
            || s.matches(".*\\b(void|int|long|double|float|boolean|char|byte|short)\\b.*")
            || s.contains(".")); 
    }

    private static boolean isProjectMethod(MethodSignature sig) {
        String cls = sig.getDeclClassType().getClassName();
        return cls.startsWith("org.joda.time");
    }



}
