package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.Client;
import java_project_yn.bank_system.data.entity.CompanyClient;
import java_project_yn.bank_system.data.entity.IndividualClient;
import java_project_yn.bank_system.data.repo.ClientRepository;
import java_project_yn.bank_system.dto.ClientDTO;
import java_project_yn.bank_system.dto.CreateCompanyClientDTO;
import java_project_yn.bank_system.dto.CreateIndividualClientDTO;
import java_project_yn.bank_system.exception.ClientNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClientServiceImplTest {

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private ClientServiceImpl clientService;

    @Test
    void createIndividual_mapsAndSaves() {
        CreateIndividualClientDTO dto = CreateIndividualClientDTO.builder()
                .firstName("Иван").lastName("Петров").egn("8001011234").username("client1")
                .build();
        IndividualClient saved = new IndividualClient();
        saved.setFirstName("Иван");
        saved.setLastName("Петров");
        saved.setEgn("8001011234");
        saved.setUsername("client1");
        given(clientRepository.save(any(Client.class))).willReturn(saved);

        ClientDTO result = clientService.createIndividual(dto);

        assertEquals("INDIVIDUAL", result.getClientType());
        assertEquals("Иван Петров", result.getDisplayName());
        assertEquals("8001011234", result.getIdentifier());
        verify(clientRepository).save(any(Client.class));
    }

    @Test
    void createCompany_mapsAndSaves() {
        CreateCompanyClientDTO dto = CreateCompanyClientDTO.builder()
                .companyName("Софтуер ЕООД").eik("203456789").representativeName("Георги Иванов")
                .build();
        CompanyClient saved = new CompanyClient();
        saved.setCompanyName("Софтуер ЕООД");
        saved.setEik("203456789");
        saved.setRepresentativeName("Георги Иванов");
        given(clientRepository.save(any(Client.class))).willReturn(saved);

        ClientDTO result = clientService.createCompany(dto);

        assertEquals("COMPANY", result.getClientType());
        assertEquals("Софтуер ЕООД", result.getDisplayName());
        assertEquals("203456789", result.getIdentifier());
    }

    @Test
    void getClientById_notFound_throws() {
        given(clientRepository.findById(99L)).willReturn(Optional.empty());
        assertThrows(ClientNotFoundException.class, () -> clientService.getClientById(99L));
    }

    @Test
    void getAllClients_returnsMappedList() {
        IndividualClient c = new IndividualClient();
        c.setFirstName("Мария");
        c.setLastName("Георгиева");
        c.setEgn("9203054321");
        given(clientRepository.findAll()).willReturn(List.of(c));

        List<ClientDTO> result = clientService.getAllClients();

        assertEquals(1, result.size());
        assertEquals("Мария Георгиева", result.get(0).getDisplayName());
    }

    @Test
    void deleteClient_notFound_throws() {
        given(clientRepository.existsById(99L)).willReturn(false);
        assertThrows(ClientNotFoundException.class, () -> clientService.deleteClient(99L));
    }
}
