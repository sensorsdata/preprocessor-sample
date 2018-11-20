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
 * @explain 该例子仅仅为了描述多个预处理模块的工作流程，对于实际的处理逻辑，完全可以与 com.sensorsdata.analytics.extractor.processor.SamplePreProcessor 合并在一起。而且，在生产环境中，也应该合并在一起。
 * @suggest 我们建议，尽可能少的创建预处理模块以提升整体处理效率
 * @since 2018/11/20
 */
public class SamplePreprocessor2 implements BatchProcessor {
  private static final Logger logger = LoggerFactory.getLogger(SamplePreprocessor2.class);

  public void process(List<RecordHandler> recordHandlerList) {
    for (RecordHandler recordHandler : recordHandlerList) {
      /*
       这里获取的 originalData 是一条符合 Sensors Analytics 数据格式定义的 Json
       数据格式定义 https://www.sensorsdata.cn/manual/data_schema.html
       */
      String originalData = recordHandler.getOriginalData();

      // 例如传入的一条需要处理的数据是:
      //
      // {
      //     "distinct_id":"2b0a6f51a3cd6775",
      //     "time":1434556935000,
      //     "type":"track",
      //     "event":"ViewProduct",
      //     "properties":{
      //         "product_name":"苹果",
      //         "product_classify":"水果",
      //     }
      // }
      //
      // 如果是“饮料”, 那么添加一个字段标记产品为“喝的”;
      // 如果是“水果”, 那么标记为“吃的”;

      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode recordNode = null;
      try {
        recordNode = objectMapper.readTree(originalData);
      } catch (IOException e) {
        /*
         这里异常需要进行处理，否则在抛出异常后，会导致之后的数据失效
         */
        logger.warn("Parse origin data failed. OriginalData: {}", e);
        /*
         出错后，不处理数据，将数据原封不动的传回
         */
        recordHandler.send();
        continue;
      }
      ObjectNode propertiesNode = (ObjectNode) recordNode.get("properties");

      if (propertiesNode != null && propertiesNode.has("product_classify")) {
        String productName = propertiesNode.get("product_classify").asText();
        if ("水果".equals(productName)) {
          propertiesNode.put("product_type", "吃的");
          // 输出日志到 /data/sa_cluster/logs/extractor 下的 extractor.log 中
          logger.info("Find a eatable thing: {}", productName);
        } else if ("饮料".equals(productName)) {
          propertiesNode.put("product_type", "喝的");
        }
      }
      /*
       在处理完数据后，将处理后的数据传回
       */
      recordHandler.send(recordNode.toString());
    }
  }
}
