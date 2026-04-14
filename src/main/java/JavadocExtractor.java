import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import sootup.core.model.SootMethod;
import sootup.java.core.views.JavaView;

public class JavadocExtractor {

    public static String extractJavadoc(JavaView view, SootMethod method) {
        try {
            if (!method.hasBody() || method.getBody().getPosition() == null) {
                return "";
            }

            Path sourceFile = SourceFileLocator.findSourceFile(method.getSignature());
            if (sourceFile == null || !Files.exists(sourceFile)) {
                return "";
            }

            List<String> allLines = Files.readAllLines(sourceFile);
            int methodLine1 = method.getBody().getPosition().getFirstLine();
            int normalizedLine = SourceFileLocator.normalizeSignatureLine(methodLine1, allLines);
            int methodLine = Math.max(0, normalizedLine - 1);

            for (int i = methodLine; i >= 0; i--) {
                String line = allLines.get(i).trim();
                if (line.endsWith("*/")) {
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
                } else if (!line.isEmpty() && !line.startsWith("//") && !line.startsWith("@") && !SourceFileLocator.looksLikeMethodHeader(line)) {
                    break;
                }
            }

            return "";
        } catch (Exception e) {
            System.err.println("Error extracting javadoc: " + e.getMessage());
            return "";
        }
    }

    public static String extractJavadocForJson(SootMethod method) {
        try {
            if (!method.hasBody() || method.getBody().getPosition() == null) return "";

            Path sourceFile = SourceFileLocator.findSourceFile(method.getSignature());
            if (sourceFile == null || !Files.exists(sourceFile)) return "";

            List<String> allLines = Files.readAllLines(sourceFile);
            int lineCount = allLines.size();
            int start = SourceFileLocator.normalizeSignatureLine(method.getBody().getPosition().getFirstLine(), allLines) - 1;

            Map<Integer, String> javadocForAll = new HashMap<>();
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
                        if (sigLine.contains("(")) methodLineIndex = j;
                        break;
                    }
                    if (methodLineIndex != -1) javadocForAll.put(methodLineIndex, jd.toString());
                    i = j;
                } else {
                    i++;
                }
            }
            return javadocForAll.getOrDefault(start, "");
        } catch (Exception e) {
            System.err.println("Error extracting javadoc: " + e.getMessage());
            return "";
        }
    }

    public static String extractJavadocCached(JavaView view, SootMethod method, Map<String, String> cache) {
        String key = method.getSignature().toString();
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        String value = extractJavadoc(view, method);
        cache.put(key, value);
        return value;
    }
}
