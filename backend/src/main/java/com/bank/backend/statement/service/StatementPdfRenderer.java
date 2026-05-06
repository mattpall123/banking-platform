package com.bank.backend.statement.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders a StatementData into PDF bytes.
 *
 * Layout (top to bottom):
 *   1. Header band: bank name + statement title + period
 *   2. Account info block: customer name, masked account number, type, currency
 *   3. Summary box: opening balance, closing balance, transaction count
 *   4. Transactions table: date, description, amount (red/green), running balance
 *   5. Footer disclaimer (small print)
 */
@Service
public class StatementPdfRenderer {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withLocale(Locale.CANADA);
    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("MMM d").withLocale(Locale.CANADA);

    private static final Font H1     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(20, 20, 20));
    private static final Font H2     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(40, 40, 40));
    private static final Font LABEL  = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(110, 110, 110));
    private static final Font VALUE  = FontFactory.getFont(FontFactory.HELVETICA, 11, new Color(20, 20, 20));
    private static final Font BODY   = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(20, 20, 20));
    private static final Font BODY_B = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(20, 20, 20));
    private static final Font SMALL  = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(140, 140, 140));
    private static final Font GREEN  = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(20, 130, 70));
    private static final Font RED    = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(180, 30, 50));

    public byte[] render(StatementData data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 50, 50, 50, 50);

        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.addTitle("Account Statement");
            doc.addCreator("Banking Platform");

            addHeader(doc, data);
            addAccountInfo(doc, data);
            addSummaryBox(doc, data);
            addTransactionsTable(doc, data);
            addFooter(doc);

        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render PDF", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }

        return out.toByteArray();
    }

    private void addHeader(Document doc, StatementData d) throws DocumentException {
        Paragraph bankName = new Paragraph("Banking Platform", H1);
        bankName.setSpacingAfter(2);
        doc.add(bankName);

        Paragraph subtitle = new Paragraph("Account Statement", H2);
        subtitle.setSpacingAfter(2);
        doc.add(subtitle);

        Paragraph period = new Paragraph(
            "Period: " + DATE_FMT.format(d.periodStart()) + " to " + DATE_FMT.format(d.periodEnd()),
            LABEL
        );
        period.setSpacingAfter(20);
        doc.add(period);

        // Horizontal rule
        doc.add(hr());
    }

    private void addAccountInfo(Document doc, StatementData d) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setSpacingBefore(15);
        t.setSpacingAfter(15);
        t.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        t.addCell(labelValue("Account holder", d.customerName()));
        t.addCell(labelValue("Account type", d.accountType()));
        t.addCell(labelValue("Account number", "••••" + tail4(d.accountNumber())));
        t.addCell(labelValue("Currency", d.currency()));

        doc.add(t);
    }

    private void addSummaryBox(Document doc, StatementData d) throws DocumentException {
        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setSpacingBefore(8);
        t.setSpacingAfter(20);

        t.addCell(boxCell("Opening balance", money(d.openingBalance(), d.currency())));
        t.addCell(boxCell("Transactions",   String.valueOf(d.lines().size())));
        t.addCell(boxCell("Closing balance", money(d.closingBalance(), d.currency())));

        doc.add(t);
    }

    private void addTransactionsTable(Document doc, StatementData d) throws DocumentException {
        Paragraph title = new Paragraph("Transactions", H2);
        title.setSpacingBefore(10);
        title.setSpacingAfter(8);
        doc.add(title);

        if (d.lines().isEmpty()) {
            doc.add(new Paragraph("No transactions in this period.", LABEL));
            return;
        }

        PdfPTable t = new PdfPTable(new float[]{ 1.5f, 5f, 2f, 2.5f });
        t.setWidthPercentage(100);
        t.setHeaderRows(1);

        t.addCell(headerCell("Date"));
        t.addCell(headerCell("Description"));
        t.addCell(headerCellRight("Amount"));
        t.addCell(headerCellRight("Balance"));

        for (StatementData.Line l : d.lines()) {
            t.addCell(bodyCell(DAY_FMT.format(l.occurredAt().atZone(ZoneId.systemDefault()))));
            t.addCell(bodyCell(l.description()));
            t.addCell(amountCell(l.signedAmount(), d.currency()));
            t.addCell(bodyCellRight(money(l.runningBalance(), d.currency())));
        }

        doc.add(t);
    }

    private void addFooter(Document doc) throws DocumentException {
        Paragraph p = new Paragraph(
            "This statement was generated automatically. " +
            "If you find an error, please contact us within 30 days.",
            SMALL
        );
        p.setSpacingBefore(30);
        doc.add(p);
    }

    // ---- helpers ----

    private PdfPCell labelValue(String label, String value) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", LABEL));
        p.add(new Chunk(value, VALUE));
        PdfPCell c = new PdfPCell(p);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(4);
        return c;
    }

    private PdfPCell boxCell(String label, String value) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", LABEL));
        p.add(new Chunk(value, H2));
        PdfPCell c = new PdfPCell(p);
        c.setBorderColor(new Color(220, 220, 220));
        c.setBackgroundColor(new Color(248, 248, 248));
        c.setPadding(10);
        return c;
    }

    private PdfPCell headerCell(String text) {
        PdfPCell c = new PdfPCell(new Paragraph(text, BODY_B));
        c.setBackgroundColor(new Color(245, 245, 245));
        c.setBorder(Rectangle.BOTTOM);
        c.setPadding(6);
        return c;
    }

    private PdfPCell headerCellRight(String text) {
        PdfPCell c = headerCell(text);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private PdfPCell bodyCell(String text) {
        PdfPCell c = new PdfPCell(new Paragraph(text, BODY));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(new Color(235, 235, 235));
        c.setPadding(5);
        return c;
    }

    private PdfPCell bodyCellRight(String text) {
        PdfPCell c = bodyCell(text);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private PdfPCell amountCell(BigDecimal signed, String currency) {
        boolean positive = signed.signum() >= 0;
        Font f = positive ? GREEN : RED;
        String prefix = positive ? "+" : "";
        Paragraph p = new Paragraph(prefix + money(signed, currency), f);
        PdfPCell c = new PdfPCell(p);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(new Color(235, 235, 235));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setPadding(5);
        return c;
    }

    private static Paragraph hr() {
        Paragraph p = new Paragraph(new Chunk(new com.lowagie.text.pdf.draw.LineSeparator(0.5f, 100, new Color(220, 220, 220), Element.ALIGN_CENTER, -2)));
        return p;
    }

    private static String tail4(String s) {
        return s == null || s.length() < 4 ? s : s.substring(s.length() - 4);
    }

    private static String money(BigDecimal amount, String currency) {
        // Negative-aware formatting: -$4.75 CAD reads cleaner than $-4.75 CAD
        boolean neg = amount.signum() < 0;
        return String.format(Locale.CANADA, "%s$%,.2f %s",
            neg ? "-" : "",
            amount.abs(),
            currency);
    }
}