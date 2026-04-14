import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import sootup.core.model.SootMethod;
import sootup.java.core.JavaSootClass;
import sootup.java.core.views.JavaView;

public class JsonContextGenerator {

    public static ObjectNode buildContextJsonNode(
        ObjectMapper mapper,
        JavaView view,
        SootMethod mutMethod,
        List<SootMethod> callers,
        List<SootMethod> callees,
        Map<String, String> javadocBySignature) {
        ObjectNode contextJson = mapper.createObjectNode();

        ObjectNode mutNode = mapper.createObjectNode();
        mutNode.put("method_name", mutMethod.getName());
        mutNode.put("signature", formatSignature(mutMethod.getSignature().toString()));
        mutNode.put("qualified_name", getDisplayDeclaringClassName(mutMethod) + "." + mutMethod.getName());

        if (mutMethod.hasBody() && mutMethod.getBody().getPosition() != null) {
            mutNode.put("line_number", SourceFileLocator.getAccurateMethodStartLine(mutMethod));
        } else {
            mutNode.put("line_number", -1);
        }

        ArrayNode paramsArray = mapper.createArrayNode();
        for (int i = 0; i < mutMethod.getParameterCount(); i++) {
            paramsArray.add(mutMethod.getParameterType(i).toString());
        }
        mutNode.set("parameters", paramsArray);

        mutNode.put("code", SourceFileLocator.getMethodSourceCode(mutMethod));
        String javadoc = getJavadocFromMap(mutMethod, javadocBySignature);
        mutNode.put("javadoc", javadoc);
        mutNode.set("class_hierarchy", buildClassHierarchyNode(view, mutMethod, mapper));
        contextJson.set("MUT", mutNode);

        ArrayNode callersArray = mapper.createArrayNode();
        for (SootMethod caller : callers) {
            ObjectNode callerNode = mapper.createObjectNode();
            callerNode.put("method_name", caller.getName());
            callerNode.put("signature", formatSignature(caller.getSignature().toString()));
            callerNode.put("qualified_name", getDisplayDeclaringClassName(caller) + "." + caller.getName());
            callerNode.put("line_number", caller.hasBody() && caller.getBody().getPosition() != null ? SourceFileLocator.getAccurateMethodStartLine(caller) : -1);
            callerNode.put("code", SourceFileLocator.getMethodSourceCode(caller));
            callerNode.put("javadoc", getJavadocFromMap(caller, javadocBySignature));
            callerNode.put("edge", formatEdge(caller, mutMethod));
            callerNode.set("class_hierarchy", buildClassHierarchyNode(view, caller, mapper));
            callersArray.add(callerNode);
        }
        contextJson.set("callers", callersArray);

        ArrayNode calleesArray = mapper.createArrayNode();
        for (SootMethod callee : callees) {
            ObjectNode calleeNode = mapper.createObjectNode();
            calleeNode.put("method_name", callee.getName());
            calleeNode.put("signature", formatSignature(callee.getSignature().toString()));
            calleeNode.put("qualified_name", getDisplayDeclaringClassName(callee) + "." + callee.getName());
            calleeNode.put("line_number", callee.hasBody() && callee.getBody().getPosition() != null ? SourceFileLocator.getAccurateMethodStartLine(callee) : -1);
            calleeNode.put("code", SourceFileLocator.getMethodSourceCode(callee));
            calleeNode.put("javadoc", getJavadocFromMap(callee, javadocBySignature));
            calleeNode.put("edge", formatEdge(mutMethod, callee));
            calleeNode.set("class_hierarchy", buildClassHierarchyNode(view, callee, mapper));
            calleesArray.add(calleeNode);
        }
        contextJson.set("callees", calleesArray);

        ObjectNode metadata = mapper.createObjectNode();
        metadata.put("call_graph_tool", "SootUp");
        metadata.put("analysis_depth", 1);
        metadata.put("class_hierarchy_included", true);
        contextJson.set("metadata", metadata);

        return contextJson;
    }

