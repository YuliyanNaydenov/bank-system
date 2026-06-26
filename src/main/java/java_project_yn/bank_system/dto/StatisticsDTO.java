package java_project_yn.bank_system.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Обобщени числа за дашборда.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatisticsDTO {
    private long totalClients;
    private long totalAccounts;
    private long activeAccounts;
    private long activeCredits;
    private long paidCredits;
    private BigDecimal totalBalance;
    private BigDecimal totalGranted;
    private List<ChartPointDTO> creditsByType;
}
