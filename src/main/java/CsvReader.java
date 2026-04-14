import com.opencsv.exceptions.CsvValidationException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CsvReader {

    public static MethodInfo loadRowById_OpenCSV(String targetId)
            throws IOException, CsvValidationException {
        String csvPath = resolveInputsCsvPath();

        try (com.opencsv.CSVReader reader =
                new com.opencsv.CSVReader(new FileReader(csvPath))) {
            String[] header = reader.readNext();
            if (header == null) {
                System.err.println("ERROR: CSV header is missing in " + csvPath);
                return null;
            }

            int focalIdx = findColumnIndex(header, "focal_method", 0);
            int testPrefixIdx = findColumnIndex(header, "test_prefix", 1);
            int docstringIdx = findColumnIndex(header, "docstring", 2);
            int idIdx = findColumnIndex(header, "id", 3);
            int filePathIdx = findColumnIndex(header, "file_path", -1);

            if (idIdx < 0) {
                System.err.println("ERROR: CSV has no id column: " + csvPath);
                return null;
            }

            String[] row;

            while ((row = reader.readNext()) != null) {
                String rowId = getField(row, idIdx);
                if (rowId.trim().equals(targetId)) {
                    CsvRowData data = new CsvRowData();
                    data.focalMethod = getField(row, focalIdx);
                    data.testPrefix = getField(row, testPrefixIdx);
                    data.docstring = getField(row, docstringIdx);
                    data.id = rowId;
                    data.filePath = filePathIdx >= 0 ? getField(row, filePathIdx) : pickCsvFilePath(row);
                    if (data.filePath == null || data.filePath.isBlank()) {
                        data.filePath = SourceFileLocator.inferFilePathFromMethodSource(data.focalMethod);
                    }

                    if (data.focalMethod == null || data.focalMethod.isBlank()) {
                        System.err.println("ERROR: Focal method is empty for ID " + data.id);
                        return null;
                    }
                    if (data.filePath == null || data.filePath.isBlank()) {
                        System.err.println("ERROR: File path is empty for ID " + data.id);
                        return null;
                    }

                    try {
                        return MethodInfo.parseMethodAndPath(data.focalMethod, data.filePath);
                    } catch (Exception e) {
                        System.err.println("ERROR parsing row ID " + data.id + ": " + e.getMessage());
                        return null;
                    }
                }
            }
        } catch (com.opencsv.exceptions.CsvMalformedLineException e) {
            System.err.println("Warning: CSV malformed, falling back to manual reader");
            try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
                String headerLine = br.readLine();
                if (headerLine == null) {
                    return null;
                }
                String[] header = parseCSVLine(headerLine);
                int focalIdx = findColumnIndex(header, "focal_method", 0);
                int testPrefixIdx = findColumnIndex(header, "test_prefix", 1);
                int docstringIdx = findColumnIndex(header, "docstring", 2);
                int idIdx = findColumnIndex(header, "id", 3);
                int filePathIdx = findColumnIndex(header, "file_path", -1);

                if (idIdx < 0) {
                    System.err.println("ERROR: CSV has no id column: " + csvPath);
                    return null;
                }

                String line;
                StringBuilder currentRow = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    currentRow.append(line).append("\n");
                    if (isCompleteCSVRow(currentRow.toString(), Math.max(0, header.length - 1))) {
                        String[] rr = parseCSVLine(currentRow.toString().trim());
                        currentRow.setLength(0);
                        String rowId = getField(rr, idIdx);
                        if (rowId.trim().equals(targetId)) {
                            CsvRowData data = new CsvRowData();
                            data.focalMethod = getField(rr, focalIdx);
                            data.testPrefix = getField(rr, testPrefixIdx);
                            data.docstring = getField(rr, docstringIdx);
                            data.id = rowId;
                            data.filePath = filePathIdx >= 0 ? getField(rr, filePathIdx) : pickCsvFilePath(rr);
                            if (data.filePath == null || data.filePath.isBlank()) {
                                data.filePath = SourceFileLocator.inferFilePathFromMethodSource(data.focalMethod);
                            }

                            if (data.focalMethod == null || data.focalMethod.isBlank()) {
                                System.err.println("ERROR: Focal method is empty for ID " + data.id);
                                continue;
                            }
                            if (data.filePath == null || data.filePath.isBlank()) {
                                System.err.println("ERROR: File path is empty for ID " + data.id);
                                continue;
                            }

                            try {
                                return MethodInfo.parseMethodAndPath(data.focalMethod, data.filePath);
                            } catch (Exception e2) {
                                System.err.println("ERROR parsing row ID " + data.id + ": " + e2.getMessage());
                            }
                        }
                    }
                }
            }
        }

        System.err.println("ERROR: Row ID " + targetId + " not found. Either the ID does not exist or its CSV row is incomplete/malformed.");
        System.err.println("Checked CSV file: " + csvPath);
        return null;
    }

    public static String resolveInputsCsvPath() {
        Path nested = ProjectConfig.ACTIVE_PROJECT_ROOT.resolve("inputs_with_id.csv");
        if (Files.exists(nested)) {
            return nested.toString();
        }

        Path workspaceLevel = Paths.get(System.getProperty("user.dir"), "inputs_with_id.csv");
        if (Files.exists(workspaceLevel)) {
            return workspaceLevel.toString();
        }
        return nested.toString();
    }

    public static String pickCsvFilePath(String[] row) {
        if (row == null) return "";
        if (row.length > 5 && row[5] != null && !row[5].trim().isEmpty()) {
            return row[5];
        }
        if (row.length > 4 && row[4] != null) {
            return row[4];
        }
        return "";
    }

    public static boolean isCompleteCSVRow(String text) {
        return isCompleteCSVRow(text, 0);
    }

    public static boolean isCompleteCSVRow(String text, int minCommas) {
        int commaCount = 0;
        int quoteCount = 0;
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoteCount++;
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                commaCount++;
            }
        }
        return commaCount >= minCommas && quoteCount % 2 == 0 && !inQuotes;
    }

    public static String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
                currentField.append(c);
            } else if (c == ',' && !inQuotes) {
                String field = currentField.toString().trim();
                if (field.startsWith("\"") && field.endsWith("\"")) {
                    field = field.substring(1, field.length() - 1);
                }
                fields.add(field);
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }

        String field = currentField.toString().trim();
        if (field.startsWith("\"") && field.endsWith("\"")) {
            field = field.substring(1, field.length() - 1);
        }
        fields.add(field);

        return fields.toArray(new String[0]);
    }

    public static List<String[]> readCsvDataRows(String csvPath) throws IOException {
        List<String[]> allRows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                return allRows;
            }

            String[] header = parseCSVLine(headerLine);
            int minCommas = Math.max(0, header.length - 1);

            StringBuilder currentRow = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                currentRow.append(line).append("\n");
                if (isCompleteCSVRow(currentRow.toString(), minCommas)) {
                    allRows.add(parseCSVLine(currentRow.toString().trim()));
                    currentRow = new StringBuilder();
                }
            }
        }
        return allRows;
    }

    private static int findColumnIndex(String[] header, String targetName, int fallback) {
        if (header == null) {
            return fallback;
        }
        for (int i = 0; i < header.length; i++) {
            if (header[i] != null && header[i].trim().equals(targetName)) {
                return i;
            }
        }
        return fallback;
    }

    private static String getField(String[] row, int idx) {
        if (row == null || idx < 0 || idx >= row.length || row[idx] == null) {
            return "";
        }
        return row[idx];
    }

    public static Set<String> collectExistingJsonIds(Path jsonOutputDir) {
        Set<String> ids = new HashSet<>();
        if (!Files.isDirectory(jsonOutputDir)) {
            return ids;
        }
        try (java.util.stream.Stream<Path> stream = Files.list(jsonOutputDir)) {
            stream
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith("id_") && name.contains("__") && name.endsWith(".json"))
                .forEach(name -> {
                    int separator = name.indexOf("__");
                    if (separator > 3) {
                        ids.add(name.substring(3, separator));
                    }
                });
        } catch (IOException e) {
            System.err.println("Warning: unable to scan existing JSON artifacts: " + e.getMessage());
        }
        return ids;
    }
}
