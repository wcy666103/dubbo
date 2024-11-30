#!/bin/bash

# 变量控制是否删除 Docker 镜像
DELETE_IMAGES=true

# 定义端口号
CONSUMER_DEBUG_PORT=31000
CONSUMER_PORT=50050
PROVIDER_DEBUG_PORT=31001
PROVIDER_PORT=50051
ISTIO_PORT=15010

# 定义操作系统类型 (可选项: linux, windows)
OS_TYPE="windows"  # 修改此变量以切换操作系统

# 定义删除 Docker 镜像的方法
function delete_docker_images() {
    if [ "$DELETE_IMAGES" = true ]; then
        echo "Deleting Docker images..."
        docker rmi localhost:5000/dubbo-demo-xds-consumer:latest || true
        docker rmi localhost:5000/dubbo-demo-xds-provider:latest || true
        docker rmi -f dubbo-demo-xds-consumer:latest || true
        docker rmi -f dubbo-demo-xds-provider:latest || true
        echo "Docker images deleted"
    else
        echo "Skipping deletion of Docker images"
    fi
}

# 根据端口占用的进程ID来终止进程
function stop_processes_by_port() {
    local ports=("$@")
    for port in "${ports[@]}"; do
        if [ "$OS_TYPE" = "linux" ]; then
            # Linux 系统命令
            pid=$(lsof -t -i:$port)
            if [ -n "$pid" ]; then
                echo "Killing process with PID $pid on port $port"
                kill -9 $pid || true
            else
                echo "No process found on port $port"
            fi
        elif [ "$OS_TYPE" = "windows" ]; then
            # Windows 系统命令
            pid=$(netstat -ano | findstr ":$port" | head -n 1 | awk '{print $5}' | tr -d '[:space:]')
            if [ -n "$pid" ] && [[ "$pid" =~ ^[0-9]+$ ]]; then
                echo "Killing process with PID $pid on port $port"
                taskkill //PID  $pid //F || true
            else
                echo "No valid process found on port $port"
            fi
        else
            echo "Unsupported OS type: $OS_TYPE"
        fi
    done
}

# 停止端口转发
stop_processes_by_port $CONSUMER_PORT $PROVIDER_PORT $ISTIO_PORT

## 删除 Kubernetes 部署和服务
#kubectl delete deployment dubbo-demo-xds-consumer dubbo-demo-xds-provider || true
#kubectl delete svc dubbo-demo-xds-consumer dubbo-demo-xds-provider || true

# 删除 ./services.yaml 文件中定义的其他资源
kubectl delete -f ./services.yaml || true

# 调用删除 Docker 镜像的方法
delete_docker_images

echo "All services and resources have been closed and deleted"
