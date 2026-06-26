package java_project_yn.bank_system.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDTO {
    private long id;
    private String type;
    private BigDecimal amount;
    private LocalDateTime timestamp;
    private BigDecimal balanceAfter;
    private String counterpartyIban;
    private String description;
    private String accountIban;
}
