# 模块作用

This module helps to introduce Curator and Zookeeper dependencies that are necessary for Dubbo to work with zookeeper as transitive dependencies

就是为了帮助使用zookeeper，使用的时候这样使用：

```xml
    <dependency>
        <groupId>org.apache.dubbo</groupId>
        <artifactId>dubbo-dependencies-zookeeper</artifactId>
        <version>${dubbo.version}</version>
        <type>pom</type>
    </dependency>
```

dubbo-dependencies-zookeeper 将自动为应用增加 Zookeeper 相关客户端的依赖，减少用户使用 Zookeeper 成本，如使用中遇到版本兼容问题，用户也可以不使用 dubbo-dependencies-zookeeper，而是自行添加 Curator、Zookeeper Client 等依赖。

由于 Dubbo 使用 Curator 作为与 Zookeeper Server 交互的编程客户端，因此，要特别注意 Zookeeper Server 与 Dubbo 版本依赖的兼容性


