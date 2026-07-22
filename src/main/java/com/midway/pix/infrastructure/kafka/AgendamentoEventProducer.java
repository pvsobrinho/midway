package com.midway.pix.infrastructure.kafka;

import com.midway.pix.domain.entity.Agendamento;
import com.midway.pix.infrastructure.kafka.event.AgendamentoSolicitadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class AgendamentoEventProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgendamentoEventProducer.class);

    private final KafkaTemplate<String, AgendamentoSolicitadoEvent> kafkaTemplate;
    private final String topic;

    public AgendamentoEventProducer(
            KafkaTemplate<String, AgendamentoSolicitadoEvent> kafkaTemplate,
            @Value("${app.kafka.topic.agendamento-solicitado}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public CompletableFuture<SendResult<String, AgendamentoSolicitadoEvent>> publicar(
            Agendamento agendamento
    ) {
        AgendamentoSolicitadoEvent evento = new AgendamentoSolicitadoEvent(
                UUID.randomUUID(),
                agendamento.getId(),
                Instant.now()
        );

        try {
            return kafkaTemplate.send(topic, chaveParticao(agendamento), evento)
                    .whenComplete((resultado, erro) -> {
                        if (erro == null) {
                            LOGGER.info(
                                    "Evento publicado: eventoId={}, agendamentoId={}, topic={}, partition={}, offset={}",
                                    evento.eventoId(),
                                    evento.agendamentoId(),
                                    topic,
                                    resultado.getRecordMetadata().partition(),
                                    resultado.getRecordMetadata().offset()
                            );
                        } else {
                            LOGGER.error(
                                    "Falha ao publicar evento: eventoId={}, agendamentoId={}, topic={}",
                                    evento.eventoId(),
                                    evento.agendamentoId(),
                                    topic,
                                    erro
                            );
                        }
                    });
        } catch (RuntimeException erro) {
            LOGGER.error(
                    "Falha imediata ao publicar evento: eventoId={}, agendamentoId={}, topic={}",
                    evento.eventoId(),
                    evento.agendamentoId(),
                    topic,
                    erro
            );
            return CompletableFuture.failedFuture(erro);
        }
    }

    private String chaveParticao(Agendamento agendamento) {
        String origem = agendamento.getIdentificadorPagador().trim().toLowerCase(Locale.ROOT)
                + "|"
                + agendamento.getChavePixRecebedor().trim().toLowerCase(Locale.ROOT);
        return UUID.nameUUIDFromBytes(origem.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
