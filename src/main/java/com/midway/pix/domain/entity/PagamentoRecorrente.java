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

    public PagamentoRecorrente(
            UUID id,
            UUID agendamentoId,
            BigDecimal valor,
            LocalDate dataAgendada,
            Instant criadoEm
    ) {
        this.id = Objects.requireNonNull(id, "id não pode ser nulo");
        this.agendamentoId = Objects.requireNonNull(agendamentoId, "agendamentoId não pode ser nulo");
        this.valor = validarValor(valor);
        this.dataAgendada = Objects.requireNonNull(dataAgendada, "dataAgendada não pode ser nula");
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm não pode ser nulo");
    }

    public static PagamentoRecorrente agendar(
            UUID id,
            UUID agendamentoId,
            BigDecimal valor,
            LocalDate dataAgendada,
            Instant criadoEm
    ) {
        return new PagamentoRecorrente(id, agendamentoId, valor, dataAgendada, criadoEm);
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

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
