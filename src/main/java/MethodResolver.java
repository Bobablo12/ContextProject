import java.util.ArrayList;
import java.util.List;
import sootup.core.model.SootMethod;

public class MethodResolver {

    public static boolean paramsMatch(List<?> sootParamTypes, List<String> csvParamTypes) {
        List<String> sootNorm = normalizeSootParamTypes(sootParamTypes);
        List<String> csvNorm = normalizeCsvParamTypes(csvParamTypes);
        if (sootNorm.size() != csvNorm.size()) return false;
        for (int i = 0; i < sootNorm.size(); i++) {
            if (!sootNorm.get(i).equals(csvNorm.get(i))) return false;
        }
        return true;
    }

    public static List<String> normalizeSootParamTypes(List<?> sootParamTypes) {
        List<String> out = new ArrayList<>();
        for (Object t : sootParamTypes) {
            String s = (t == null) ? "" : t.toString();
            s = s.replace("...", "[]").replaceAll("<.*?>", "");
            out.add(MethodInfo.toSimpleTypeName(s));
        }
        return out;
    }

    public static List<String> normalizeCsvParamTypes(List<String> csvParamTypes) {
        List<String> out = new ArrayList<>();
        for (String t : csvParamTypes) {
            if (t == null) t = "";
            String s = t.trim()
                .replace("...", "[]")
                .replaceAll("<.*?>", "")
                .replaceAll("^@\\w+\\s+", "");
            s = eraseGenericTypeVariable(s);
            out.add(MethodInfo.toSimpleTypeName(s));
        }
        return out;
    }

    public static String eraseGenericTypeVariable(String s) {
        if (s == null || s.isEmpty()) return s;
        String base = s;
        String suffix = "";
        if (base.endsWith("[]")) {
            base = base.substring(0, base.length() - 2);
            suffix = "[]";
        }
        if (base.matches("[A-Z]")) return "Object" + suffix;
        return s;
    }

    public static SootMethod findMethodInClass(List<SootMethod> methodsInClass, MethodInfo info) {
        if (methodsInClass == null) {
            return null;
        }
        String expectedMethodName = (info.sootMethodName == null || info.sootMethodName.isBlank())
            ? info.methodName
            : info.sootMethodName;
        for (SootMethod m : methodsInClass) {
            if (m.getName().equals(expectedMethodName) && paramsMatch(m.getParameterTypes(), info.parameterTypes)) {
                return m;
            }
        }
        return null;
    }
}
