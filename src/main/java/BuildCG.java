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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.FileReader;


// TODO:
//Correct accuracy of line numbers when there is a comment right under method definition
//Make sure the method body is being extracted by their method source file path
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

            MethodInfo info = loadRowById_OpenCSV("2361");

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
            String methodname = "hashCode";
            int methodline = 158;
            String methodParaString = info.parameterString;
            String methodclassname = "org.apache.commons.lang3.AnnotationUtils"; //EXTRACT FROM CSV TOO

            System.out.println("Target method: " + methodname + "(" + methodParaString + ") in file: " + methodclassname);

            

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
                        if (method.hasBody() && method.getName().equals(methodname) && className.equals(methodclassname) && method.getBody().getPosition().getFirstLine() - 1 == methodline) { //&& method.getBody().getPosition().getFirstLine() - 1 == methodline
                            // String paramString = "";
                            // paramString = simplifyParameterList(method.getParameterTypes().toString());
                            // if (!method.getParameterTypes().toString().equals("[" + methodParaString + "]")) {
                            //     continue;
                            // }

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
        try (CSVReader reader = new CSVReader(new FileReader(csvPath))) {

            String[] header = reader.readNext(); // skip header

            String[] row;

            while ((row = reader.readNext()) != null) {

                // Expected columns:
                // 0 focal_method
                // 1 test_prefix
                // 2 docstring
                // 3 id
                // 4 file_path

                if (row.length < 5) continue;

                if (row[3].trim().equals(targetId)) {

                    CSVRowData data = new CSVRowData();
                    data.focalMethod = row[0];
                    data.testPrefix  = row[1];
                    data.docstring   = row[2];
                    data.id          = row[3];
                    data.filePath    = row[4];

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

        throw new RuntimeException("Row ID not found: " + targetId);
    }


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

        ArrayList < MethodSignature > methodList2 = new ArrayList < > (methodsList);
        int startLineNumbers[] = new int[methodList2.size() + 1];
        /************************************** */
        String[] fileNameStrings = new String[methodList2.size() + 1];
        /************************************** */
        for (MethodSignature calleeSig: methodList2) {
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
                startLineNumbers[methodList2.indexOf(calleeSig) + 1] = line;

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
        String[] methodsNames = new String[methodList2.size() + 1];
        int endLineNumbers[] = new int[methodList2.size() + 1];
        methodsNames[0] = method.getName();
        
        if (method.hasBody() && method.getBody().getPosition() != null) {
            startLineNumbers[0] = method.getBody().getPosition().getFirstLine() - 1; //////////////////////////////////////////////////////////////////// -1
            endLineNumbers[0] = method.getBody().getPosition().getLastLine();
            /************************************** */
            Path sourceFile = findSourceFile(methodSig, sourceRoot);
            fileNameStrings[0] = sourceFile.toString();
            /************************************** */
        }

        for (int i = 0; i < methodList2.size(); i++) {
            MethodSignature msig = methodList2.get(i);
            methodsNames[i + 1] = methodList2.get(i).getName();
            Optional < JavaSootMethod > calleeOpt = view.getMethod(msig);
            if (calleeOpt.isPresent()) {
                JavaSootMethod calleeMethod = calleeOpt.get();
                if (!calleeMethod.hasBody() || calleeMethod.getBody().getPosition() == null) {
                    continue;
                }
                Body calleeBody = calleeMethod.getBody();
                //startLineNumbers[i + 1] = calleeBody.getPosition().getFirstLine(); /////////////////////////////////////////// new line
                endLineNumbers[i + 1] = calleeBody.getPosition().getLastLine();
                /************************************** */
                Path sourceFile = findSourceFile(msig, sourceRoot);
                if (sourceFile == null) {
                    continue;
                }
                fileNameStrings[i + 1] = sourceFile.toString();
                /************************************** */
            }
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
        List<String> parameterTypes;

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

        return info;
    }

    public static List<String> extractParameterTypes(String paramString) {

        List<String> types = new ArrayList<>();

        if (paramString == null || paramString.isBlank()) return types;

        String[] params = paramString.split(",");

        for (String p : params) {

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

    public static List<String> simplifyParameterList(List<String> fqTypes) {

        List<String> simple = new ArrayList<>();

        for (String t : fqTypes) {
            simple.add(toSimpleTypeName(t));
        }

        return simple;
    }





}