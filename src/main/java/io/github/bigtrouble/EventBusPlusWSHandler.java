package io.github.bigtrouble;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;


@Slf4j
@RequiredArgsConstructor
public class EventBusPlusWSHandler extends AbstractWebSocketHandler {

  private static final ObjectMapper om = new ObjectMapper();
  static {
    om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }
  private static final Map<String, Map<String, MessageConsumer<?>>> sessionHolder = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, Message<Object>> localReplyHolderMap = new ConcurrentHashMap<>();

  private final Supplier<ApplicationContext> applicationContext;

  private EventBus eb() {
    val vertx = applicationContext.get().getBean(Vertx.class);
    return vertx.eventBus();
  }

  @Override
  public void afterConnectionEstablished(@NonNull WebSocketSession session) {
    sendMessage(session, EventBusMessage.DONE);
  }

  @Override
  public void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) throws Exception {
    val json = message.getPayload();
    val ebMessage = om.readValue(json, EventBusMessage.class);
    switch (ebMessage.getType()) {
      case "ping" -> sendMessage(session, EventBusMessage.PONG);
      case "register" -> onRegister(session, ebMessage);
      case "unregister" -> onUnRegister(session, ebMessage.getAddress());
      case "send" -> onSendMessage(session, ebMessage);
      case "publish" -> eb().publish(ebMessage.getAddress(), ebMessage.getBody());
      default -> log.error("fix me. {}", json);
    }
  }


  @Override
  public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus closeStatus) {
    val consumers = sessionHolder.remove(session.getId());
    if (consumers != null) {
      consumers.values().forEach(c -> {
        c.unregister()
          .onFailure(err -> log.error(err.getMessage(), err))
          .onSuccess(ok -> log.info("address {} unregistered.", c.address()));
      });
    }
  }


  /**
   * @param session Session
   * @param msg     EventBusMessage
   */
  private void onSendMessage(WebSocketSession session, EventBusMessage msg) {
    val address = msg.getAddress();
    val body = msg.getBody();
    val replyAddress = msg.getReplyAddress();
    if (replyAddress != null) {
      eb().request(address, body, h -> {
        if (h.succeeded()) {
          this.deliverMessage(session, replyAddress, true, h.result());
        } else {
          this.deliverErrorMessage(session, replyAddress, h.cause());
        }
      });
    } else {
      val m2 = localReplyHolderMap.remove(address);
      if (m2 != null) {
        m2.reply(body);
      } else {
        eb().send(address, body);
      }
    }
  }

  /**
   * @param session Session
   * @param address Address
   */
  private void onUnRegister(WebSocketSession session, String address) {
    val map = sessionHolder.get(session.getId());
    if (map == null) {
      log.error("sessionHolder does not contain session {}", session);
      return;
    }
    val c = map.remove(address);
    if (c != null) {
      c.unregister()
        .onFailure(err -> log.error(err.getMessage(), err))
        .onSuccess(ok -> log.info("address {} unregistered", address));
    } else {
      log.error("unregister error! address:{}", address);
    }
  }

  /**
   * @param session Session
   * @param msg     EventBusMessage
   */
  private void onRegister(WebSocketSession session, EventBusMessage msg) {
    val address = msg.getAddress();
    val trackId = msg.getTrackId();
    val c = eb().consumer(address);
    c.handler(h -> deliverMessage(session, address, false, h));
    c.completionHandler(ar -> {
      val reply = new EventBusMessage();
      reply.setTrackId(trackId);
      reply.setBody(Map.of("address", address));
      sendMessage(session, reply);
    });
    val map = sessionHolder.computeIfAbsent(session.getId(), k -> new ConcurrentHashMap<>());
    val old = map.put(address, c);
    if (old != null) {
      log.error("[FIX ME] Duplicate consumer address. {}", address);
    }

  }


  private void deliverMessage(WebSocketSession session, String address, boolean isReply, Message<Object> h) {
    val msg = new EventBusMessage();
    if (isReply) {
      msg.setReplyAddress(address);
    } else {
      msg.setAddress(address);
      val replyAddress = h.replyAddress();
      if (replyAddress != null) {
        msg.setReplyAddress(replyAddress);
        localReplyHolderMap.put(replyAddress, h);
      }
    }
    msg.setBody(h.body());
    sendMessage(session, msg);
  }


  private void deliverErrorMessage(WebSocketSession session, String address, Throwable e) {
    log.info("todo");
  }


  /**
   * @param session Session
   * @param msg     EventBusMessage
   */
  private void sendMessage(WebSocketSession session, EventBusMessage msg) {
    try {
      session.sendMessage(new TextMessage(om.writeValueAsString(msg)));
    } catch (Exception e) {
      log.error("Error sending message.", e);
    }
  }


}
