import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CliArgumentParser {
    public static class CliOptions {
        public String projectArg = ProjectConfig.DEFAULT_PROJECT_ARG;
        public String targetId = "500";
        public boolean batchMode = false;
        public LinkedHashSet<String> requestedIds = new LinkedHashSet<>();
    }

    public static CliOptions parseCliOptions(String[] args) {
        CliOptions options = new CliOptions();
        List<String> positional = new ArrayList<>();
        LinkedHashSet<String> duplicates = new LinkedHashSet<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i] == null ? "" : args[i].trim();
            if (arg.isEmpty()) {
                continue;
            }

            if (arg.equalsIgnoreCase("--batch") || arg.equalsIgnoreCase("batch") || arg.equalsIgnoreCase("all")) {
                options.batchMode = true;
                continue;
            }

            if (arg.startsWith("--ids=")) {
                parseAndAddIds(arg.substring("--ids=".length()), options.requestedIds, duplicates);
                continue;
            }

            if (arg.equals("--ids")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for --ids. Example: --ids 12,15,200");
                }
                String next = args[++i] == null ? "" : args[i].trim();
                parseAndAddIds(next, options.requestedIds, duplicates);
                continue;
            }

            if (arg.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            }

            positional.add(arg);
        }

        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate IDs in --ids: " + String.join(", ", duplicates));
        }

        if (!positional.isEmpty() && !positional.get(0).isBlank()) {
            options.projectArg = positional.get(0);
        }
        if (positional.size() > 1 && !positional.get(1).isBlank()) {
            options.targetId = positional.get(1);
        }
        return options;
    }

    public static void parseAndAddIds(String raw, LinkedHashSet<String> ids, Set<String> duplicates) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("--ids value cannot be empty. Example: --ids 12,15,200");
        }
        String[] tokens = raw.split(",");
        for (String token : tokens) {
            String id = token == null ? "" : token.trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Malformed --ids list: empty token detected.");
            }
            if (!id.matches("\\d+")) {
                throw new IllegalArgumentException("Malformed ID in --ids: '" + id + "'. IDs must be numeric.");
            }
            if (!ids.add(id)) {
                duplicates.add(id);
            }
        }
    }
}
