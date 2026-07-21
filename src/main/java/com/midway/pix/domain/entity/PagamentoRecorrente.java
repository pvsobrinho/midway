package com.midway.pix.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class PagamentoRecorrente {

    private final UUID id;
    private final UUID agendamentoId;
    private final BigDecimal valor;
    private final LocalDate dataAgendada;
    private final Instant criadoEm;

    private StatusPagamento status;
    private int numeroTentativas;
    private String codigoTransacaoPix;
    private String motivoFalha;
    private Instant atualizadoEm;
    private Instant enviadoEm;
    private Instant processadoEm;

    public PagamentoRecorrente(
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
        this.id = Objects.requireNonNull(id, "id não pode ser nulo");
        this.agendamentoId = Objects.requireNonNull(agendamentoId, "agendamentoId não pode ser nulo");
        this.valor = validarValor(valor);
        this.dataAgendada = Objects.requireNonNull(dataAgendada, "dataAgendada não pode ser nula");
        this.status = Objects.requireNonNull(status, "status não pode ser nulo");
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm não pode ser nulo");
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm não pode ser nulo");
        this.codigoTransacaoPix = codigoTransacaoPix;
        this.motivoFalha = motivoFalha;
        this.enviadoEm = enviadoEm;
        this.processadoEm = processadoEm;

        if (numeroTentativas < 0) {
            throw new IllegalArgumentException("numeroTentativas não pode ser negativo");
        }
        this.numeroTentativas = numeroTentativas;
    }

    public static PagamentoRecorrente agendar(
            UUID id,
            UUID agendamentoId,
            BigDecimal valor,
            LocalDate dataAgendada,
            Instant criadoEm
    ) {
        return new PagamentoRecorrente(
                id,
                agendamentoId,
                valor,
                dataAgendada,
                StatusPagamento.AGENDADO,
                0,
                null,
                null,
                criadoEm,
                criadoEm,
                null,
                null
        );
    }

    public void iniciarProcessamento(Instant atualizadoEm) {
        this.status = StatusPagamento.PROCESSANDO;
        this.numeroTentativas++;
        atualizarData(atualizadoEm);
    }

    public void registrarEnvio(String codigoTransacaoPix, Instant enviadoEm) {
        Objects.requireNonNull(codigoTransacaoPix, "codigoTransacaoPix não pode ser nulo");
        if (codigoTransacaoPix.isBlank()) {
            throw new IllegalArgumentException("codigoTransacaoPix não pode estar vazio");
        }

        this.codigoTransacaoPix = codigoTransacaoPix;
        this.status = StatusPagamento.ENVIADO;
        this.enviadoEm = validarInstante(enviadoEm);
        this.atualizadoEm = this.enviadoEm;
    }

    public void concluir(Instant processadoEm) {
        this.status = StatusPagamento.CONCLUIDO;
        this.motivoFalha = null;
        this.processadoEm = validarInstante(processadoEm);
        this.atualizadoEm = this.processadoEm;
    }

    public void registrarFalha(String motivoFalha, Instant processadoEm) {
        Objects.requireNonNull(motivoFalha, "motivoFalha não pode ser nulo");
        if (motivoFalha.isBlank()) {
            throw new IllegalArgumentException("motivoFalha não pode estar vazio");
        }

        this.status = StatusPagamento.FALHOU;
        this.motivoFalha = motivoFalha;
        this.processadoEm = validarInstante(processadoEm);
        this.atualizadoEm = this.processadoEm;
    }

    public void cancelar(Instant atualizadoEm) {
        this.status = StatusPagamento.CANCELADO;
        atualizarData(atualizadoEm);
    }

    private void atualizarData(Instant atualizadoEm) {
        this.atualizadoEm = validarInstante(atualizadoEm);
    }

    private Instant validarInstante(Instant instante) {
        Objects.requireNonNull(instante, "instante de auditoria não pode ser nulo");
        if (instante.isBefore(criadoEm)) {
            throw new IllegalArgumentException("instante de auditoria não pode ser anterior à criação");
        }
        return instante;
    }

    private static BigDecimal validarValor(BigDecimal valor) {
        Objects.requireNonNull(valor, "valor não pode ser nulo");
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("valor deve ser maior que zero");
        }
        return valor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAgendamentoId() {
        return agendamentoId;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDate getDataAgendada() {
        return dataAgendada;
    }

    public StatusPagamento getStatus() {
        return status;
    }

    public int getNumeroTentativas() {
        return numeroTentativas;
    }

    public String getCodigoTransacaoPix() {
        return codigoTransacaoPix;
    }

    public String getMotivoFalha() {
        return motivoFalha;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public Instant getEnviadoEm() {
        return enviadoEm;
    }

    public Instant getProcessadoEm() {
        return processadoEm;
    }
}
