package com.midway.pix.fixture;

import com.midway.pix.domain.entity.Agendamento;
import com.midway.pix.domain.entity.Periodicidade;
import com.midway.pix.domain.entity.StatusAgendamento;
import com.midway.pix.domain.entity.StatusRisco;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class AgendamentoFixture {

    public static final Instant DATA_CRIACAO = Instant.parse("2025-01-01T12:00:00Z");
    public static final LocalDate PRIMEIRO_PAGAMENTO = LocalDate.of(2026, 8, 1);

    private AgendamentoFixture() {
    }

    public static Agendamento valido() {
        return novo().build();
    }

    public static Builder novo() {
        return new Builder();
    }

    public static final class Builder {

        private UUID id = UUID.randomUUID();
        private String chaveIdempotencia = "agendamento-001";
        private String identificadorPagador = "cliente-123";
        private String chavePixRecebedor = "recebedor@exemplo.com";
        private BigDecimal valor = new BigDecimal("250.00");
        private String descricao = "Mensalidade";
        private Periodicidade periodicidade = Periodicidade.MENSAL;
        private LocalDate dataPrimeiroPagamento = PRIMEIRO_PAGAMENTO;
        private LocalDate dataFim = PRIMEIRO_PAGAMENTO.plusYears(1);
        private StatusAgendamento status = StatusAgendamento.PENDENTE_ANALISE;
        private StatusRisco statusRisco;
        private String motivoAnalise;
        private Instant bloqueadoAte;
        private Instant criadoEm = DATA_CRIACAO;
        private Instant atualizadoEm = DATA_CRIACAO;
        private Instant analisadoEm;

        public Builder comId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder comChaveIdempotencia(String chaveIdempotencia) {
            this.chaveIdempotencia = chaveIdempotencia;
            return this;
        }

        public Builder comPagador(String identificadorPagador) {
            this.identificadorPagador = identificadorPagador;
            return this;
        }

        public Builder comRecebedor(String chavePixRecebedor) {
            this.chavePixRecebedor = chavePixRecebedor;
            return this;
        }

        public Builder comValor(String valor) {
            this.valor = new BigDecimal(valor);
            return this;
        }

        public Builder comValor(BigDecimal valor) {
            this.valor = valor;
            return this;
        }

        public Builder comDescricao(String descricao) {
            this.descricao = descricao;
            return this;
        }

        public Builder comPeriodicidade(Periodicidade periodicidade) {
            this.periodicidade = periodicidade;
            return this;
        }

        public Builder comPrimeiroPagamento(LocalDate dataPrimeiroPagamento) {
            this.dataPrimeiroPagamento = dataPrimeiroPagamento;
            return this;
        }

        public Builder comDataFim(LocalDate dataFim) {
            this.dataFim = dataFim;
            return this;
        }

        public Builder criadoEm(Instant criadoEm) {
            this.criadoEm = criadoEm;
            this.atualizadoEm = criadoEm;
            return this;
        }

        public Builder atualizadoEm(Instant atualizadoEm) {
            this.atualizadoEm = atualizadoEm;
            return this;
        }

        public Builder comSituacao(
                StatusAgendamento status,
                StatusRisco statusRisco,
                String motivoAnalise
        ) {
            this.status = status;
            this.statusRisco = statusRisco;
            this.motivoAnalise = motivoAnalise;
            this.analisadoEm = statusRisco == null ? null : atualizadoEm;
            return this;
        }

        public Builder bloqueadoAte(Instant bloqueadoAte) {
            this.bloqueadoAte = bloqueadoAte;
            return this;
        }

        public Agendamento build() {
            return new Agendamento(
                    id,
                    chaveIdempotencia,
                    identificadorPagador,
                    chavePixRecebedor,
                    valor,
                    descricao,
                    periodicidade,
                    dataPrimeiroPagamento,
                    dataFim,
                    status,
                    statusRisco,
                    motivoAnalise,
                    bloqueadoAte,
                    criadoEm,
                    atualizadoEm,
                    analisadoEm
            );
        }
    }
}
