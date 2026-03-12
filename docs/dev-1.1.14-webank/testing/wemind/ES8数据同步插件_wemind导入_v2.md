# Exchangis

##/所有目录/SIT/1.1.14/ES8数据同步插件

###需求：000001

- 标签：后端-冒烟测试

  - TC001：ES8 Writer基础连接-无认证连接

    - 步骤：
1、配置ES8集群地址为http://localhost:9200
2、不配置username和password
3、配置index为test_index
4、启动数据同步任务
5、写入测试数据

    - 预期结果：
连接成功，可以正常执行索引操作，数据写入成功

  - TC002：ES8 Writer基础连接-用户名密码认证

    - 步骤：
1、配置ES8集群地址为http://localhost:9200
2、配置username为elastic
3、配置password为加密密码
4、配置index为test_index
5、启动数据同步任务

    - 预期结果：
密码解密成功，认证通过，可以正常执行索引操作

  - TC003：ES8 Writer基础连接-SSL/HTTPS连接

    - 步骤：
1、配置ES8集群地址为http://localhost:9200
2、配置secure为true
3、配置index为test_index
4、启动数据同步任务
5、验证URL自动转换为https://

    - 预期结果：
http://自动转换为https://，连接成功，可以正常执行索引操作

  - TC004：ES8 Writer基础连接-SSL客户端证书认证

    - 步骤：
1、配置ES8集群地址为https://localhost:9200
2、配置secure为true
3、配置keystorePath为有效的jks文件路径
4、配置keystorePassword为正确密码
5、配置index为test_index
6、启动数据同步任务

    - 预期结果：
SSLContext创建成功，客户端证书验证通过，连接成功

  - TC005：ES8 Writer基础连接-SSL+用户名密码双重认证

    - 步骤：
1、配置ES8集群地址为https://localhost:9200
2、配置secure为true
3、配置keystorePath和keystorePassword
4、配置username和password
5、配置index为test_index
6、启动数据同步任务

    - 预期结果：
SSLContext创建成功，认证通过，连接成功

  - TC006：ES8 Writer基础连接-多节点高可用

    - 步骤：
1、配置多个ES节点地址，用逗号分隔
2、配置endPoints为http://node1:9200,http://node2:9200,http://node3:9200
3、配置index为test_index
4、启动数据同步任务
5、模拟节点故障

    - 预期结果：
HttpHost数组创建成功，连接到多个节点，故障自动转移

- 标签：后端-功能测试

  - TC007：动态索引模式-单占位符日期模式

    - 步骤：
1、配置index为logs-{date}
2、配置column包含date字段（类型为keyword）
3、配置message字段（类型为text）
4、写入测试数据，date字段值为2024-03-13
5、验证实际索引名称

    - 预期结果：
目标索引名为logs-2024-03-13，数据写入成功

  - TC008：动态索引模式-单占位符类型模式

    - 步骤：
1、配置index为order-{type}
2、配置column包含type字段（类型为keyword）
3、配置orderId字段（类型为keyword）
4、写入测试数据，type字段值为online
5、验证实际索引名称

    - 预期结果：
目标索引名为order-online，数据写入成功

  - TC009：动态索引模式-多占位符组合模式

    - 步骤：
1、配置index为order-{type}-{status}
2、配置column包含type和status字段
3、配置orderId字段
4、写入测试数据，type为online，status为paid
5、验证实际索引名称

    - 预期结果：
目标索引名为order-online-paid，数据写入成功

  - TC010：动态索引模式-占位符字段缺失

    - 步骤：
1、配置index为logs-{missingField}
2、配置column包含message字段
3、写入测试数据，不包含missingField字段
4、验证实际索引名称

    - 预期结果：
目标索引名为logs-（空字符串），不抛出异常

  - TC011：动态索引模式-占位符字段值类型限制

    - 步骤：
