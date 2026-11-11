package com.medicare.reporting;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.medicare.entity.HospitalSettings;
import com.medicare.entity.Invoice;
import com.medicare.entity.InvoiceItem;
import com.medicare.entity.Payment;
import com.medicare.enums.InvoiceStatus;
import com.medicare.enums.PaymentType;

import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfGState;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/** Renders an invoice with its payments as an A4 PDF. Cancelled invoices carry a "CANCELLED" watermark. */
@Component
public class InvoicePdfGenerator {

    private static final Color BRAND = new Color(13, 148, 136);
    private static final Color LINE = new Color(226, 232, 240);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    public byte[] generate(Invoice inv, List<Payment> payments, HospitalSettings hospital, ZoneId zone) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 42, 42, 42, 54);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new Footer(inv.getStatus() == InvoiceStatus.CANCELLED));
        doc.addTitle("Invoice " + inv.getInvoiceNo());
        doc.addCreator("MediCare");
        doc.open();

        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND);
        Font h = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
        Font white = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

        doc.add(new Paragraph(hospital.getHospitalName(), title));
        doc.add(new Paragraph(join(hospital.getAddress(), hospital.getPhone(), hospital.getEmail()), small));
        doc.add(Chunk.NEWLINE);

        PdfPTable meta = new PdfPTable(2);
        meta.setWidthPercentage(100);
        meta.addCell(box("INVOICE", inv.getInvoiceNo() + "\nIssued: " + DATE_TIME.format(inv.getIssuedAt().atZone(zone))
                + "\nStatus: " + inv.getStatus().name().replace('_', ' '), h, body));
        meta.addCell(box("BILLED TO", inv.getPatient().getUser().getFullName() + "\nPatient ID " + inv.getPatient().getPatientCode(), h, body));
        doc.add(meta);
        doc.add(Chunk.NEWLINE);

        PdfPTable items = new PdfPTable(new float[]{0.4f, 3.6f, 0.7f, 1.2f, 1.3f});
        items.setWidthPercentage(100);
        for (String col : new String[]{"#", "Description", "Qty", "Unit price", "Amount"}) {
            PdfPCell c = new PdfPCell(new Phrase(col, white));
            c.setBackgroundColor(BRAND);
            c.setPadding(5);
            c.setBorder(Rectangle.NO_BORDER);
            items.addCell(c);
        }
        int n = 1;
        for (InvoiceItem i : inv.getItems()) {
            items.addCell(cell(String.valueOf(n++), body, Element.ALIGN_LEFT));
            items.addCell(cell(i.getDescription(), body, Element.ALIGN_LEFT));
            items.addCell(cell(String.valueOf(i.getQuantity()), body, Element.ALIGN_RIGHT));
            items.addCell(cell(money(i.getUnitPrice()), body, Element.ALIGN_RIGHT));
            items.addCell(cell(money(i.getLineTotal()), body, Element.ALIGN_RIGHT));
        }
        doc.add(items);

        PdfPTable totals = new PdfPTable(new float[]{4f, 1.5f});
        totals.setWidthPercentage(60);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setSpacingBefore(8);
        row(totals, "Subtotal", money(inv.getSubtotal()), body);
        if (inv.getDiscountAmount().signum() > 0) {
            row(totals, "Discount", "- " + money(inv.getDiscountAmount()), body);
        }
        row(totals, "Tax (" + inv.getTaxRatePercent().stripTrailingZeros().toPlainString() + "%)", money(inv.getTaxAmount()), body);
        row(totals, "Total", money(inv.getTotalAmount()), bold);
        row(totals, "Paid", money(inv.getAmountPaid()), body);
        if (inv.getRefundedAmount().signum() > 0) {
            row(totals, "Refunded", "- " + money(inv.getRefundedAmount()), body);
        }
        row(totals, "Balance due", money(inv.balanceDue()), bold);
        doc.add(totals);

        if (!payments.isEmpty()) {
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("Payments", h));
            PdfPTable pay = new PdfPTable(new float[]{1.6f, 1.8f, 1.6f, 1.8f, 1.3f});
            pay.setWidthPercentage(100);
            pay.setSpacingBefore(4);
            for (Payment p : payments) {
                pay.addCell(cell(p.getPaymentNo(), small, Element.ALIGN_LEFT));
                pay.addCell(cell(DATE_TIME.format(p.getPaidAt().atZone(zone)), small, Element.ALIGN_LEFT));
                pay.addCell(cell(p.getMethod().name().replace("_SIMULATED", "").replace('_', ' '), small, Element.ALIGN_LEFT));
                pay.addCell(cell(p.getTransactionReference() == null ? "-" : p.getTransactionReference(), small, Element.ALIGN_LEFT));
                pay.addCell(cell((p.getType() == PaymentType.REFUND ? "- " : "") + money(p.getAmount()), small, Element.ALIGN_RIGHT));
            }
            doc.add(pay);
        }
        if (inv.getNotes() != null) {
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("Notes: " + inv.getNotes(), small));
        }
        if (inv.getStatus() == InvoiceStatus.CANCELLED) {
            doc.add(new Paragraph("Cancelled: " + inv.getCancellationReason(), small));
        }
        doc.close();
        return out.toByteArray();
    }

    static String money(BigDecimal v) {
        return "Rs. " + v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void row(PdfPTable t, String label, String value, Font f) {
        PdfPCell a = new PdfPCell(new Phrase(label, f));
        a.setBorder(Rectangle.NO_BORDER);
        a.setPadding(3);
        PdfPCell b = new PdfPCell(new Phrase(value, f));
        b.setBorder(Rectangle.NO_BORDER);
        b.setPadding(3);
        b.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(a);
        t.addCell(b);
    }

    private static PdfPCell cell(String v, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(v, f));
        c.setPadding(5);
        c.setBorderColor(LINE);
        c.setHorizontalAlignment(align);
        return c;
    }

    private static PdfPCell box(String label, String value, Font h, Font body) {
        PdfPCell c = new PdfPCell();
        c.setPadding(6);
        c.setBorderColor(LINE);
        c.addElement(new Paragraph(label, h));
        c.addElement(new Paragraph(value, body));
        return c;
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                sb.append(sb.isEmpty() ? "" : "  ·  ").append(p);
            }
        }
        return sb.toString();
    }

    private static final class Footer extends PdfPageEventHelper {
        private final boolean cancelled;

        Footer(boolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase("MediCare academic prototype - fictional data - payments are recorded, not processed.",
                            FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY)),
                    (document.left() + document.right()) / 2, document.bottom() - 24, 0);
            if (cancelled) {
                PdfContentByte under = writer.getDirectContentUnder();
                under.saveState();
                PdfGState gs = new PdfGState();
                gs.setFillOpacity(0.15f);
                under.setGState(gs);
                ColumnText.showTextAligned(under, Element.ALIGN_CENTER,
                        new Phrase("CANCELLED", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 80, Color.RED)), 297, 421, 45);
                under.restoreState();
            }
        }
    }
}
