package java_project_yn.bank_system.exception;

import java.time.LocalDateTime;

/**
 * Унифициран формат за грешки, връщан от REST API-то.
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path);
    }
}
