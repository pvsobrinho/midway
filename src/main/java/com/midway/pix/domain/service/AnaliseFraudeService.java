package com.midway.pix.domain.service;

import com.midway.pix.domain.entity.Agendamento;
import com.midway.pix.domain.entity.StatusAgendamento;
import com.midway.pix.domain.entity.StatusRisco;
import com.midway.pix.domain.repository.AgendamentoRepository;
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
        Optional<String> motivoVolumeRecente = identificarVolumeRecente(agendamento);

        if (agendamento.getValor().compareTo(LIMITE_REJEICAO) > 0) {
            resultado = new ResultadoAnalise(
                    StatusRisco.REJEITADO,
                    "Valor acima do limite permitido"
            );
        } else if (existeAgendamentoSemelhante(agendamento)) {
            resultado = new ResultadoAnalise(
                    StatusRisco.REVISAO_MANUAL,
                    "Existe outro agendamento com o mesmo pagador, destinatário, valor e data"
            );
        } else if (motivoVolumeRecente.isPresent()) {
            resultado = new ResultadoAnalise(
                    StatusRisco.REVISAO_MANUAL,
                    motivoVolumeRecente.get()
            );
        } else if (agendamento.getValor().compareTo(LIMITE_REVISAO) > 0) {
            resultado = new ResultadoAnalise(
                    StatusRisco.REVISAO_MANUAL,
                    "Valor requer revisão manual"
            );
        } else if (duracaoSuperiorADoisAnos(agendamento)) {
            resultado = new ResultadoAnalise(
                    StatusRisco.REVISAO_MANUAL,
                    "Período da recorrência superior a dois anos"
            );
        } else {
            resultado = new ResultadoAnalise(
                    StatusRisco.APROVADO,
                    "Nenhum indício de risco identificado"
            );
        }

        LOGGER.info(
                "Análise de risco concluída: agendamentoId={}, resultado={}, motivo={}",
                agendamento.getId(),
                resultado.status(),
                resultado.motivo()
        );
        return resultado;
    }

    private boolean existeAgendamentoSemelhante(Agendamento novoAgendamento) {
        return agendamentoRepository.buscarTodos().stream()
                .filter(existente -> !existente.getId().equals(novoAgendamento.getId()))
                .filter(this::estaEmAberto)
                .anyMatch(existente -> mesmoAgendamento(existente, novoAgendamento));
    }

    private Optional<String> identificarVolumeRecente(Agendamento novoAgendamento) {
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
            return Optional.of("Dez ou mais agendamentos para o mesmo destinatário em 60 minutos");
        }

        long ultimosCincoMinutos = contarDesde(
                agendamentosRecentes,
                novoAgendamento.getCriadoEm().minus(JANELA_CURTA)
        );
        if (ultimosCincoMinutos >= 3) {
            return Optional.of("Quatro ou mais agendamentos para o mesmo destinatário em 5 minutos");
        }

        return Optional.empty();
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

    public record ResultadoAnalise(StatusRisco status, String motivo) {
    }
}
