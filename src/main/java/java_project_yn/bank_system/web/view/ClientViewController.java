package java_project_yn.bank_system.web.view;

import jakarta.validation.Valid;
import java_project_yn.bank_system.dto.ClientDTO;
import java_project_yn.bank_system.dto.CreateCompanyClientDTO;
import java_project_yn.bank_system.dto.CreateIndividualClientDTO;
import java_project_yn.bank_system.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientViewController {

    private final ClientService clientService;

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        List<ClientDTO> clients = clientService.getAllClients();
        if (q != null && !q.isBlank()) {
            String s = q.trim().toLowerCase();
            clients = clients.stream()
                    .filter(c -> contains(c.getDisplayName(), s)
                            || contains(c.getIdentifier(), s)
                            || contains(c.getUsername(), s))
                    .collect(Collectors.toList());
        }
        model.addAttribute("clients", clients);
        model.addAttribute("q", q);
        return "clients/clients";
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }

    // ── Физическо лице ────────────────────────────────────────────────────────

    @GetMapping("/new/individual")
    public String newIndividual(Model model) {
        model.addAttribute("client", new CreateIndividualClientDTO());
        model.addAttribute("isEdit", false);
        return "clients/individual-form";
    }

    @PostMapping("/new/individual")
    public String createIndividual(@Valid @ModelAttribute("client") CreateIndividualClientDTO dto,
                                   BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", false);
            return "clients/individual-form";
        }
        clientService.createIndividual(dto);
        return "redirect:/clients?created";
    }

    @GetMapping("/{id}/edit/individual")
    public String editIndividual(@PathVariable long id, Model model) {
        ClientDTO existing = clientService.getClientById(id);
        CreateIndividualClientDTO dto = new CreateIndividualClientDTO();
        dto.setFirstName(existing.getFirstName());
        dto.setLastName(existing.getLastName());
        dto.setEgn(existing.getEgn());
        dto.setUsername(existing.getUsername());
        model.addAttribute("client", dto);
        model.addAttribute("isEdit", true);
        model.addAttribute("clientId", id);
        return "clients/individual-form";
    }

    @PostMapping("/{id}/edit/individual")
    public String updateIndividual(@PathVariable long id,
                                   @Valid @ModelAttribute("client") CreateIndividualClientDTO dto,
                                   BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", true);
            model.addAttribute("clientId", id);
            return "clients/individual-form";
        }
        clientService.updateIndividual(id, dto);
        return "redirect:/clients?updated";
    }

    // ── Юридическо лице ───────────────────────────────────────────────────────

    @GetMapping("/new/company")
    public String newCompany(Model model) {
        model.addAttribute("client", new CreateCompanyClientDTO());
        model.addAttribute("isEdit", false);
        return "clients/company-form";
    }

    @PostMapping("/new/company")
    public String createCompany(@Valid @ModelAttribute("client") CreateCompanyClientDTO dto,
                                BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", false);
            return "clients/company-form";
        }
        clientService.createCompany(dto);
        return "redirect:/clients?created";
    }

    @GetMapping("/{id}/edit/company")
    public String editCompany(@PathVariable long id, Model model) {
        ClientDTO existing = clientService.getClientById(id);
        CreateCompanyClientDTO dto = new CreateCompanyClientDTO();
        dto.setCompanyName(existing.getCompanyName());
        dto.setEik(existing.getEik());
        dto.setRepresentativeName(existing.getRepresentativeName());
        dto.setUsername(existing.getUsername());
        model.addAttribute("client", dto);
        model.addAttribute("isEdit", true);
        model.addAttribute("clientId", id);
        return "clients/company-form";
    }

    @PostMapping("/{id}/edit/company")
    public String updateCompany(@PathVariable long id,
                                @Valid @ModelAttribute("client") CreateCompanyClientDTO dto,
                                BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", true);
            model.addAttribute("clientId", id);
            return "clients/company-form";
        }
        clientService.updateCompany(id, dto);
        return "redirect:/clients?updated";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id) {
        clientService.deleteClient(id);
        return "redirect:/clients?deleted";
    }
}