1、配置index为data-{value}
2、配置column包含value字段（类型为object）
3、写入测试数据，value字段值为Map类型
4、验证实际索引名称

    - 预期结果：
目标索引名为data-（空字符串），代码日志警告字段值类型不支持

  - TC012：文档ID生成-单字段文档ID

    - 步骤：
1、配置idField为userId
2、配置column包含userId字段（类型为keyword）
3、配置name字段（类型为text）
4、写入测试数据，userId为U001
5、查询ES文档ID

    - 预期结果：
ES文档ID为U001，重复写入相同userId时覆盖原文档

  - TC013：文档ID生成-多字段组合文档ID

    - 步骤：
1、配置idField为userId,orderId
2、配置column包含userId和orderId字段
3、配置amount字段
4、写入测试数据，userId为U001，orderId为ORD001
5、查询ES文档ID

    - 预期结果：
ES文档ID为U001ORD001，重复写入相同组合时覆盖原文档

  - TC014：文档ID生成-未配置文档ID

    - 步骤：
1、不配置idField
2、配置column包含message字段
3、写入测试数据
4、查询ES文档ID

    - 预期结果：
ES自动生成文档ID（如AuBd6HwBYqxW5YzQ7zqb），每次写入都创建新文档

  - TC015：文档ID生成-文档ID字段部分缺失

    - 步骤：
1、配置idField为userId,orderId,itemId
2、配置column包含userId、orderId、itemId字段
3、写入测试数据，仅包含userId和orderId
4、查询ES文档ID

    - 预期结果：
ES文档ID为U001ORD001（itemId缺失跳过），不抛出异常

  - TC016：数据类型映射-字符串类型keyword和text

    - 步骤：
1、配置column包含title字段（类型为text）
2、配置tags字段（类型为keyword）
3、写入测试数据，title为Hello World，tags为java,elasticsearch
4、查询ES数据

    - 预期结果：
字段值正确写入ES，可以正常查询

  - TC017：数据类型映射-数值类型integer和long

    - 步骤：
1、配置column包含count字段（类型为integer）
2、配置timestamp字段（类型为long）
3、写入测试数据，count为100，timestamp为1678838400000
4、查询ES数据

    - 预期结果：
数值正确转换，范围检查正确

  - TC018：数据类型映射-数值类型double和float

    - 步骤：
1、配置column包含price字段（类型为double）
2、配置rate字段（类型为float）
3、写入测试数据，price为99.99，rate为3.14
4、查询ES数据

    - 预期结果：
数值正确转换，精度保持

  - TC019：数据类型映射-布尔类型boolean

    - 步骤：
1、配置column包含isActive字段（类型为boolean）
2、写入测试数据，isActive为true
3、查询ES数据

    - 预期结果：
布尔值正确写入ES

  - TC020：数据类型映射-日期类型date

    - 步骤：
1、配置dateFormat为yyyy-MM-dd HH:mm:ss
2、配置column包含createTime字段（类型为date）
3、配置format为yyyy-MM-dd'T'HH:mm:ss
4、配置timezone为Asia/Shanghai
5、写入测试数据，createTime为2024-03-13T10:30:00
6、查询ES数据

    - 预期结果：
日期正确解析，按dateFormat格式化为2024-03-13 10:30:00，时区转换正确

  - TC021：数据类型映射-二进制类型binary

    - 步骤：
1、配置column包含content字段（类型为binary）
2、写入测试数据，content为字节数组[0x01, 0x02, 0x03]
3、查询ES数据

    - 预期结果：
二进制数据正确写入ES，Base64编码存储

  - TC022：数据类型映射-向量类型dense_vector

    - 步骤：
1、配置column包含vector字段（类型为dense_vector）
2、配置dims为3
3、写入测试数据，vector为[0.1, 0.2, 0.3]
4、查询ES数据

    - 预期结果：
向量正确解析为[0.1, 0.2, 0.3]，维度与配置的dims一致

  - TC023：数据类型映射-向量类型解析错误

    - 步骤：
