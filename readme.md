# max-translator-bot
Telegram 翻译机器人

本项目演示如何基于 HTTP 协议开发一个具备翻译功能的 Telegram 机器人。项目主要使用 Webhook 与 Telegram 平台进行通信，并通过调用翻译模型实现自动中英文翻译，支持自动语言检测、翻译缓存与使用记录统计。

## 目录

- [项目简介](#项目简介)
- [特性](#特性)
- [环境要求](#环境要求)
- [安装与配置](#安装与配置)
- [代码结构](#代码结构)
  - [配置模块](#配置模块)
  - [翻译服务](#翻译服务)
  - [Webhook 控制器](#webhook-控制器)
  - [测试类](#测试类)
- [使用说明](#使用说明)
- [示例](#示例)
- [开源地址](#开源地址)

## 项目简介

Telegram 翻译机器人基于 HTTP 协议，通过 Webhook 实现消息的接收和响应。项目中通过调用翻译服务，将用户发送的文本自动翻译为另一种语言，并返回翻译结果。主要功能包括：
- 自动检测消息语言（中文或英文）
- 调用翻译模型获得翻译文本
- 支持超长文本拆分翻译，并采用缓存策略避免重复计算
- 使用 Tio Boot 整合 Telegram Bot，实现消息的即时响应

## 特性

- **多协议支持**：项目支持 HTTP 协议，可用于大多数业务场景；同时也介绍 MTProto 协议的区别。
- **自动语言检测**：通过正则表达式判断文本是否包含中文，自动设置翻译方向。
- **提示词配置**：通过预定义的提示词文件指导翻译模型，仅返回翻译结果。
- **翻译缓存**：计算文本 MD5 值以检测缓存，避免重复翻译，提升效率。
- **文本拆分**：长文本超过最大令牌数时，采用递归算法按 Markdown 结构或二分法拆分文本。
- **Tio Boot 整合**：整合 Tio Boot 平台，自动注册 Telegram Bot，并实现资源的正确释放。

## 环境要求

- JDK 8 或以上版本
- Maven 项目管理
- Telegram API Token（通过 [BotFather](https://t.me/BotFather) 获取）
- HTTPS 服务器（用于配置 Telegram Webhook）
- 数据库支持（项目中使用 JFinal ActiveRecord 管理缓存记录）

## 安装与配置

1. **克隆项目**

   ```bash
   git clone https://github.com/litongjava/max-translator-bot.git
   cd max-translator-bot
   ```

2. **配置 Telegram Bot Token**

   在 `app.properties` 中添加如下配置，并将 `telegram.bot.token` 替换为您的实际 Token：

   ```properties
   telegram.bot.token=YOUR_TELEGRAM_BOT_TOKEN
   ```

3. **设置 Webhook**

   根据项目文档设置 Webhook 地址，并确保服务器可通过 HTTPS 访问。具体步骤请参考：
   
   [设置 Webhook](https://tio-boot.litongjava.com/zh/23_tio-utils/10.html#%E8%AE%BE%E7%BD%AE-webhook)

4. **提示词文件**

   在 `src/main/resources/prompts/translator_prompt.txt` 文件中定义翻译提示词，示例如下：

   ```enjoy
   You are a helpful translator.
   - Please translate the following #(src_lang) into #(dst_lang).
   - Preserve the format of the source content during translation.
   - Do not provide any explanations or text apart from the translation.

   source text:
   #(source_text)
   ```

## 代码结构

### 配置模块

1. **TranslateAdminAppConfig**

   用于初始化后台数据库配置，便于翻译记录的保存：

   ```java
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
   ```

2. **TelegramBotConfig**

   完成 Telegram Bot 的初始化配置，将 Bot 注册到 Telegram 管理类中，并添加销毁方法：

   ```java
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
   ```

### 翻译服务

1. **TranslatorTextVo**

   封装翻译请求的关键信息（源文本、源语言、目标语言）：

   ```java
   package com.litongjava.gpt.translator.vo;

   import lombok.AllArgsConstructor;
   import lombok.Data;
   import lombok.NoArgsConstructor;

   @Data
   @NoArgsConstructor
   @AllArgsConstructor
   public class TranslatorTextVo {
     // 例如："今天的任务你完成了吗?", Chinese, English
     private String srcText, srcLang, destLang;
   }
   ```

2. **TranslatorService**

   核心类，负责构造翻译请求、文本拆分、调用翻译模型以及缓存记录管理：

   ```java
   package com.litongjava.gpt.translator.services;

   import java.util.ArrayList;
   import java.util.HashMap;
   import java.util.List;
   import java.util.Map;

   import com.jfinal.template.Template;
   import com.litongjava.db.activerecord.Db;
   import com.litongjava.db.activerecord.Row;
   import com.litongjava.gemini.GeminiClient;
   import com.litongjava.gemini.GoogleGeminiModels;
   import com.litongjava.gpt.translator.constant.TableNames;
   import com.litongjava.gpt.translator.vo.TranslatorTextVo;
   import com.litongjava.template.PromptEngine;
   import com.litongjava.tio.utils.crypto.Md5Utils;
   import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;
   import com.litongjava.utils.TokenUtils;

   public class TranslatorService {

     // Define the maximum number of tokens per request
     private static final int MAX_TOKENS_PER_REQUEST = 1048576 / 3;

     /**
      * Translate method that handles splitting the text if it exceeds the token limit.
      */
     public String translate(String chatId, TranslatorTextVo translatorTextVo) {
       String srcText = translatorTextVo.getSrcText();
       String md5 = Md5Utils.getMD5(srcText);
       Row record = Db.findColumnsById(TableNames.max_kb_sentence_tanslate_cache, "dst_text", "md5", md5);
       String dstText = null;
       if (record != null) {
         dstText = record.getStr("dst_text");
         if (dstText != null) {
           return dstText;
         }
       }

       // Split the srcText into chunks if necessary
       int countTokens = TokenUtils.countTokens(srcText);
       StringBuilder translatedBuilder = new StringBuilder();
       long totalStart = System.currentTimeMillis();
       if (countTokens > MAX_TOKENS_PER_REQUEST) {
         List<String> textChunks = splitTextIntoChunks(srcText, countTokens, MAX_TOKENS_PER_REQUEST);

         for (String chunk : textChunks) {
           TranslatorTextVo chunkVo = new TranslatorTextVo();
           chunkVo.setSrcLang(translatorTextVo.getSrcLang());
           chunkVo.setDestLang(translatorTextVo.getDestLang());
           chunkVo.setSrcText(chunk);

           long start = System.currentTimeMillis();
           String translatedChunk = this.translate(chunkVo);
           translatedBuilder.append(translatedChunk);
           long end = System.currentTimeMillis();

           // save each chunk's translation to the cache
           saveTranslation(chatId, translatorTextVo, chunk, translatedChunk, end - start);
         }
       } else {
         TranslatorTextVo chunkVo = new TranslatorTextVo();
         chunkVo.setSrcLang(translatorTextVo.getSrcLang());
         chunkVo.setDestLang(translatorTextVo.getDestLang());
         chunkVo.setSrcText(srcText);

         String translatedChunk = this.translate(chunkVo);

         translatedBuilder.append(translatedChunk);
       }

       long totalEnd = System.currentTimeMillis();
       String finalTranslatedText = translatedBuilder.toString().trim();

       // Optionally, save the combined translation to the cache
       saveCombinedTranslation(chatId, translatorTextVo, srcText, finalTranslatedText, totalEnd - totalStart);

       return finalTranslatedText;
     }

     /**
      * Helper method to split text into chunks based on the maximum token limit.
      */
     /**
      * Splits text into chunks based on the maximum token limit using a recursive binary splitting approach.
      * This method prioritizes splitting by Markdown structure (e.g., paragraphs) to preserve formatting.
      */
     public List<String> splitTextIntoChunks(String text, int tokenCount, int maxTokens) {
       List<String> chunks = new ArrayList<>();
       splitRecursive(text, tokenCount, maxTokens, chunks);
       return chunks;
     }

     private void splitRecursive(String text, int tokenCount, int maxTokens, List<String> chunks) {
       if (tokenCount <= maxTokens) {
         chunks.add(text.trim());
         return;
       }

       // Attempt to split by Markdown paragraphs
       String[] paragraphs = text.split("\n{2,}");
       if (paragraphs.length > 1) {
         for (String para : paragraphs) {
           splitRecursive(para, tokenCount, maxTokens, chunks);
         }
         return;
       }

       // Attempt to split by Markdown headings
       String[] headings = text.split("(?m)^#{1,6} ");
       if (headings.length > 1) {
         for (String section : headings) {
           // Add the heading back since split removes the delimiter
           String trimmedSection = section.trim();
           if (!trimmedSection.startsWith("#")) {
             trimmedSection = "#" + trimmedSection;
           }
           splitRecursive(trimmedSection, tokenCount, maxTokens, chunks);
         }
         return;
       }

       // If unable to split by structure, perform binary split
       int mid = text.length() / 2;
       // Ensure we split at a whitespace to avoid breaking words
       while (mid < text.length() && !Character.isWhitespace(text.charAt(mid))) {
         mid++;
       }
       if (mid == text.length()) {
         // No whitespace found; force split
         mid = text.length() / 2;
       }

       String firstHalf = text.substring(0, mid);
       String secondHalf = text.substring(mid);

       splitRecursive(firstHalf, tokenCount, maxTokens, chunks);
       splitRecursive(secondHalf, tokenCount, maxTokens, chunks);
     }

     /**
      * Save each chunk's translation to the cache.
      */
     private void saveTranslation(String chatId, TranslatorTextVo originalVo, String srcChunk, String dstChunk, long elapsed) {
       String md5 = Md5Utils.getMD5(srcChunk);
       long id = SnowflakeIdUtils.id();
       Row saveRecord = Row.by("id", id).set("md5", md5).set("from", "telegram").set("user_id", chatId)
           //
           .set("src_lang", originalVo.getSrcLang()).set("src_text", srcChunk)
           //
           .set("dst_lang", originalVo.getDestLang()).set("dst_text", dstChunk).set("elapsed", elapsed);
       //
       Db.save(TableNames.max_kb_sentence_tanslate_cache, saveRecord);
     }

     /**
      * Optionally save the combined translation to the cache.
      */
     private void saveCombinedTranslation(String chatId, TranslatorTextVo originalVo, String srcText, String dstText, long elapsed) {
       String md5 = Md5Utils.getMD5(srcText);
       long id = SnowflakeIdUtils.id();
       Row saveRecord = Row.by("id", id).set("md5", md5).set("from", "telegram").set("user_id", chatId)
           //
           .set("src_lang", originalVo.getSrcLang()).set("src_text", srcText)
           //
           .set("dst_lang", originalVo.getDestLang()).set("dst_text", dstText).set("elapsed", elapsed);
       Db.save(TableNames.max_kb_sentence_tanslate_cache, saveRecord);
     }

     /**
      * Original translate method that translates a single chunk.
      */
     public String translate(TranslatorTextVo translatorTextVo) {
       String srcLang = translatorTextVo.getSrcLang();
       String destLang = translatorTextVo.getDestLang();
       String srcText = translatorTextVo.getSrcText();

       Template template = PromptEngine.getTemplate("translator_prompt.txt");
       Map<String, String> values = new HashMap<>();

       values.put("src_lang", srcLang);
       values.put("dst_lang", destLang);
       values.put("source_text", srcText);

       String prompt = template.renderToString(values);
       String response = GeminiClient.chatWithModel(GoogleGeminiModels.GEMINI_2_0_FLASH_EXP, "user", prompt);
       return response;
     }
   }
   ```

### Webhook 控制器

`TelegramWebhookController` 类处理来自 Telegram 的 Webhook 请求，解析消息内容，调用翻译服务并将翻译结果返回给用户：

```java
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
      responseText = Aop.get(TranslatorService.class).translate(chatId, translatorTextVo);
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
```

### 测试类

提供测试代码验证翻译服务的正确性：

```java
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
    System.out.println(translate); // How is the weather today?
  }

  @Test
  public void translateWithTelegram() {
    TioBootTest.runWith(TranslateAdminAppConfig.class);
    TranslatorTextVo translatorTextVo = new TranslatorTextVo();
    translatorTextVo.setDestLang("English").setSrcLang("Chinese").setSrcText("今天天气怎么样,亲");
    String translate = Aop.get(TranslatorService.class).translate("001", translatorTextVo);
    System.out.println(translate); // How is the weather today?
  }
}
```

## 使用说明

1. **启动服务器**  
   确保服务器正常运行，并且已配置 HTTPS 和正确的 Webhook 地址。

2. **与机器人对话**  
   在 Telegram 搜索您的机器人账号，发送需要翻译的文本（例如："你好，世界！" 或 "Good morning!"）。

3. **翻译响应**  
   机器人将自动检测文本中的语言，并返回翻译后的结果。

## 示例

- 用户发送：`你好，世界！`  
  机器人回复：`Hello, World!`

- 用户发送：`Good morning!`  
  机器人回复：`早上好！`

## 开源地址

项目代码托管在 GitHub 上，欢迎访问、参考和贡献：

- GitHub 地址： [https://github.com/litongjava/max-translator-bot](https://github.com/litongjava/max-translator-bot)
- 体验翻机器人：@maxtranslatorbot

---

欢迎使用并提出宝贵意见，让这个项目变得更加完善！