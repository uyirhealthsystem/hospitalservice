package com.uyir.hospital.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hospitalServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Uyir Hospital Service API")
                        .version("0.0.1")
                        .description(
                                "REST API for hospital registry, doctor roster/live check-in, and emergency-SOS "
                                        + "hospital lookup. All endpoints are currently unauthenticated (open "
                                        + "security posture until JWT/OAuth2 is decided)."))
                .tags(List.of(
                        new Tag().name("Hospitals").description("Hospital registry - CRUD, search, geospatial lookup"),
                        new Tag().name("Doctors").description("Doctor roster - CRUD, search, live check-in/check-out"),
                        new Tag().name("Emergency SOS")
                                .description("Cross-cutting read-only lookup joining hospitals and live doctor presence")));
    }
}
