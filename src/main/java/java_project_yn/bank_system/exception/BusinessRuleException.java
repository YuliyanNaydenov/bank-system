package java_project_yn.bank_system.exception;

/**
 * Хвърля се при нарушено бизнес правило — напр. сума над максимума за
 * избрания вид кредит, операция върху закрита сметка и т.н.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
