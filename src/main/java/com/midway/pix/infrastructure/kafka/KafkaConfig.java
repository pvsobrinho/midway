package com.midway.pix.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.retrytopic.RetryTopicSchedulerWrapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableKafkaRetryTopic
public class KafkaConfig {

    @Bean
    public NewTopic agendamentoSolicitadoTopic(
            @Value("${app.kafka.topic.agendamento-solicitado}") String nomeTopico
    ) {
        return TopicBuilder.name(nomeTopico)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public RetryTopicSchedulerWrapper retryTopicSchedulerWrapper() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("kafka-retry-");
        return new RetryTopicSchedulerWrapper(scheduler);
    }
}
