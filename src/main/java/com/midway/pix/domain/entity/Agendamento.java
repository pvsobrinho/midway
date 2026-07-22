package com.midway.pix.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class Agendamento {

    private static final String AGUARDANDO_ANALISE = "Aguardando processamento da análise antifraude";
    private static final String AGUARDANDO_REVISAO = "Agendamento aguardando revisão manual";

    private final UUID id;
    private final String chaveIdempotencia;
    private final String identificadorPagador;
    private final String chavePixRecebedor;
    private final BigDecimal valor;
    private final String descricao;
    private final Periodicidade periodicidade;
    private final LocalDate dataPrimeiroPagamento;
    private final LocalDate dataFim;
    private final Instant criadoEm;

    private StatusAgendamento status;
    private StatusRisco statusRisco;
    private String motivoAnalise;
    private Instant bloqueadoAte;
    private Instant atualizadoEm;
    private Instant analisadoEm;

    public Agendamento(
            UUID id,
            String chaveIdempotencia,
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
            Instant bloqueadoAte,
            Instant criadoEm,
            Instant atualizadoEm,
            Instant analisadoEm
    ) {
        this.id = Objects.requireNonNull(id, "id não pode ser nulo");
        this.chaveIdempotencia = validarTexto(chaveIdempotencia, "chaveIdempotencia");
        this.identificadorPagador = validarTexto(identificadorPagador, "identificadorPagador");
        this.chavePixRecebedor = validarTexto(chavePixRecebedor, "chavePixRecebedor");
        this.valor = validarValor(valor);
        this.descricao = descricao;
        this.periodicidade = Objects.requireNonNull(periodicidade, "periodicidade não pode ser nula");
        this.dataPrimeiroPagamento = Objects.requireNonNull(
                dataPrimeiroPagamento,
                "dataPrimeiroPagamento não pode ser nula"
        );
        this.dataFim = dataFim;
        this.status = Objects.requireNonNull(status, "status não pode ser nulo");
        this.statusRisco = statusRisco;
        this.motivoAnalise = definirMotivoPendente(status, statusRisco, motivoAnalise);
        this.bloqueadoAte = bloqueadoAte;
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm não pode ser nulo");
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm não pode ser nulo");
        this.analisadoEm = analisadoEm;

        if (dataFim != null && dataFim.isBefore(dataPrimeiroPagamento)) {
            throw new IllegalArgumentException("dataFim não pode ser anterior ao primeiro pagamento");
        }
    }

    public static Agendamento criar(
            UUID id,
            String chaveIdempotencia,
            String identificadorPagador,
            String chavePixRecebedor,
            BigDecimal valor,
            String descricao,
            Periodicidade periodicidade,
            LocalDate dataPrimeiroPagamento,
            LocalDate dataFim,
            Instant criadoEm
    ) {
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
                StatusAgendamento.PENDENTE_ANALISE,
                null,
                null,
                null,
                criadoEm,
                criadoEm,
                null
        );
    }

    public void registrarAnalise(
            StatusRisco resultado,
            String motivoAnalise,
            Instant bloqueadoAte,
            Instant analisadoEm
    ) {
        Objects.requireNonNull(resultado, "resultado da análise não pode ser nulo");
        String motivo = validarTexto(motivoAnalise, "motivoAnalise");
        Instant instanteAnalise = validarInstante(analisadoEm);
        StatusAgendamento novoStatus = switch (resultado) {
            case APROVADO -> StatusAgendamento.ATIVO;
            case REJEITADO -> StatusAgendamento.REJEITADO;
            case REVISAO_MANUAL -> StatusAgendamento.PENDENTE_ANALISE;
        };
        this.statusRisco = resultado;
        this.motivoAnalise = motivo;
        this.bloqueadoAte = bloqueadoAte;
        this.status = novoStatus;
        this.analisadoEm = instanteAnalise;
        this.atualizadoEm = instanteAnalise;
    }

    private Instant validarInstante(Instant instante) {
        Objects.requireNonNull(instante, "instante de auditoria não pode ser nulo");
        if (instante.isBefore(criadoEm)) {
            throw new IllegalArgumentException("instante de auditoria não pode ser anterior à criação");
        }
        return instante;
    }

    private static String validarTexto(String valor, String campo) {
        Objects.requireNonNull(valor, campo + " não pode ser nulo");
        if (valor.isBlank()) {
            throw new IllegalArgumentException(campo + " não pode estar vazio");
        }
        return valor;
    }

    private static BigDecimal validarValor(BigDecimal valor) {
        Objects.requireNonNull(valor, "valor não pode ser nulo");
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("valor deve ser maior que zero");
        }
        return valor;
    }

    private static String definirMotivoPendente(
            StatusAgendamento status,
            StatusRisco statusRisco,
            String motivoAnalise
    ) {
        if (motivoAnalise != null && !motivoAnalise.isBlank()) {
            return motivoAnalise;
        }
        if (status != StatusAgendamento.PENDENTE_ANALISE) {
            return motivoAnalise;
        }
        return statusRisco == StatusRisco.REVISAO_MANUAL
                ? AGUARDANDO_REVISAO
                : AGUARDANDO_ANALISE;
    }

    public UUID getId() {
        return id;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public String getIdentificadorPagador() {
        return identificadorPagador;
    }

    public String getChavePixRecebedor() {
        return chavePixRecebedor;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getDescricao() {
        return descricao;
    }

    public Periodicidade getPeriodicidade() {
        return periodicidade;
    }

    public LocalDate getDataPrimeiroPagamento() {
        return dataPrimeiroPagamento;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public StatusAgendamento getStatus() {
        return status;
    }

    public StatusRisco getStatusRisco() {
        return statusRisco;
    }

    public String getMotivoAnalise() {
        return motivoAnalise;
    }

    public Instant getBloqueadoAte() {
        return bloqueadoAte;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public Instant getAnalisadoEm() {
        return analisadoEm;
    }
}
