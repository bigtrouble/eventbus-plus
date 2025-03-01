package com.kiabus;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationContext;

class SpringUtil {

  @Setter
  @Getter
  private static ApplicationContext applicationContext;

}
