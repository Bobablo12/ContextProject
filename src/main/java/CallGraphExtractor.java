import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import sootup.callgraph.CallGraph;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.views.JavaView;

public class CallGraphExtractor {

    public static ArrayList<MethodSignature> printCallsRecursive(
        JavaView view,
        MethodSignature methodSig,
        CallGraph cg,
        FileWriter writer,
        int depth,
        int maxDepth,
        Set<MethodSignature> recursionStack,
        boolean single,
        ArrayList<MethodSignature> methodsList) throws IOException {

        if (maxDepth != -1 && depth > maxDepth) {
            return methodsList;
        }

        String indent = "    ".repeat(depth);

        if (recursionStack.contains(methodSig)) {
            writer.write(indent + "[cycle detected: " + methodSig.getDeclClassType() + "." + methodSig.getName() + "]\n");
            return methodsList;
        }

        recursionStack.add(methodSig);

        for (CallGraph.Call call : cg.callsFrom(methodSig)) {
            MethodSignature callee = call.getTargetMethodSignature();

            if (SourceFileLocator.isSpecialMethod(callee)) continue;

            if (!methodsList.contains(callee)) {
                methodsList.add(callee);
            }

            int line = -1;
            Optional<JavaSootMethod> calleeOpt = view.getMethod(callee);
            if (calleeOpt.isPresent()) {
                JavaSootMethod calleeMethod = calleeOpt.get();
                if (!calleeMethod.hasBody() || calleeMethod.getBody().getPosition() == null) {
                    if (!single) {
                        writer.write(indent + "to <" +
                            callee.getDeclClassType() + ": " +
                            callee.getType() + " " +
                            callee.getName() + "> [library, no source]\n");
                    }
                    continue;
                }
                line = SourceFileLocator.getAccurateMethodStartLine(calleeMethod);
            }

            boolean isProject = SourceFileLocator.isProjectMethod(callee);

            if (!single && line != -1) {
                writer.write(indent + "to <" +
                    callee.getDeclClassType() + ": " +
                    callee.getType() + " " +
                    callee.getName() + "> (line " + line + ") (depth " + depth + ")\n");
            }

            if (isProject) {
                if (maxDepth != -1 && depth + 1 > maxDepth) {
                    writer.write(indent + "    [max depth " + maxDepth + " reached, stopping]\n");
                } else {
                    printCallsRecursive(view, callee, cg, writer, depth + 1, maxDepth, recursionStack, single, methodsList);
                }
            }
        }

        recursionStack.remove(methodSig);
        return methodsList;
    }

    public static int getCallSiteLineNumber(CallGraph.Call call) {
        if (call == null) return -1;
        try {
            sootup.core.jimple.common.stmt.InvokableStmt stmt = call.getInvokableStmt();
            if (stmt == null || stmt.getPositionInfo() == null || stmt.getPositionInfo().getStmtPosition() == null) {
                return -1;
            }
            return stmt.getPositionInfo().getStmtPosition().getFirstLine();
        } catch (Exception e) {
            return -1;
        }
    }

    public static int getAccurateMethodStartLine(JavaView view, MethodSignature sig) {
        if (view == null || sig == null) return -1;
        Optional<JavaSootMethod> opt = view.getMethod(sig);
        if (!opt.isPresent()) return -1;
        return SourceFileLocator.getAccurateMethodStartLine(opt.get());
    }
}
