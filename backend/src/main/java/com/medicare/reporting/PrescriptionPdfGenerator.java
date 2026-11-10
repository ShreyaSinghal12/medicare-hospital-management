package com.medicare.reporting;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.medicare.entity.HospitalSettings;
import com.medicare.entity.Prescription;
import com.medicare.entity.PrescriptionItem;
import com.medicare.enums.PrescriptionStatus;

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

/**
 * Renders a prescription version as an A4 PDF (OpenPDF). Superseded versions carry a large
 * "SUPERSEDED" watermark. The document contains only what the doctor entered.
 */
@Component
public class PrescriptionPdfGenerator {

    private static final Color BRAND = new Color(13, 148, 136);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    public byte[] generate(Prescription p, HospitalSettings hospital, ZoneId zone) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 42, 42, 42, 54);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        boolean superseded = p.getStatus() == PrescriptionStatus.SUPERSEDED;
        writer.setPageEvent(new Footer(superseded));
        doc.addTitle("Prescription " + p.getPrescriptionNo());
        doc.addCreator("MediCare");
        doc.open();

        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND);
        Font h = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
        Font white = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

        doc.add(new Paragraph(hospital.getHospitalName(), title));
        doc.add(new Paragraph(join(hospital.getAddress(), hospital.getPhone(), hospital.getEmail()), small));
        doc.add(Chunk.NEWLINE);

        PdfPTable meta = new PdfPTable(2);
        meta.setWidthPercentage(100);
        var pt = p.getPatient();
        var d = p.getDoctor();
        int age = Period.between(pt.getDateOfBirth(), p.getConsultation().getAppointment().getAppointmentDate()).getYears();
        meta.addCell(box("PRESCRIPTION", p.getPrescriptionNo() + "  (version " + p.getVersionNumber() + ")\n"
                + "Issued: " + (p.getFinalizedAt() == null ? "—" : DATE_TIME.format(p.getFinalizedAt().atZone(zone))), h, body));
        meta.addCell(box("PATIENT", pt.getUser().getFullName() + "\nID " + pt.getPatientCode() + " · " + age + " years", h, body));
        meta.addCell(box("PRESCRIBER", d.getUser().getFullName() + ", " + d.getQualifications() + "\n"
                + d.getSpecialization() + " · " + d.getDepartment().getName() + "\nLicence " + d.getLicenseNumber(), h, body));
        meta.addCell(box("CONSULTATION", DATE.format(p.getConsultation().getAppointment().getAppointmentDate())
                + "\nRef " + p.getConsultation().getAppointment().getReferenceNo(), h, body));
        doc.add(meta);
        doc.add(Chunk.NEWLINE);

        Paragraph rx = new Paragraph("Rx", FontFactory.getFont(FontFactory.TIMES_BOLDITALIC, 22, BRAND));
        doc.add(rx);

        PdfPTable items = new PdfPTable(new float[]{0.4f, 2.6f, 1.3f, 1.4f, 1.2f, 0.8f});
        items.setWidthPercentage(100);
        items.setSpacingBefore(4);
        for (String col : new String[]{"#", "Medicine & strength", "Dosage", "Frequency", "Duration", "Qty"}) {
            PdfPCell c = new PdfPCell(new Phrase(col, white));
            c.setBackgroundColor(BRAND);
            c.setPadding(5);
            c.setBorder(Rectangle.NO_BORDER);
            items.addCell(c);
        }
        int n = 1;
        for (PrescriptionItem i : p.getItems()) {
            Color bg = n % 2 == 0 ? new Color(240, 253, 250) : Color.WHITE;
            String medicine = i.getMedicineName() + (i.getStrength() == null ? "" : " " + i.getStrength())
                    + (i.getInstructions() == null ? "" : "\n" + i.getInstructions());
            for (String v : new String[]{String.valueOf(n), medicine, i.getDosage(), i.getFrequency(), i.getDuration(),
                    i.getQuantity() == null ? "—" : String.valueOf(i.getQuantity())}) {
                PdfPCell c = new PdfPCell(new Phrase(v, body));
                c.setBackgroundColor(bg);
                c.setPadding(5);
                c.setBorderColor(new Color(226, 232, 240));
                items.addCell(c);
            }
            n++;
        }
        doc.add(items);

        if (p.getGeneralInstructions() != null) {
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("General instructions", h));
            doc.add(new Paragraph(p.getGeneralInstructions(), body));
        }
        if (p.getVersionNumber() > 1 && p.getAmendmentReason() != null) {
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("Revision of version " + (p.getVersionNumber() - 1) + ": " + p.getAmendmentReason(), small));
        }
        doc.add(Chunk.NEWLINE);
        Paragraph sign = new Paragraph("Electronically finalized by " + d.getUser().getFullName(), body);
        sign.setAlignment(Element.ALIGN_RIGHT);
        doc.add(sign);
        doc.close();
        return out.toByteArray();
    }

    private static PdfPCell box(String label, String value, Font h, Font body) {
        PdfPCell c = new PdfPCell();
        c.setPadding(6);
        c.setBorderColor(new Color(226, 232, 240));
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

    /** Footer disclaimer on every page, plus the watermark for superseded versions. */
    private static final class Footer extends PdfPageEventHelper {
        private final boolean superseded;

        Footer(boolean superseded) {
            this.superseded = superseded;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase("MediCare academic prototype - fictional data - not valid for dispensing outside this demo.",
                            FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY)),
                    (document.left() + document.right()) / 2, document.bottom() - 24, 0);
            if (superseded) {
                PdfContentByte under = writer.getDirectContentUnder();
                under.saveState();
                PdfGState gs = new PdfGState();
                gs.setFillOpacity(0.15f);
                under.setGState(gs);
                ColumnText.showTextAligned(under, Element.ALIGN_CENTER,
                        new Phrase("SUPERSEDED", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 80, Color.RED)),
                        297, 421, 45);
                under.restoreState();
            }
        }
    }
}
