package com.uyir.hospital.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JacksonConfig {

    @Bean
    public JacksonModule geoJsonPointDeserializerModule() {
        return new SimpleModule().addDeserializer(GeoJsonPoint.class, new GeoJsonPointDeserializer());
    }

    // Jackson 3 defaults FAIL_ON_NULL_FOR_PRIMITIVES to true (Jackson 2 defaulted it to false),
    // so an absent JSON field that Lombok's all-args constructor maps to a primitive boolean/int
    // (e.g. SurgicalNetwork.laserEquipmentAvailable when only joinedSurgicalNetwork is sent)
    // throws instead of defaulting. Every optional nested DTO group relies on partial payloads.
    @Bean
    public JsonMapperBuilderCustomizer primitiveNullToleranceCustomizer() {
        return builder -> builder.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }
}
