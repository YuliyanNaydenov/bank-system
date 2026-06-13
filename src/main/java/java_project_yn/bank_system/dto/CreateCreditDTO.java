package java_project_yn.bank_system.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCreditDTO {

    @NotNull(message = "Изберете клиент")
    private Long clientId;

    @NotNull(message = "Изберете вид кредит")
    private Long creditTypeId;

    @NotNull(message = "Сумата е задължителна")
    @DecimalMin(value = "0.0", inclusive = false, message = "Сумата трябва да е положителна")
    private BigDecimal amount;

    @NotNull(message = "Срокът е задължителен")
    @Min(value = 1, message = "Срокът трябва да е поне 1 месец")
    private Integer termMonths;
}
