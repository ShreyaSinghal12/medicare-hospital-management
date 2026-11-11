package com.medicare.reporting;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * RFC 4180 CSV with a UTF-8 BOM (so spreadsheet programs detect the encoding).
 *
 * <p>Formula injection: a cell starting with {@code = + - @}, tab or carriage return could be executed as a formula
 * when the file is opened in a spreadsheet. Such cells are prefixed with an apostrophe. Plain negative numbers are
 * left as they are.</p>
 */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static byte[] write(List<String> header, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        line(sb, header);
        rows.forEach(r -> line(sb, r));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void line(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cell(cells.get(i)));
        }
        sb.append("\r\n");
    }

    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 && !v.matches("-?\\d+(\\.\\d+)?")) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
