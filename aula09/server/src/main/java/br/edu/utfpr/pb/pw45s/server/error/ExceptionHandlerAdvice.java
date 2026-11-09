package br.edu.utfpr.pb.pw45s.server.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class ExceptionHandlerAdvice {

    @ExceptionHandler({MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    private ApiError handlerValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        BindingResult result = exception.getBindingResult();
        Map<String, String> validationErrors = new HashMap<>();
        for (FieldError fieldError: result.getFieldErrors()) {
            validationErrors.put(
                    fieldError.getField(),
                    fieldError.getDefaultMessage());
        }
        // Erro de validação é causado pelo cliente (4xx), portanto é registrado como WARN
        log.atWarn()
                .addKeyValue("event", "http.validation_error")
                .addKeyValue("http.path", request.getServletPath())
                .addKeyValue("validation.fields", String.join(",", validationErrors.keySet()))
                .log("Validation error on {}: {}", request.getServletPath(), validationErrors);
        return new ApiError(400, "validation error",
                request.getServletPath(), validationErrors);
    }

}
