package java_project_yn.bank_system.data.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Отпуснат кредит — клиент, вид, сума, срок и статус.
 * Към него се пази погасителен план (списък от вноски).
 */
@Entity
@Table(name = "credit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Credit extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "client_id")
    @JsonIgnore
    private Client client;

    @ManyToOne(optional = false)
    @JoinColumn(name = "credit_type_id")
    private CreditType creditType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CreditStatus status = CreditStatus.ACTIVE;

    @OneToMany(mappedBy = "credit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("monthNumber ASC")
    @Builder.Default
    private List<Installment> installments = new ArrayList<>();
}
