package com.uyir.hospital.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.autoconfigure.contributor.HealthContributorAutoConfiguration;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.mongodb.autoconfigure.health.MongoHealthContributorAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.health.contributor.Status;
import org.springframework.data.mongodb.core.MongoTemplate;

class MongoHealthIndicatorTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final MongoDatabase database = mock(MongoDatabase.class);
    private final MongoHealthIndicator indicator = new MongoHealthIndicator(mongoTemplate);

    @Test
    void pingSucceeds_upWithOwnDatabaseOnly() {
        when(mongoTemplate.executeCommand("{ ping: 1 }")).thenReturn(new Document("ok", 1.0));
        when(mongoTemplate.getDb()).thenReturn(database);
        when(database.getName()).thenReturn("hospital");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("database", "hospital");
        // Only the service's own database is touched - never a cluster-wide database listing.
        verify(mongoTemplate).executeCommand("{ ping: 1 }");
        verify(mongoTemplate).getDb();
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void pingFails_down() {
        when(mongoTemplate.executeCommand(anyString())).thenThrow(new IllegalStateException("not authorized"));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void springBootsOwnMongoHealthIndicator_backsOff() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        HealthContributorAutoConfiguration.class, MongoHealthContributorAutoConfiguration.class))
                .withBean(MongoClient.class, () -> mock(MongoClient.class))
                .withBean(MongoTemplate.class, () -> mongoTemplate)
                .withUserConfiguration(MongoHealthIndicator.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).getBean("mongoHealthIndicator").isInstanceOf(MongoHealthIndicator.class);
                    assertThat(context).doesNotHaveBean(org.springframework.boot.mongodb.health.MongoHealthIndicator.class);
                    assertThat(context).doesNotHaveBean("mongoHealthContributor");
                });
    }
}
