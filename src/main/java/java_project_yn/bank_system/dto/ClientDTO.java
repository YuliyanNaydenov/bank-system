package java_project_yn.bank_system.dto;

import lombok.*;

/**
 * Презентационен модел за клиент — обединява физически и юридически лица.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientDTO {
    private long id;
    /** INDIVIDUAL или COMPANY. */
    private String clientType;
    private String typeLabel;
    private String displayName;
    private String identifier;
    private String username;
    private int accountsCount;
    private int creditsCount;

    // Сурови полета — използват се при редактиране на формите
    private String firstName;
    private String lastName;
    private String egn;
    private String companyName;
    private String eik;
    private String representativeName;
}
