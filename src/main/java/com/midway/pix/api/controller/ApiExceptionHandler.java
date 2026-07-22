package com.midway.pix.api.controller;

import com.midway.pix.api.dto.response.ErroResponse;
import com.midway.pix.shared.LogSeguro;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> tratarValidacaoDoCorpo(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<String> mensagens = exception.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .distinct()
                .toList();
        return responderValidacao(HttpStatus.BAD_REQUEST, mensagens, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErroResponse> tratarValidacaoDoMetodo(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        List<String> mensagens = exception.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .distinct()
                .toList();
        return responderValidacao(HttpStatus.BAD_REQUEST, mensagens, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErroResponse> tratarStatus(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String mensagem = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return responderValidacao(status, List.of(mensagem), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResponse> tratarArgumentoInvalido(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return responderValidacao(HttpStatus.BAD_REQUEST, List.of(exception.getMessage()), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> tratarCorpoInvalido(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return responderValidacao(
                HttpStatus.BAD_REQUEST,
                List.of("corpo da requisição inválido ou malformado"),
                request
        );
    }

    private ResponseEntity<ErroResponse> responderValidacao(
            HttpStatus status,
            List<String> mensagens,
            HttpServletRequest request
    ) {
        String chaveIdempotencia = LogSeguro.mascarar(request.getHeader("Idempotency-Key"));
        LOGGER.warn(
                "Validação da requisição: método={}, caminho={}, idempotencyKey={}, status={}, mensagens={}",
                request.getMethod(),
                request.getRequestURI(),
                chaveIdempotencia,
                status.value(),
                mensagens
        );

        ErroResponse response = new ErroResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                mensagens,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(response);
    }
}
