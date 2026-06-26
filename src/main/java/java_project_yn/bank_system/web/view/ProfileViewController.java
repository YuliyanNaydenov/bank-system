package java_project_yn.bank_system.web.view;

import jakarta.validation.Valid;
import java_project_yn.bank_system.dto.ChangePasswordDTO;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.service.ClientService;
import java_project_yn.bank_system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ProfileViewController {

    private final UserService userService;
    private final ClientService clientService;

    @GetMapping("/profile")
    public String profile(Authentication auth, Model model) {
        populate(auth, model);
        model.addAttribute("form", new ChangePasswordDTO());
        return "profile/profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@Valid @ModelAttribute("form") ChangePasswordDTO form,
                                 BindingResult bindingResult,
                                 Authentication auth,
                                 Model model) {
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Паролите не съвпадат!");
        }
        if (bindingResult.hasErrors()) {
            populate(auth, model);
            return "profile/profile";
        }
        try {
            userService.changePassword(auth.getName(), form.getCurrentPassword(), form.getNewPassword());
        } catch (BusinessRuleException ex) {
            populate(auth, model);
            model.addAttribute("error", ex.getMessage());
            return "profile/profile";
        }
        return "redirect:/profile?pwchanged";
    }

    private void populate(Authentication auth, Model model) {
        model.addAttribute("username", auth.getName());
        String roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(", "));
        model.addAttribute("roles", roles);

        boolean isClient = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("client"));
        if (isClient) {
            try {
                model.addAttribute("client", clientService.getClientByUsername(auth.getName()));
            } catch (RuntimeException ignored) {
                // клиентският профил може да липсва — не е проблем
            }
        }
    }
}
