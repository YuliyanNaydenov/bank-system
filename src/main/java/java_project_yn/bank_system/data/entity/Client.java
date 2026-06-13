package java_project_yn.bank_system.data.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Базов клас за клиент на банката.
 * Използва се JPA наследяване SINGLE_TABLE с дискриминатор client_type,
 * така че физическите и юридическите лица се пазят в една таблица "client".
 */
@Entity
@Table(name = "client")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "client_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
public abstract class Client extends BaseEntity {

    /** Потребителско име за връзка с локалния акаунт (роля "client"). */
    @Column(name = "username", unique = true)
    private String username;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<Account> accounts = new HashSet<>();

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<Credit> credits = new HashSet<>();

    /** Човеко-четимо име (имена на лицето или име на фирмата). */
    @Transient
    public abstract String getDisplayName();

    /** Идентификатор — ЕГН за физическо лице, ЕИК за юридическо. */
    @Transient
    public abstract String getIdentifier();

    /** Етикет на вида клиент за презентацията. */
    @Transient
    public abstract String getTypeLabel();
}
