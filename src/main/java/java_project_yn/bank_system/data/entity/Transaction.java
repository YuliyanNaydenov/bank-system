package java_project_yn.bank_system.data.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Движение по сметка — депозит, теглене или превод.
 * Пази момента, сумата, наличността след операцията и (за преводи) насрещния IBAN.
 */
@Entity
@Table(name = "transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "tx_timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id")
    @JsonIgnore
    private Account account;

    /** Насрещен IBAN при превод (иначе null). */
    @Column(name = "counterparty_iban")
    private String counterpartyIban;

    @Column(name = "description")
    private String description;
}
