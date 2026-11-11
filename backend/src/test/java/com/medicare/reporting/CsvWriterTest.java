package com.medicare.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    void quotesSpecialCharactersAndNeutralisesFormulas() {
        assertThat(CsvWriter.cell("plain")).isEqualTo("plain");
        assertThat(CsvWriter.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvWriter.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvWriter.cell("two\nlines")).isEqualTo("\"two\nlines\"");
        assertThat(CsvWriter.cell("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(CsvWriter.cell("+1+1")).isEqualTo("'+1+1");
        assertThat(CsvWriter.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvWriter.cell("-cmd")).isEqualTo("'-cmd");
        assertThat(CsvWriter.cell("-45.50")).isEqualTo("-45.50");
        assertThat(CsvWriter.cell(null)).isEmpty();
    }

    @Test
    void writesBomHeaderAndCrlfRows() {
        String csv = new String(CsvWriter.write(List.of("A", "B"), List.of(List.of("1", "x,y"))), StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo("﻿A,B\r\n1,\"x,y\"\r\n");
    }
}
