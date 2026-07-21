package com.midway.pix.api.dto.response;

import com.midway.pix.domain.entity.Periodicidade;
import com.midway.pix.domain.entity.StatusAgendamento;
import com.midway.pix.domain.entity.StatusRisco;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AgendamentoResponse(
        UUID id,
        String identificadorPagador,
        String chavePixRecebedor,
        BigDecimal valor,
        String descricao,
        Periodicidade periodicidade,
        LocalDate dataPrimeiroPagamento,
        LocalDate dataFim,
        StatusAgendamento status,
        StatusRisco statusRisco,
        String motivoAnalise,
        Instant criadoEm,
        Instant atualizadoEm,
        Instant analisadoEm,
        List<PagamentoRecorrenteResponse> pagamentos
) {
}
