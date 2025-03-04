package io.github.bigtrouble;

import io.vertx.core.Vertx;
import lombok.NonNull;
import lombok.val;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication(scanBasePackageClasses = {
  TestWebServer.class,
})
public class TestWebServer extends WebMvcConfigurationSupport {

  static {
    System.setProperty("server.port", "49090");
  }

  public static void main(String[] args) {
    SpringApplication.run(TestWebServer.class, args);
  }


  @Configuration
  @EnableWebSocket
  public static class MyWebSocketConfig implements WebSocketConfigurer, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
      registry.addHandler(new EventBusPlusWSHandler(() -> this.applicationContext), "/eventbus")//设置连接路径和处理
        .setAllowedOrigins("*");
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
      this.applicationContext = applicationContext;
    }
  }


  @Bean
  public Vertx getVertx() {
    val c1 = new AtomicInteger();
    val c2 = new AtomicInteger();

    val vertx = Vertx.vertx();
    val eb = vertx.eventBus();
    vertx.setPeriodic(1000, id -> eb.send("test-send-s2c", "send server -> client:" + c1.getAndIncrement()));

    vertx.setPeriodic(1000, id -> eb.publish("test-publish-s2c", "publish server -> client" + c2.getAndIncrement()));

    vertx.eventBus().consumer("test-receive-send-c2s", message -> System.out.println(message.body()));
    vertx.eventBus().consumer("test-receive-publish-c2s", message -> System.out.println(message.body()));

    vertx.eventBus().consumer("test-receive-c2s-request", message -> message.reply(message.body().toString() + " -- ok"));

    return vertx;
  }

}
