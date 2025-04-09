package com.litongjava.gpt.translator.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class TranslatorTextVo {
  // 今天的任务你完成了吗?,Chinese,English
  private String srcText, srcLang, destLang;
}
