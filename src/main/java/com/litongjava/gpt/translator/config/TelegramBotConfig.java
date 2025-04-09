package com.litongjava.gpt.translator.config;
import com.litongjava.annotation.AConfiguration;
import com.litongjava.annotation.Initialization;
import com.litongjava.hook.HookCan;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.telegram.Telegram;
import com.litongjava.tio.utils.telegram.TelegramBot;

@AConfiguration
public class TelegramBotConfig {

  @Initialization
  public void config() {
    String botToken = EnvUtils.getStr("telegram.bot.token");
    // 创建一个 Telegram bot 实例
    TelegramBot bot = new TelegramBot(botToken);
    // 将 bot 添加到 Telegram 管理类中
    Telegram.addBot(bot);

    // 添加销毁方法，确保在应用关闭时清理资源
    HookCan.me().addDestroyMethod(() -> {
      Telegram.clearBot();
    });
  }
}

