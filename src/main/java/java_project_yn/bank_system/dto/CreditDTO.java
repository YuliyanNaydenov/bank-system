package java_project_yn.bank_system.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditDTO {
    private long id;
    private long clientId;
    private String clientName;
    private long creditTypeId;
    private String creditTypeName;
    private BigDecimal annualInterestRate;
    private BigDecimal amount;
    private int termMonths;
    private LocalDate startDate;
    private String status;
    private BigDecimal monthlyPayment;
    private BigDecimal totalToRepay;
    private BigDecimal remainingBalance;
    private int paidInstallments;
    private boolean hasOverdue;
    private List<InstallmentDTO> installments;
}
