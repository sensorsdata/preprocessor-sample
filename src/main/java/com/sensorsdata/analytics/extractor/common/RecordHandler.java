package com.sensorsdata.analytics.extractor.common;

/**
 * @auther Codievilky August
 * @since 2018/11/20
 */
public interface RecordHandler {
  /**
   * @return 获取到具体到每一条的日志
   */
  String getOriginalData();

  /**
   * 回传原来数据
   */
  void send();

  /**
   * 当一条数据会生成多条数据时，应使用同一个 RecordHandler 调用多次
   * 当对于源数据不进行修改的时，则不需要调用该方法
   *
   * @param data 修改后的数据
   */
  void send(String data);

  /**
   * @return 发送数据中的项目名，没有数据则为 null
   * 需要注意的是，这里获取的项目名称，并不一定是最终的项目。
   * 神策内部判断某条数据项目的逻辑为：
   * 1. 判断数据的 json 中是否有 project 字段，如果有则使用该字段
   * 2. 当数据中获取不到时，则使用该字段返回的项目名，也就是数据接收地址的项目名称
   * 3. 如果上述都没有，则使用 default 项目
   */
  String getNginxLogProject();

  /**
   * @return 发送数据端的 user agent 的值
   */
  String getNginxUserAgent();

  /**
   * @return 发送数据端的 ip 值
   */
  String getNginxLogIp();

  /**
   * @return nginx 接收到数据时的服务器时间戳
   */
  long getNginxLogTime();

  /**
   * @return 发送数据时，cookie 的值，没有数据则为 null
   */
  String getNginxLogCookie();

  /**
   * @return 发送数据使用的 token，没有数据则为 null
   */
  String getImportToken();
}
