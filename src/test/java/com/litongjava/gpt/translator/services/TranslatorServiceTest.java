package com.litongjava.gpt.translator.services;

import org.junit.Test;

import com.litongjava.gpt.translator.config.TranslateAdminAppConfig;
import com.litongjava.gpt.translator.vo.TranslatorTextVo;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.tio.boot.testing.TioBootTest;
import com.litongjava.tio.utils.environment.EnvUtils;

public class TranslatorServiceTest {

  @Test
  public void translate() {
    EnvUtils.load();
    TranslatorTextVo translatorTextVo = new TranslatorTextVo();
    translatorTextVo.setDestLang("English").setSrcLang("Chinese").setSrcText("今天天气怎么样");
    String translate = Aop.get(TranslatorService.class).translate(translatorTextVo);
    System.out.println(translate); //How is the weather today?
  }

  @Test
  public void translateWithTelegram() {
    TioBootTest.runWith(TranslateAdminAppConfig.class);
    TranslatorTextVo translatorTextVo = new TranslatorTextVo();
    translatorTextVo.setDestLang("English").setSrcLang("Chinese").setSrcText("今天天气怎么样,亲");
    String translate = Aop.get(TranslatorService.class).translate("001", translatorTextVo);
    System.out.println(translate); //How is the weather today?
  }
}
