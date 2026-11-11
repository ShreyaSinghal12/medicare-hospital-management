package com.medicare.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import com.medicare.exception.BusinessRuleException;

import org.junit.jupiter.api.Test;

class PdfUploadValidatorTest {

    static byte[] pdf(String body) {
        return ("%PDF-1.7\n" + body + "\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void acceptsPlainPdfIncludingNamesThatOnlyStartLikeActiveContent() {
        assertThatCode(() -> PdfUploadValidator.validate("Report.PDF", pdf("<< /JSomething 1 /Launcher 2 >>")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsActiveContent() {
        for (String token : new String[]{"/JavaScript", "/JS ", "/Launch<<", "/EmbeddedFiles[", "/RichMedia "}) {
            assertThatThrownBy(() -> PdfUploadValidator.validate("a.pdf", pdf("<< " + token + " >>")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "UNSAFE_FILE");
        }
    }

    @Test
    void rejectsWrongSignatureMissingEofAndEmptyFiles() {
        assertThatThrownBy(() -> PdfUploadValidator.validate("a.pdf", "<html>".getBytes(StandardCharsets.US_ASCII)))
                .hasFieldOrPropertyWithValue("code", "INVALID_FILE_TYPE");
        assertThatThrownBy(() -> PdfUploadValidator.validate("a.pdf", "%PDF-1.4 no end".getBytes(StandardCharsets.US_ASCII)))
                .hasFieldOrPropertyWithValue("code", "INVALID_FILE_TYPE");
        assertThatThrownBy(() -> PdfUploadValidator.validate("a.pdf", new byte[0]))
                .hasFieldOrPropertyWithValue("code", "FILE_REQUIRED");
        assertThatThrownBy(() -> PdfUploadValidator.validate(null, pdf("x")))
                .hasFieldOrPropertyWithValue("code", "INVALID_FILE_TYPE");
    }

    @Test
    void displayNameDropsPathsAndUnsafeCharacters() {
        assertThat(PdfUploadValidator.safeDisplayName("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(PdfUploadValidator.safeDisplayName("C:\\Users\\x\\<script>.pdf")).isEqualTo("_script_.pdf");
        assertThat(PdfUploadValidator.safeDisplayName(".pdf")).isEqualTo("report.pdf");
        assertThat(PdfUploadValidator.safeDisplayName("a".repeat(300) + ".pdf")).hasSize(100).endsWith(".pdf");
    }
}
