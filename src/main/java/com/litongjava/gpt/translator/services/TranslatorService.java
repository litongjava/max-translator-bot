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
   * This implementation splits the text by sentences to preserve meaning.
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
    ;
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
