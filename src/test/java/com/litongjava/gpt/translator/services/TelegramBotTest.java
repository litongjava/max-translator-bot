package com.litongjava.gpt.translator.services;

import org.junit.Test;

import com.litongjava.model.http.response.ResponseVo;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.json.JsonUtils;
import com.litongjava.tio.utils.telegram.TelegramBot;

public class TelegramBotTest {

  @Test
  public void test() {
    EnvUtils.load();
    String token = EnvUtils.getStr("telegram.bot.token");
    String webHook = EnvUtils.getStr("telegram.bot.webhook");
    TelegramBot telegramBot = new TelegramBot("main", token);

    // 设置 Webhook
    ResponseVo setWebhook = telegramBot.setWebhook(webHook);
    System.out.println("Set Webhook Response: " + JsonUtils.toJson(setWebhook));

    // 获取 Webhook 信息
    ResponseVo webhookInfo = telegramBot.getWebhookInfo();
    System.out.println("Webhook Info: " + JsonUtils.toJson(webhookInfo));

    // 删除 Webhook（如果需要）
    // ResponseVo deleteWebhook = telegramBot.deleteWebhook();
    // System.out.println("Delete Webhook Response: " + JsonUtils.toJson(deleteWebhook));
  }

}
