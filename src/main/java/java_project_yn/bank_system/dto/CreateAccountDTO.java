package java_project_yn.bank_system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAccountDTO {

    @NotNull(message = "Изберете притежател на сметката")
    private Long ownerId;

    /** Начална наличност (по подразбиране 0). */
    @PositiveOrZero(message = "Началната наличност не може да е отрицателна")
    @Builder.Default
    private BigDecimal initialBalance = BigDecimal.ZERO;
}
