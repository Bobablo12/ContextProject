import java.util.ArrayList;
import java.util.List;

public class MethodInfo {
    public String methodName;
    public String sootMethodName;
    public String parameterString;
    public List<String> parameterTypes;
    public String className;
    public String originalFilePath;
    public String parsedFilePath;

    public static MethodInfo parseMethodAndPath(String methodSource, String filePath) {
        MethodInfo info = new MethodInfo();
        String src = methodSource.replace("\n", " ").replace("\r", " ").trim();
        int openParen = src.indexOf("(");
        int closeParen = src.indexOf(")", openParen);

        if (openParen == -1 || closeParen == -1) {
            throw new RuntimeException("Invalid method source");
        }
        info.parameterString = src.substring(openParen + 1, closeParen).trim();

        int nameEnd = openParen;
        int nameStart = nameEnd - 1;
        while (nameStart >= 0 && Character.isJavaIdentifierPart(src.charAt(nameStart))) {
            nameStart--;
        }
        info.methodName = src.substring(nameStart + 1, nameEnd);
        info.parameterTypes = extractParameterTypes(info.parameterString);

        info.originalFilePath = filePath;
        String cleanPath = filePath.replace("\\", "/")
            .replace("_ESTest.java", ".java")
            .replace("/test/java/", "/main/java/");
        info.parsedFilePath = cleanPath;
        info.className = extractClassNameFromCsvPath(filePath);
        info.sootMethodName = isConstructorLikeMethodName(info.methodName, info.className) ? "<init>" : info.methodName;
        return info;
    }

    public static List<String> extractParameterTypes(String paramString) {
        List<String> types = new ArrayList<>();
        if (paramString == null || paramString.isBlank()) return types;

        String[] params = paramString.split(",");
        for (String p : params) {
            p = p.trim().replace("final ", "");
            String[] parts = p.split("\\s+");
            if (parts.length >= 1) types.add(parts[0]);
        }
        return types;
    }

    public static String toSimpleTypeName(String fqcn) {
        if (fqcn == null || fqcn.isEmpty()) return fqcn;
        fqcn = fqcn.replaceAll("<.*?>", "");
        boolean isArray = fqcn.endsWith("[]");
        if (isArray) fqcn = fqcn.substring(0, fqcn.length() - 2);
        int lastDot = fqcn.lastIndexOf('.');
        String simple = (lastDot >= 0) ? fqcn.substring(lastDot + 1) : fqcn;
        return isArray ? simple + "[]" : simple;
    }

    private static boolean isConstructorLikeMethodName(String methodName, String className) {
        if (methodName == null || methodName.isBlank() || className == null || className.isBlank()) {
            return false;
        }
        if ("<init>".equals(methodName)) {
            return true;
        }

        String simpleClass = className;
        int lastDot = simpleClass.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < simpleClass.length()) {
            simpleClass = simpleClass.substring(lastDot + 1);
        }

        String nestedSimple = simpleClass;
        int nestedSep = nestedSimple.lastIndexOf('$');
        if (nestedSep >= 0 && nestedSep + 1 < nestedSimple.length()) {
            nestedSimple = nestedSimple.substring(nestedSep + 1);
        }

        return methodName.equals(simpleClass) || methodName.equals(nestedSimple);
    }

    public static String extractClassNameFromCsvPath(String csvFilePath) {
        if (csvFilePath == null || csvFilePath.isBlank()) return "";

        String path = csvFilePath.replace("\\", "/")
            .replace("_ESTest.java", ".java");

        if (path.endsWith(".java")) path = path.substring(0, path.length() - 5);

        int idx = path.indexOf("java/");
        if (idx == -1) {
            int srcIdx = path.indexOf("src/");
            if (srcIdx != -1) path = path.substring(srcIdx + 4);
        } else {
            path = path.substring(idx + 5);
        }

        return path.replace("/", ".");
    }
}