1、配置column包含vector字段（类型为dense_vector）
2、配置dims为3
3、写入测试数据，vector为not an array
4、验证错误处理

    - 预期结果：
抛出DataXException，错误码为ES8xWriter-13，错误描述为Vector parse error

  - TC024：数据类型映射-对象类型object

    - 步骤：
1、配置column包含address字段（类型为object）
2、写入测试数据，address为{"city":"Shenzhen","zip":"518000"}
3、查询ES数据

    - 预期结果：
JSON字符串解析为对象，对象正确嵌套在文档中

  - TC025：数据类型映射-嵌套对象多级字段

    - 步骤：
1、配置columnNameSeparator为.
2、配置column包含profile.name字段（类型为text）
3、配置profile.age字段（类型为integer）
4、配置address.city字段（类型为keyword）
5、配置address.zip字段（类型为keyword）
6、写入测试数据
7、查询ES数据结构

    - 预期结果：
文档结构为嵌套对象，包含profile和address两个子对象

  - TC026：自动类型推测-自动推测开启

    - 步骤：
1、配置allowIndexNotExist为true
2、配置autoCreateIndex为true
3、配置column列表，不指定type
4、写入测试数据，包含LONG、STRING、INT、BOOLEAN、DOUBLE类型
5、查询ES字段类型

    - 预期结果：
id映射为LONG，name映射为TEXT，age映射为INTEGER，active映射为BOOLEAN，score映射为DOUBLE

  - TC027：自动类型推测-自动推测关闭

    - 步骤：
1、配置allowIndexNotExist为false
2、配置column列表，不指定type
3、写入测试数据，id为123（Column.Type.LONG）
4、查询ES字段类型

    - 预期结果：
id字段作为TEXT类型写入，日志警告Unknown type, using TEXT as default

  - TC028：索引管理-自动创建索引

    - 步骤：
1、配置index为new_index
2、配置autoCreateIndex为true
3、配置cleanup为false
4、配置column列表，包含title和tags字段
5、启动数据同步任务
6、验证索引创建

    - 预期结果：
索引new_index创建成功，Mapping设置成功，字段类型与配置一致

  - TC029：索引管理-索引不存在但不允许自动创建

    - 步骤：
1、配置index为new_index
2、配置autoCreateIndex为false
3、配置column列表
4、启动数据同步任务
5、验证错误处理

    - 预期结果：
抛出DataXException，错误码为ES8xWriter-11，错误描述为Index needs to be built but autoCreateIndex is false

  - TC030：索引管理-清理已存在索引

    - 步骤：
1、预先创建existing_index索引
2、配置index为existing_index
3、配置autoCreateIndex为true
4、配置cleanup为true
5、配置column列表
6、启动数据同步任务
7、验证索引重建

    - 预期结果：
旧索引被删除，新索引创建成功，Mapping重新设置

  - TC031：索引管理-允许索引不存在

    - 步骤：
1、配置index为nonexistent_index
2、配置allowIndexNotExist为true
3、配置column列表
4、启动数据同步任务
5、写入数据

    - 预期结果：
Job.prepare()不检查索引存在性，Task.startWrite()直接写入

  - TC032：索引管理-动态索引模式不预创建索引

    - 步骤：
1、配置index为logs-{date}
2、配置autoCreateIndex为true
3、配置column包含date和message字段
4、启动数据同步任务
5、验证Job阶段不创建索引

    - 预期结果：
Job.prepare()不创建索引，Task.startWrite()动态创建索引（如logs-2024-03-13）

  - TC033：索引管理-从已存在索引获取Mapping

    - 步骤：
1、预先创建existing_index索引，包含Mapping
2、配置index为existing_index
3、配置column为空列表
4、启动数据同步任务
5、验证column自动填充

    - 预期结果：
从existing_index获取Mapping，column自动填充为索引的字段列表

  - TC034：索引管理-动态索引模式无法获取Mapping

    - 步骤：
