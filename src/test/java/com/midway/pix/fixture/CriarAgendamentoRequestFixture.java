package com.midway.pix.fixture;

import com.midway.pix.api.dto.request.CriarAgendamentoRequest;
import com.midway.pix.domain.entity.Periodicidade;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class CriarAgendamentoRequestFixture {

    private CriarAgendamentoRequestFixture() {
    }

    public static CriarAgendamentoRequest valido() {
        return novo().build();
    }

    public static Builder novo() {
        return new Builder();
    }

    public static final class Builder {

        private String identificadorPagador = "cliente-123";
        private String chavePixRecebedor = "recebedor@exemplo.com";
        private BigDecimal valor = new BigDecimal("250.00");
        private String descricao = "Mensalidade";
        private Periodicidade periodicidade = Periodicidade.MENSAL;
        private LocalDate dataPrimeiroPagamento = LocalDate.now().plusDays(3);
        private LocalDate dataFim = LocalDate.now().plusYears(1);

        public Builder comPagador(String identificadorPagador) {
            this.identificadorPagador = identificadorPagador;
            return this;
        }

        public Builder comRecebedor(String chavePixRecebedor) {
            this.chavePixRecebedor = chavePixRecebedor;
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

        public CriarAgendamentoRequest build() {
            return new CriarAgendamentoRequest(
                    identificadorPagador,
                    chavePixRecebedor,
                    valor,
                    descricao,
                    periodicidade,
                    dataPrimeiroPagamento,
                    dataFim
            );
        }
    }
}
