package app.morrow.api;

import app.morrow.auth.AccountAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AccountAuthService.InvalidAccountIdException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalidAccount(AccountAuthService.InvalidAccountIdException error) {
        return new ErrorResponse(error.getMessage());
    }

    record ErrorResponse(String message) {}
}
