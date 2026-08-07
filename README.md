# SDH 架构（分析云 3.0.0 及以上套餐）的 PreProcessor 预处理模块

## 1.概述

如果是老架构（分析云 3.0.0 以下套餐或非套餐），请参考文档 [老架构预处理文档](https://github.com/sensorsdata/preprocessor-sample/blob/master/doc/preprocessor_sdg_sdf.md)，新老架构下的预处理核心逻辑其实是一样的，只有部分命令参数有变化，为了能让文档更简洁直观，分为了两个独立的文档。

Sensors Analytics 从 1.14 开始为用户提供新版本的"数据预处理模块"（之后简称为预处理模块），即为 SDK 等方式接入的数据（不包括批量导入工具方式）提供一个简单的 ETL 流程，使数据接入更加灵活。

可以使用“数据预处理模块”处理的数据来源包括：

* SDK（各语言 SDK 直接发送的数据）;
* LogAgent;
* FormatImporter;

使用 BatchImporter 和 HdfsImporter 批量导入数据的情况除外。

例如 SDK 发来一条数据，传入“数据预处理模块”时格式如下：

```json
{
    "distinct_id":"2b0a6f51a3cd6775",
    "time":1434556935000,
    "type":"track",
    "event":"ViewProduct",
    "project": "default",
    "ip":"123.123.123.123",
    "properties":{
        "product_name":"苹果"
    }
}
```

这时希望增加一个字段 `product_classify`，表示产品的分类，可通过“数据预处理模块”将数据处理成：

```json
{
    "distinct_id":"2b0a6f51a3cd6775",
    "time":1434556935000,
    "type":"track",
    "event":"ViewProduct",
    "project": "default",
    "properties":{
        "product_name":"苹果",
        "product_classify":"水果"
    }
}
```

## 2.开发方法

SDH 架构的预处理不再兼容旧版预处理，如果您目前使用的旧版需要先升级，可以参考 [旧版预处理升级指南](https://github.com/sensorsdata/preprocessor-sample/blob/master/doc/PreviousUserGuidance.md)

相比之前版本提供的预处理模块，新版本的预处理模块提供了批量处理的能力，并且可以支持添加多个预处理模块。因此，新版本的预处理接口相比之前较为复杂。一个预处理模块需要使用到两个 Java 接口`com.sensorsdata.analytics.extractor.common.RecordHandler`与`com.sensorsdata.analytics.extractor.processor.BatchProcessor`。这两个接口的定义如下

RecordHandler.java

```java
package com.sensorsdata.analytics.extractor.common;

public interface RecordHandler {
  /**
   * @return 获取到具体到每一条的日志
   */
  String getOriginalData();

  /**
   * 将源数据不进行修改直接传输给下一个预处理或者最终交给神策
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
```

BatchProcessor.java

```java
package com.sensorsdata.analytics.extractor.processor;

import com.sensorsdata.analytics.extractor.common.RecordHandler;

import java.util.List;

public interface BatchProcessor {
  /**
   * @param recordHandlerList 批量处理的记录
   * @usage 通过调用 {@link com.sensorsdata.analytics.extractor.common.RecordHandler#getOriginalData()} 来获取源数据
   * 通过调用 {@link com.sensorsdata.analytics.extractor.common.RecordHandler#send(java.lang.String)} 将生成好的数据发送出，如果一条记录生成多条，则需要调用多次。
   */
  void process(List<RecordHandler> recordHandlerList);
}

```

其中只有`com.sensorsdata.analytics.extractor.processor.BatchProcessor`需要实现。在 BatchProcessor 的 process 方法中，它的入参是一批需要处理的数据对象。

    * 可以通过`com.sensorsdata.analytics.extractor.common.RecordHandler#getOriginalData()`方法来获取到 一条符合 [Sensors Analytics 的数据格式定义](https://manual.sensorsdata.cn/sa/latest/zh_cn/%E6%95%B0%E6%8D%AE%E6%A0%BC%E5%BC%8F-185863996.html)的 JSON 文本，例如概述中的第一个 JSON。RecordHandler 中还提供其他方法，可以获取和日志相关的其他数据。
    
    * 当希望将处理完后的数据交给下一个预处理或者交由神策处理时，可以通过调用`com.sensorsdata.analytics.extractor.common.RecordHandler#send(java.lang.String)`实现，如果需要有一条数据产生多条数据时，则调用多次；如果需要抛掉该条数据，则不用进行调用。
    
    * 由于处理的过程中是批量处理，如果在处理的过程中，抛出了异常，会导致之后数据都被抛出。因此，建议保证程序的正确性。

    * 如果预处理逻辑需要按项目区分处理，请不要直接把 `RecordHandler#getNginxLogProject()` 当作最终项目名；应参考下文「如何正确获取实际生效的项目名」。

### 2.1 注意
如果在预处理中修改了用户关联字段值，如果 identities 存在，务必同步修改；比如 login_id 需要解密，请同时解密 identities.$identity_login_id

| 用户标识属性       | 对应的 identities 属性      |
| ------------ | ---------------------- |
| login_id     | $identity_login_id     |
| anonymous_id | $identity_anonymous_id |

数据样例如下：
```
{
    "distinct_id": "d1",
    "login_id": "l1",
    "anonymous_id": "a1",
    "identities": {
        "$identity_login_id": "l1",
        "$identity_anonymous_id": "a1"
    }
}
```

### 2.2 如何正确获取实际生效的项目名

`RecordHandler#getNginxLogProject()` 返回的是数据接收地址中的项目名，**并不一定是最终实际生效的项目名**。

神策内部判断某条数据所属项目的逻辑为：

1. 优先使用数据 Json 中的 `project` 字段；
2. 数据未指定时，使用数据接收地址中的项目名（即 `getNginxLogProject()` 的返回值）；
3. 都未指定时，使用 `default` 项目。

因此，如果预处理需要按项目做分支处理，请按上述优先级自行解析“实际生效的项目名”，而不是只调用 `getNginxLogProject()`。

本 repo 的样例代码 `SamplePreProcessor` 中已提供参考实现 `resolveProjectName`，核心逻辑如下：

```java
/*
 * 获取实际生效的项目名：
 * 1. 优先使用数据 Json 中的 project 字段；
 * 2. 数据未指定时，使用数据接收地址中的项目名；
 * 3. 都未指定时，使用 default 项目。
 */
String projectName = resolveProjectName(recordNode, recordHandler);

static String resolveProjectName(JsonNode recordNode, RecordHandler recordHandler) {
  if (recordNode.has("project")) {
    return recordNode.get("project").asText().trim();
  }

  String nginxProject = recordHandler.getNginxLogProject();
  return nginxProject == null ? "default" : nginxProject;
}
```

## 3. 编译打包

我们需要将编写好预处理模块打包安装到神策的环境中

本 repo 附带的样例使用了 Jackson 库解析 JSON，并使用 Maven 做包管理，编译并打包本 repo 代码可通过：

```bash
git clone git@github.com:sensorsdata/preprocessor-sample.git
cd preprocessor-sample
mvn clean package
```

执行编译后可在 `target` 目录下找到 `preprocessor-sample-1.0-SNAPSHOT.jar`。

### 3.1 依赖管理与冲突规避

预处理 JAR 会与宿主程序运行在同一个 JVM 进程中。如果预处理程序额外引入了第三方依赖，尤其是 Guava、Jackson 这类在不同版本之间较容易出现兼容性问题的库，就有可能与宿主程序已有依赖发生冲突，进而出现 `NoSuchMethodError`、`ClassCastException`、序列化/反序列化异常等问题。

因此，依赖管理建议按以下原则处理：

* 如果预处理只是使用神策环境中已经提供的依赖，并且确认版本完全兼容，可以直接复用宿主依赖。
* 如果预处理需要自行引入第三方依赖，推荐在打包时使用 `maven-shade-plugin`，并对高风险依赖启用 `relocation` 做包名重写，而不只是简单打成 fat jar。
* 对于 Guava、Jackson、Apache Commons 等通用基础库，推荐默认按“需要 relocation”的依赖处理，避免与宿主程序类路径中的同名类互相覆盖。

需要特别说明的是：仅仅使用 shade 将依赖打入最终 JAR，并不能彻底避免冲突；只有开启 `relocation` 后，把例如 `com.google.common`、`com.fasterxml` 这类包重写到预处理自己的命名空间下，才能真正做到类隔离。

本 repo 当前已经启用了 `maven-shade-plugin`。如果您新增了容易冲突的依赖，建议进一步补充类似下面的配置：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>2.4.3</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <createDependencyReducedPom>false</createDependencyReducedPom>
                <relocations>
                    <relocation>
                        <pattern>com.google.common</pattern>
                        <shadedPattern>your.preprocessor.shaded.com.google.common</shadedPattern>
                    </relocation>
                    <relocation>
                        <pattern>com.fasterxml</pattern>
                        <shadedPattern>your.preprocessor.shaded.com.fasterxml</shadedPattern>
                    </relocation>
                </relocations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

其中 `your.preprocessor.shaded` 只是示例前缀，请替换为您自己的包名前缀。更多说明可参考 [如何避免预处理的依赖冲突](doc/%E5%A6%82%E4%BD%95%E9%81%BF%E5%85%8D%E9%A2%84%E5%A4%84%E7%90%86%E4%BE%9D%E8%B5%96%E5%86%B2%E7%AA%81.md)。

## 4. 测试 JAR

preprocessor-tools 使用用于测试、部署预处理模块的工具，只能运行于部署 Sensors Analytics 的机器上。

将编译出的 JAR 文件上传到部署 Sensors Analytics 的机器上，例如 `preprocessor-sample-1.0-SNAPSHOT.jar`。

切换到`sa_cluster`账户:

```
sudo su - sa_cluster
```

* 3.0.0 套餐对应的命令是 integratoradmin preprocessor（integratoradmin preprocessor -h）
* 3.0.1 及以上套餐对应的命令是 horizonadmin inflow preprocessor（仍然兼容 integratoradmin preprocessor）

直接运行 preprocessor-tools 将输出参数列表如：

```
[sa_cluster@sensors-server ~]$ horizonadmin inflow preprocessor -h
Usage: <main class> [options] [command] [command options]
  Options:
    -h, --help
      帮助，（具体可参见 https://github.com/sensorsdata/preprocessor-sample，如果您在 1.14 
      之前的版本配置过预处理，请务必查看该文档） 
      Default: false
  Commands:
    info      查看所有安装的预处理
      Usage: info

    modify      修改预处理的配置
      Usage: modify [options]
        Options:
          -i, --id
            进行配置的预处理 id 列表, 多个 id 请以逗号隔开。只有在指定了 --amount/-a 或 --timeout/-t 
            时，才需要指定该参数 
          -a, --amount
            预处理模块一次处理中数据的最多的条数，请通过 --id/-i 参数指定要修改的预处理
          -t, --timeout
            将数据交给下一个预处理的最长等待时间（单位为秒），请通过 --id/-i 参数指定要修改的预处理
          -o, --order
            新的预处理模块处理顺序，填写预处理模块 id 列表，以逗号隔开。请填写上所有预处理模块的 id
          -p, --path
            要更新的 JAR 包的位置，可以是文件也可以是目录，但会覆盖之前传输的，所以请全量上传
          -u, --use_url_class_loader
            是否使用默认的 URL 类加载器去加载预处理包。默认为 false，如果预处理代码中依赖神策环境的 jar 包请设置为 true, 否则请勿指定该参数。

    install      安装预处理
      Usage: install [options]
        Options:
          -c, --class
            添加新的预处理。填写类的全名，可以填写多个，请以逗号隔开。如果只是更新 JAR 包而类名不变，请勿指定该参数
        * -p, --path
            要上传 JAR 的位置，可以是文件也可以是目录，但会覆盖之前传输的，所以请全量上传
        * --when_exception_use_original
            当 BatchProcessor 抛异常时导入原始数据而不是直接报错, yes 表示预处理遇到异常时使用原始数据导入, no 
            表示遇到异常时报错 
            Possible Values: [YES, NO]
          --with-integrator-stop
            在卸载预处理模块后，不自动启动 integrator scheduler 和 integrator web (3.0.1 及以上套餐是 horizon stream_manger 和 horizon web)
            Default: false

    run      运行指定的预处理方法, 以标准输入的逐行数据作为参数输入, 将返回结果输出到标准输出
      Usage: run [options]
        Options:
        * -p, --path
            包含预处理的 JAR 的位置
          -c, --class
            实现预处理的类全名,可以填写多个类名(以逗号隔开),若不填写，则使用已经安装神策的预处理类

    run_with_real_time_data      用本机实时的数据作为输入, 将返回结果输出到标准输出
      Usage: run_with_real_time_data [options]
        Options:
        * -p, --path
            包含预处理的 JAR 的位置
          -c, --class
            实现预处理的类全名,可以填写多个类名(以逗号隔开),若不填写，则使用已经安装神策的预处理类
        * --when_exception_use_original
            当 BatchProcessor 抛异常时导入原始数据而不是直接报错, yes 表示预处理遇到异常时使用原始数据导入, no 
            表示遇到异常时报错 
            Possible Values: [YES, NO]

    uninstall      卸载预处理
      Usage: uninstall [options]
        Options:
          -c, --class
            实现预处理的类全名,可以填写多个类名(以逗号隔开)
          -i, --id
            预处理 id 列表, 多个 id 请以逗号隔开
          -a, --all
            清除之前所有的预处理
            Default: false
          --with-integrator-stop
            在卸载预处理模块后，不自动启动 integrator scheduler 和 integrator web (3.0.1 及以上套餐是 horizon stream_manger 和 horizon web)
            Default: false
```

### 4.1  测试运行

使用`run`方法加在 JAR 并实例化 Class，以标准输入的逐行数据作为预处理函数的输入，并将处理结果输出到标准输出。其中 -c, --class 为可选参数，若不填写，默认使用之前通过`install`安装的所有预处理模块进行处理；否则，会使用 -c 中传输的 class list 作为预处理模块，处理顺序与填写时顺序相同（3.0.0 套餐使用命令 integratoradmin preprocessor）。

```
horizonadmin inflow preprocessor run --path preprocessor_jar_dir/ --class com.sensorsdata.analytics.extractor.processor.SamplePreProcessor,com.sensorsdata.analytics.extractor.processor.SamplePreProcessor2

样例数据（老架构下的预处理是直接使用原始数据，在 SDH 架构下的预处理需要将数据包在 payload 里面）
{"payload":{"distinct_id":"2b0a6f51a3cd6775","time":1434556935000,"type":"track","event":"ViewProduct","project": "default","ip":"123.123.123.123","properties":{"product_name":"苹果"}}}
```

### 4.2 以线上实时数据测试运行

使用方法与`run`方法相同，均需要提供 JAR 包的地址与所有需要测试的预处理的 Class。与`run`方法不同的是，`run_with_real_time_data`的输入数据真实的环境中的线上数据，并且最后会将输入与输出都输出到标准输出中（3.0.0 套餐使用命令 integratoradmin preprocessor）。

```
horizonadmin inflow preprocessor run_with_real_time_data --path preprocessor_jar_dir/ --class com.sensorsdata.analytics.extractor.processor.SamplePreProcessor,com.sensorsdata.analytics.extractor.processor.SamplePreProcessor2
```

## 5.安装

安装分为两步，首先需要将打包后生成的 JAR 包安装到神策服务器中，然后需要将所有编写的预处理模块 Class 名称配置存储神策服务器中。

使用 preprocessor-tools 的`install`方法可以上传打包后的 JAR 包并且可以将每一个预处理模块的主类安装到神策。示例命令如下（3.0.0 套餐使用命令 integratoradmin preprocessor）：

```
horizonadmin inflow preprocessor install --path preprocessor_jar_dir/ --when_exception_use_original no --class com.sensorsdata.analytics.extractor.processor.SamplePreProcessor,com.sensorsdata.analytics.extractor.processor.SamplePreProcessor2 
```

* 每次上传会将之前上传的所有的 JAR 包清理掉，因此如果有多个 JAR 包需要上传，请将这些 JAR 包放到一个目录里，通过指定目录将他们上传
*  建议先上传 JAR 包，再安装这些预处理类
*  集群版安装预处理模块会自动分发，不需要每台机器操作;

## 6. 预处理细节查看与配置修改

在新的预处理模块的模式中，多个预处理模块会按照一定顺序对日志进行处理，并且每个预处理模块一次会批量处理一批数据。可以通过 preprocessor-tools 的`info`方法查看所有预处理模块的细节，可以执行如下命令（3.0.0 套餐使用命令 integratoradmin preprocessor）：

```
horizonadmin inflow preprocessor info
```

输出的日志的关键部分如下：

```
PreProcessorTool logger initialized.
-------------------- All PreProcessors are as follow --------------------
all preprocessor.[content=
{"id":1,"class_name":"com.sensorsdata.analytics.extractor.processor.SamplePreProcessor","batch_process_num":30,"batch_process_timeout":1,"handle_order":1}
{"id":2,"class_name":"com.sensorsdata.analytics.extractor.processor.SamplePreProcessor2","batch_process_num":30,"batch_process_timeout":1,"handle_order":2}]
PreProcessor id list(order by process order): [1, 2]
---------------------------------------------------------------------
2024-10-08 15:40:46,134 INFO cmd finished.
```

在日志中，每一行日志的 JSON 对象都表示一个预处理模块的配置，其中

* `id`是预处理模块的唯一标识
* `class_name`是预处理模块的 Class 的名称
* `batch_process_num`是预处理模块一次处理中数据的最多的条数。
* `batch_prcess_timeout`是将数据交给下一个预处理的最长等待时间
* `handle_order`所描述的是预处理模块处理的顺序，由小到大表示着预处理模块处理的有前到后。
* 神策服务器当发现新的日志时，会积攒下来，当足够`batch_process_num`设置的条数时，会将积攒的数据移交给之后的预处理模块进行处理，当在`batch_prcess_timeout`所设定的时间（单位是秒）依然没有新的数据进入，即使不足够`batch_process_num`中设定的条数，依然会交给之后的预处理模块处理。

### 6.1 预处理配置修改

修改预处理的配置时，请使用 preprocessor-tools 的 `modify`方法，具体使用命令如下（3.0.0 套餐使用命令 integratoradmin preprocessor）：

* 如果要修改某个预处理模块的一次处理的最大条数时：

    ```
    horizonadmin inflow preprocessor modify --id 1 --amount 50
    # 如果提示缺少参数，请使用完整参数命令
    horizonadmin inflow preprocessor modify --id 1 --path preprocessor_jar_dir/ --timeout 1 --amount 50
    ```

    以上命令将 id 为 1 的预处理模块的一次处理的最大条数设定为了 50条。

*  如果要修改某一些预处理模块的最长等待时间时:

    ```
    horizonadmin inflow preprocessor modify --id 1,2 --timeout 3
    # 如果提示缺少参数，请使用完整参数命令
    horizonadmin inflow preprocessor modify --id 1,2 --path preprocessor_jar_dir/ --amount 50 --timeout 3
    ```

    以上命令将 id 为 1 与 2 的预处理模块的最长等待时间设定为了 3 秒。

*    如果要修改预处理模块处理属性时，直接指定新的预处理排序即可：

     ```
     horizonadmin inflow preprocessor modify --order 2,1
     # 如果提示缺少参数，请使用完整参数命令
     horizonadmin inflow preprocessor modify --id 1,2 --timeout 1 --path preprocessor_jar_dir/ --amount 30 --order 2,1
     ```

     修改之后，数据会先通过 id 为 2 的预处理模块处理之后，才由 id 为 2 的预处理模块儿处理。

*   如果要更新预处理模块的 JAR 包是，可以直接上传新的 JAR 包

    ```
     horizonadmin inflow preprocessor modify --path preprocessor_jar_dir/
     # 如果提示缺少参数，请使用完整参数命令
     horizonadmin inflow preprocessor modify --id 1,2 --timeout 1 --amount 30 --path preprocessor_jar_dir/
    ```


## 7.验证

安装好预处理模块后，为了验证处理结果是否符合预期，可以开启 SDK 的 [`Debug 模式`](https://manual.sensorsdata.cn/sa/latest/zh_cn/debug-150667671.html) 校验数据。

1.  使用 [Debug 实时数据查询](https://manual.sensorsdata.cn/sa/latest/zh_cn/debug-143163594.html);
2.  配置 SDK 使用 [`Debug 模式`](https://manual.sensorsdata.cn/sa/latest/zh_cn/debug-150667671.html);
3.  发送一条测试用的数据，观察是否进行了预期处理即可;

## 8. 卸载

若不再需要预处理模块，可以通过 preprocessor-tools 的 `uninstall` 方法卸载，执行如下命令（3.0.0 套餐使用命令 integratoradmin preprocessor）：

```
horizonadmin inflow preprocessor uninstall --class com.sensorsdata.analytics.extractor.processor.SamplePreProcessor,com.sensorsdata.analytics.extractor.processor.SamplePreProcessor2
```

-   若希望更新 JAR 包，请直接使用工具“安装”新的 JAR 包即可，不需要先进行卸载;

如果想要清除之前所有的预处理，可以执行如下命令:
```
horizonadmin inflow preprocessor uninstall --all
```
