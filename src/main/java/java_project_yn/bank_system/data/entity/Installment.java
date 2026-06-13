package java_project_yn.bank_system.data.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Една вноска от погасителния план (анюитетен метод).
 * Пази размер на вноската, частта главница, частта лихва и остатъка след плащане.
 */
@Entity
@Table(name = "installment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Installment extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "credit_id")
    @JsonIgnore
    private Credit credit;

    /** Пореден номер на месеца (1..n). */
    @Column(name = "month_number", nullable = false)
    private int monthNumber;

    @Column(name = "payment_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "principal_part", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalPart;

    @Column(name = "interest_part", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestPart;

    @Column(name = "remaining_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingBalance;

    @Column(name = "paid", nullable = false)
    @Builder.Default
    private boolean paid = false;
}
