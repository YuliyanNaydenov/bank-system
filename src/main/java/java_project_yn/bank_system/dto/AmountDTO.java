package java_project_yn.bank_system.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * Сума за депозит или теглене.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmountDTO {

    @NotNull(message = "Сумата е задължителна")
    @DecimalMin(value = "0.0", inclusive = false, message = "Сумата трябва да е положителна")
    private BigDecimal amount;
}
