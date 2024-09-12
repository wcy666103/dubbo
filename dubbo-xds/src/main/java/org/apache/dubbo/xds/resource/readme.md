XdsResourceType 又是父类，定义一些解析资源的公共方法
像XdsClusterResource extends XdsResourceType<CdsUpdate>类就是子类并且泛型类型都定义好了


ClusterLoadAssignment 类是Envoy中的类， 通常用于管理 Envoy 中的负载均衡策略。
在 Envoy 中，ClusterLoadAssignment 是一个重要的概念，用于定义一个集群中的所有端点及其负载分配策略。
这个类通常包含多个 Endpoint 和负载均衡策略信息。
