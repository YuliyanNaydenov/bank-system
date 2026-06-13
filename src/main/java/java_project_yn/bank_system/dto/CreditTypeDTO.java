package java_project_yn.bank_system.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditTypeDTO {
    private long id;
    private String name;
    private BigDecimal annualInterestRate;
    private BigDecimal maxAmount;
    private Integer maxTermMonths;
}
