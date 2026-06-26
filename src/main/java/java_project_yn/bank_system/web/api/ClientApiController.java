package java_project_yn.bank_system.web.api;

import jakarta.validation.Valid;
import java_project_yn.bank_system.dto.ClientDTO;
import java_project_yn.bank_system.dto.CreateCompanyClientDTO;
import java_project_yn.bank_system.dto.CreateIndividualClientDTO;
import java_project_yn.bank_system.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clients")
public class ClientApiController {

    private final ClientService clientService;

    @GetMapping
    public List<ClientDTO> getAll(Authentication authentication) {
        if (isClientOnly(authentication)) {
            try {
                return List.of(clientService.getClientByUsername(authentication.getName()));
            } catch (RuntimeException ex) {
                // Клиент-потребител без свързан клиентски профил → празен списък
                return List.of();
            }
        }
        return clientService.getAllClients();
    }

    @GetMapping("/{id}")
    public ClientDTO getById(@PathVariable long id) {
        return clientService.getClientById(id);
    }

    @PostMapping("/individual")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientDTO createIndividual(@Valid @RequestBody CreateIndividualClientDTO dto) {
        return clientService.createIndividual(dto);
    }

    @PostMapping("/company")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientDTO createCompany(@Valid @RequestBody CreateCompanyClientDTO dto) {
        return clientService.createCompany(dto);
    }

    @PutMapping("/individual/{id}")
    public ClientDTO updateIndividual(@PathVariable long id, @Valid @RequestBody CreateIndividualClientDTO dto) {
        return clientService.updateIndividual(id, dto);
    }

    @PutMapping("/company/{id}")
    public ClientDTO updateCompany(@PathVariable long id, @Valid @RequestBody CreateCompanyClientDTO dto) {
        return clientService.updateCompany(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        clientService.deleteClient(id);
    }

    private boolean isClientOnly(Authentication auth) {
        boolean client = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("client"));
        boolean staff = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("admin") || a.getAuthority().equals("employee"));
        return client && !staff;
    }
}
