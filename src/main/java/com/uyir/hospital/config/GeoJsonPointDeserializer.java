package com.uyir.hospital.config;

import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

public class GeoJsonPointDeserializer extends ValueDeserializer<GeoJsonPoint> {

    @Override
    public GeoJsonPoint deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        JsonNode coordinates = ctxt.readTree(p).get("coordinates");
        return new GeoJsonPoint(coordinates.get(0).asDouble(), coordinates.get(1).asDouble());
    }
}
