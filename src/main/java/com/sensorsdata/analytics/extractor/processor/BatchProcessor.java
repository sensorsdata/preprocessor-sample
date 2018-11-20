package com.sensorsdata.analytics.extractor.processor;

import com.sensorsdata.analytics.extractor.common.RecordHandler;

import java.util.List;

/**
 * @auther Codievilky August
 * @since 2018/11/20
 */
public interface BatchProcessor {
  /**
   * @param recordHandlerList 批量处理的记录
   * @usage 通过调用 {@link com.sensorsdata.analytics.extractor.common.RecordHandler#getOriginalData()} 来获取源数据
   * 通过调用 {@link com.sensorsdata.analytics.extractor.common.RecordHandler#send(java.lang.String)} 将生成好的数据发送出，如果一条记录生成多条，则需要调用多次。
   */
  void process(List<RecordHandler> recordHandlerList);
}
