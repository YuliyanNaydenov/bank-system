package java_project_yn.bank_system.web.view;

import jakarta.validation.Valid;
import java_project_yn.bank_system.dto.CreateEmployeeDTO;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.exception.DuplicateEntityException;
import java_project_yn.bank_system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeViewController {

    private final UserService userService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("employees", userService.getEmployees());
        return "employees/employees";
    }

    @GetMapping("/new")
    public String newEmployee(Model model) {
        model.addAttribute("employee", new CreateEmployeeDTO());
        return "employees/employee-form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("employee") CreateEmployeeDTO dto,
                         BindingResult bindingResult, Model model) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Паролите не съвпадат!");
        }
        if (bindingResult.hasErrors()) {
            return "employees/employee-form";
        }
        try {
            userService.createEmployee(dto);
        } catch (DuplicateEntityException | BusinessRuleException ex) {
            model.addAttribute("error", ex.getMessage());
            return "employees/employee-form";
        }
        return "redirect:/employees?created";
    }

    @PostMapping("/{id}/disable")
    public String disable(@PathVariable long id) {
        userService.setEmployeeEnabled(id, false);
        return "redirect:/employees?updated";
    }

    @PostMapping("/{id}/enable")
    public String enable(@PathVariable long id) {
        userService.setEmployeeEnabled(id, true);
        return "redirect:/employees?updated";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id) {
        userService.deleteUser(id);
        return "redirect:/employees?deleted";
    }
}
