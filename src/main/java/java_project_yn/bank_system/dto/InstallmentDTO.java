package java_project_yn.bank_system.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstallmentDTO {
    private long id;
    private int monthNumber;
    private BigDecimal paymentAmount;
    private BigDecimal principalPart;
    private BigDecimal interestPart;
    private BigDecimal remainingBalance;
    private boolean paid;
}