1、配置index为logs-{date}
2、配置column为空列表
3、启动数据同步任务
4、验证错误处理

    - 预期结果：
抛出DataXException，错误码为ES8xWriter-10，错误描述为Cannot get columns from index

  - TC035：批量写入与性能-BulkIngester批量写入配置

    - 步骤：
1、配置bulkActions为1000
2、配置bulkPerTask为5
3、写入2500条记录
4、观察批量提交情况

    - 预期结果：
BulkIngester.maxOperations为1000，分3批提交（1000+1000+500），flushInterval为5秒

  - TC036：批量写入与性能-脏数据收集

    - 步骤：
1、配置column包含id和data字段
2、写入包含部分非法数据的记录
3、执行数据同步任务
4、检查脏数据收集

    - 预期结果：
失败记录收集到DirtyRecord，DirtyRecord包含文档ID和错误原因

  - TC037：BulkOperationVariant模式-写入BulkOperationVariant

    - 步骤：
1、配置CustomProcessor生成BulkOperationVariant对象
2、创建IndexOperation，包含index、document和id
3、调用startWrite方法
4、验证数据写入

    - 预期结果：
BulkOperationVariant正确写入，支持IndexOperation、UpdateOperation等

  - TC038：BulkOperationVariant模式-BulkOperationVariant与动态索引不兼容

    - 步骤：
1、配置index为logs-{date}
2、配置column包含date和message字段
3、创建BulkOperationVariant，index字段为空
4、调用startWrite方法
5、验证错误处理

    - 预期结果：
抛出DataXException，错误描述为Incompatible between post processor and index

  - TC039：配置项验证-clientConfig超时配置

    - 步骤：
1、配置clientConfig.timeout为60000
2、配置clientConfig.connTimeout为5000
3、配置clientConfig.sockTimeout为60000
4、创建RestClient
5、验证超时参数

    - 预期结果：
socketTimeout为60000ms，connectTimeout为5000ms，requestTimeout为60000ms

  - TC040：配置项验证-indexSettings配置

    - 步骤：
1、配置autoCreateIndex为true
2、配置settings.number_of_shards为3
3、配置settings.number_of_replicas为2
4、配置settings.refresh_interval为1s
5、创建索引
6、验证索引设置

    - 预期结果：
索引分片数为3，副本数为2，刷新间隔为1s

  - TC041：配置项验证-columnNameSeparator配置

    - 步骤：
1、配置columnNameSeparator为_
2、配置column包含user_profile_name字段
3、写入测试数据
4、查询ES文档结构

    - 预期结果：
文档结构为嵌套对象，包含user.profile.name三级结构

  - TC042：资源清理-Task正常关闭

    - 步骤：
1、执行Task完成数据写入
2、调用Task.destroy()方法
3、验证资源释放

    - 预期结果：
bulkIngester.close()成功，restClient.close()成功，transport.close()成功

  - TC043：资源清理-Job异常时关闭客户端

    - 步骤：
1、Job.prepare()执行过程中模拟异常
2、验证finally块执行
3、检查资源释放

    - 预期结果：
restClient.close()在finally块中执行，无资源泄漏

- 标签：后端-异常测试

  - TC044：异常处理-缺少必需参数endPoints

    - 步骤：
1、不配置elasticUrls
2、配置index为test_index
3、启动数据同步任务
4、验证错误处理

    - 预期结果：
抛出DataXException，错误码为ES8xWriter-03，错误描述为Parameter 'endPoints(elasticUrls)' is required

  - TC045：异常处理-缺少必需参数index

    - 步骤：
1、配置elasticUrls为http://localhost:9200
2、不配置index
3、启动数据同步任务
4、验证错误处理

    - 预期结果：
抛出DataXException，错误码为ES8xWriter-03，错误描述为Necessary value (index)

  - TC046：异常处理-密码解密失败

    - 步骤：
