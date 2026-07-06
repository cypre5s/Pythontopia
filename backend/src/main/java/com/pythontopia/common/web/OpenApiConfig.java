package com.pythontopia.common.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI pythontopiaOpenApi() {
    return new OpenAPI()
        .info(new Info().title("Pythontopia API").version("0.1.0"))
        .addTagsItem(new Tag().name("auth").description("Student and teacher authentication"))
        .addTagsItem(new Tag().name("classroom").description("Lightweight class management"))
        .addTagsItem(new Tag().name("learner").description("Learner profile access"))
        .addTagsItem(new Tag().name("world").description("Learning town world state"))
        .addTagsItem(new Tag().name("course").description("Python language course pack"));
  }
}
