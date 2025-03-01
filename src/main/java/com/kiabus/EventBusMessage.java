package com.kiabus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventBusMessage {

  private String type;
  private String data;
  private String address;
  private String replyAddress;
  private String trackId;
  private Object body;

  public static final EventBusMessage PONG = new EventBusMessage();
  public static final EventBusMessage DONE = new EventBusMessage();
  static {
    PONG.setType("pong");
    DONE.setType("done");
  }
}
