# Ping-Pong 微服务限流示例

本项目演示了两个基于 **Spring WebFlux** 的微服务——`ping-service` 和 `pong-service`——如何通过 **响应式编程**、**全局限流** 和 **跨进程文件锁** 实现相互通信与流量控制。

- **Ping Service**：每秒尝试向 Pong 发送一次 "Hello" 请求，同时受到**跨 JVM 的全局限流**（2 RPS）控制。
- **Pong Service**：处理 Ping 的请求，并返回 "World"，但其内部有**每秒仅处理 1 个请求**的节流控制，超出的请求返回 HTTP 429。

---


---

## 架构概览
| 技术 | 说明 |
|-----|---|
| Ping Instance 1 | (JVM 1) |
| Pong Service | (每秒仅处理 1 个) |
| Ping Instance 2 | (JVM 2)|
|...|
|Ping Instance N|

所有 Ping 实例共享一个文件锁，实现全局限流（2 RPS）。

Pong 服务内部使用内存计数器，每秒只允许 1 个请求通过。

## 技术栈

| 技术 | 说明 |
|------|------|
| Java 17 | 基础运行环境 |
| Spring Boot 3.2.5 | 应用框架 |
| Spring WebFlux | 响应式 Web 框架 |
| Reactor | 响应式流库（Flux / Mono） |
| Spock (Groovy) | 测试框架 |
| JaCoCo | 代码覆盖率工具 |
| Maven | 构建工具 |

---

## 模块结构
ping-pong/<br/>
├── pom.xml # 父 POM，管理依赖和插件<br/>
├── pong-service/ # Pong 微服务<br/>
│ ├── pom.xml<br/>
│ └── src/<br/>
│ ├── main/<br/>
│ │ ├── java/com/example/pong/<br/>
│ │ │ ├── PongApplication.java<br/>
│ │ │ ├── web/PongController.java<br/>
│ │ │ └── ratelimit/<br/>
│ │ │ ├── OnePerSecondLimiter.java<br/>
│ │ │ └── ThrottlingWebFilter.java<br/>
│ │ └── resources/application.yml<br/>
│ └── test/<br/>
│ └── groovy/com/example/pong/<br/>
│ ├── web/PongControllerSpec.groovy<br/>
│ └── ratelimit/<br/>
│ ├── OnePerSecondLimiterSpec.groovy<br/>
│ └── ThrottlingWebFilterSpec.groovy<br/>
└── ping-service/ # Ping 微服务<br/>
├── pom.xml<br/>
└── src/<br/>
├── main/<br/>
│ ├── java/com/example/ping/<br/>
│ │ ├── PingApplication.java<br/>
│ │ ├── config/PingConfig.java<br/>
│ │ ├── client/PongClient.java<br/>
│ │ ├── client/PongResponse.java<br/>
│ │ ├── ratelimit/FileLockRateLimiter.java<br/>
│ │ └── scheduler/PingScheduler.java<br/>
│ └── resources/application.yml<br/>
└── test/<br/>
└── groovy/com/example/ping/<br/>
├── client/PongClientSpec.groovy<br/>
├── ratelimit/FileLockRateLimiterSpec.groovy<br/>
└── scheduler/PingSchedulerSpec.groovy<br/>


---

## 环境

- **JDK 17** 或更高版本
- **Maven 3.6+**

---

## 构建项目

在项目根目录下执行：

```bash
mvn clean package
```
该命令会编译所有模块、运行单元测试、生成 JaCoCo 覆盖率报告，并打包可执行 jar。


## 编译产物

pong-service/target/pong-service-0.0.1-SNAPSHOT.jar<br/>
ping-service/target/ping-service-0.0.1-SNAPSHOT.jar

## 运行服务

- 启动 Pong Service
```bash
  java -jar pong-service/target/pong-service-0.0.1-SNAPSHOT.jar
```

- 启动多个 Ping 实例

  Ping 服务支持通过环境变量 INSTANCE_ID 区分不同实例的日志文件。启动多个实例，每个在独立终端中运行。

