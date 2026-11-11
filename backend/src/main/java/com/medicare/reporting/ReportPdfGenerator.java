package com.medicare.reporting;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/** Renders a tabular report as a landscape A4 PDF with repeating headers and page numbers. */
@Component
public class ReportPdfGenerator {

    private static final Color BRAND = new Color(13, 148, 136);

    public byte[] generate(String hospitalName, String title, String subtitle, List<String> header, List<List<String>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 32, 40);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, Document d) {
                ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_CENTER,
                        new Phrase(hospitalName + "  ·  " + title + "  ·  page " + w.getPageNumber() + "  ·  fictional demo data",
                                FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY)),
                        (d.left() + d.right()) / 2, d.bottom() - 18, 0);
            }
        });
        doc.addTitle(title);
        doc.addCreator("MediCare");
        doc.open();
        doc.add(new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BRAND)));
        doc.add(new Paragraph(subtitle, FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY)));

        PdfPTable table = new PdfPTable(header.size());
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        table.setHeaderRows(1);
        Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Color.WHITE);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Color.BLACK);
        for (String h : header) {
            PdfPCell c = new PdfPCell(new Phrase(h, head));
            c.setBackgroundColor(BRAND);
            c.setPadding(3);
            c.setBorder(Rectangle.NO_BORDER);
            table.addCell(c);
        }
        int n = 0;
        for (List<String> row : rows) {
            Color bg = n++ % 2 == 0 ? Color.WHITE : new Color(240, 253, 250);
            for (String v : row) {
                PdfPCell c = new PdfPCell(new Phrase(v == null ? "" : v, body));
                c.setPadding(3);
                c.setBackgroundColor(bg);
                c.setBorderColor(new Color(226, 232, 240));
                table.addCell(c);
            }
        }
        if (rows.isEmpty()) {
            PdfPCell c = new PdfPCell(new Phrase("No records in this period.", body));
            c.setColspan(header.size());
            c.setPadding(6);
            table.addCell(c);
        }
        doc.add(table);
        doc.close();
        return out.toByteArray();
    }
}
