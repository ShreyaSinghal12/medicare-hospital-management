package com.medicare.storage;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import com.medicare.exception.BusinessRuleException;

/**
 * Checks an uploaded lab report before it is stored. The browser-supplied content type and file name are
 * never trusted:
 * <ul>
 *   <li>size between 1 byte and {@link #MAX_BYTES};</li>
 *   <li>name ends in {@code .pdf};</li>
 *   <li>content starts with the PDF signature {@code %PDF-} and has an {@code %%EOF} marker near the end;</li>
 *   <li>no active content: JavaScript, launch actions or embedded files are rejected.</li>
 * </ul>
 * The active-content check reads uncompressed PDF syntax only. Content inside compressed object streams is
 * not inspected, which is why a production deployment should add a virus scanner (see README).
 */
public final class PdfUploadValidator {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final byte[] SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int EOF_WINDOW = 2048;
    /** PDF name objects followed by a delimiter, so e.g. "/JSomething" does not match "/JS". */
    private static final List<Pattern> ACTIVE_CONTENT = List.of(
            Pattern.compile("/JavaScript(?![A-Za-z0-9])"),
            Pattern.compile("/JS(?![A-Za-z0-9])"),
            Pattern.compile("/Launch(?![A-Za-z0-9])"),
            Pattern.compile("/EmbeddedFiles?(?![A-Za-z0-9])"),
            Pattern.compile("/RichMedia(?![A-Za-z0-9])"));

    private PdfUploadValidator() {
    }

    public static void validate(String originalFilename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessRuleException("FILE_REQUIRED", "Choose a PDF file to upload");
        }
        if (content.length > MAX_BYTES) {
            throw new BusinessRuleException("FILE_TOO_LARGE", "The report must be 5 MB or smaller");
        }
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new BusinessRuleException("INVALID_FILE_TYPE", "Only PDF files (.pdf) can be uploaded");
        }
        if (!startsWithSignature(content)) {
            throw new BusinessRuleException("INVALID_FILE_TYPE", "The file is not a valid PDF document");
        }
        String text = new String(content, StandardCharsets.ISO_8859_1);
        if (!text.substring(Math.max(0, text.length() - EOF_WINDOW)).contains("%%EOF")) {
            throw new BusinessRuleException("INVALID_FILE_TYPE", "The PDF file is incomplete or damaged");
        }
        for (Pattern p : ACTIVE_CONTENT) {
            if (p.matcher(text).find()) {
                throw new BusinessRuleException("UNSAFE_FILE",
                        "The PDF contains scripts, actions or embedded files and was rejected. Save it as a plain PDF and try again.");
            }
        }
    }

    /** Keeps only a safe display name: no path, limited characters, at most 100 characters. */
    public static String safeDisplayName(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^A-Za-z0-9._ ()-]", "_").trim();
        if (name.isEmpty() || name.equalsIgnoreCase(".pdf")) {
            name = "report.pdf";
        }
        if (name.length() > 100) {
            name = name.substring(0, 96) + ".pdf";
        }
        return name;
    }

    private static boolean startsWithSignature(byte[] content) {
        if (content.length < SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (content[i] != SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }
}