```bash
  java -jar -DINSTANCE_ID=1 ping-service/target/ping-service-0.0.1-SNAPSHOT.jar
  java -jar -DINSTANCE_ID=2 ping-service/target/ping-service-0.0.1-SNAPSHOT.jar
```

# 配置说明

本文档详细说明 Ping-Pong 项目中各个微服务的配置项、默认值及覆盖方式。

---

### 配置文件位置

- Pong Service：`pong-service/src/main/resources/application.yml`
- Ping Service：`ping-service/src/main/resources/application.yml`

---

### Pong Service 配置

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `server.port` | `8081` | Pong 服务监听端口 |
| `spring.application.name` | `pong-service` | 应用名称，用于日志和监控标识 |

#### 示例 (`application.yml`)

```yaml
server:
  port: 8081

spring:
  application:
    name: pong-service
```

### Ping Service

|属性|默认值| 说明  |
|-----|-----|-----|
|server.port|0| 随机端口（避免多实例端口冲突） |
|spring.application.name|ping-service| 应用名称 |
|app.pong.base-url|http://localhost:8081| Pong 服务基础地址 |
|app.pong.path|/hello| Pong 接口路径 |
|app.ping.interval-ms|1000| Ping 发送间隔（毫秒） |
|app.rate-limit.lock-file|${java.io.tmpdir}/ping-rate.lock|文件锁路径|
|app.rate-limit.max-requests-per-second|2|全局每秒允许的最大请求数|
|logging.file.name|logs/ping-${INSTANCE_ID:default}.log|日志文件路径|

#### 示例 (application.yml)

```yaml
server:
  port: 0

spring:
  application:
    name: ping-service

app:
  pong:
    base-url: http://localhost:8081
    path: /hello
  ping:
    interval-ms: 1000
  rate-limit:
    lock-file: ${java.io.tmpdir}/ping-rate.lock
    max-requests-per-second: 2

logging:
  file:
    name: logs/ping-${INSTANCE_ID:default}.log
```

# 测试

### 运行测试
```bash
  mvn test
```

### 覆盖率报告

- Pong 服务覆盖率报告：pong-service/target/site/jacoco/index.html
- Ping 服务覆盖率报告：ping-service/target/site/jacoco/index.html

# 限流机制详解

### Pong 端：每秒仅处理 1 个请求
  实现类：OnePerSecondLimiter

- 基于内存计数器和时间窗口（秒）。
- 使用 synchronized 保证线程安全。
- 每当一个请求到达，检查当前秒是否与记录窗口一致：
   - 若不同，重置窗口并计数为 1，允许通过。
   - 若相同且计数 < 1（即已经处理了 1 个），拒绝并返回 false。
- 拒绝的请求由 ThrottlingWebFilter 转换为 HTTP 429 响应。


### Ping 端：跨进程全局限流 2 RPS
实现类：FileLockRateLimiter

- 利用 Java NIO FileLock 实现跨 JVM 的互斥访问。
- 多个 Ping 进程共享同一个锁文件（默认 /tmp/ping-rate.lock）。
- 锁文件内容记录 currentSecond:count，每次获取锁后读取并更新计数。
- 若当前秒内计数已满（达到 max-requests-per-second），则拒绝。
- 为了避免同一 JVM 内多个线程同时获取文件锁导致 OverlappingFileLockException，内部使用 ReentrantLock 进行 JVM 级互斥。
- 在响应式链中，通过 tryAcquireAsync() 将阻塞的文件 I/O 隔离到 boundedElastic 调度器，避免阻塞 Reactor 主线程。


# 日志

- 输出位置

  每个 Ping 实例的日志位于 logs/ping-<INSTANCE_ID>.log（或 logs/ping-default.log）。

- 内容

|日志内容| 说明                  |
|-----|---------------------|
|Result=SENT_AND_RESPONDED - Pong responded: World|请求成功发送，并收到 Pong 的正常响应|
|Result=SENT_AND_THROTTLED - Pong throttled it|请求成功发送，但被 Pong 限流，返回 429|
|Result=RATE_LIMITED - Request not sent as being rate limited|请求未发送，因为全局 Ping 限流已满|
|Result=ERROR - ...|发送请求时发生异常（例如 Pong 服务不可达）|