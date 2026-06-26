package java_project_yn.bank_system.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

/**
 * Физическо лице — име, фамилия и ЕГН.
 */
@Entity
@DiscriminatorValue("INDIVIDUAL")
@Getter
@Setter
@NoArgsConstructor
public class IndividualClient extends Client {

    @NotBlank(message = "Името е задължително")
    @Column(name = "first_name")
    private String firstName;

    @NotBlank(message = "Фамилията е задължителна")
    @Column(name = "last_name")
    private String lastName;

    @Column(name = "egn", unique = true)
    @NotBlank(message = "ЕГН е задължително")
    @Pattern(regexp = "\\d{10}", message = "ЕГН трябва да съдържа точно 10 цифри")
    private String egn;

    @Override
    public String getDisplayName() {
        return firstName + " " + lastName;
    }

    @Override
    public String getIdentifier() {
        return egn;
    }

    @Override
    public String getTypeLabel() {
        return "Физическо лице";
    }
}
