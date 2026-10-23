package com.uyir.hospital.event;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

// Kafka is used for hospital events only when it is both selected and configured:
// hospital.events.provider=kafka (the default) AND KAFKA_BROKERS set (spring.kafka.bootstrap-servers).
// Without brokers - a plain local run - LocalHospitalEventPublisher takes over instead of a listener retrying
// localhost:9092 forever. Same rule authservice uses for its events.
final class KafkaEventsCondition {

    private KafkaEventsCondition() {
    }

    static boolean kafkaEnabled(Environment env) {
        return "kafka".equalsIgnoreCase(env.getProperty("hospital.events.provider", "kafka").trim())
                && !env.getProperty("spring.kafka.bootstrap-servers", "").isBlank();
    }

    static final class Enabled implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return kafkaEnabled(context.getEnvironment());
        }
    }

    static final class Disabled implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !kafkaEnabled(context.getEnvironment());
        }
    }
}
