package com.litongjava.gpt.translator.config;

import com.litongjava.annotation.AConfiguration;
import com.litongjava.annotation.Initialization;
import com.litongjava.tio.boot.admin.config.TioAdminDbConfiguration;

@AConfiguration
public class TranslateAdminAppConfig {

  @Initialization
  public void config() {
    new TioAdminDbConfiguration().config();
  }
}
