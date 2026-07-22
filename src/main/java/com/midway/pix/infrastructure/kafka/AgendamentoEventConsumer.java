package com.midway.pix.infrastructure.kafka;

import com.midway.pix.domain.entity.Agendamento;
import com.midway.pix.domain.entity.PagamentoRecorrente;
import com.midway.pix.domain.entity.StatusAgendamento;
import com.midway.pix.domain.entity.StatusRisco;
import com.midway.pix.domain.repository.AgendamentoRepository;
import com.midway.pix.domain.repository.PagamentoRecorrenteRepository;
import com.midway.pix.domain.service.AnaliseFraudeService;
import com.midway.pix.infrastructure.kafka.event.AgendamentoSolicitadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AgendamentoEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgendamentoEventConsumer.class);

    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoRecorrenteRepository pagamentoRepository;
    private final AnaliseFraudeService analiseFraudeService;

    public AgendamentoEventConsumer(
            AgendamentoRepository agendamentoRepository,
            PagamentoRecorrenteRepository pagamentoRepository,
            AnaliseFraudeService analiseFraudeService
    ) {
        this.agendamentoRepository = agendamentoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.analiseFraudeService = analiseFraudeService;
    }

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 1000, multiplier = 2),
            retryTopicSuffix = "-retry",
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(
            topics = "${app.kafka.topic.agendamento-solicitado}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void processar(AgendamentoSolicitadoEvent evento) {
        Agendamento agendamento = buscarAgendamento(evento.agendamentoId());

        if (agendamento.getStatus() != StatusAgendamento.PENDENTE_ANALISE) {
            LOGGER.info(
                    "Evento ignorado por já ter sido processado: eventoId={}, agendamentoId={}, statusRisco={}",
                    evento.eventoId(),
                    evento.agendamentoId(),
                    agendamento.getStatusRisco()
            );
            return;
        }

        AnaliseFraudeService.ResultadoAnalise resultado = analiseFraudeService.analisar(agendamento);
        agendamento.registrarAnalise(
                resultado.status(),
                resultado.motivo(),
                resultado.bloqueadoAte(),
                Instant.now()
        );
        agendamentoRepository.salvar(agendamento);

        if (resultado.status() == StatusRisco.APROVADO
                && pagamentoRepository.buscarPorAgendamentoId(agendamento.getId()).isEmpty()) {
            pagamentoRepository.salvar(PagamentoRecorrente.agendar(
                    UUID.randomUUID(),
                    agendamento.getId(),
                    agendamento.getValor(),
                    agendamento.getDataPrimeiroPagamento(),
                    Instant.now()
            ));
        }

        LOGGER.info(
                "Evento processado: eventoId={}, agendamentoId={}, status={}, statusRisco={}",
                evento.eventoId(),
                agendamento.getId(),
                agendamento.getStatus(),
                agendamento.getStatusRisco()
        );
    }

    @DltHandler
    public void tratarDlt(AgendamentoSolicitadoEvent evento) {
        Agendamento agendamento = buscarAgendamento(evento.agendamentoId());
        if (agendamento.getStatus() == StatusAgendamento.PENDENTE_ANALISE) {
            agendamento.registrarAnalise(
                    StatusRisco.REVISAO_MANUAL,
                    "Falha ao processar a análise antifraude",
                    null,
                    Instant.now()
            );
            agendamentoRepository.salvar(agendamento);
        }

        LOGGER.error(
                "Evento enviado para DLT: eventoId={}, agendamentoId={}, statusRisco={}",
                evento.eventoId(),
                evento.agendamentoId(),
                agendamento.getStatusRisco()
        );
    }

    private Agendamento buscarAgendamento(UUID id) {
        return agendamentoRepository.buscarPorId(id)
                .orElseThrow(() -> new IllegalStateException("agendamento não encontrado: " + id));
    }
}
