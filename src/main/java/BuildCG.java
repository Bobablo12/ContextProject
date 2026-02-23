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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;
import java.util.Scanner;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVParserBuilder;
import com.opencsv.exceptions.CsvValidationException;

import java.io.File;
import java.io.FileReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

// TODO:
//Correct accuracy of line numbers when there is a comment right under method definition
//Make sure the method body is being extracted by their method source file path
//correct printcalls recusive for depth traversal
//Try intputing methods from the meta.csv file 
//method name, line number, full qualified name, callers, callees, javadoc, parameters, library calls (the one with -1 as line numbers)

//PICK RANDOM FILES AND METHODS AND SEE IF THEY APPEAR ON THE CALLGRAPH 
//Have multiple inputs from the CSV file instead of hardcoding one ID (maybe an array of inputs)
//Inputs need to be array of ids and the project path 

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
            //     System.getProperty("user.dir"),
            //     "joda-time",
            //     "target",
            //     "classes"
            // ).toString();

            String projectPath = Paths.get(
                System.getProperty("user.dir"),
                "commons-lang3-3.12.0-src",
                "target",
                "classes"
            ).toString();
            //Scanner scanner = new Scanner(System.in);

            //System.out.print("Enter Method ID: ");
            //String targetId = scanner.nextLine();

            MethodInfo info = loadRowById_OpenCSV("14916");

            System.out.println("Using classpath: " + projectPath);

            AnalysisInputLocation inputLocation =
                new JavaClassPathAnalysisInputLocation(projectPath);

            JavaView view = new JavaView(inputLocation);

            List < JavaSootClass > allClasses = view.getClasses().collect(Collectors.toList());

            int classCount = 1;

            List < MethodSignature > entryPoints = new ArrayList < > ();
            int totalMethods = 0;

            for (JavaSootClass sootClass: allClasses) {
                String className = sootClass.getType().toString();

                int methodCount = 0;
                for (SootMethod method: sootClass.getMethods()) {

                    // Entry-point filter
                    if (!method.hasBody()) continue; // skip abstract / native
                    if (!method.isConcrete()) continue; // only API-level methods
                    if (isSpecialMethod(method.getSignature())) continue; // skip class & instance initializers

                    entryPoints.add(method.getSignature());
                }

            }

            CallGraphAlgorithm cha = new ClassHierarchyAnalysisAlgorithm(view); //ClassHierarchyAnalysisAlgorithm(view);
            CallGraph cg = cha.initialize(entryPoints);
            String methodname = info.methodName;
            //int methodline = 158;
            String methodParaString = info.parameterString;
            
            String methodclassname = info.className; //"org.apache.commons.lang3.AnnotationUtils"; //EXTRACT FROM CSV TOO
