package java_project_yn.bank_system.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCreditTypeDTO {

    @NotBlank(message = "Името на кредитния вид е задължително")
    private String name;

    @NotNull(message = "Лихвеният процент е задължителен")
    @DecimalMin(value = "0.0", inclusive = false, message = "Лихвеният процент трябва да е положителен")
    private BigDecimal annualInterestRate;

    @NotNull(message = "Максималната сума е задължителна")
    @DecimalMin(value = "0.0", inclusive = false, message = "Максималната сума трябва да е положителна")
    private BigDecimal maxAmount;

    @NotNull(message = "Максималният срок е задължителен")
    @Min(value = 1, message = "Максималният срок трябва да е поне 1 месец")
    private Integer maxTermMonths;
}
