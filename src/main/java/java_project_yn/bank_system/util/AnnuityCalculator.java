package java_project_yn.bank_system.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Изчислява анюитетен погасителен план.
 *
 * <p>При анюитетния метод месечната вноска е постоянна за целия период:
 * <pre>
 *     P · r
 * A = ─────────────
 *     1 − (1 + r)⁻ⁿ
 * </pre>
 * където <b>P</b> е главницата, <b>r</b> — месечният лихвен процент (годишен / 12 / 100),
 * а <b>n</b> — броят месеци.
 *
 * <p>Лихвата за всеки месец се изчислява върху оставащата главница, която намалява.
 * В началото по-голяма част от вноската е лихва; към края — главница.
 * Всички парични суми се закръгляват до 2 знака (HALF_UP), а последната вноска
 * се изравнява така, че остатъкът да стане точно 0.
 */
public final class AnnuityCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int CALC_SCALE = 12;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private AnnuityCalculator() {
    }

    /** Един ред от погасителния план. */
    public record ScheduleRow(
            int monthNumber,
            BigDecimal payment,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal remaining) {
    }

    /** Месечен лихвен процент като десетична дроб (напр. 0.0070833 за 8.5% годишно). */
    public static BigDecimal monthlyRate(BigDecimal annualPercent) {
        return annualPercent
                .divide(BigDecimal.valueOf(100), CALC_SCALE, RM)
                .divide(BigDecimal.valueOf(12), CALC_SCALE, RM);
    }

    /** Размер на анюитетната месечна вноска, закръглен до 2 знака. */
    public static BigDecimal monthlyPayment(BigDecimal principal, BigDecimal annualPercent, int months) {
        if (months <= 0) {
            throw new IllegalArgumentException("Срокът трябва да е поне 1 месец");
        }
        BigDecimal r = monthlyRate(annualPercent);
        if (r.signum() == 0) {
            // Безлихвен кредит — равни вноски само от главница
            return principal.divide(BigDecimal.valueOf(months), MONEY_SCALE, RM);
        }
        BigDecimal onePlusR = BigDecimal.ONE.add(r);
        BigDecimal pow = onePlusR.pow(months);                       // (1 + r)^n
        BigDecimal numerator = principal.multiply(r).multiply(pow);  // P · r · (1+r)^n
        BigDecimal denominator = pow.subtract(BigDecimal.ONE);       // (1+r)^n − 1
        return numerator.divide(denominator, MONEY_SCALE, RM);
    }

    /**
     * Генерира пълния погасителен план месец по месец.
     */
    public static List<ScheduleRow> generate(BigDecimal principal, BigDecimal annualPercent, int months) {
        BigDecimal r = monthlyRate(annualPercent);
        BigDecimal payment = monthlyPayment(principal, annualPercent, months);

        List<ScheduleRow> rows = new ArrayList<>(months);
        BigDecimal balance = principal.setScale(MONEY_SCALE, RM);

        for (int month = 1; month <= months; month++) {
            BigDecimal interest = balance.multiply(r).setScale(MONEY_SCALE, RM);
            BigDecimal principalPart;
            BigDecimal pay;

            if (month == months) {
                // Последна вноска — изплаща целия остатък (елиминира закръгленията)
                principalPart = balance;
                pay = balance.add(interest).setScale(MONEY_SCALE, RM);
            } else {
                principalPart = payment.subtract(interest).setScale(MONEY_SCALE, RM);
                pay = payment;
            }

            balance = balance.subtract(principalPart).setScale(MONEY_SCALE, RM);
            if (balance.signum() < 0) {
                balance = BigDecimal.ZERO.setScale(MONEY_SCALE, RM);
            }
            rows.add(new ScheduleRow(month, pay, principalPart, interest, balance));
        }
        return rows;
    }
}
