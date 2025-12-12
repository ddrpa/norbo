# Norbo

Norbo 是一个基于 Java 注解处理器（Pluggable Annotation Processing API）的代码生成工具，用于自动生成 MyBatis-Plus DAO 层样板代码。

## 设计思路

在使用 MyBatis-Plus 开发时，每个实体类通常需要配套的 Mapper 接口和 Service 类。Norbo 通过注解处理器在编译期自动生成这些代码，减少重复劳动。

核心技术栈：

- **Pluggable Annotation Processing API**：Java 编译器提供的扩展机制，允许在编译期处理注解并生成源代码
- **JavaPoet**：Square 开源的 Java 代码生成库，提供类型安全的 API 构建 Java 源文件

生成的代码输出到 `target/generated-sources/annotations/` 目录，由编译器自动纳入编译范围。

## 安装

添加 Maven 依赖：

```xml
<dependency>
  <groupId>cc.ddrpa.dorian</groupId>
  <artifactId>norbo</artifactId>
  <version>0.1.0</version>
</dependency>
```

Norbo 通过 `META-INF/services/javax.annotation.processing.Processor` 声明了注解处理器，无需额外配置即可生效。

如果项目中已在 `maven-compiler-plugin` 显式配置了其他注解处理器（如 Lombok），需要将 Norbo 加入 `annotationProcessorPaths`：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
            <path>
                <groupId>cc.ddrpa.dorian</groupId>
                <artifactId>norbo</artifactId>
                <version>0.1.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

## 注解说明

### @MPMapper

生成 MyBatis-Plus Mapper 接口。

**属性**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | `""` | 生成类的包名 |
| `packageName` | String | `""` | 同 `value`，优先级更高 |

若未指定包名，生成的类与被注解类位于同一包下。

**示例**

```java
@MPMapper
public class User {
    private Long id;
    private String name;
}
```

生成代码：

```java
package com.example.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

### @MPRepository

生成 MyBatis-Plus Service 实现类。

**属性**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | `""` | 生成类的包名 |
| `packageName` | String | `""` | 同 `value`，优先级更高 |

生成的 Repository 类继承 `ServiceImpl`，并假定同包下存在对应的 Mapper 接口。因此通常需要与 `@MPMapper` 配合使用。

**示例**

```java
@MPMapper
@MPRepository
public class User {
    private Long id;
    private String name;
}
```

生成代码：

```java
package com.example.entity;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserRepository extends ServiceImpl<UserMapper, User> {
}
```

### @MPTypeHandler

生成 MyBatis-Plus JSON 类型处理器，用于将 Java 对象序列化为 JSON 存储到数据库。

**适用场景**

- 实体类中包含复杂对象字段，需要以 JSON 格式存储
- 需要处理泛型类型（如 `List<String>`）

**属性**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | `""` | 生成类的包名 |
| `packageName` | String | `""` | 同 `value`，优先级更高 |

该注解可用于类或字段。生成的 TypeHandler 继承 `AbstractJsonTypeHandler`，内置 Jackson ObjectMapper 并配置了常用选项：

- 注册 `JavaTimeModule` 和 `Jdk8Module`
- 禁用时间戳格式输出
- 容忍未知属性和空值

**示例**

注解于字段：

```java
public class Order {
    private Long id;
    
    @MPTypeHandler
    private List<OrderItem> items;
}
```

注解于类：

```java
@MPTypeHandler
public class Address {
    private String city;
    private String street;
}
```

生成的 TypeHandler 类名规则：

- 非泛型类型：`{ClassName}TypeHandler`
- 泛型类型：`{RawType}Of{TypeArg1}And{TypeArg2}TypeHandler`

例如 `List<OrderItem>` 生成 `ListOfOrderItemTypeHandler`。

### @JeecgBootController

生成 JeecgBoot 风格的 Controller 类，包含标准 CRUD 接口。

该注解为特定框架设计，生成的 Controller 依赖 JeecgBoot 基础设施。

**属性**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | `""` | 模块描述，用于日志和 Swagger 文档 |

**生成的接口**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list` | 分页查询 |
| POST | `/add` | 新增 |
| POST | `/edit` | 编辑 |
| DELETE | `/delete` | 按 ID 删除 |
| DELETE | `/deleteBatch` | 批量删除 |
| GET | `/queryById` | 按 ID 查询 |
| GET | `/exportXls` | 导出 Excel |
| POST | `/importExcel` | 导入 Excel |

**示例**

```java
@MPMapper
@MPRepository
@JeecgBootController("计划管理")
public class Plan {
    private Long id;
    private String name;
}
```

![生成的 Controller 代码示例](showcase-jeecgboot-controller.png)

## 使用示例

### 基本用法

```java
package com.example.entity;

@MPMapper
@MPRepository
public class Article {
    private Long id;
    private String title;
    private String content;
    private LocalDateTime createTime;
}
```

执行 `mvn compile` 后，在 `target/generated-sources/annotations/com/example/entity/` 目录下生成：

- `ArticleMapper.java`
- `ArticleRepository.java`

### 指定生成类的包名

```java
package com.example.entity;

@MPMapper(packageName = "com.example.mapper")
@MPRepository(packageName = "com.example.service")
public class Article {
    private Long id;
    private String title;
}
```

生成的 Mapper 位于 `com.example.mapper` 包，Repository 位于 `com.example.service` 包。

### 处理 JSON 字段

```java
package com.example.entity;

@MPMapper
@MPRepository
public class Product {
    private Long id;
    private String name;
    
    @MPTypeHandler
    private Map<String, Object> attributes;
    
    @MPTypeHandler
    private List<String> tags;
}
```

在实体类中使用生成的 TypeHandler：

```java
@TableField(typeHandler = MapOfStringAndObjectTypeHandler.class)
private Map<String, Object> attributes;

@TableField(typeHandler = ListOfStringTypeHandler.class)
private List<String> tags;
```

## 技术细节

### 注解处理器工作流程

1. 编译器扫描源代码中的注解
2. 调用对应的 Processor 处理注解元素
3. Processor 使用 JavaPoet 构建源代码
4. 通过 `Filer` 写入生成的源文件
5. 编译器将生成的源文件纳入后续编译

### 包名推断规则

三个属性按优先级依次检查：

1. `packageName` 属性（若非空）
2. `value` 属性（若非空）
3. 被注解元素所在的包

### 类名生成规则

| 注解 | 生成类名 |
|------|----------|
| `@MPMapper` | `{EntityName}Mapper` |
| `@MPRepository` | `{EntityName}Repository` |
| `@MPTypeHandler` | `{TypeName}TypeHandler` |
| `@JeecgBootController` | `{EntityName}Controller` |

## 依赖要求

- Java 17+
- MyBatis-Plus 3.x
- Spring Framework（用于 `@Service` 注解）
- Jackson（用于 TypeHandler 的 JSON 序列化）

## 许可证

Apache License 2.0