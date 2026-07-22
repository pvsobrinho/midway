package com.midway.pix.infrastructure.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record AgendamentoSolicitadoEvent(
        UUID eventoId,
        UUID agendamentoId,
        Instant solicitadoEm
) {
}
