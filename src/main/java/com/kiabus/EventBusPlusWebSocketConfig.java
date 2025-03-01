package com.kiabus;

import io.vertx.core.Vertx;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Slf4j
@Configuration
@EnableWebSocket
public class EventBusPlusWebSocketConfig implements ApplicationContextAware {

  @Bean
  @ConditionalOnMissingBean
  public ServerEndpointExporter serverEndpointExporter() {
    return new ServerEndpointExporter();
  }

  @Bean
  @ConditionalOnMissingBean
  public Vertx vertx() {
    Vertx vertx = Vertx.vertx();
//    vertx.setPeriodic(1000, ar -> {
//      vertx.eventBus().publish("abcd", System.currentTimeMillis());
//    });
    vertx.eventBus().consumer("abcd-1", h -> log.info("Received message: {}", h.body()));
    vertx.eventBus().consumer("abcd-2", h -> log.info("Received message: {}", h.body()));

    return vertx;
  }

  @Override
  public void setApplicationContext(@NonNull ApplicationContext ctx) throws BeansException {
    SpringUtil.setApplicationContext(ctx);
  }

}
