package com.linkedin.searchservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Where records go when a consumer cannot process them.
 *
 * <p>Without this, a record that throws is retried a few times and then dropped with a log
 * line - the event is gone and nothing can replay it. With a dead-letter recoverer the
 * original record is republished to {@code <topic>.DLT}, where it can be inspected and, after
 * a fix, replayed into the consumer by hand.
 *
 * <p>Retries are deliberately few and close together (three attempts, 500ms apart): the
 * failures worth retrying here are transient (a downstream store briefly unavailable), and a
 * long retry blocks the whole partition, delaying every healthy record behind it.
 *
 * <p>Scope: this covers failures raised by the listener, i.e. a record that deserialised but
 * could not be processed. A record that cannot be deserialised at all still produces a
 * DeserializationException, and forwarding those requires a byte-array producer so the raw
 * payload can be copied; today they are logged and skipped by ErrorHandlingDeserializer.
 */
@Configuration
public class KafkaErrorConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {

        // Default naming appends .DLT to the topic, and partitions follow the original.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 2L));
    }
}
