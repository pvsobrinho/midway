package com.midway.pix.api.controller;

import com.midway.pix.api.dto.request.CriarAgendamentoRequest;
import com.midway.pix.api.dto.response.AgendamentoResponse;
import com.midway.pix.api.dto.response.ErroResponse;
import com.midway.pix.application.AgendamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Agendamentos", description = "Gerenciamento de agendamentos Pix recorrentes")
public class AgendamentoController {

    private final AgendamentoService agendamentoService;

    public AgendamentoController(AgendamentoService agendamentoService) {
        this.agendamentoService = agendamentoService;
    }

    @PostMapping
    @Operation(summary = "Criar um agendamento Pix recorrente")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Agendamento recebido para análise"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))
            )
    })
    public ResponseEntity<AgendamentoResponse> criar(
            @Parameter(
                    description = "Chave única da tentativa de criação",
                    required = true,
                    example = "assinatura-cliente-123"
            )
            @RequestHeader("Idempotency-Key") @NotBlank String chaveIdempotencia,
            @Valid @RequestBody CriarAgendamentoRequest request
    ) {
        AgendamentoResponse response = agendamentoService.criar(chaveIdempotencia, request);
        return ResponseEntity
                .accepted()
                .location(URI.create("/v1/agendamentos/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar um agendamento pelo ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agendamento encontrado"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Agendamento não encontrado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))
            )
    })
    public ResponseEntity<AgendamentoResponse> buscarPorId(
            @Parameter(description = "ID do agendamento", required = true)
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(agendamentoService.buscarPorId(id));
    }
}
