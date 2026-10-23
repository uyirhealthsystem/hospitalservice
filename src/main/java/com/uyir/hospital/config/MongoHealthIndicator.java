package com.uyir.hospital.config;

import org.bson.Document;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

// Replaces Spring Boot's own Mongo health check - same bean name, so Boot's auto-configuration backs off.
// Boot 4's version lists every database on the cluster and runs `hello` against each one, including Atlas's
// internal `local` database. A database-scoped Atlas user isn't allowed there, so every health check (k8s
// probes, docker healthcheck, IDE actuator view) logged "(Unauthorized) not authorized on local to execute
// command { hello: 1 }" and reported DOWN. This pings only the service's own database.
@Component("mongoHealthIndicator")
public class MongoHealthIndicator extends AbstractHealthIndicator {

    private final MongoTemplate mongoTemplate;

    public MongoHealthIndicator(MongoTemplate mongoTemplate) {
        super("MongoDB health check failed");
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        Document result = mongoTemplate.executeCommand("{ ping: 1 }");
        builder.up().withDetail("database", mongoTemplate.getDb().getName()).withDetail("ping", result.get("ok"));
    }
}
