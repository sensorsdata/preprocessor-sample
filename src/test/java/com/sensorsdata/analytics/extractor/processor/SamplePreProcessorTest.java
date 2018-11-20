package com.sensorsdata.analytics.extractor.processor;

import com.sensorsdata.analytics.extractor.common.RecordHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @auther Codievilky August
 * @since 2018/11/20
 */
public class SamplePreProcessorTest {

  private List<RecordHandler> getInputHandler() {
    RecordHandler recordHandler = new RecordHandler() {
      String record =
          "{\"distinct_id\":\"2b0a6f51a3cd6775\",\"time\":1434556935000,\"type\":\"track\",\"event\":\"ViewProduct\",\"properties\":{\"product_name\":\"苹果\"}}";

      @Override public String getOriginalData() {
        return record;
      }

      @Override public void send() {

      }

      @Override public void send(String data) {
        record = data;
      }

      @Override public String getNginxLogProject() {
        return null;
      }

      @Override public String getNginxUserAgent() {
        return null;
      }

      @Override public String getNginxLogIp() {
        return null;
      }

      @Override public long getNginxLogTime() {
        return 0;
      }

      @Override public String getNginxLogCookie() {
        return null;
      }

      @Override public String getImportToken() {
        return null;
      }
    };
    return Collections.singletonList(recordHandler);
  }

  @Test public void testForFirstProcessor() throws IOException {
    List<RecordHandler> recordHandler = getInputHandler();
    SamplePreProcessor samplePreProcessor = new SamplePreProcessor();
    samplePreProcessor.process(recordHandler);
    String result = recordHandler.get(0).getOriginalData();
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonNode = objectMapper.readTree(result);
    Assert.assertEquals("水果", jsonNode.get("properties").get("product_classify").asText());
  }

  @Test public void testForSecondProcessor() throws Exception {
    List<RecordHandler> recordHandler = getInputHandler();
    SamplePreProcessor samplePreProcessor = new SamplePreProcessor();
    SamplePreprocessor2 samplePreprocessor2 = new SamplePreprocessor2();
    samplePreProcessor.process(recordHandler);
    samplePreprocessor2.process(recordHandler);
    String result = recordHandler.get(0).getOriginalData();
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonNode = objectMapper.readTree(result);
    Assert.assertEquals("吃的", jsonNode.get("properties").get("product_type").asText());
  }
}
