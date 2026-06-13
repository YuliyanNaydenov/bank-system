package java_project_yn.bank_system.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тестове за анюитетния калкулатор — ядрото на бизнес логиката.
 */
class AnnuityCalculatorTest {

    @Test
    void monthlyPayment_knownValue() {
        // 10000 лв., 8.5% годишно, 12 месеца → ~872.20 лв. месечна вноска
        BigDecimal payment = AnnuityCalculator.monthlyPayment(
                new BigDecimal("10000"), new BigDecimal("8.5"), 12);
        assertEquals(new BigDecimal("872.20"), payment);
    }

    @Test
    void generate_returnsRowForEveryMonth() {
        List<AnnuityCalculator.ScheduleRow> rows = AnnuityCalculator.generate(
                new BigDecimal("10000"), new BigDecimal("8.5"), 12);
        assertEquals(12, rows.size());
        assertEquals(1, rows.get(0).monthNumber());
        assertEquals(12, rows.get(11).monthNumber());
    }

    @Test
    void generate_principalSumEqualsLoanAndEndsAtZero() {
        BigDecimal principal = new BigDecimal("10000");
        List<AnnuityCalculator.ScheduleRow> rows =
                AnnuityCalculator.generate(principal, new BigDecimal("8.5"), 12);

        BigDecimal principalSum = rows.stream()
                .map(AnnuityCalculator.ScheduleRow::principal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, principalSum.compareTo(principal),
                "Сборът на главниците трябва да е равен на отпуснатата сума");
        assertEquals(0, rows.get(rows.size() - 1).remaining().compareTo(BigDecimal.ZERO),
                "Остатъкът след последната вноска трябва да е 0");
    }

    @Test
    void generate_paymentEqualsPrincipalPlusInterest() {
        List<AnnuityCalculator.ScheduleRow> rows =
                AnnuityCalculator.generate(new BigDecimal("25000"), new BigDecimal("5.0"), 24);
        for (AnnuityCalculator.ScheduleRow row : rows) {
            assertEquals(0, row.payment().compareTo(row.principal().add(row.interest())),
                    "Вноска = главница + лихва за месец " + row.monthNumber());
        }
    }

    @Test
    void generate_interestDecreasesAndPrincipalIncreases() {
        List<AnnuityCalculator.ScheduleRow> rows =
                AnnuityCalculator.generate(new BigDecimal("50000"), new BigDecimal("6.0"), 36);
        for (int i = 1; i < rows.size(); i++) {
            assertTrue(rows.get(i).interest().compareTo(rows.get(i - 1).interest()) <= 0,
                    "Лихвата трябва да намалява");
            assertTrue(rows.get(i).principal().compareTo(rows.get(i - 1).principal()) >= 0,
                    "Главницата трябва да нараства");
        }
    }

    @Test
    void zeroInterest_splitsPrincipalEvenly() {
        List<AnnuityCalculator.ScheduleRow> rows =
                AnnuityCalculator.generate(new BigDecimal("5000"), new BigDecimal("0"), 10);
        assertEquals(new BigDecimal("500.00"), rows.get(0).payment());
        assertEquals(0, rows.get(0).interest().compareTo(BigDecimal.ZERO));
        assertEquals(0, rows.get(9).remaining().compareTo(BigDecimal.ZERO));
    }

    @Test
    void invalidTerm_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> AnnuityCalculator.monthlyPayment(new BigDecimal("1000"), new BigDecimal("5"), 0));
    }
}
