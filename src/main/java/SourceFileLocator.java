import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;

public class SourceFileLocator {

    private static final Map<String, String> inferredPathCache = new HashMap<>();

    public static Path findSourceFile(MethodSignature sig, Path sourceRoot) {
        String className = sig.getDeclClassType().getClassName();

        int lastDot = className.lastIndexOf('.');
        int innerSep = className.indexOf('$', lastDot + 1);
        String sourceClassName = (innerSep >= 0) ? className.substring(0, innerSep) : className;

        String simpleName;
        if (lastDot == -1) {
            simpleName = sourceClassName + ".java";
        } else {
            simpleName = sourceClassName.substring(lastDot + 1) + ".java";
        }

        Path searchRoot = sourceRoot;
        if (lastDot != -1) {
            String pkgPath = sourceClassName.substring(0, lastDot).replace('.', '/');
            searchRoot = sourceRoot.resolve(pkgPath);
        }

        if (!Files.exists(searchRoot)) return null;

        try (var paths = Files.walk(searchRoot)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equals(simpleName))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            System.err.println("Error searching for source of " + className);
            return null;
        }
    }

    public static Path findSourceFile(MethodSignature sig) {
        List<Path> sourceRoots = collectSourceRoots(ProjectConfig.ACTIVE_PROJECT_ROOT);
        for (Path root : sourceRoots) {
            Path candidate = findSourceFile(sig, root);
            if (candidate != null && Files.exists(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    public static int normalizeSignatureLine(int sootLine, List<String> lines) {
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

    public static int computeMethodEndLine(Path sourceFile, int startLine) {
        try {
            List<String> allLines = Files.readAllLines(sourceFile);
            int n = allLines.size();
            int idx = Math.max(0, startLine - 1);

            int braceLine = idx;
            while (braceLine < n && !allLines.get(braceLine).contains("{")) {
                braceLine++;
            }
            if (braceLine >= n) return Math.max(1, startLine);

            int depth = 0;
            for (int i = braceLine; i < n; i++) {
                String line = allLines.get(i);
                for (int j = 0; j < line.length(); j++) {
                    char c = line.charAt(j);
                    if (c == '{') depth++;
                    if (c == '}') {
                        depth--;
                        if (depth == 0) return i + 1;
                    }
                }
            }
            return n;
        } catch (IOException e) {
            return Math.max(1, startLine);
        }
    }

    public static boolean looksLikeMethodHeader(String s) {
        s = s.trim();
        if (s.isEmpty()) return false;
        if (s.startsWith("if") || s.startsWith("for") || s.startsWith("while") || s.startsWith("switch") || s.startsWith("catch")) return false;
        if (s.startsWith("@")) return false;
        if (!s.contains("(")) return false;
        if (s.startsWith("new ")) return false;
        return s.matches(".*\\b(public|protected|private|static|final|native|synchronized)\\b.*") ||
            s.matches(".*\\b(void|int|long|double|float|boolean|char|byte|short)\\b.*") ||
            s.contains(".");
    }

    public static String getMethodSourceCode(SootMethod method) {
        try {
            if (!method.hasBody() || method.getBody().getPosition() == null) return "";

            Path sourceFile = findSourceFile(method.getSignature());
            if (sourceFile == null || !Files.exists(sourceFile)) return "";

            List<String> allLines = Files.readAllLines(sourceFile);
            int lineCount = allLines.size();
            int startLine = getAccurateMethodStartLine(method);
            int start = Math.max(0, startLine - 1);
            int end = computeMethodEndLine(sourceFile, startLine);
            if (end < startLine) end = startLine + 1;
            if (end > lineCount) end = lineCount;

            StringBuilder code = new StringBuilder();
            for (int k = start; k < end; k++) code.append(allLines.get(k)).append("\n");
            return code.toString();
        } catch (Exception e) {
            System.err.println("Error extracting source code: " + e.getMessage());
            return "";
        }
    }

    public static int getAccurateMethodStartLine(SootMethod method) {
        if (method == null || !method.hasBody() || method.getBody().getPosition() == null) {
            return -1;
        }

        int sootLine = method.getBody().getPosition().getFirstLine();
        try {
            Path sourceFile = findSourceFile(method.getSignature());
            if (sourceFile == null || !Files.exists(sourceFile)) {
                return sootLine;
            }
            List<String> allLines = Files.readAllLines(sourceFile);
            int normalized = normalizeSignatureLine(sootLine, allLines);
            return refineMethodStartLine(allLines, normalized, method.getName(), method.getParameterCount());
        } catch (Exception e) {
            return sootLine;
        }
    }

    public static int refineMethodStartLine(List<String> lines, int candidateLine, String methodName, int expectedParamCount) {
        if (lines == null || lines.isEmpty()) return candidateLine;
        int idx = Math.max(0, Math.min(lines.size() - 1, candidateLine - 1));

        if (isMatchingMethodHeader(lines.get(idx), methodName, expectedParamCount)) {
            return idx + 1;
        }

        int lower = Math.max(0, idx - 120);
        for (int i = idx; i >= lower; i--) {
            if (isMatchingMethodHeader(lines.get(i), methodName, expectedParamCount)) {
                return i + 1;
            }
        }

        return Math.max(1, candidateLine);
    }

    public static boolean isMatchingMethodHeader(String line, String methodName, int expectedParamCount) {
        if (line == null || methodName == null) return false;
        if (!looksLikeMethodHeader(line) || !line.contains(methodName + "(")) return false;
        if (expectedParamCount < 0) return true;
        int actualParamCount = countParametersInHeader(line);
        return actualParamCount < 0 || actualParamCount == expectedParamCount;
    }

    public static int countParametersInHeader(String line) {
        int open = line.indexOf('(');
        int close = line.indexOf(')', open + 1);
        if (open < 0 || close < 0 || close <= open) return -1;

        String inside = line.substring(open + 1, close).trim();
        if (inside.isEmpty()) return 0;

        int count = 1;
        int depthAngles = 0;
        for (int i = 0; i < inside.length(); i++) {
            char c = inside.charAt(i);
            if (c == '<') depthAngles++;
            else if (c == '>') depthAngles = Math.max(0, depthAngles - 1);
            else if (c == ',' && depthAngles == 0) count++;
        }
        return count;
    }

    public static boolean isProjectMethod(MethodSignature sig) {
        Path source = findSourceFile(sig);
        return source != null && Files.exists(source);
    }

    public static boolean isSpecialMethod(MethodSignature sig) {
        String name = sig.getName();
        return name.equals("<clinit>") || name.equals("<init>");
    }

    public static String inferFilePathFromMethodSource(String focalMethod) {
        if (focalMethod == null || focalMethod.isBlank()) {
            return "";
        }

        String normalizedSnippet = normalizeWhitespace(focalMethod);
        if (inferredPathCache.containsKey(normalizedSnippet)) {
            return inferredPathCache.get(normalizedSnippet);
        }

        String signatureLine = extractSignatureLine(focalMethod);
        if (signatureLine.isBlank()) {
            inferredPathCache.put(normalizedSnippet, "");
            return "";
        }

        String normalizedSignature = normalizeWhitespace(signatureLine);
        List<Path> roots = collectSourceRoots(ProjectConfig.ACTIVE_PROJECT_ROOT);

        for (Path root : roots) {
            try (var paths = Files.walk(root)) {
                Path matched = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsNormalizedSignature(path, normalizedSignature))
                    .findFirst()
                    .orElse(null);
                if (matched != null) {
                    String result = matched.toString();
                    inferredPathCache.put(normalizedSnippet, result);
                    return result;
                }
            } catch (IOException ignored) {
            }
        }

        inferredPathCache.put(normalizedSnippet, "");
        return "";
    }

    private static boolean containsNormalizedSignature(Path sourceFile, String normalizedSignature) {
        try {
            String content = Files.readString(sourceFile);
            return normalizeWhitespace(content).contains(normalizedSignature);
        } catch (IOException e) {
            return false;
        }
    }

    private static String extractSignatureLine(String focalMethod) {
        String[] lines = focalMethod.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    private static String normalizeWhitespace(String input) {
        return input == null ? "" : input.replaceAll("\\s+", " ").trim();
    }

    private static List<Path> collectSourceRoots(Path projectRoot) {
        List<Path> roots = new ArrayList<>();

        Path directMain = projectRoot.resolve("src").resolve("main").resolve("java");
        Path directTest = projectRoot.resolve("src").resolve("test").resolve("java");
        if (Files.isDirectory(directMain)) roots.add(directMain);
        if (Files.isDirectory(directTest)) roots.add(directTest);

        try (var stream = Files.walk(projectRoot, 8)) {
            stream
                .filter(Files::isDirectory)
                .filter(path -> path.getFileName().toString().equals("java"))
                .filter(path -> path.getParent() != null
                    && (path.getParent().getFileName().toString().equals("main")
                        || path.getParent().getFileName().toString().equals("test")))
                .filter(path -> path.getParent().getParent() != null
                    && path.getParent().getParent().getFileName().toString().equals("src"))
                .forEach(path -> {
                    if (!roots.contains(path)) {
                        roots.add(path);
                    }
                });
        } catch (IOException ignored) {
        }

        return roots;
    }

    public static Path resolveProjectRoot(String projectArg) {
        Path userDir = Paths.get(System.getProperty("user.dir"));
        if (projectArg == null || projectArg.isBlank()) {
            return userDir;
        }

        Path argPath = Paths.get(projectArg);
        Path candidate = argPath.isAbsolute() ? argPath.normalize() : userDir.resolve(projectArg).normalize();
        Path nested = candidate.resolve(projectArg);
        Path nestedMain = nested.resolve("src").resolve("main").resolve("java");
        Path candidateMain = candidate.resolve("src").resolve("main").resolve("java");

        if (Files.isDirectory(nestedMain)) {
            return nested;
        }
        if (Files.isDirectory(candidateMain)) {
            return candidate;
        }

        if (Files.isDirectory(candidate)) {
            return candidate;
        }

        System.err.println("Warning: project path does not exist or has no Java sources: " + candidate);
        return candidate;
    }
}
