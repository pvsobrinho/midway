package com.midway.pix.fixture;

import com.midway.pix.domain.entity.PagamentoRecorrente;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PagamentoRecorrenteFixture {

    public static final Instant DATA_CRIACAO = Instant.parse("2025-01-01T12:00:00Z");

    private PagamentoRecorrenteFixture() {
    }

    public static PagamentoRecorrente valido() {
        return novo().build();
    }

    public static Builder novo() {
        return new Builder();
    }

    public static final class Builder {

        private UUID id = UUID.randomUUID();
        private UUID agendamentoId = UUID.randomUUID();
        private BigDecimal valor = new BigDecimal("250.00");
        private LocalDate dataAgendada = LocalDate.of(2026, 8, 1);
        private Instant criadoEm = DATA_CRIACAO;

        public Builder comId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder comAgendamentoId(UUID agendamentoId) {
            this.agendamentoId = agendamentoId;
            return this;
        }

        public Builder comValor(BigDecimal valor) {
            this.valor = valor;
            return this;
        }

        public Builder comDataAgendada(LocalDate dataAgendada) {
            this.dataAgendada = dataAgendada;
            return this;
        }

        public Builder criadoEm(Instant criadoEm) {
            this.criadoEm = criadoEm;
            return this;
        }

        public PagamentoRecorrente build() {
            return new PagamentoRecorrente(id, agendamentoId, valor, dataAgendada, criadoEm);
        }
    }
}
