package java_project_yn.bank_system.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * Превод от текущата сметка към сметка с подаден IBAN.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferDTO {

    @NotBlank(message = "IBAN на получателя е задължителен")
    private String toIban;

    @NotNull(message = "Сумата е задължителна")
    @DecimalMin(value = "0.0", inclusive = false, message = "Сумата трябва да е положителна")
    private BigDecimal amount;
}
