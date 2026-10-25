package com.factory.events.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.factory.events.enums.EventType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(DuplicateEventException.class)
    public ProblemDetail handleDuplicateEvent(DuplicateEventException exception) {
        return problem(HttpStatus.CONFLICT, "Evento duplicado", exception.getMessage(), "DUPLICATE_EVENT");
    }

    @ExceptionHandler(FeatureNotImplementedException.class)
    public ProblemDetail handleNotImplemented(FeatureNotImplementedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, exception.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "Existem campos inválidos na requisição.", "VALIDATION_ERROR");
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message",
                        error.getDefaultMessage() == null ? "Valor inválido" : error.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String detail = "Corpo da requisição ausente ou JSON inválido.";
        Throwable cause = exception.getCause();
        if (cause instanceof InvalidFormatException invalidFormat) {
            if (invalidFormat.getTargetType() == EventType.class) {
                detail = "Tipo de evento inválido. Utilize PRODUCTION ou QUALITY.";
            } else if (invalidFormat.getTargetType() == LocalDateTime.class) {
                detail = "Data e hora inválidas. Utilize o formato ISO 8601, por exemplo 2026-10-24T10:30:00.";
            }
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Requisição inválida", detail, "INVALID_JSON");
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Tipo de conteúdo inválido",
                "Tipo de conteúdo não suportado. Utilize application/json.", "UNSUPPORTED_CONTENT_TYPE");
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDataAccessException(DataAccessException exception) {
        log.error("Falha ao acessar o banco de dados", exception);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Falha de persistência",
                "Não foi possível acessar os dados. Tente novamente mais tarde.", "DATABASE_ERROR");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityException(DataIntegrityViolationException exception) {
        log.error("Falha de integridade ao persistir os dados", exception);
        return problem(HttpStatus.CONFLICT, "Conflito de dados",
                "Não foi possível salvar o evento porque os dados violam uma restrição de integridade.",
                "DATA_INTEGRITY_ERROR");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception exception) {
        log.error("Erro inesperado ao processar a requisição", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro interno ao processar a requisição.", "INTERNAL_ERROR");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
