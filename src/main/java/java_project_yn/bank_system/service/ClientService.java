package java_project_yn.bank_system.service;

import java_project_yn.bank_system.dto.ClientDTO;
import java_project_yn.bank_system.dto.CreateCompanyClientDTO;
import java_project_yn.bank_system.dto.CreateIndividualClientDTO;

import java.util.List;

public interface ClientService {
    List<ClientDTO> getAllClients();
    ClientDTO getClientById(long id);
    ClientDTO getClientByUsername(String username);
    ClientDTO createIndividual(CreateIndividualClientDTO dto);
    ClientDTO createCompany(CreateCompanyClientDTO dto);
    ClientDTO updateIndividual(long id, CreateIndividualClientDTO dto);
    ClientDTO updateCompany(long id, CreateCompanyClientDTO dto);
    void deleteClient(long id);
}
