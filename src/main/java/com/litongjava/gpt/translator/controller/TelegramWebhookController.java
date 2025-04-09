package com.litongjava.gpt.translator.controller;

import com.alibaba.fastjson2.JSONObject;
import com.litongjava.annotation.RequestPath;
import com.litongjava.gpt.translator.services.TranslatorService;
import com.litongjava.gpt.translator.vo.TranslatorTextVo;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.utils.json.FastJson2Utils;
import com.litongjava.tio.utils.telegram.Telegram;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestPath("/telegram")
public class TelegramWebhookController {

  @RequestPath("/webhook")
  public HttpResponse handleTelegramWebhook(HttpRequest request) {
    String bodyString = request.getBodyString();

    JSONObject jsonObject = FastJson2Utils.parseObject(bodyString);
    JSONObject message = jsonObject.getJSONObject("message");

    JSONObject chat = message.getJSONObject("chat");
    String chatId = chat.getString("id");

    String text = message.getString("text");
    log.info("Received text: {}", text);

    // 根据文本内容设置源语言和目标语言
    String srcLang;
    String destLang;

    if (containsChinese(text)) {
      srcLang = "Chinese";
      destLang = "English";
    } else {
      srcLang = "English";
      destLang = "Chinese";
    }

    // 创建翻译请求对象
    TranslatorTextVo translatorTextVo = new TranslatorTextVo();
    translatorTextVo.setSrcText(text);
    translatorTextVo.setSrcLang(srcLang);
    translatorTextVo.setDestLang(destLang);
    String responseText;
    try {
      // 调用翻译服务
      responseText=Aop.get(TranslatorService.class).translate(chatId,translatorTextVo);
    } catch (Exception e) {
      log.error("Exception", e);
      responseText = "Exception: " + e.getMessage();
    }

    // 发送翻译结果回 Telegram
    Telegram.use().sendMessage(chatId.toString(), responseText);
    return TioRequestContext.getResponse();
  }

  /**
   * 判断文本中是否包含中文字符
   *
   * @param text 输入的文本
   * @return 如果包含中文字符则返回 true，否则返回 false
   */
  private boolean containsChinese(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    // 使用正则表达式检查是否包含中文字符
    return text.matches(".*[\\u4e00-\\u9fa5]+.*");
  }
}
