package io.github.bigtrouble;

import io.vertx.core.Vertx;
import lombok.val;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication(scanBasePackageClasses = {
  TestWebServer.class,
  EventBusPlusWebSocketConfig.class
})
public class TestWebServer extends WebMvcConfigurationSupport {

  static {
    System.setProperty("server.port", "49090");
  }

  public static void main(String[] args) {
    SpringApplication.run(TestWebServer.class, args);
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
