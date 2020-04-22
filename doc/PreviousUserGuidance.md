## 旧版预处理升级指南
注：该文档主要提供给在 1.14 版本之前使用过预处理，在升级到 1.14 版本后，想要使用新的预处理的客户
### 1. 新版本的预处理的优点
1. 支持批处理
    * 新版本的预处理支持批处理。如果预处理中有一些比较耗时的操作时，可以通过批处理提高整个预处理的性能。
    
2. 支持添加多个预处理
    * 多个预处理会顺序的执行，并且可以自定义处理的顺序

3. 支持对已经添加的预处理进行简单的管理
    
### 2. 新版旧版使用对比
* 新版与旧版的预处理的接口已经改变，因此：
```java
/**
* 旧版预处理
*/
public String process(String record) {
  // 处理 record 的内容
  // 返回处理后的内容
  return newRecord;
} 

/**
* 新版本预处理，一次处理一批数据
* 每一个 RecordHandler 都存储着原来的一条数据
* 遍历整个 List 来处理所有的数据 
*/
public void process(List<RecordHandler> recordHandlerList) {
  // 与之前一样，流式处理
  for (RecordHandler recordHandler : recordHandlerList) {
    String record = recordHandler.getOriginalData();
    // 处理 record 的内容
    recordHandler.send(record);
  }
}
```
* 在新的预处理中，一次的处理会提供一批日志处理器对象，每个对象是对一条日志记录的封装。
* 批量处理后，每条日志请保证都是调用自己的日志处理器的 send() 函数。
* 如果有一条日志记录生成多条日志的需求。有两种方案
    1. 可以与旧版本的一样，给 send() 中传入一个 JSON 数组。
    2. 也可以多次调用 send() 函数，每次传入一个 JSON 对象或者 JSON 数组。
* 如果存在有一条日志记录生成多条日志的逻辑，请保证生成后的日志个数与顺序是**等幂**的。也就是说，如果给定输入的日志条数，输出的日志的**数量**与**顺序**都应该相同。否则可能会导致**数据进度的混乱**。
* 在新的预处理中，如果批量处理过程中会抛出异常，那么当前的批量处理过程会退化为逐条处理。并且会通过 ext_processor_when_exception_use_original 选项来决定，是否保留会出现异常的数据。
 
### 3. 日志记录对象介绍
在新的预处理接口中，传入的对象是一个封装好的日志对象。该对象把一条日志最原始的数据提供给使用者。
1. 数据的内容由 getOriginalData() 方法获取，是纯粹日志部分解析出的数据。
2. 所有以 getNginx 开头的函数，都是在发送日志的请求中的相关的参数。例如：
    * 如果 getNginxLogProject() 的结果返回的是 production，说明数据接收地址中的项目名是 production。
    * getNginxLogIp() 获取的 IP 的值是发送数据端的 IP 的值。
3. 以 getNginx 开头函数中获取的数据会作为默认值，只有在数据中没有该字段时，默认的补充上去。例如：
    * 如果数据中的 project 字段有值，并且与 getNginxLogProject() 中的值不一样，则会优先使用数据中的 project 字段的值。
    * 如果数据中的 project 字段没值，则会使用 getNginxLogProject() 中获取的值。
4. 处理好的数据通过 send() 函数发送。

### 4. 新版本预处理编写示例
1. 逐条处理 

    * 如果之前已经写好了预处理，并且预处理类名为 OldExtProcessor，新版预处理示例如下：
    ```java
    import com.sensorsdata.analytics.extractor.common.RecordHandler;
    import com.sensorsdata.analytics.extractor.processor.BatchProcessor;
    import OldExtProcessor;
    
    import java.util.List;
    
    public class SimpleStreamPreprocessor implements BatchProcessor {
      private OldExtProcessor oldExtProcessor;
    
      public SimpleStreamPreprocessor() {
        oldExtProcessor = new OldExtProcessor();
      }
    
      public void process(List<RecordHandler> recordHandlerList) {
        for (RecordHandler recordHandler : recordHandlerList) {
          String record = recordHandler.getOriginalData();
          String result = oldExtProcessor.process(record);
          recordHandler.send(result);
        }
      }
    }
    ```

2. 当需要通过网络查询时，可以使用批量处理
   * 一般情况下，网络请求是一个耗时请求，如果每条日志都进行请求，则会降低处理的性能。可以通过批量处理进行优化。
    ```java
    import com.sensorsdata.analytics.extractor.common.RecordHandler;
    import com.sensorsdata.analytics.extractor.processor.BatchProcessor;
    
    import com.fasterxml.jackson.databind.JsonNode;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import com.fasterxml.jackson.databind.node.ObjectNode;
    
    import java.util.ArrayList;
    import java.util.List;
    import java.util.Map;
    
    public class SimpleBatchPreprocessor implements BatchProcessor {
    
      @Override
      public void process(List<RecordHandler> recordHandlerList) {
        List<String> needQueryData = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        // 用来缓存解析的 JSON 结果，防止多次解析，影响性能。
        JsonNode[] parsedJsonArray = new JsonNode[recordHandlerList.size()];
        int i = -1;
        for (RecordHandler recordHandler : recordHandlerList) {
          // 读取日志数据
          String data = recordHandler.getOriginalData();
          JsonNode jsonNode;
          try {
            jsonNode = objectMapper.readTree(data);
          } catch (Exception e) {
            // 解析失败，则不处理该数据
            recordHandler.send();
            continue;
          }
          // 缓存解析的 JSON
          parsedJsonArray[++i] = jsonNode;
          JsonNode videoId = jsonNode.get("properties").get("video_id");
          // 将需要查询的数据存起来
          if (videoId != null) {
            needQueryData.add(videoId.asText());
          }
        }
        // query 实现了批量查询，将一个批量的结果返回出来。
        Map<String, String> queryResult = query(needQueryData);
        // 补充数据并且发送
        i = -1;
        for (RecordHandler recordHandler : recordHandlerList) {
          JsonNode jsonNode = parsedJsonArray[++i];
          if (jsonNode == null) {
            // 之前被过滤掉的数据
            continue;
          }
          ObjectNode propertiesNode = (ObjectNode) jsonNode.get("properties");
          JsonNode videoId = propertiesNode.get("video_id");
          if (videoId != null) {
            // 直接查询缓存的结果
            String videoInfo = queryResult.get(videoId.asText());
            propertiesNode.put("video_info", videoInfo);
          }
          recordHandler.send(jsonNode.toString());
        }
      }
    }
    ```
     