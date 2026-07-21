package com.midway.pix.api.controller;

import com.midway.pix.api.dto.request.CriarAgendamentoRequest;
import com.midway.pix.api.dto.response.AgendamentoResponse;
import com.midway.pix.application.AgendamentoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/v1/agendamentos")
public class AgendamentoController {

    private final AgendamentoService agendamentoService;

    public AgendamentoController(AgendamentoService agendamentoService) {
        this.agendamentoService = agendamentoService;
    }

    @PostMapping
    public ResponseEntity<AgendamentoResponse> criar(
            @RequestHeader("Idempotency-Key") @NotBlank String chaveIdempotencia,
            @Valid @RequestBody CriarAgendamentoRequest request
    ) {
        AgendamentoResponse response = agendamentoService.criar(chaveIdempotencia, request);
        return ResponseEntity
                .created(URI.create("/v1/agendamentos/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgendamentoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(agendamentoService.buscarPorId(id));
    }
}
