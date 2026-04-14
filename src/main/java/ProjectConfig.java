import java.nio.file.Path;
import java.nio.file.Paths;

public class ProjectConfig {
    public static final int MAX_CG_DEPTH = 1;
    public static final String DEFAULT_PROJECT_ARG = "commons-lang3-3.12.0-src";
    public static final String JSON_OUTPUT_DIR = "method_context_json";

    public static Path ACTIVE_PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
}
