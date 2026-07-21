package com.midway.pix.api.dto.response;

import com.midway.pix.domain.entity.StatusPagamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PagamentoRecorrenteResponse(
        UUID id,
        UUID agendamentoId,
        BigDecimal valor,
        LocalDate dataAgendada,
        StatusPagamento status,
        int numeroTentativas,
        String codigoTransacaoPix,
        String motivoFalha,
        Instant criadoEm,
        Instant atualizadoEm,
        Instant enviadoEm,
        Instant processadoEm
) {
}
