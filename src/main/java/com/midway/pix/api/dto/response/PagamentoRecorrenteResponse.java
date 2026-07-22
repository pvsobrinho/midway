package com.midway.pix.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PagamentoRecorrenteResponse(
        UUID id,
        UUID agendamentoId,
        BigDecimal valor,
        LocalDate dataAgendada,
        Instant criadoEm
) {
}
