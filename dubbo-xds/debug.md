# debug流程
1. 装好k8s和对应的isitio
2. mvn clean install -T 1C -DskipTests 来install一下，过程中会创建镜像的
3. 运行
```shell
kubectl create namespace dubbo-demo
```
```shell
kubectl config set-context --current --namespace=dubbo-demo
```

```shell
docker run -d -p 5000:5000 --restart=always --name registry registry:latest
docker start local-registry
```
3. 在对应的目录中使用gitbash来启动update.sh脚本
4. 创建 remote debug，绑定到 31000 端口 到xds-demo-consumer项目中
5. 对xds模块的关键代码打断点，进行debug
6. 关闭的时候关闭一下

```shell

kubectl delete namespace dubbo-demo
```

```shell
docker stop registry
```