1、配置elasticUrls为http://localhost:9200
2、配置password为invalid_encrypted_string
3、启动数据同步任务
4、验证错误处理

    - 预期结果：
抛出DataXException，错误码为ES8xWriter-11，错误描述为Failed to decrypt password

  - TC047：异常处理-不支持的映射类型

    - 步骤：
1、配置elasticUrls为http://localhost:9200
2、配置index为test_index
3、配置column包含data字段，类型为nonexistent_type
4、写入数据
5、验证错误处理

    - 预期结果：
抛出DataXException，错误码为ES8xWriter-08，错误描述为Unsupported mapping type

  - TC048：异常处理-删除索引失败

    - 步骤：
1、配置index为test_index
2、配置cleanup为true
3、配置autoCreateIndex为true
4、模拟删除索引失败
5、启动数据同步任务
6、验证错误处理

    - 预期结果：
抛出DataXException，错误码为ES8xWriter-06，错误描述为Failed to delete index

  - TC049：异常处理-连接失败

    - 步骤：
1、配置elasticUrls为http://unreachable-host:9200
2、配置index为test_index
3、启动数据同步任务
4、验证错误处理

    - 预期结果：
抛出DataXException，错误码为ES8xWriter-01，错误描述为Cannot connect to Elasticsearch 8.x server

  - TC050：异常处理-SSL上下文构建失败

    - 步骤：
1、配置elasticUrls为https://localhost:9200
2、配置secure为true
3、配置keystorePath为file:///invalid/path.jks
4、配置keystorePassword为wrong_password
5、启动数据同步任务
6、验证错误处理

    - 预期结果：
抛出DataXException，错误码为ES8xWriter-01，错误描述为Failed to build SSL context

  - TC051：异常处理-批量写入失败

    - 步骤：
1、配置elasticUrls为http://localhost:9200
2、配置index为test_index
3、配置column列表
4、模拟网络断开严重错误
5、写入数据
6、验证错误处理

    - 预期结果：
bulkError设置为true，后续写入停止，抛出BULK_REQ_ERROR异常

- 标签：后端-性能测试

  - TC052：性能测试-大批量数据写入

    - 步骤：
1、配置bulkActions为5000
2、配置bulkPerTask为10
3、准备10万条测试数据
4、执行数据同步任务
5、记录写入性能指标

    - 预期结果：
数据全部写入成功，记录TPS、平均响应时间、错误率

  - TC053：性能测试-动态索引批量写入

    - 步骤：
1、配置index为logs-{date}
2、配置bulkActions为1000
3、准备包含不同日期的测试数据
4、执行数据同步任务
5、记录动态索引创建性能

    - 预期结果：
动态索引创建成功，记录索引创建耗时和写入TPS

- 标签：后端-集成测试

  - TC054：集成测试-端到端数据同步

    - 步骤：
1、准备源数据（MySQL或文件）
2、配置ES8 Writer连接参数
3、配置字段映射关系
4、配置动态索引模式
5、配置文档ID生成规则
6、执行完整数据同步任务
7、验证数据一致性

    - 预期结果：
源数据全部成功写入ES，索引结构正确，文档ID正确生成，数据完整无丢失

  - TC055：集成测试-多源数据合并写入ES

    - 步骤：
1、准备多个数据源（MySQL、CSV等）
2、配置多个数据同步任务
3、配置相同的ES索引和文档ID规则
4、并发执行数据同步任务
5、验证ES数据正确性

    - 预期结果：
多源数据正确合并到ES，文档ID冲突正确处理，数据一致性保证

  - TC056：集成测试-与CustomProcessor集成

    - 步骤：
1、开发CustomProcessor实现BulkOperationVariant生成
2、配置ES8 Writer使用CustomProcessor
3、准备测试数据
4、执行数据同步任务
5、验证BulkOperationVariant正确处理

    - 预期结果：
CustomProcessor正确生成BulkOperationVariant，ES正确处理各种操作类型
