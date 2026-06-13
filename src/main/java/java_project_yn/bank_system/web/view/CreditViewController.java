package java_project_yn.bank_system.web.view;

import jakarta.validation.Valid;
import java_project_yn.bank_system.dto.CreateCreditDTO;
import java_project_yn.bank_system.service.ClientService;
import java_project_yn.bank_system.service.CreditService;
import java_project_yn.bank_system.service.CreditTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/credits")
@RequiredArgsConstructor
public class CreditViewController {

    private final CreditService creditService;
    private final ClientService clientService;
    private final CreditTypeService creditTypeService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("credits", creditService.getAllCredits());
        return "credits/credits";
    }

    @GetMapping("/new")
    public String newCredit(Model model) {
        model.addAttribute("credit", new CreateCreditDTO());
        model.addAttribute("allClients", clientService.getAllClients());
        model.addAttribute("allCreditTypes", creditTypeService.getAllCreditTypes());
        return "credits/credit-form";
    }

    @PostMapping("/new")
    public String grant(@Valid @ModelAttribute("credit") CreateCreditDTO dto,
                        BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("allClients", clientService.getAllClients());
            model.addAttribute("allCreditTypes", creditTypeService.getAllCreditTypes());
            return "credits/credit-form";
        }
        creditService.grantCredit(dto);
        return "redirect:/credits?granted";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        model.addAttribute("credit", creditService.getCreditById(id));
        return "credits/credit-detail";
    }

    @PostMapping("/{creditId}/installments/{installmentId}/pay")
    public String pay(@PathVariable long creditId, @PathVariable long installmentId) {
        creditService.payInstallment(creditId, installmentId);
        return "redirect:/credits/" + creditId + "?paid";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id) {
        creditService.deleteCredit(id);
        return "redirect:/credits?deleted";
    }
}
