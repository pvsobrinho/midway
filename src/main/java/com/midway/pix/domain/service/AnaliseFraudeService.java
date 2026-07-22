package com.midway.pix.domain.service;

import com.midway.pix.domain.entity.Agendamento;
import com.midway.pix.domain.entity.StatusAgendamento;
import com.midway.pix.domain.entity.StatusRisco;
import com.midway.pix.domain.repository.AgendamentoRepository;
import com.midway.pix.shared.LogSeguro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AnaliseFraudeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnaliseFraudeService.class);
    private static final BigDecimal LIMITE_REJEICAO = new BigDecimal("10000.00");
    private static final BigDecimal LIMITE_REVISAO = new BigDecimal("5000.00");
    private static final Duration JANELA_CURTA = Duration.ofMinutes(5);
    private static final Duration JANELA_LONGA = Duration.ofMinutes(60);
    private final AgendamentoRepository agendamentoRepository;

    public AnaliseFraudeService(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
    }

    public ResultadoAnalise analisar(Agendamento agendamento) {
        ResultadoAnalise resultado;
        Optional<Instant> bloqueioAtivo = buscarBloqueioAtivo(agendamento);
        Optional<ResultadoAnalise> resultadoVolumeRecente = analisarVolumeRecente(agendamento);

        if (bloqueioAtivo.isPresent()) {
            resultado = new ResultadoAnalise(
                    StatusRisco.REJEITADO,
                    "Recebimentos deste pagador estão bloqueados temporariamente",
                    bloqueioAtivo.get()
            );
        } else if (agendamento.getValor().compareTo(LIMITE_REJEICAO) > 0) {
            resultado = new ResultadoAnalise(
                    StatusRisco.REJEITADO,
                    "Valor acima do limite permitido",
                    null
            );
        } else if (existeAgendamentoSemelhante(agendamento)) {
            resultado = new ResultadoAnalise(
                    StatusRisco.REVISAO_MANUAL,
                    "Existe outro agendamento com o mesmo pagador, destinatário, valor e data",
                    null
            );
        } else if (possuiDoisAgendamentosNaMesmaData(agendamento)) {
            resultado = new ResultadoAnalise(
                    StatusRisco.REVISAO_MANUAL,
                    "Terceiro ou posterior agendamento para o mesmo destinatário e data",
                    null
            );
        } else if (resultadoVolumeRecente.isPresent()) {
            resultado = resultadoVolumeRecente.get();
        } else if (agendamento.getValor().compareTo(LIMITE_REVISAO) > 0) {
            resultado = new ResultadoAnalise(
                    StatusRisco.REVISAO_MANUAL,
                    "Valor requer revisão manual",
                    null
            );
        } else if (duracaoSuperiorADoisAnos(agendamento)) {
            resultado = new ResultadoAnalise(
                    StatusRisco.REVISAO_MANUAL,
                    "Período da recorrência superior a dois anos",
                    null
            );
        } else {
            resultado = new ResultadoAnalise(
                    StatusRisco.APROVADO,
                    "Nenhum indício de risco identificado",
                    null
            );
        }

        registrarResultado(agendamento, resultado);
        return resultado;
    }

    private void registrarResultado(Agendamento agendamento, ResultadoAnalise resultado) {
        String mensagem = "Análise de risco: agendamentoId={}, idempotencyKey={}, pagador={}, recebedor={}, "
                + "valor={}, primeiroPagamento={}, dataFim={}, resultado={}, motivo={}, bloqueadoAte={}";
        Object[] dados = {
                agendamento.getId(),
                LogSeguro.mascarar(agendamento.getChaveIdempotencia()),
                LogSeguro.mascarar(agendamento.getIdentificadorPagador()),
                LogSeguro.mascarar(agendamento.getChavePixRecebedor()),
                agendamento.getValor(),
                agendamento.getDataPrimeiroPagamento(),
                agendamento.getDataFim(),
                resultado.status(),
                resultado.motivo(),
                resultado.bloqueadoAte()
        };

        if (resultado.status() == StatusRisco.APROVADO) {
            LOGGER.info(mensagem, dados);
        } else {
            LOGGER.warn(mensagem, dados);
        }
    }

    private boolean existeAgendamentoSemelhante(Agendamento novoAgendamento) {
        return agendamentoRepository.buscarTodos().stream()
                .filter(existente -> !existente.getId().equals(novoAgendamento.getId()))
                .filter(this::estaEmAberto)
                .anyMatch(existente -> mesmoAgendamento(existente, novoAgendamento));
    }

    private Optional<ResultadoAnalise> analisarVolumeRecente(Agendamento novoAgendamento) {
        List<Agendamento> agendamentosRecentes = agendamentoRepository.buscarTodos().stream()
                .filter(existente -> !existente.getId().equals(novoAgendamento.getId()))
                .filter(this::estaEmAberto)
                .filter(existente -> mesmoPagadorERecebedor(existente, novoAgendamento))
                .filter(existente -> !existente.getCriadoEm().isAfter(novoAgendamento.getCriadoEm()))
                .toList();

        long ultimaHora = contarDesde(
                agendamentosRecentes,
                novoAgendamento.getCriadoEm().minus(JANELA_LONGA)
        );
        if (ultimaHora >= 9) {
            return Optional.of(new ResultadoAnalise(
                    StatusRisco.REVISAO_MANUAL,
                    "Dez ou mais agendamentos para o mesmo destinatário em 60 minutos",
                    novoAgendamento.getCriadoEm().plus(Duration.ofHours(24))
            ));
        }

        long ultimosCincoMinutos = contarDesde(
                agendamentosRecentes,
                novoAgendamento.getCriadoEm().minus(JANELA_CURTA)
        );
        if (ultimosCincoMinutos >= 3) {
            return Optional.of(new ResultadoAnalise(
                    StatusRisco.REVISAO_MANUAL,
                    "Quatro ou mais agendamentos para o mesmo destinatário em 5 minutos",
                    null
            ));
        }

        return Optional.empty();
    }

    private Optional<Instant> buscarBloqueioAtivo(Agendamento novoAgendamento) {
        return agendamentoRepository.buscarTodos().stream()
                .filter(existente -> mesmoPagadorERecebedor(existente, novoAgendamento))
                .map(Agendamento::getBloqueadoAte)
                .filter(java.util.Objects::nonNull)
                .filter(bloqueadoAte -> bloqueadoAte.isAfter(novoAgendamento.getCriadoEm()))
                .max(Instant::compareTo);
    }

    private boolean possuiDoisAgendamentosNaMesmaData(Agendamento novoAgendamento) {
        return agendamentoRepository.buscarTodos().stream()
                .filter(existente -> !existente.getId().equals(novoAgendamento.getId()))
                .filter(this::estaEmAberto)
                .filter(existente -> mesmoPagadorERecebedor(existente, novoAgendamento))
                .filter(existente -> !existente.getCriadoEm().isAfter(novoAgendamento.getCriadoEm()))
                .filter(existente -> existente.getDataPrimeiroPagamento()
                        .equals(novoAgendamento.getDataPrimeiroPagamento()))
                .count() >= 2;
    }

    private long contarDesde(List<Agendamento> agendamentos, Instant inicio) {
        return agendamentos.stream()
                .filter(agendamento -> !agendamento.getCriadoEm().isBefore(inicio))
                .count();
    }

    private boolean estaEmAberto(Agendamento agendamento) {
        return agendamento.getStatus() != StatusAgendamento.CANCELADO
                && agendamento.getStatus() != StatusAgendamento.REJEITADO
                && agendamento.getStatus() != StatusAgendamento.CONCLUIDO;
    }

    private boolean mesmoAgendamento(Agendamento existente, Agendamento novoAgendamento) {
        return mesmoPagadorERecebedor(existente, novoAgendamento)
                && existente.getValor().compareTo(novoAgendamento.getValor()) == 0
                && existente.getDataPrimeiroPagamento()
                .equals(novoAgendamento.getDataPrimeiroPagamento());
    }

    private boolean mesmoPagadorERecebedor(Agendamento existente, Agendamento novoAgendamento) {
        return existente.getIdentificadorPagador()
                .equalsIgnoreCase(novoAgendamento.getIdentificadorPagador())
                && existente.getChavePixRecebedor()
                .equalsIgnoreCase(novoAgendamento.getChavePixRecebedor());
    }

    private boolean duracaoSuperiorADoisAnos(Agendamento agendamento) {
        return agendamento.getDataFim() != null
                && agendamento.getDataFim().isAfter(agendamento.getDataPrimeiroPagamento().plusYears(2));
    }

    public record ResultadoAnalise(StatusRisco status, String motivo, Instant bloqueadoAte) {
    }
}
