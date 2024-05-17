package com.sensorsdata.analytics.extractor.processor;

import com.sensorsdata.analytics.extractor.common.RecordHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * @auther Codievilky August
 * @since 2018/11/20
 */
public class SamplePreProcessor implements BatchProcessor {

  private static final Logger logger = LoggerFactory.getLogger(SamplePreProcessor.class);

  public void process(List<RecordHandler> recordHandlerList) {
    for (RecordHandler recordHandler : recordHandlerList) {
      /*
       这里获取的 originalData 是一条符合 Sensors Analytics 数据格式定义的 Json
       数据格式定义 https://www.sensorsdata.cn/manual/data_schema.html
       */
      String originalData = recordHandler.getOriginalData();
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode recordNode = null;
      try {
        recordNode = objectMapper.readTree(originalData);
      } catch (IOException e) {
        /*
         这里异常需要进行处理，否则在抛出异常后，会导致之后的数据失效
         */
        logger.warn("Parse origin data failed. OriginalData: {}", originalData, e);
        /*
         出错后，不处理数据，将数据原封不动的传回
         */
        recordHandler.send();
        continue;
      }


      /**
       *  {
       *     "lib": { "$lib": "Java", "$lib_method": "code", "$lib_version": "3.1.13" },
       *     "distinct_id": "108828724",
       *     "time": 1715847003996,
       *     "type": "profile_set",
       *     "properties": { "$is_login_id": true },
       *     "recv_time": 1715847004000,
       *     "project_id": 1,
       *     "project": "default",
       *     "ver": 2
       *   }
       */
      JsonNode projectNode = recordNode.get("project");
      JsonNode typeNode = recordNode.get("type");

      ObjectNode libNode = (ObjectNode) recordNode.get("lib");
      ObjectNode propertiesNode = (ObjectNode) recordNode.get("properties");
      if (libNode != null && propertiesNode != null && projectNode != null && typeNode != null) {
        // { "$lib": "Java", "$lib_method": "code", "$lib_version": "3.1.13" },
        JsonNode libNodeInner = libNode.get("$lib");
        JsonNode libMethodNode = libNode.get("$lib_method");
        JsonNode libVersionNode = libNode.get("$lib_version");
        JsonNode isLoginIdNode = propertiesNode.get("$is_login_id");
        if (libNodeInner != null && libMethodNode != null && libVersionNode != null && isLoginIdNode != null) {
          String project = projectNode.asText();
          String type = typeNode.asText();
          String lib = libNodeInner.asText();
          String libMethod = libMethodNode.asText();
          String libVersion = libVersionNode.asText();
          String isLoginId = isLoginIdNode.asText();
          if (project.equals("default") &&
            type.equals("profile_set") &&
            lib.equals("Java") &&
            libMethod.equals("code") &&
            libVersion.equals("3.1.13") &&
            isLoginId.equals("true")
          ) {
            logger.warn("filter sps invalid data. [data={}]", originalData);
            continue;
          }
        }
      }
      /*
       在处理完数据后，将处理后的数据传回
       */
      recordHandler.send(recordNode.toString());
    }
  }
}
