import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility to add sequential ID column to CSV files missing it.
 * Creates inputs_with_id.csv from inputs.csv if it doesn't already exist.
 * Reconstructs multiline CSV rows and inserts an ID column if missing.
 */
public class CsvIdAssigner {

    /**
     * Ensures that inputs_with_id.csv exists for the given project.
     * If it doesn't exist, creates it by adding sequential IDs to inputs.csv.
    * Reconstructs multiline rows and inserts an ID column while preserving existing schema.
     *
     * @param projectRoot Path to the project root directory
     */
    public static void ensureInputsWithId(Path projectRoot) {
        try {
            Path inputsWithIdPath = projectRoot.resolve("inputs_with_id.csv");
            Path inputsPath = projectRoot.resolve("inputs.csv");

            // If inputs_with_id.csv already exists, nothing to do
            if (Files.exists(inputsWithIdPath)) {
                return;
            }

            // If inputs.csv doesn't exist, log and return
            if (!Files.exists(inputsPath)) {
                System.err.println("Warning: inputs.csv not found in " + projectRoot);
                return;
            }

            List<String> physicalLines = Files.readAllLines(inputsPath, StandardCharsets.UTF_8);
            if (physicalLines.isEmpty()) {
                System.err.println("Warning: inputs.csv is empty in " + projectRoot);
                return;
            }

            String headerLine = physicalLines.get(0);
            String[] headerCols = CsvReader.parseCSVLine(headerLine);
            int idIndex = findColumnIndex(headerCols, "id");

            if (idIndex >= 0) {
                Files.copy(inputsPath, inputsWithIdPath);
                System.out.println("Copied inputs.csv to inputs_with_id.csv for " + projectRoot.getFileName() + " (id column already present)");
                return;
            }

            int insertIndex = findColumnIndex(headerCols, "file_path");
            if (insertIndex < 0) {
                insertIndex = headerCols.length;
            }

            List<String> dataPhysicalLines = physicalLines.subList(1, physicalLines.size());
            List<String[]> parsedRows = reconstructRows(dataPhysicalLines, Math.max(0, headerCols.length - 1));

            String[] outputHeader = insertAt(headerCols, insertIndex, "id");

            try (FileWriter writer = new FileWriter(inputsWithIdPath.toFile())) {
                CsvWriter.writeCsvRow(writer, outputHeader);

                int rowId = 1;
                for (String[] row : parsedRows) {
                    List<String> mutableRow = new ArrayList<>(Arrays.asList(row));
                    while (mutableRow.size() < insertIndex) {
                        mutableRow.add("");
                    }
                    mutableRow.add(insertIndex, String.valueOf(rowId));
                    CsvWriter.writeCsvRow(writer, mutableRow.toArray(new String[0]));
                    rowId++;
                }

                System.out.println("Created inputs_with_id.csv for " + projectRoot.getFileName() + " with " + (rowId - 1) + " data rows");
            }

        } catch (IOException e) {
            System.err.println("Error creating inputs_with_id.csv for " + projectRoot + ": " + e.getMessage());
        }
    }

    private static List<String[]> reconstructRows(List<String> physicalLines, int minCommas) {
        List<String[]> rows = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : physicalLines) {
            if (current.length() > 0) {
                current.append("\n");
            }
            current.append(line);

            if (CsvReader.isCompleteCSVRow(current.toString(), minCommas)) {
                rows.add(CsvReader.parseCSVLine(current.toString()));
                current.setLength(0);
            }
        }

        if (current.length() > 0) {
            rows.add(CsvReader.parseCSVLine(current.toString()));
        }

        return rows;
    }

    private static int findColumnIndex(String[] headerCols, String name) {
        for (int i = 0; i < headerCols.length; i++) {
            if (headerCols[i] != null && headerCols[i].trim().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static String[] insertAt(String[] input, int index, String value) {
        String[] result = new String[input.length + 1];
        System.arraycopy(input, 0, result, 0, index);
        result[index] = value;
        System.arraycopy(input, index, result, index + 1, input.length - index);
        return result;
    }

    /**
     * Batch version: ensures inputs_with_id.csv for multiple projects.
     *
     * @param projectRoots List of project root paths
     */
    public static void ensureInputsWithIdForAll(List<Path> projectRoots) {
        for (Path projectRoot : projectRoots) {
            ensureInputsWithId(projectRoot);
        }
    }

    /**
     * Main entry point: can be called with project directory as argument.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java CsvIdAssigner <project_root_path>");
            System.exit(1);
        }

        Path projectRoot = Paths.get(args[0]);
        if (!Files.isDirectory(projectRoot)) {
            System.err.println("Error: " + projectRoot + " is not a directory");
            System.exit(1);
        }

        ensureInputsWithId(projectRoot);
    }
}

