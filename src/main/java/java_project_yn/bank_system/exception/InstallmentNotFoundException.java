package java_project_yn.bank_system.exception;

public class InstallmentNotFoundException extends RuntimeException {
    public InstallmentNotFoundException(String message) {
        super(message);
    }
}