;
            System.out.println("Target method: " + methodname + "(" + methodParaString + ") in file: " + methodclassname);
            //System.out.println("Method line number from Soot: " + methodline);

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
                for (JavaSootClass sootClass: allClasses) {
                    String className = sootClass.getType().toString();

                    for (SootMethod method: sootClass.getMethods()) {
                        MethodSignature methodSig = method.getSignature();
                        if (method.hasBody() && method.getName().equals(methodname) && className.equals(methodclassname)) { //&& method.getBody().getPosition().getFirstLine() - 1 == methodline
                            String paramString = "";
                            //paramString = simplifyParameterList(method.getParameterTypes());
                            //System.out.println("Soot method parameter types: " + paramString);
                            if (!paramsMatch(method.getParameterTypes(), info.parameterTypes)) continue;

                            contains = true;

                            Optional < JavaSootMethod > optMethod = view.getMethod(methodSig);
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
                        writer.write("<" + method.getReturnType() + ": " + method.getDeclClassType() + " " + method.getName() + "(" + method.getParameterTypes() + ")> (line " + defLine + ")\n");

                        boolean hasToCalls = cg.callsFrom(methodSig).iterator().hasNext();
                        boolean hasFromCalls = cg.callsTo(methodSig).iterator().hasNext();

                        // print TO calls
                        if (hasToCalls) {
                            printCallsRecursive(view, methodSig, cg, writer, 1, new HashSet < > (), false, new ArrayList < > ());
                        }

                        // print FROM calls
                        if (hasFromCalls) {
                            for (CallGraph.Call call: cg.callsTo(methodSig)) {
                                MethodSignature sig = call.sourceMethodSignature();
                                int line = call.getLineNumber();
                                if (line <= 0) {
                                    continue;
                                }

                                writer.write("    from <" +
                                    sig.getDeclClassType() + ": " +
                                    sig.getType() + " " +
                                    sig.getName() + "> (line " + line + ")\n");
                            }
                        }

                        // print NO CALLS only if neither exists
                        if (!hasToCalls && !hasFromCalls) {
                            writer.write("    (no calls)\n");
                        }

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
    // ===== CSV Row Container =====
    static class CSVRowData {
        String id;
        String focalMethod;
        String testPrefix;
        String docstring;
        String filePath;
    }

    public static MethodInfo loadRowById_OpenCSV(String targetId)
    throws IOException, CsvValidationException {
        String csvPath = Paths.get(
            System.getProperty("user.dir"),
            "commons-lang3-3.12.0-src", "inputs_with_id.csv"
        ).toString();
        
        try (java.io.BufferedReader br = new java.io.BufferedReader(new FileReader(csvPath))) {
            String headerLine = br.readLine(); // skip header
            String line;
            StringBuilder currentRow = new StringBuilder();
            
            while ((line = br.readLine()) != null) {
                currentRow.append(line).append("\n");
                
                // Check if this line completes a full CSV row
                // A row is complete when quote count is even (all quotes matched)
                if (isCompleteCSVRow(currentRow.toString())) {
                    String[] row = parseCSVLine(currentRow.toString().trim());
                    currentRow = new StringBuilder();
                    
                    if (row.length < 5) {
                        continue;
                    }
                    
                    // ID is at position 3
                    if (row[3].trim().equals(targetId)) {
                        CSVRowData data = new CSVRowData();
                        data.focalMethod = row[0];
                        data.testPrefix = row[1];
                        data.docstring = row[2];
                        data.id = row[3];
                        data.filePath = row[4];

                        // ===== DEBUG PRINT ALL COLUMNS =====
                        System.out.println("\n===== CSV ROW FOUND =====");
                        System.out.println("ID: " + data.id);
                        System.out.println("\n--- FILE PATH ---\n" + data.filePath);
                        System.out.println("\n--- DOCSTRING ---\n" + data.docstring);
                        System.out.println("\n--- FOCAL METHOD ---\n" + data.focalMethod);
                        System.out.println("\n--- TEST PREFIX ---\n" + data.testPrefix);
                        System.out.println("=========================\n");

                        MethodInfo info = parseMethodAndPath(data.focalMethod, data.filePath);
                        return info;
                    }
                }
            }
        }

        throw new RuntimeException("Row ID not found: " + targetId);
    }
    
    // Check if accumulated text forms a complete CSV row (all quotes matched)
    private static boolean isCompleteCSVRow(String text) {
        int commaCount = 0;
        int quoteCount = 0;
        boolean inQuotes = false;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoteCount++;
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                commaCount++;
            }
        }
        
        // We need at least 4 commas for 5 fields, and even number of quotes
        return commaCount >= 4 && quoteCount % 2 == 0 && !inQuotes;
    }
    
    // Lenient CSV parser that handles malformed quotes gracefully
    private static String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
                currentField.append(c);
            } else if (c == ',' && !inQuotes) {
                // End of field
                String field = currentField.toString().trim();
                // Remove surrounding quotes if they exist
                if (field.startsWith("\"") && field.endsWith("\"")) {
                    field = field.substring(1, field.length() - 1);
                }
                fields.add(field);
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        // Add last field
        String field = currentField.toString().trim();
        if (field.startsWith("\"") && field.endsWith("\"")) {
            field = field.substring(1, field.length() - 1);
        }
        fields.add(field);
        
        return fields.toArray(new String[0]);
    }

    // private static ArrayList < MethodSignature > printCallsRecursive(
    //     JavaView view,
    //     MethodSignature methodSig,
    //     CallGraph cg,
    //     FileWriter writer,
    //     int depth,
    //     Set < MethodSignature > recursionStack,
    //     Boolean single,
    //     ArrayList < MethodSignature > methodsList) throws IOException {
        
    //     return printCallsRecursive(view, methodSig, cg, writer, depth, 1, recursionStack, single, methodsList);
    // }

    // private static ArrayList < MethodSignature > printCallsRecursive(
    //     JavaView view,
    //     MethodSignature methodSig,
    //     CallGraph cg,
    //     FileWriter writer,
    //     int depth,
    //     int maxDepth,
    //     Set < MethodSignature > recursionStack,
    //     Boolean single,
    //     ArrayList < MethodSignature > methodsList) throws IOException {

    //     // Stop recursing if max depth reached
    //     if (depth > maxDepth) {
    //         return methodsList;
    //     }

    //     String indent = "    ".repeat(depth);

    //     // Detect cycles
    //     if (recursionStack.contains(methodSig)) {
    //         writer.write(indent + "[cycle detected]\n");
    //         return methodsList;
    //     }

    //     recursionStack.add(methodSig);
    //     if (depth == 1) {
    //         for (CallGraph.Call call: cg.callsFrom(methodSig)) {
    //             methodsList.add(call.targetMethodSignature());
    //         }
    //     }

    //     // Explore callees only if we haven't reached max depth
    //     if (depth < maxDepth) {
    //         for (CallGraph.Call call: cg.callsFrom(methodSig)) {
    //             MethodSignature callee = call.targetMethodSignature();

    //             if (isSpecialMethod(callee)) continue;
    //             int line = -1;
    //             Optional < JavaSootMethod > calleeOpt = view.getMethod(callee);
    //             if (calleeOpt.isPresent()) {
    //                 JavaSootMethod calleeMethod = calleeOpt.get();
    //                 if (!calleeMethod.hasBody() || calleeMethod.getBody().getPosition() == null) {
    //                     continue;
    //                 }
    //                 Body calleeBody = calleeMethod.getBody();
    //                 line = (calleeBody.getPosition().getFirstLine() - 1);
    //             }

    //             boolean project = isProjectMethod(callee);

    //             if (!single && line != -1) {
    //                 writer.write(indent + "to <" +
    //                     callee.getDeclClassType() + ": " +
    //                     callee.getType() + " " +
    //                     callee.getName() + "> (line " + line + ")\n");
    //             }
    //             if (project) {
    //                 printCallsRecursive(view, callee, cg, writer, depth + 1, maxDepth, recursionStack, single, methodsList);
    //             }
    //         }
    //     }

    //     recursionStack.remove(methodSig);
    //     return methodsList;
    // }
    private static ArrayList < MethodSignature > printCallsRecursive(
        JavaView view,
        MethodSignature methodSig,
        CallGraph cg,
        FileWriter writer,
        int depth,
        Set < MethodSignature > recursionStack,
        Boolean single,
        ArrayList < MethodSignature > methodsList) throws IOException {

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
            for (CallGraph.Call call: cg.callsFrom(methodSig)) {
                if (isSpecialMethod(call.targetMethodSignature())) continue;
                //if (!isProjectMethod(call.targetMethodSignature())) continue; //skip library callees
                //if (methodsList.contains(call.targetMethodSignature())) continue; //avoid duplicates in first level
                methodsList.add(call.targetMethodSignature());
            }
        }

        // Explore callees
        for (CallGraph.Call call: cg.callsFrom(methodSig)) {
            //isFirstLevel = false;
            MethodSignature callee = call.targetMethodSignature();
            
            if (isSpecialMethod(callee)) continue;
            int line = -1;
            //skip library callees
            // if (!isProjectMethod(callee)) {
            //     continue;
            // }
            Optional < JavaSootMethod > calleeOpt = view.getMethod(callee);
            if (calleeOpt.isPresent()) {
                JavaSootMethod calleeMethod = calleeOpt.get();
                if (!calleeMethod.hasBody() || calleeMethod.getBody().getPosition() == null) {
                    continue;
                }
                Body calleeBody = calleeMethod.getBody();
                line = (calleeBody.getPosition().getFirstLine() - 1);
            }

            boolean project = isProjectMethod(callee);

            if (!single && line != -1) {
                writer.write(indent + "to <" +
                    callee.getDeclClassType() + ": " +
                    callee.getType() + " " +
                    callee.getName() + "> (line " + line + ")\n");
            }
            if (project) {
                printCallsRecursive(view, callee, cg, writer, depth + 1, recursionStack, single, methodsList);
            }
            // if (!project && single) {
            //     writer.write("to <" +
            //         callee.getDeclClassType() + ": " +
            //         callee.getType() + " " +
            //         callee.getName() + "\n");
            // }

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
            writer.write("<" +
                sootClass.getType() + ": " +
                method.getName() + "()> (line " + (method.getBody().getPosition().getFirstLine() - 1) + ")\n");
        }
        ArrayList < MethodSignature > methodsList = printCallsRecursive(view, methodSig, cg, writer, 1, new HashSet < > (), true, new ArrayList < MethodSignature > ());
        ArrayList < MethodSignature > methodList2 = new ArrayList < > ();
        for (MethodSignature msig: methodsList) {
            if (methodList2.contains(msig)) continue;
            methodList2.add(msig);
        }
        //ArrayList < MethodSignature > methodList2 = new ArrayList < > (methodsList);
        //int startLineNumbers[] = new int[methodList2.size() + 1];
        /************************************** */
        //String[] fileNameStrings = new String[methodList2.size() + 1];
        /************************************** */
        for (MethodSignature calleeSig: methodsList) {
            if (isSpecialMethod(calleeSig)) continue;

            Optional < JavaSootMethod > calleeOpt = view.getMethod(calleeSig);
            if (calleeOpt.isPresent()) {
                JavaSootMethod calleeMethod = calleeOpt.get();

                if (!calleeMethod.hasBody() || calleeMethod.getBody().getPosition() == null) {
                    continue;
                }
                Body calleeBody = calleeMethod.getBody();
                //System.out.println("Callee method: " + calleeSig.getName() + ", start line: " + calleeBody.getPosition().getFirstLine());
                int line = (calleeBody.getPosition().getFirstLine() - 1);
                writer.write("to <" +
                    calleeMethod.getDeclClassType() + ": " + calleeSig.getType() + " " +
                    calleeSig.getName() + "> (line " + line + ")\n");
                //CHECK HERE
                //startLineNumbers[methodList2.indexOf(calleeSig) + 1] = line;

                // int line = calleeBody.getPosition().getFirstLine();   // keep 1-based
                // writer.write("to <" 
                //     + sootClass.getType() + ": "
                //     + calleeSig.getName() + "()> (line " + (line - 1) + ")\n");

                // startLineNumbers[methodList2.indexOf(calleeSig) + 1] = line;

                //printCallsRecursive(calleeSig, cg, writer, 2, new HashSet<>(), methodsList); //CALLERS
            }
        }
        Path sourceRoot = Paths.get(
            System.getProperty("user.dir"),
            "commons-lang3-3.12.0-src",
            "src",
            "main",
            "java"
        );

        for (CallGraph.Call call: cg.callsTo(methodSig)) {

            MethodSignature callerSig = call.sourceMethodSignature();
            if (isSpecialMethod(callerSig)) continue;

            int line = call.getLineNumber();
            // if (!methodList2.contains(callerSig)) {
            //     methodList2.add(callerSig);
            //     startLineNumbers[methodList2.indexOf(callerSig)] = line;
            // } //DO SOMETHING HERE TO ADD CALLERS TOO
            writer.write("from <" +
                callerSig.getDeclClassType() + ": " +
                callerSig.getType() + " " +
                callerSig.getName() + "> (line " + line + ")\n");
            //printCallsRecursive(view, callerSig, cg, writer, 1, new HashSet<>(), false, methodsList); //CALLERS
        }
        
        writer.close();

        /* FINALIZE methodList2 FIRST */
        for (CallGraph.Call call : cg.callsTo(methodSig)) {

            MethodSignature callerSig = call.sourceMethodSignature();
            if (isSpecialMethod(callerSig)) continue;

            if (!methodList2.contains(callerSig)) {
                methodList2.add(callerSig);
            }
        }

        for (MethodSignature calleeSig : methodsList) {
            System.out.println("Callee in methodList2: " + calleeSig.getName());
        }


        /* NOW create arrays AFTER final methodList2 size is known */
        String[] methodsNames = new String[methodList2.size() + 1];
        int startLineNumbers[] = new int[methodList2.size() + 1];
        int endLineNumbers[] = new int[methodList2.size() + 1];
        String[] fileNameStrings = new String[methodList2.size() + 1];
        methodsNames[0] = method.getName();

        startLineNumbers[0] =
            method.getBody().getPosition().getFirstLine() - 1;

        endLineNumbers[0] =
            method.getBody().getPosition().getLastLine();

        Path src = findSourceFile(methodSig, sourceRoot);
        if (src != null)
            fileNameStrings[0] = src.toString();

        /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
         // Collect callers for JSON export
        List<SootMethod> callerMethods = new ArrayList<>();
        for (CallGraph.Call call : cg.callsTo(methodSig)) {
            MethodSignature callerSig = call.sourceMethodSignature();
            Optional<JavaSootMethod> callerOpt = view.getMethod(callerSig);
            if (callerOpt.isPresent()) {
                callerMethods.add(callerOpt.get());
            }
        }
        
        // Collect callees for JSON export
        List<SootMethod> calleeMethods = new ArrayList<>();
        for (MethodSignature calleeSig : methodList2) {
            Optional<JavaSootMethod> calleeOpt = view.getMethod(calleeSig);
            if (calleeOpt.isPresent()) {
                calleeMethods.add(calleeOpt.get());
            }
        }
        
        // Export to JSON
        String outputPath = "method_context_" + method.getName() + ".json";
        saveContextToJson(view, method, callerMethods, calleeMethods, outputPath);
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        methodsNames[0] = method.getName();

        if (method.hasBody() && method.getBody().getPosition() != null) {
            startLineNumbers[0] = method.getBody().getPosition().getFirstLine() - 1; //////////////////////////////////////////////////////////////////// -1
            endLineNumbers[0] = method.getBody().getPosition().getLastLine();
            /************************************** */
            Path sourceFile = findSourceFile(methodSig, sourceRoot);
            fileNameStrings[0] = sourceFile.toString();
            /************************************** */
        }


        // for (int i = 0; i < methodList2.size(); i++) {
        //     MethodSignature msig = methodList2.get(i);
        //     methodsNames[i + 1] = methodList2.get(i).getName();
        //     Optional < JavaSootMethod > calleeOpt = view.getMethod(msig);
        //     if (calleeOpt.isPresent()) {
        //         JavaSootMethod calleeMethod = calleeOpt.get();
        //         if (!calleeMethod.hasBody() || calleeMethod.getBody().getPosition() == null) {
        //             continue;
        //         }
        //         Body calleeBody = calleeMethod.getBody();
        //         //startLineNumbers[i + 1] = calleeBody.getPosition().getFirstLine(); /////////////////////////////////////////// new line
        //         endLineNumbers[i + 1] = calleeBody.getPosition().getLastLine();
        //         /************************************** */
        //         Path sourceFile = findSourceFile(msig, sourceRoot);
        //         if (sourceFile == null) {
        //             continue;
        //         }
        //         fileNameStrings[i + 1] = sourceFile.toString();
        //         /************************************** */
        //     }
            
        // }
        for (int i = 0; i < methodList2.size(); i++) {

            MethodSignature msig = methodList2.get(i);

            methodsNames[i + 1] = msig.getName();

            Optional<JavaSootMethod> opt = view.getMethod(msig);
            if (!opt.isPresent()) continue;

            JavaSootMethod m = opt.get();

            if (!m.hasBody() || m.getBody().getPosition() == null)
                continue;

            startLineNumbers[i + 1] =
                m.getBody().getPosition().getFirstLine() - 1;

            endLineNumbers[i + 1] =
                m.getBody().getPosition().getLastLine();

            Path file = findSourceFile(msig, sourceRoot);
            if (file != null)
                fileNameStrings[i + 1] = file.toString();
        }



        System.out.println("source root: " + sourceRoot.toString());
        printMethodBody(fileNameStrings,
            methodsNames,
            startLineNumbers,
            endLineNumbers);

    }

    private static String[] printMethodBody(
        String[] fileName,
        String[] methodNames,
        int[] startLineNumbers,
        int[] endLineNumbers
    ) {
        String[] javadocs = new String[methodNames.length];
        java.util.Arrays.fill(javadocs, "");

        try (
            FileWriter bodyWriter =
            new FileWriter("Output_Single_Method_BodySourceCode.txt"); FileWriter jdWriter =
            new FileWriter("Output_Single_Method_Javadocs.txt");
        ) {



            for (int idx = 0; idx < methodNames.length; idx++) {

                if (fileName[idx] == null) continue;

                Path file = Paths.get(fileName[idx]);
                if (!Files.exists(file)) {
                    System.err.println("Source file not found: " + file);
                    continue;
                }

                List < String > allLines = Files.readAllLines(file);
                int lineCount = allLines.size();

                // Normalize start line ONLY using this file
                startLineNumbers[idx] =
                    normalizeSignatureLine(startLineNumbers[idx], allLines);

                // ----------------- Build Javadoc map for THIS file -----------------
                Map < Integer, String > javadocForAll = new HashMap < > ();
                int i = 0;

                while (i < lineCount) {
                    String line = allLines.get(i).trim();

                    if (line.startsWith("/**")) {
                        StringBuilder jd = new StringBuilder();
                        int j = i;

                        while (j < lineCount) {
                            jd.append(allLines.get(j)).append("\n");
                            if (allLines.get(j).trim().endsWith("*/")) {
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
                            if (sigLine.contains("(")) {
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
                // -------------------------------------------------------------------
                
                int start = startLineNumbers[idx];
                int end = endLineNumbers[idx];

                if (start < 1 || start > lineCount || end <= start) continue;
                if (end > lineCount) end = lineCount;

                // -------- Expand method end using brace matching --------
                int openBraces = 0;
                for (int k = start - 1; k < end; k++) {
                    for (char c: allLines.get(k).toCharArray()) {
                        if (c == '{') openBraces++;
                        if (c == '}') openBraces--;
                    }
                }

                int k2 = end - 1;
                while (openBraces > 0 && k2 + 1 < lineCount) {
                    k2++;
                    for (char c: allLines.get(k2).toCharArray()) {
                        if (c == '{') openBraces++;
                        if (c == '}') openBraces--;
                    }
                    end = k2 + 1;
                }
                // --------------------------------------------------------

                int startIndex0 = start - 1;
                String jd = javadocForAll.getOrDefault(startIndex0, "");
                javadocs[idx] = jd;

                // ---------------- Write method body ----------------
                bodyWriter.write("\n===== FILE: " + file + " =====\n");
                bodyWriter.write(methodNames[idx] +
                    " (lines " + start + "-" + end + ")\n");

                for (int k = start - 1; k < end; k++) {
                    bodyWriter.write(allLines.get(k) + "\n");
                }
                bodyWriter.write("\n");

                // ---------------- Write Javadoc ----------------
                jdWriter.write("\n===== " + methodNames[idx] +
                    " @ " + file + ":" + start + " =====\n");

                if (jd.isEmpty()) {
                    jdWriter.write("No javadoc found\n");
                } else {
                    jdWriter.write(jd);
                }
                // System.out.println("Method Start (0-based): " + startIndex0);
                // System.out.println("Javadoc Keys: " + javadocForAll.keySet());

            }

        } catch (IOException e) {
            System.err.println("Error extracting method bodies or javadocs:");
            e.printStackTrace();
        }

        return javadocs;
    }

    // static Path findSourceFile(SootMethod method, Path sourceRoot) {
    //     String className = method.getDeclaringClassType().getClassName();

    //     String relativePath = className.replace('.', '/') + ".java";
    //     return sourceRoot.resolve(relativePath);
    // }
    static Path findSourceFile(MethodSignature sig, Path sourceRoot) {
        String className = sig.getDeclClassType().getClassName();

        // Extract simple class name safely
        String simpleName;
        int lastDot = className.lastIndexOf('.');

        if (lastDot == -1) {
            // No package (or synthetic name)
            simpleName = className + ".java";
        } else {
            simpleName = className.substring(lastDot + 1) + ".java";
        }

        // Extract package path safely
        Path searchRoot = sourceRoot;
        if (lastDot != -1) {
            String pkgPath =
                className.substring(0, lastDot).replace('.', '/');
            searchRoot = sourceRoot.resolve(pkgPath);
        }

        if (!Files.exists(searchRoot)) {
            return null;
        }

        try (var paths = Files.walk(searchRoot)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equals(simpleName))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            System.err.println("Error searching for source of " + className);
            e.printStackTrace();
            return null;
        }
    }

    static int normalizeSignatureLine(int sootLine, List < String > lines) {
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

        return (s.matches(".*\\b(public|protected|private|static|final|native|synchronized)\\b.*") ||
            s.matches(".*\\b(void|int|long|double|float|boolean|char|byte|short)\\b.*") ||
            s.contains("."));
    }

    private static boolean isProjectMethod(MethodSignature sig) {
        String cls = sig.getDeclClassType().getClassName();
        return cls.startsWith("org.apache.commons.lang3");
    }
    private static boolean isSpecialMethod(MethodSignature sig) {
        String name = sig.getName();
        return name.equals("<clinit>") || name.equals("<init>");
    }
    static class MethodInfo {
        String methodName;
        String parameterString;
        List < String > parameterTypes;
        String className;

        String originalFilePath;
        String parsedFilePath;
    }

    public static MethodInfo parseMethodAndPath(String methodSource, String filePath) {

        MethodInfo info = new MethodInfo();

        // ===============================
        // CLEAN METHOD SOURCE
        // ===============================
        String src = methodSource.replace("\n", " ")
            .replace("\r", " ")
            .trim();

        // ===============================
        // METHOD NAME + PARAM STRING
        // ===============================
        int openParen = src.indexOf("(");
        int closeParen = src.indexOf(")", openParen);

        if (openParen == -1 || closeParen == -1) {
            throw new RuntimeException("Invalid method source");
        }

        // Parameter String
        info.parameterString = src.substring(openParen + 1, closeParen).trim();

        // Method Name
        int nameEnd = openParen;
        int nameStart = nameEnd - 1;

        while (nameStart >= 0 && Character.isJavaIdentifierPart(src.charAt(nameStart))) {
            nameStart--;
        }

        info.methodName = src.substring(nameStart + 1, nameEnd);

        // ===============================
        // PARAMETER TYPE LIST
        // ===============================
        info.parameterTypes = extractParameterTypes(info.parameterString);

        // ===============================
        // FILE PATH PARSER
        // ===============================
        info.originalFilePath = filePath;

        String cleanPath = filePath;

        // Normalize slashes
        cleanPath = cleanPath.replace("\\", "/");

        // Remove ESTest
        cleanPath = cleanPath.replace("_ESTest.java", ".java");

        // Convert test → main
        cleanPath = cleanPath.replace("/test/java/", "/main/java/");

        info.parsedFilePath = cleanPath;
        info.className = extractClassNameFromCsvPath(filePath);

        return info;
    }

    public static List < String > extractParameterTypes(String paramString) {

        List < String > types = new ArrayList < > ();

        if (paramString == null || paramString.isBlank()) return types;

        String[] params = paramString.split(",");

        for (String p: params) {

            p = p.trim();
            p = p.replace("final ", "");

            String[] parts = p.split("\\s+");

            if (parts.length >= 1) {
                types.add(parts[0]);
            }
        }

        return types;
    }
    public static String toSimpleTypeName(String fqcn) {

        if (fqcn == null || fqcn.isEmpty()) return fqcn;

        // Remove generics if present
        fqcn = fqcn.replaceAll("<.*?>", "");

        // Handle arrays
        boolean isArray = fqcn.endsWith("[]");
        if (isArray) {
            fqcn = fqcn.substring(0, fqcn.length() - 2);
        }

        // Remove package
        int lastDot = fqcn.lastIndexOf('.');
        String simple = (lastDot >= 0) ? fqcn.substring(lastDot + 1) : fqcn;

        return isArray ? simple + "[]" : simple;
    }

    private static boolean paramsMatch(List < ? > sootParamTypes, List < String > csvParamTypes) {

        List < String > sootNorm = normalizeSootParamTypes(sootParamTypes);
        List < String > csvNorm = normalizeCsvParamTypes(csvParamTypes);

        if (sootNorm.size() != csvNorm.size()) return false;

        for (int i = 0; i < sootNorm.size(); i++) {
            if (!sootNorm.get(i).equals(csvNorm.get(i))) return false;
        }
        return true;
    }

    private static List < String > normalizeSootParamTypes(List < ? > sootParamTypes) {

        List < String > out = new ArrayList < > ();

        for (Object t: sootParamTypes) {
            // SootUp Type objects usually have a stable toString() (often fully-qualified)
            String s = (t == null) ? "" : t.toString();

            // Normalize varargs if Soot prints it differently (rare, but safe)
            s = s.replace("...", "[]");

            // Drop generics if any appear
            s = s.replaceAll("<.*?>", "");

            // Convert to simple name while preserving arrays
            out.add(toSimpleTypeName(s));
        }
        System.out.println("Normalized Soot parameter types: " + out);
        return out;
    }

    private static List < String > normalizeCsvParamTypes(List < String > csvParamTypes) {

        List < String > out = new ArrayList < > ();

        for (String t: csvParamTypes) {
            if (t == null) t = "";

            String s = t.trim();

            // Convert Java varargs to array form to match typical bytecode representation
            s = s.replace("...", "[]");

            // Drop generics
            s = s.replaceAll("<.*?>", "");

            // If CSV includes annotations like @Nonnull String, strip leading annotations
            s = s.replaceAll("^@\\w+\\s+", "");

            out.add(toSimpleTypeName(s));
        }
        System.out.println("Normalized CSV parameter types: " + out);
        return out;
    }
    public static String extractClassNameFromCsvPath(String csvFilePath) {

        if (csvFilePath == null || csvFilePath.isBlank()) {
            return "";
        }

        // Normalize slashes
        String path = csvFilePath.replace("\\", "/");

        // Remove test suffix like _ESTest.java
        path = path.replace("_ESTest.java", ".java");

        // Remove .java extension
        if (path.endsWith(".java")) {
            path = path.substring(0, path.length() - 5);
        }

        /*
           Find start of package path.
           Works for both:
           src/main/java/
           src/test/java/
        */
        int idx = path.indexOf("java/");
        if (idx == -1) {
            // fallback: just try last package-like segment
            int srcIdx = path.indexOf("src/");
            if (srcIdx != -1) {
                path = path.substring(srcIdx + 4);
            }
        } else {
            path = path.substring(idx + 5); // skip "java/"
        }

        // Convert folder path → package name
        String fqcn = path.replace("/", ".");

        return fqcn;
    }
     private static void saveContextToJson(
            JavaView view,
            SootMethod mutMethod,
            List<SootMethod> callers,
            List<SootMethod> callees,
            String outputPath) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode contextJson = mapper.createObjectNode();

            // 1. MUT Section
            ObjectNode mutNode = mapper.createObjectNode();
            mutNode.put("method_name", mutMethod.getName());
            mutNode.put("signature", formatSignature(mutMethod.getSignature().toString()));
            mutNode.put("qualified_name", mutMethod.getDeclaringClassType().getClassName() + "." + mutMethod.getName());
            
            // Line number
            if (mutMethod.hasBody() && mutMethod.getBody().getPosition() != null) {
                mutNode.put("line_number", mutMethod.getBody().getPosition().getFirstLine() - 1);
            } else {
                mutNode.put("line_number", -1);
            }
            
            // Parameters
            ArrayNode paramsArray = mapper.createArrayNode();
            for (int i = 0; i < mutMethod.getParameterCount(); i++) {
                paramsArray.add(mutMethod.getParameterType(i).toString());
            }
            mutNode.set("parameters", paramsArray);
            
            // Code
            mutNode.put("code", mutMethod.getBody() != null ? mutMethod.getBody().toString() : "");
            
            // Javadoc
            String javadoc = extractJavadoc(view, mutMethod);
            mutNode.put("javadoc", javadoc);

            contextJson.set("MUT", mutNode);

            // 2. Callers Array
            ArrayNode callersArray = mapper.createArrayNode();
            for (SootMethod caller : callers) {
                ObjectNode callerNode = mapper.createObjectNode();
                callerNode.put("method_name", caller.getName());
                callerNode.put("signature", formatSignature(caller.getSignature().toString()));
                callerNode.put("qualified_name", caller.getDeclaringClassType().getClassName() + "." + caller.getName());
                
                // Line number
                if (caller.hasBody() && caller.getBody().getPosition() != null) {
                    callerNode.put("line_number", caller.getBody().getPosition().getFirstLine() - 1);
                } else {
                    callerNode.put("line_number", -1);
                }
                
                // Parameters
                ArrayNode callerParamsArray = mapper.createArrayNode();
                for (int i = 0; i < caller.getParameterCount(); i++) {
                    callerParamsArray.add(caller.getParameterType(i).toString());
                }
                callerNode.set("parameters", callerParamsArray);
                
                callerNode.put("code", caller.getBody() != null ? caller.getBody().toString() : "");
                String callerJavadoc = extractJavadoc(view, caller);
                callerNode.put("javadoc", callerJavadoc);
                callerNode.put("edge", formatEdge(caller, mutMethod));
                callersArray.add(callerNode);
            }
            contextJson.set("callers", callersArray);

            // 3. Callees Array
            ArrayNode calleesArray = mapper.createArrayNode();
            for (SootMethod callee : callees) {
                ObjectNode calleeNode = mapper.createObjectNode();
                calleeNode.put("method_name", callee.getName());
                calleeNode.put("signature", formatSignature(callee.getSignature().toString()));
                calleeNode.put("qualified_name", callee.getDeclaringClassType().getClassName() + "." + callee.getName());
                
                // Line number
                if (callee.hasBody() && callee.getBody().getPosition() != null) {
                    calleeNode.put("line_number", callee.getBody().getPosition().getFirstLine() - 1);
                } else {
                    calleeNode.put("line_number", -1);
                }
                
                // Parameters
                ArrayNode calleeParamsArray = mapper.createArrayNode();
                for (int i = 0; i < callee.getParameterCount(); i++) {
                    calleeParamsArray.add(callee.getParameterType(i).toString());
                }
                calleeNode.set("parameters", calleeParamsArray);
                
                calleeNode.put("code", callee.getBody() != null ? callee.getBody().toString() : "");
                String calleeJavadoc = extractJavadoc(view, callee);
                calleeNode.put("javadoc", calleeJavadoc);
                calleeNode.put("edge", formatEdge(mutMethod, callee));
                calleesArray.add(calleeNode);
            }
            contextJson.set("callees", calleesArray);

            // 4. Metadata
            ObjectNode metadata = mapper.createObjectNode();
            metadata.put("call_graph_tool", "SootUp");
            metadata.put("analysis_depth", 1);
            contextJson.set("metadata", metadata);

            // 5. Save to file
            File outputFile = new File(outputPath);
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, contextJson);

            System.out.println("\nJSON output saved to: " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("Error saving JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String extractJavadoc(JavaView view, SootMethod method) {
        try {
            if (!method.hasBody() || method.getBody().getPosition() == null) {
                return "";
            }
            
            MethodSignature sig = method.getSignature();
            Path sourceRoot = Paths.get(System.getProperty("user.dir"), "commons-lang3-3.12.0-src", "target", "classes");
            Path sourceFile = findSourceFile(sig, sourceRoot);
            
            if (sourceFile == null || !Files.exists(sourceFile)) {
                return "";
            }
            
            List<String> allLines = Files.readAllLines(sourceFile);
            int methodLine1 = method.getBody().getPosition().getFirstLine();
            int normalizedLine = normalizeSignatureLine(methodLine1, allLines);
            int methodLine = Math.max(0, normalizedLine - 1);
            
            // Search backwards from method line for javadoc
            for (int i = methodLine; i >= 0; i--) {
                String line = allLines.get(i).trim();
                if (line.endsWith("*/")) {
                    // Found end of javadoc, now find the start
                    int start = i;
                    while (start >= 0 && !allLines.get(start).trim().startsWith("/**")) {
                        start--;
                    }
                    if (start >= 0) {
                        StringBuilder jd = new StringBuilder();
                        for (int j = start; j <= i; j++) {
                            jd.append(allLines.get(j)).append("\n");
                        }
                        return jd.toString();
                    }
                } else if (!line.isEmpty()
                        && !line.startsWith("//")
                        && !line.startsWith("@")
                        && !looksLikeMethodHeader(line)) {
                    
                    break;
                }
            }
            
            return "";
        } catch (Exception e) {
            System.err.println("Error extracting javadoc: " + e.getMessage());
            return "";
        }
    }

    private static String formatSignature(String sootSignature) {
        // Convert: <stackdemo.StackServiceA: void push(java.lang.String)>
        // To: stackdemo.StackServiceA.push(String)
        return sootSignature
                .replace("<", "")
                .replace(">", "")
                .replace(": void ", ".")
                .replace(": ", ".")
                .replace("java.lang.", "")
                .replace("(java.lang.String)", "(String)")
                .replace("(int,int)", "(int, int)");
    }

    private static String formatEdge(SootMethod from, SootMethod to) {
        String fromName = from.getSignature().toString()
                .replace("<", "").replace(">", "")
                .split(": ")[1].split("\\(")[0];
        String toName = to.getSignature().toString()
                .replace("<", "").replace(">", "")
                .split(": ")[1].split("\\(")[0];

        String fromClass = from.getDeclaringClassType().getClassName();
        String toClass = to.getDeclaringClassType().getClassName();

        return fromClass + "." + fromName + " -> " + toClass + "." + toName;
    }

}



