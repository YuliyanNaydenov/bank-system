package java_project_yn.bank_system.exception;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Обработва изключенията на view контролерите — връща HTML страница с грешка.
 */
@ControllerAdvice(basePackages = "java_project_yn.bank_system.web.view")
public class ViewExceptionHandler {

    @ExceptionHandler({
            ClientNotFoundException.class,
            AccountNotFoundException.class,
            CreditNotFoundException.class,
            CreditTypeNotFoundException.class,
            InstallmentNotFoundException.class
    })
    public String handleNotFound(RuntimeException ex, Model model) {
        model.addAttribute("statusCode", 404);
        model.addAttribute("errorTitle", "Записът не е намерен");
        model.addAttribute("message", ex.getMessage());
        return "errors/errors";
    }

    @ExceptionHandler(DuplicateEntityException.class)
    public String handleDuplicate(DuplicateEntityException ex, Model model) {
        model.addAttribute("statusCode", 409);
        model.addAttribute("errorTitle", "Записът вече съществува");
        model.addAttribute("message", ex.getMessage());
        return "errors/errors";
    }

    @ExceptionHandler(BusinessRuleException.class)
    public String handleBusinessRule(BusinessRuleException ex, Model model) {
        model.addAttribute("statusCode", 422);
        model.addAttribute("errorTitle", "Нарушено бизнес правило");
        model.addAttribute("message", ex.getMessage());
        return "errors/errors";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(Model model) {
        model.addAttribute("statusCode", 403);
        model.addAttribute("errorTitle", "Нямате права за достъп");
        model.addAttribute("message", "Нямате необходимите права за достъп до тази страница.");
        return "errors/errors";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException ex, Model model) {
        model.addAttribute("statusCode", 400);
        model.addAttribute("errorTitle", "Невалидни данни");
        model.addAttribute("message", ex.getMessage());
        return "errors/errors";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneral(Exception ex, Model model) {
        model.addAttribute("statusCode", 500);
        model.addAttribute("errorTitle", "Възникна грешка");
        model.addAttribute("message", "Неочаквана грешка: " + ex.getMessage());
        return "errors/errors";
    }
}
