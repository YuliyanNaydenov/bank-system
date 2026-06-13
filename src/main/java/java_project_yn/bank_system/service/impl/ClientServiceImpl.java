package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.Client;
import java_project_yn.bank_system.data.entity.CompanyClient;
import java_project_yn.bank_system.data.entity.IndividualClient;
import java_project_yn.bank_system.data.repo.ClientRepository;
import java_project_yn.bank_system.dto.ClientDTO;
import java_project_yn.bank_system.dto.CreateCompanyClientDTO;
import java_project_yn.bank_system.dto.CreateIndividualClientDTO;
import java_project_yn.bank_system.exception.ClientNotFoundException;
import java_project_yn.bank_system.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;

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
        IndividualClient client = new IndividualClient();
        client.setFirstName(dto.getFirstName());
        client.setLastName(dto.getLastName());
        client.setEgn(dto.getEgn());
        client.setUsername(emptyToNull(dto.getUsername()));
        return toDto(clientRepository.save(client));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public ClientDTO createCompany(CreateCompanyClientDTO dto) {
        CompanyClient client = new CompanyClient();
        client.setCompanyName(dto.getCompanyName());
        client.setEik(dto.getEik());
        client.setRepresentativeName(dto.getRepresentativeName());
        client.setUsername(emptyToNull(dto.getUsername()));
        return toDto(clientRepository.save(client));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public ClientDTO updateIndividual(long id, CreateIndividualClientDTO dto) {
        Client entity = getEntity(id);
        if (!(entity instanceof IndividualClient client)) {
            throw new IllegalArgumentException("Клиент с id=" + id + " не е физическо лице!");
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
        clientRepository.deleteById(id);
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
