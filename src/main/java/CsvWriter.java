import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class CsvWriter {

    public static void writeCsvRow(FileWriter writer, String[] row) throws IOException {
        for (int i = 0; i < row.length; i++) {
            if (i > 0) writer.write(",");
            String value = row[i] == null ? "" : row[i];
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                writer.write("\"" + value.replace("\"", "\"\"") + "\"");
            } else {
                writer.write(value);
            }
        }
        writer.write("\n");
    }

    public static void updateCsvJsonPathForTargetId(String targetId, String jsonRelativePath) {
        try {
            String csvPath = CsvReader.resolveInputsCsvPath();

            List<String[]> allRows = new ArrayList<>();
            String[] headerCols;

            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(csvPath))) {
                String headerLine = br.readLine();
                if (headerLine == null) return;
                headerCols = CsvReader.parseCSVLine(headerLine);

                StringBuilder currentRow = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    currentRow.append(line).append("\n");
                    if (CsvReader.isCompleteCSVRow(currentRow.toString())) {
                        allRows.add(CsvReader.parseCSVLine(currentRow.toString().trim()));
                        currentRow.setLength(0);
                    }
                }
            }

            headerCols = ensureColumns(headerCols, allRows, new String[] { "json_file_path" });
            int idColIdx = findColumnIndex(headerCols, "id");

            boolean updated = false;
            for (int rowIdx = 0; rowIdx < allRows.size(); rowIdx++) {
                String[] row = allRows.get(rowIdx);
                if (idColIdx < 0 || idColIdx >= row.length) continue;
                if (!targetId.equals(row[idColIdx].trim())) continue;

                List<String> newRow = new ArrayList<>(Arrays.asList(row));
                while (newRow.size() < headerCols.length) {
                    newRow.add("");
                }

                setColumnValue(headerCols, newRow, "json_file_path", jsonRelativePath);
                clearLegacyEmbeddedColumns(headerCols, newRow);
                allRows.set(rowIdx, newRow.toArray(new String[0]));
                updated = true;
                break;
            }

            if (!updated) return;

            try (FileWriter writer = new FileWriter(csvPath)) {
                writeCsvRow(writer, headerCols);
                for (String[] csvRow : allRows) {
                    writeCsvRow(writer, csvRow);
                }
            }
        } catch (Exception e) {
            System.err.println("Error updating CSV json path for target ID " + targetId + ": " + e.getMessage());
        }
    }

    public static void clearLegacyEmbeddedColumns(String[] headerCols, List<String> row) {
        String[] legacyColumns = new String[] {
            "mut_method_name",
            "mut_signature",
            "mut_line_number",
            "mut_parameters",
            "mut_code",
            "mut_javadoc",
            "mut_class_hierarchy",
            "callers_info",
            "callees_info",
            "callers_count",
            "callees_count",
            "class_hierarchy_included"
        };
        for (String col : legacyColumns) {
            setColumnValue(headerCols, row, col, "");
        }
    }

    public static void setColumnValue(String[] headerCols, List<String> row, String colName, String value) {
        int idx = findColumnIndex(headerCols, colName);
        if (idx != -1) {
            row.set(idx, value);
        }
    }

    public static int findColumnIndex(String[] columns, String columnName) {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].trim().equals(columnName)) {
                return i;
            }
        }
        return -1;
    }

    public static String[] ensureColumns(String[] headerCols, List<String[]> allRows, String[] requiredColumns) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (String col : headerCols) {
            merged.add(col == null ? "" : col.trim());
        }
        for (String col : requiredColumns) {
            merged.add(col);
        }

        String[] newHeader = merged.toArray(new String[0]);
        if (newHeader.length == headerCols.length) {
            return headerCols;
        }

        for (int i = 0; i < allRows.size(); i++) {
            String[] oldRow = allRows.get(i);
            String[] expanded = new String[newHeader.length];
            int copyLen = Math.min(oldRow.length, expanded.length);
            if (copyLen > 0) {
                System.arraycopy(oldRow, 0, expanded, 0, copyLen);
            }
            for (int j = copyLen; j < expanded.length; j++) {
                expanded[j] = "";
            }
            allRows.set(i, expanded);
        }

        System.out.println("Extended CSV header from " + headerCols.length + " to " + newHeader.length + " columns.");
        return newHeader;
    }
}
