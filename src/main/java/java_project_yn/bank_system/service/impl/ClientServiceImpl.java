package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.Client;
import java_project_yn.bank_system.data.entity.CompanyClient;
import java_project_yn.bank_system.data.entity.CreditStatus;
import java_project_yn.bank_system.data.entity.IndividualClient;
import java_project_yn.bank_system.data.repo.ClientRepository;
import java_project_yn.bank_system.data.repo.CreditRepository;
import java_project_yn.bank_system.dto.ClientDTO;
import java_project_yn.bank_system.dto.CreateCompanyClientDTO;
import java_project_yn.bank_system.dto.CreateIndividualClientDTO;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.exception.ClientNotFoundException;
import java_project_yn.bank_system.exception.DuplicateEntityException;
import java_project_yn.bank_system.service.AuditService;
import java_project_yn.bank_system.service.ClientService;
import java_project_yn.bank_system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final CreditRepository creditRepository;
    private final UserService userService;
    private final AuditService auditService;

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public List<ClientDTO> getAllClients() {
        return clientRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ClientDTO getClientById(long id) {
        return toDto(getEntity(id));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ClientDTO getClientByUsername(String username) {
        Client client = clientRepository.findByUsername(username)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Клиент с потребителско име '" + username + "' не е намерен!"));
        return toDto(client);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public ClientDTO createIndividual(CreateIndividualClientDTO dto) {
        if (clientRepository.existsByEgn(dto.getEgn())) {
            throw new DuplicateEntityException("Клиент с ЕГН " + dto.getEgn() + " вече съществува!");
        }
        IndividualClient client = new IndividualClient();
        client.setFirstName(dto.getFirstName());
        client.setLastName(dto.getLastName());
        client.setEgn(dto.getEgn());
        client.setUsername(emptyToNull(dto.getUsername()));
        ClientDTO saved = toDto(clientRepository.save(client));
        auditService.log("Нов клиент", "Физическо лице: " + saved.getDisplayName() + " (" + saved.getIdentifier() + ")");
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public ClientDTO createCompany(CreateCompanyClientDTO dto) {
        if (clientRepository.existsByEik(dto.getEik())) {
            throw new DuplicateEntityException("Клиент с ЕИК " + dto.getEik() + " вече съществува!");
        }
        String username = emptyToNull(dto.getUsername());
        String password = emptyToNull(dto.getPassword());

        // Ако се задава вход — нужни са и потребителско име, и парола
        if (username != null || password != null) {
            if (username == null || password == null) {
                throw new BusinessRuleException("За създаване на вход въведете и потребителско име, и парола.");
            }
            if (password.length() < 4) {
                throw new BusinessRuleException("Паролата трябва да е поне 4 символа.");
            }
            userService.createClientLogin(username, password);
        }

        CompanyClient client = new CompanyClient();
        client.setCompanyName(dto.getCompanyName());
        client.setEik(dto.getEik());
        client.setRepresentativeName(dto.getRepresentativeName());
        client.setUsername(username);
        ClientDTO saved = toDto(clientRepository.save(client));
        auditService.log("Нов клиент", "Юридическо лице: " + saved.getDisplayName() + " (" + saved.getIdentifier() + ")");
        return saved;
    }

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public ClientDTO updateIndividual(long id, CreateIndividualClientDTO dto) {
        Client entity = getEntity(id);
        if (!(entity instanceof IndividualClient client)) {
            throw new IllegalArgumentException("Клиент с id=" + id + " не е физическо лице!");
        }
        if (!dto.getEgn().equals(client.getEgn()) && clientRepository.existsByEgn(dto.getEgn())) {
            throw new DuplicateEntityException("Клиент с ЕГН " + dto.getEgn() + " вече съществува!");
        }
        client.setFirstName(dto.getFirstName());
        client.setLastName(dto.getLastName());
        client.setEgn(dto.getEgn());
        client.setUsername(emptyToNull(dto.getUsername()));
        return toDto(clientRepository.save(client));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public ClientDTO updateCompany(long id, CreateCompanyClientDTO dto) {
        Client entity = getEntity(id);
        if (!(entity instanceof CompanyClient client)) {
            throw new IllegalArgumentException("Клиент с id=" + id + " не е юридическо лице!");
        }
        if (!dto.getEik().equals(client.getEik()) && clientRepository.existsByEik(dto.getEik())) {
            throw new DuplicateEntityException("Клиент с ЕИК " + dto.getEik() + " вече съществува!");
        }
        client.setCompanyName(dto.getCompanyName());
        client.setEik(dto.getEik());
        client.setRepresentativeName(dto.getRepresentativeName());
        client.setUsername(emptyToNull(dto.getUsername()));
        return toDto(clientRepository.save(client));
    }

    @Override
    @PreAuthorize("hasAuthority('admin')")
    public void deleteClient(long id) {
        if (!clientRepository.existsById(id)) {
            throw new ClientNotFoundException("Клиент с id=" + id + " не е намерен!");
        }
        if (creditRepository.existsByClient_IdAndStatus(id, CreditStatus.ACTIVE)) {
            throw new BusinessRuleException("Клиент с активни кредити не може да бъде изтрит!");
        }
        clientRepository.deleteById(id);
        auditService.log("Изтрит клиент", "Клиент id=" + id);
    }

    // ── Помощни методи ──────────────────────────────────────────────────────

    private Client getEntity(long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ClientNotFoundException("Клиент с id=" + id + " не е намерен!"));
    }

    private ClientDTO toDto(Client client) {
        String type = (client instanceof CompanyClient) ? "COMPANY" : "INDIVIDUAL";
        ClientDTO.ClientDTOBuilder builder = ClientDTO.builder()
                .id(client.getId())
                .clientType(type)
                .typeLabel(client.getTypeLabel())
                .displayName(client.getDisplayName())
                .identifier(client.getIdentifier())
                .username(client.getUsername())
                .accountsCount(client.getAccounts() == null ? 0 : client.getAccounts().size())
                .creditsCount(client.getCredits() == null ? 0 : client.getCredits().size());

        if (client instanceof IndividualClient ind) {
            builder.firstName(ind.getFirstName())
                    .lastName(ind.getLastName())
                    .egn(ind.getEgn());
        } else if (client instanceof CompanyClient comp) {
            builder.companyName(comp.getCompanyName())
                    .eik(comp.getEik())
                    .representativeName(comp.getRepresentativeName());
        }
        return builder.build();
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
