package java_project_yn.bank_system.web.view;

import jakarta.validation.Valid;
import java_project_yn.bank_system.dto.CreateCreditTypeDTO;
import java_project_yn.bank_system.dto.CreditTypeDTO;
import java_project_yn.bank_system.service.CreditTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/credit-types")
@RequiredArgsConstructor
public class CreditTypeViewController {

    private final CreditTypeService creditTypeService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("creditTypes", creditTypeService.getAllCreditTypes());
        return "credit-types/credit-types";
    }

    @GetMapping("/new")
    public String newType(Model model) {
        model.addAttribute("creditType", new CreateCreditTypeDTO());
        model.addAttribute("isEdit", false);
        return "credit-types/credit-type-form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("creditType") CreateCreditTypeDTO dto,
                         BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", false);
            return "credit-types/credit-type-form";
        }
        creditTypeService.createCreditType(dto);
        return "redirect:/credit-types?created";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable long id, Model model) {
        CreditTypeDTO existing = creditTypeService.getCreditTypeById(id);
        CreateCreditTypeDTO dto = CreateCreditTypeDTO.builder()
                .name(existing.getName())
                .annualInterestRate(existing.getAnnualInterestRate())
                .maxAmount(existing.getMaxAmount())
                .maxTermMonths(existing.getMaxTermMonths())
                .build();
        model.addAttribute("creditType", dto);
        model.addAttribute("isEdit", true);
        model.addAttribute("creditTypeId", id);
        return "credit-types/credit-type-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable long id,
                         @Valid @ModelAttribute("creditType") CreateCreditTypeDTO dto,
                         BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", true);
            model.addAttribute("creditTypeId", id);
            return "credit-types/credit-type-form";
        }
        creditTypeService.updateCreditType(id, dto);
        return "redirect:/credit-types?updated";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id) {
        creditTypeService.deleteCreditType(id);
        return "redirect:/credit-types?deleted";
    }
}
