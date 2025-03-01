package com.kiabus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

@SpringBootApplication(scanBasePackageClasses = {
  EventBusPlusWebSocketConfig.class
})
public class TestWebServer extends WebMvcConfigurationSupport {

  static {
    System.setProperty("server.port", "49090");
  }

  public static void main(String[] args) {
    SpringApplication.run(TestWebServer.class, args);
  }

}
