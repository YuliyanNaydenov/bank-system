package java_project_yn.bank_system.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

/**
 * Юридическо лице — име на фирма, ЕИК и представител.
 */
@Entity
@DiscriminatorValue("COMPANY")
@Getter
@Setter
@NoArgsConstructor
public class CompanyClient extends Client {

    @NotBlank(message = "Името на фирмата е задължително")
    @Column(name = "company_name")
    private String companyName;

    @Column(name = "eik", unique = true)
    @NotBlank(message = "ЕИК е задължителен")
    @Pattern(regexp = "\\d{9}|\\d{13}", message = "ЕИК трябва да е 9 или 13 цифри")
    private String eik;

    @NotBlank(message = "Представителят е задължителен")
    @Column(name = "representative_name")
    private String representativeName;

    @Override
    public String getDisplayName() {
        return companyName;
    }

    @Override
    public String getIdentifier() {
        return eik;
    }

    @Override
    public String getTypeLabel() {
        return "Юридическо лице";
    }
}
