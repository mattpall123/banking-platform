package com.bank.backend.statement;

import com.bank.backend.statement.service.StatementData;
import com.bank.backend.statement.service.StatementPdfRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates a real PDF on disk so you can open it in Preview and eyeball
 * the layout. Not a unit test in the strict sense — it's a "show me what
 * this looks like" tool.
 *
 * Run:  mvn -Dtest=StatementPdfRendererSmokeTest test
 * Open: backend/build/sample-statement.pdf
 */
class StatementPdfRendererSmokeTest {

    @Test
    @DisplayName("renders a sample statement to disk")
    void renders_sample_statement() throws Exception {
        var renderer = new StatementPdfRenderer();

        var data = new StatementData(
            "Frontend Tester",
            "100000000001",
            "Chequing",
            "CAD",
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 4, 30),
            new BigDecimal("1000.00"),
            new BigDecimal("1342.50"),
            List.of(
                new StatementData.Line(
                    Instant.parse("2026-04-02T09:14:00Z"),
                    "Coffee shop",
                    new BigDecimal("-4.75"),
                    new BigDecimal("995.25")
                ),
                new StatementData.Line(
                    Instant.parse("2026-04-05T13:30:00Z"),
                    "Payroll deposit",
                    new BigDecimal("1500.00"),
                    new BigDecimal("2495.25")
                ),
                new StatementData.Line(
                    Instant.parse("2026-04-07T19:02:00Z"),
                    "Transfer to savings",
                    new BigDecimal("-200.00"),
                    new BigDecimal("2295.25")
                ),
                new StatementData.Line(
                    Instant.parse("2026-04-12T11:45:00Z"),
                    "Hydro bill",
                    new BigDecimal("-87.00"),
                    new BigDecimal("2208.25")
                ),
                new StatementData.Line(
                    Instant.parse("2026-04-15T08:00:00Z"),
                    "Interac e-Transfer from M. Park",
                    new BigDecimal("65.00"),
                    new BigDecimal("2273.25")
                ),
                new StatementData.Line(
                    Instant.parse("2026-04-22T16:30:00Z"),
                    "Grocery store",
                    new BigDecimal("-130.75"),
                    new BigDecimal("2142.50")
                ),
                new StatementData.Line(
                    Instant.parse("2026-04-28T10:00:00Z"),
                    "Transfer from savings",
                    new BigDecimal("200.00"),
                    new BigDecimal("2342.50")
                ),
                new StatementData.Line(
                    Instant.parse("2026-04-30T14:20:00Z"),
                    "ATM withdrawal",
                    new BigDecimal("-1000.00"),
                    new BigDecimal("1342.50")
                )
            )
        );

        byte[] pdf = renderer.render(data);

        Path out = Path.of("build", "sample-statement.pdf");
        Files.createDirectories(out.getParent());
        Files.write(out, pdf);

        System.out.println("=== Wrote PDF to: " + out.toAbsolutePath() + " ===");
        System.out.println("Size: " + pdf.length + " bytes");

        assertThat(pdf).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F'); // PDF magic header
        assertThat(pdf.length).isGreaterThan(1500);
    }
}