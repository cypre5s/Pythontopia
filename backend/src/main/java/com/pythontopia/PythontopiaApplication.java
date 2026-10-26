package com.pythontopia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.pythontopia")
@EnableAsync
public class PythontopiaApplication {

  public static void main(String[] args) {
    SpringApplication.run(PythontopiaApplication.class, args);
  }
}
