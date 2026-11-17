package br.edu.faculdade.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tratarTamanho() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("erro", "O arquivo excede o limite de 100 MB."));
    }
    @ExceptionHandler({IllegalArgumentException.class, NoSuchElementException.class})
    ResponseEntity<Map<String, String>> tratarRequisicao(RuntimeException e) {
        return ResponseEntity.badRequest().body(erro(e));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> tratarIntegracao(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(erro(e));
    }

    private Map<String, String> erro(RuntimeException e) {
        return Map.of("erro", Optional.ofNullable(e.getMessage()).orElse("Operação inválida"));
    }
}
