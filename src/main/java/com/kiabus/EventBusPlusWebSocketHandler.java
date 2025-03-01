package com.kiabus;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@ServerEndpoint(value = "/kiabus/eventbus")
public class EventBusPlusWebSocketHandler {

  private static final ObjectMapper om = new ObjectMapper();
  static {
    om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }
  private static final Map<String, Map<String, MessageConsumer<?>>> sessionHolder = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, Message<Object>> localReplyHolderMap = new ConcurrentHashMap<>();

  private EventBus eb() {
    val vertx = SpringUtil.getApplicationContext().getBean(Vertx.class);
    return vertx.eventBus();
  }

  @OnOpen
  public void onOpen(Session session) {
    sendMessage(session, EventBusMessage.DONE);
  }

  @OnMessage
  public void onMessage(Session session, String json) throws JsonProcessingException {
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


  @OnClose
  public void onClose(Session session) {
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
  private void onSendMessage(Session session, EventBusMessage msg) {
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
  private void onUnRegister(Session session, String address) {
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
  private void onRegister(Session session, EventBusMessage msg) {
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


  private void deliverMessage(Session session, String address, boolean isReply, io.vertx.core.eventbus.Message<Object> h) {
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


  private void deliverErrorMessage(Session session, String address, Throwable e) {
    log.info("todo");
  }


  /**
   * @param session Session
   * @param msg     EventBusMessage
   */
  private void sendMessage(Session session, EventBusMessage msg) {
    try {
      session.getBasicRemote().sendText(om.writeValueAsString(msg));
    } catch (Exception e) {
      log.error("Error sending message.", e);
    }
  }


}
