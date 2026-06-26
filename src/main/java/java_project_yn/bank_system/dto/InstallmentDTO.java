package java_project_yn.bank_system.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstallmentDTO {
    private long id;
    private int monthNumber;
    private LocalDate dueDate;
    private BigDecimal paymentAmount;
    private BigDecimal principalPart;
    private BigDecimal interestPart;
    private BigDecimal remainingBalance;
    private boolean paid;
    private boolean overdue;
}
