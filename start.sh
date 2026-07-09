#!/bin/bash

set -e

echo "=========================================="
echo "  轨道交通站点候车休息台管理系统启动脚本"
echo "=========================================="

if [ -f ".env" ]; then
    source .env
fi

APP_NAME=${APP_NAME:-railway-rest-bench}
FRONTEND_PORT=${FRONTEND_PORT:-8127}
BACKEND_PORT=${BACKEND_PORT:-8137}
MYSQL_PORT=${MYSQL_PORT:-3353}
REDIS_PORT=${REDIS_PORT:-6426}

echo ""
echo "[1/3] 检查端口占用情况..."

for port in $FRONTEND_PORT $BACKEND_PORT $MYSQL_PORT $REDIS_PORT; do
    if lsof -Pi :$port -sTCP:LISTEN -t > /dev/null 2>&1; then
        echo "  ERROR: 端口 $port 已被占用！"
        echo "  进程信息:"
        lsof -Pi :$port -sTCP:LISTEN
        exit 1
    else
        echo "  端口 $port: 可用"
    fi
done

echo ""
echo "[2/3] 构建并启动 Docker 容器..."
docker compose up -d --build

echo ""
echo "[3/3] 等待服务启动..."
sleep 15

echo ""
echo "=========================================="
echo "  服务启动完成！"
echo "=========================================="
echo ""
echo "前端地址: http://localhost:$FRONTEND_PORT"
echo "后端API:  http://localhost:$BACKEND_PORT/api"
echo "MySQL:    127.0.0.1:$MYSQL_PORT"
echo "Redis:    127.0.0.1:$REDIS_PORT"
echo ""
echo "查看日志: docker compose logs -f"
echo "停止服务: docker compose down"