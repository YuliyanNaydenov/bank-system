package java_project_yn.bank_system.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Вид кредит (потребителски / ипотечен и др.).
 * Параметрите се конфигурират от администратор:
 * годишен лихвен процент, максимална сума и максимален срок.
 */
@Entity
@Table(name = "credit_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditType extends BaseEntity {

    @Column(name = "name", unique = true, nullable = false)
    @NotBlank(message = "Името на кредитния вид е задължително")
    private String name;

    /** Годишен лихвен процент, напр. 8.50 означава 8.50%. */
    @Column(name = "annual_interest_rate", nullable = false, precision = 6, scale = 2)
    @NotNull(message = "Лихвеният процент е задължителен")
    @DecimalMin(value = "0.0", inclusive = false, message = "Лихвеният процент трябва да е положителен")
    private BigDecimal annualInterestRate;

    @Column(name = "max_amount", nullable = false, precision = 19, scale = 2)
    @NotNull(message = "Максималната сума е задължителна")
    @DecimalMin(value = "0.0", inclusive = false, message = "Максималната сума трябва да е положителна")
    private BigDecimal maxAmount;

    @Column(name = "max_term_months", nullable = false)
    @NotNull(message = "Максималният срок е задължителен")
    @Min(value = 1, message = "Максималният срок трябва да е поне 1 месец")
    private Integer maxTermMonths;
}
