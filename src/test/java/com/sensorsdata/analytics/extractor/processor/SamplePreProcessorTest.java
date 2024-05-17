package com.sensorsdata.analytics.extractor.processor;

import com.sensorsdata.analytics.extractor.common.RecordHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @auther Codievilky August
 * @since 2018/11/20
 */
public class SamplePreProcessorTest {
  private static List<String> sendData = new ArrayList<>();


  static class RecordHandlerTest implements  RecordHandler{
    private String record;
    public RecordHandlerTest(String record) {
      this.record = record;
    }

    @Override public String getOriginalData() {
      return record;
    }

    @Override public void send() {
      sendData.add(record);
    }

    @Override public void send(String data) {
      record = data;
      send();
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
  }
  @Test
  public void test() {
    String record1 =
      "{\"distinct_id\":\"2b0a6f51a3cd6775\",\"time\":1434556935000,\"type\":\"track\",\"event\":\"ViewProduct\",\"properties\":{\"product_name\":\"苹果\"}}";
    // 过滤
    String record2 =
      "{\"lib\":{\"$lib\":\"Java\",\"$lib_method\":\"code\",\"$lib_version\":\"3.1.13\"},\"distinct_id\":\"108828724\",\"time\":1715847003996,\"type\":\"profile_set\",\"properties\":{\"$is_login_id\":true},\"recv_time\":1715847004000,\"project_id\":1,\"project\":\"default\",\"ver\":2}";
    String record3 =
      "{\"lib\":{\"$lib\":\"Java\",\"$lib_method\":\"code\",\"$lib_version\":\"3.1.12\"},\"distinct_id\":\"108828724\",\"time\":1715847003996,\"type\":\"profile_set\",\"properties\":{\"$is_login_id\":true},\"recv_time\":1715847004000,\"project_id\":1,\"project\":\"default\",\"ver\":2}";
    String record4 =
      "{\"lib\":{\"$lib\":\"Java\",\"$lib_method\":\"code\",\"$lib_version\":\"3.1.12\"},\"distinct_id\":\"108828724\",\"time\":1715847003996,\"type\":\"profile_set\",\"properties\":{},\"recv_time\":1715847004000,\"project_id\":1,\"project\":\"default\",\"ver\":2}";
    String record5 =
      "{\"lib\":{\"$lib\":\"Java\",\"$lib_method\":\"code\",\"$lib_version\":\"3.1.12\"},\"distinct_id\":\"108828724\",\"time\":1715847003996,\"type\":\"profile_set\",\"recv_time\":1715847004000,\"project_id\":1,\"project\":\"default\",\"ver\":2}";


    SamplePreProcessor samplePreProcessor = new SamplePreProcessor();
    List<RecordHandler> recordHandlerList = new ArrayList<>();
    recordHandlerList.add(new RecordHandlerTest(record1));
    recordHandlerList.add(new RecordHandlerTest(record2));
    recordHandlerList.add(new RecordHandlerTest(record3));
    recordHandlerList.add(new RecordHandlerTest(record4));
    recordHandlerList.add(new RecordHandlerTest(record5));

    samplePreProcessor.process(recordHandlerList);
    System.out.println(sendData);
    assert sendData.size() == 4;
  }
}
