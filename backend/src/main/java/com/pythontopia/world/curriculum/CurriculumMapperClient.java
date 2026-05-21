package com.pythontopia.world.curriculum;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.world.curriculum.dto.MapperRequest;
import com.pythontopia.world.curriculum.dto.MapperResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class CurriculumMapperClient {

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public CurriculumMapperClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  public MapperResponse callMap(MapperRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<MapperRequest> entity = new HttpEntity<>(request, headers);
    return restTemplate
        .exchange(
            properties.graphUrl() + "/agents/curriculum/map",
            HttpMethod.POST,
            entity,
            MapperResponse.class)
        .getBody();
  }
}
