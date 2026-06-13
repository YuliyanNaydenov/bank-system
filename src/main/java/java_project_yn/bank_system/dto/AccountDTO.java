package java_project_yn.bank_system.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountDTO {
    private long id;
    private String iban;
    private BigDecimal balance;
    private String status;
    private long ownerId;
    private String ownerName;
}