    public static void saveContextToJson(
        JavaView view,
        SootMethod mutMethod,
        List<SootMethod> callers,
        List<SootMethod> callees,
        String outputPath,
        Map<String, String> javadocBySignature) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode contextJson = buildContextJsonNode(mapper, view, mutMethod, callers, callees, javadocBySignature);
            File outputFile = new File(outputPath);
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, contextJson);
            System.out.println("JSON output saved to: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error saving JSON: " + e.getMessage());
        }
    }

    public static String getJavadocFromMap(SootMethod method, Map<String, String> javadocBySignature) {
        if (method == null) return "";
        if (javadocBySignature == null) return JavadocExtractor.extractJavadocForJson(method);
        String key = method.getSignature().toString();
        String jd = javadocBySignature.get(key);
        if (jd != null && !jd.isBlank()) return jd;
        return JavadocExtractor.extractJavadocForJson(method);
    }

    public static ObjectNode buildClassHierarchyNode(JavaView view, SootMethod method, ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        String rawClassName = method.getDeclaringClassType().toString();
        String className = normalizeClassNameForOutput(rawClassName);
        String simpleName = className;
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) simpleName = className.substring(lastDot + 1);
        String packageName = lastDot >= 0 ? className.substring(0, lastDot) : "";

        node.put("class_name", className);
        node.put("simple_name", simpleName);
        node.put("package_name", packageName);

        ArrayNode superclasses = mapper.createArrayNode();
        ArrayNode interfaces = mapper.createArrayNode();
        ArrayNode directSubclasses = mapper.createArrayNode();

        List<JavaSootClass> classes = view.getClasses().sequential().collect(Collectors.toList());
        JavaSootClass targetClass = null;
        for (JavaSootClass cls : classes) {
            if (safeTypeClassName(cls).equals(rawClassName)) {
                targetClass = cls;
                break;
            }
        }

        if (targetClass != null) {
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            Object current = targetClass;
            while (current != null) {
                List<String> directParents = getSuperclassNamesReflective(current);
                String nextParent = null;
                for (String parent : directParents) {
                    if (parent != null && !parent.isEmpty() && seen.add(parent)) {
                        superclasses.add(normalizeClassNameForOutput(parent));
                        if (nextParent == null) {
                            nextParent = parent;
                        }
                    }
                }

                if (nextParent == null) {
                    break;
                }
                current = findClassByName(classes, nextParent);
            }

            for (String iface : getInterfaceNamesReflective(targetClass)) {
                interfaces.add(normalizeClassNameForOutput(iface));
            }

            for (JavaSootClass cls : classes) {
                String clsName = safeTypeClassName(cls);
                if (clsName.equals(rawClassName)) continue;
                List<String> parents = getSuperclassNamesReflective(cls);
                if (parents.contains(rawClassName)) {
                    directSubclasses.add(normalizeClassNameForOutput(clsName));
                }
            }
        }

        node.set("superclasses", superclasses);
        node.set("interfaces", interfaces);
        node.set("direct_subclasses", directSubclasses);
        return node;
    }

    private static JavaSootClass findClassByName(List<JavaSootClass> classes, String className) {
        for (JavaSootClass cls : classes) {
            if (safeTypeClassName(cls).equals(className)) {
                return cls;
            }
        }
        return null;
    }

    private static String safeTypeClassName(JavaSootClass cls) {
        try {
            Object type = cls.getType();
            String result = type.toString();
            if (result != null && !result.isEmpty()) {
                return result;
            }
            try {
                var m = type.getClass().getMethod("getClassName");
                Object value = m.invoke(type);
                return value == null ? "" : value.toString();
            } catch (Exception ignored) {
                return "";
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private static List<String> getSuperclassNamesReflective(Object sootClassObj) {
        List<String> out = new ArrayList<>();
        for (String methodName : Arrays.asList("getSuperclass", "getSuperClass", "getSuperclassOpt")) {
            List<String> names = invokeAndExtractClassNames(sootClassObj, methodName);
            if (!names.isEmpty()) {
                out.addAll(names);
                break;
            }
        }
        return out;
    }

    private static List<String> getInterfaceNamesReflective(Object sootClassObj) {
        List<String> out = new ArrayList<>();
        for (String methodName : Arrays.asList("getInterfaces", "getImplementedInterfaces")) {
            List<String> names = invokeAndExtractClassNames(sootClassObj, methodName);
            if (!names.isEmpty()) {
                out.addAll(names);
                break;
            }
        }
        return out;
    }

    private static List<String> invokeAndExtractClassNames(Object target, String methodName) {
        try {
            var m = target.getClass().getMethod(methodName);
            Object result = m.invoke(target);
            return extractClassNamesFromUnknown(result);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static List<String> extractClassNamesFromUnknown(Object value) {
        List<String> out = new ArrayList<>();
        if (value == null) return out;
        if (value instanceof Optional) {
            Optional<?> opt = (Optional<?>) value;
            if (opt.isPresent()) {
                out.addAll(extractClassNamesFromUnknown(opt.get()));
            }
            return out;
        }
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                String n = toClassName(item);
                if (!n.isEmpty()) out.add(n);
            }
            return out;
        }
        String n = toClassName(value);
        if (!n.isEmpty()) out.add(n);
        return out;
    }

    private static String toClassName(Object value) {
        if (value == null) return "";
        String text = value.toString();
        if (text != null && !text.isBlank()) {
            return text;
        }
        try {
            var m = value.getClass().getMethod("getClassName");
            Object name = m.invoke(value);
            if (name != null) {
                return name.toString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public static String formatSignature(String sootSignature) {
        return sootSignature
            .replace("<", "")
            .replace(">", "")
            .replace(": void ", ".")
            .replace(": ", ".")
            .replace("java.lang.", "")
            .replace("(java.lang.String)", "(String)")
            .replace("(int,int)", "(int, int)");
    }

    public static String formatEdge(SootMethod from, SootMethod to) {
        String fromName = from.getSignature().toString().replace("<", "").replace(">", "").split(": ")[1].split("\\(")[0];
        String toName = to.getSignature().toString().replace("<", "").replace(">", "").split(": ")[1].split("\\(")[0];
        String fromClass = getDisplayDeclaringClassName(from);
        String toClass = getDisplayDeclaringClassName(to);
        return fromClass + "." + fromName + " -> " + toClass + "." + toName;
    }

    public static String getDisplayDeclaringClassName(SootMethod method) {
        if (method == null || method.getDeclaringClassType() == null) return "";
        return normalizeClassNameForOutput(method.getDeclaringClassType().getClassName());
    }

    public static String normalizeClassNameForOutput(String className) {
        if (className == null || className.isBlank()) return "";
        int lastDot = className.lastIndexOf('.');
        String pkg = lastDot >= 0 ? className.substring(0, lastDot) : "";
        String simple = lastDot >= 0 ? className.substring(lastDot + 1) : className;
        simple = simple.replaceAll("\\$\\d+$", "");
        simple = simple.replace('$', '.');
        return pkg.isEmpty() ? simple : pkg + "." + simple;
    }

    public static String buildRowJsonFileName(String rowId, MethodInfo info) {
        String safeId = sanitizeFilePart(rowId);
        String safeClass = sanitizeFilePart(info.className);
        String safeMethod = sanitizeFilePart(info.methodName);
        return "id_" + safeId + "__" + safeClass + "__" + safeMethod + ".json";
    }

    public static String buildSingleJsonFileName(String targetId, SootMethod method) {
        String safeId = sanitizeFilePart(targetId);
        String safeClass = sanitizeFilePart(method.getDeclaringClassType().getClassName());
        String safeMethod = sanitizeFilePart(method.getName());
        return "target_" + safeId + "__" + safeClass + "__" + safeMethod + ".json";
    }

    public static void linkOrCopyJsonArtifact(Path source, Path target) throws IOException {
        try {
            Files.deleteIfExists(target);
            Files.createLink(target, source);
        } catch (UnsupportedOperationException | IOException e) {
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String sanitizeFilePart(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
