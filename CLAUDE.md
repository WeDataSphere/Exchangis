# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Exchangis是微众银行开源的数据交换工具，支持异构数据源之间的结构化和非结构化数据同步传输。基于插件化框架设计，与Linkis计算中间件深度集成。

## Build Commands

### 后端构建 (Java/Maven)
```bash
# 首次构建需先安装父POM
mvn -N install

# 完整构建
mvn clean package

# 跳过测试
mvn clean package -DskipTests

# 单模块构建示例
mvn clean package -pl exchangis-job/exchangis-job-server -am
```

### 前端构建 (Vue/FES.js)
```bash
cd web
npm install

# 开发模式
npm run dev

# 生产构建
npm run build
# 或
npm run prod

# 单元测试
npm run test:unit
```

### 代码格式检查
```bash
# Spotless代码格式检查
mvn spotless:check

# 自动修复格式问题
mvn spotless:apply
```

## Architecture

### 模块结构
```
exchangis/
├── exchangis-common/          # 通用工具、配置、异常处理
├── exchangis-datasource/      # 数据源管理层
│   ├── core/                  # 核心UI和VO定义
│   ├── linkis/                # Linkis数据源集成
│   ├── service/               # 服务实现
│   └── extension/             # 扩展数据源(TDSQL等)
├── exchangis-job/             # 作业管理层
│   ├── common/                # 共享数据模型
│   ├── builder/               # 作业构建和转换
│   ├── launcher/              # 任务执行启动
│   ├── server/                # REST接口服务
│   └── metrics/               # 性能监控指标
├── exchangis-engines/         # 数据同步引擎层
│   ├── engine-core/           # 引擎抽象框架
│   ├── engine-server/         # 引擎管理服务
│   ├── datax/                 # 阿里DataX引擎
│   └── sqoop/                 # Apache Sqoop引擎
├── exchangis-plugins/         # 插件系统
│   └── appconn/               # DSS AppConn集成
├── exchangis-privilege/       # 权限管理
├── exchangis-project/         # 项目管理
├── exchangis-server/          # 主服务聚合层
├── assembly-package/          # 部署打包
│   ├── sbin/                  # 部署脚本
│   └── config/                # 配置模板
└── web/                       # Vue3前端
```

### 核心依赖关系
- **Linkis**: 计算中间件，版本1.9.0-wds，提供数据源管理和任务执行能力
- **DSS**: DataSphere Studio，通过AppConn实现三级规范集成（SSO/组织结构/开发流程）
- **Spring Boot**: 2.7.10，主服务框架
- **MyBatis**: 2.1.2，数据持久化

### 数据流
1. 用户通过前端创建同步作业
2. `exchangis-job-server`接收请求，调用`exchangis-job-builder`构建任务
3. `exchangis-engines`将任务转换为具体引擎(DataX/Sqoop)的执行格式
4. 通过Linkis提交到计算集群执行
5. `exchangis-job-metrics`收集执行指标并反馈

## Tech Stack

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端语言 | Java | 1.8 |
| 后端框架 | Spring Boot | 2.7.10 |
| ORM | MyBatis | 2.1.2 |
| 前端框架 | Vue | 3.1.4 |
| 前端脚手架 | FES.js | 2.0.0 |
| UI组件库 | Ant Design Vue | 2.2.7 |
| 构建工具 | Maven | 3.8.1+ |

## Database

- MySQL 5.5+
- DDL/DML脚本位于`db/`目录
- 版本化脚本按`db/{version}/`组织

## Key Configuration Files

- `exchangis-server/src/main/resources/application.yml` - 主服务配置
- `assembly-package/config/application-exchangis.properties` - 部署配置模板
- `web/.fes.js` / `web/.fes.prod.js` - 前端配置

## Testing

```bash
# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=TestDataXJobBuilder

# 前端测试
cd web && npm run test:unit
```

测试文件分布在:
- `src/test/java/` - 标准位置
- `src/main/test/` - 部分模块使用此位置
