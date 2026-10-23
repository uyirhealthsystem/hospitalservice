package com.uyir.hospital.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

// Which event beans start, with Spring Boot's real Kafka auto-configuration in the context. The point: a plain
// local run (no KAFKA_BROKERS) must start cleanly with no Kafka listener - a listener pointed at a broker that
// isn't there retries and logs forever.
class KafkaEventsConditionTest {

    @Configuration
    @EnableConfigurationProperties(HospitalEventProperties.class)
    @Import({EmergencyAlertHub.class, KafkaHospitalEventPublisher.class, LocalHospitalEventPublisher.class, HospitalEventListener.class})
    static class EventBeans {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, KafkaAutoConfiguration.class))
            .withUserConfiguration(EventBeans.class)
            .withPropertyValues("hospital.events.topic=hospital.events", "hospital.events.sse-timeout=30m",
                    // Never actually connect, even when brokers are configured below.
                    "spring.kafka.listener.auto-startup=false");

    @Test
    void noBrokersConfigured_usesLocalPublisherAndStartsNoListener() {
        runner.withPropertyValues("spring.kafka.bootstrap-servers=").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).getBean(HospitalEventPublisher.class).isInstanceOf(LocalHospitalEventPublisher.class);
            assertThat(context).doesNotHaveBean(HospitalEventListener.class);
            assertThat(context).doesNotHaveBean(KafkaHospitalEventPublisher.class);
        });
    }

    @Test
    void brokersConfigured_usesKafkaPublisherAndListener() {
        runner.withPropertyValues("spring.kafka.bootstrap-servers=kafka:9092").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).getBean(HospitalEventPublisher.class).isInstanceOf(KafkaHospitalEventPublisher.class);
            assertThat(context).hasSingleBean(HospitalEventListener.class);
            assertThat(context).doesNotHaveBean(LocalHospitalEventPublisher.class);
        });
    }

    @Test
    void providerLocal_winsEvenWithBrokers() {
        runner.withPropertyValues("spring.kafka.bootstrap-servers=kafka:9092", "hospital.events.provider=local").run(context -> {
            assertThat(context).getBean(HospitalEventPublisher.class).isInstanceOf(LocalHospitalEventPublisher.class);
            assertThat(context).doesNotHaveBean(HospitalEventListener.class);
        });
    }
}
