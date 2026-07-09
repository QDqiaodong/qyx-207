# 轨道交通站点候车休息台线路站点关联绑定系统

## 项目简介

本系统用于管理轨道交通（地铁/轻轨）站点的候车休息台，核心功能是实现休息台设备与线路、站点的双重关联绑定。系统支持休息台档案管理、线路站点联动、关联信息同步调整以及按线路/站点进行汇总查询。

## 技术栈

- **前端**: Vue 3 + Vite + Element Plus + TypeScript
- **后端**: Spring Boot 3.3 + JDK 17 + Spring Data JPA + Spring Data Redis
- **数据库**: MySQL 8.0
- **缓存**: Redis 7 (SortedSet 缓存休息台材质参数)
- **容器化**: Docker + Docker Compose

## 核心功能

1. **候车休息台基础建档**: 编号、材质、摆放规格、位置描述
2. **线路 + 站点双重绑定**: 登记休息台所属轨道线路与具体站点
3. **关联信息同步调整**: 线路/站点变更时自动留存变更台账
4. **按线路汇总**: 查询某线路下所有站点的休息台
5. **按站点反向查询**: 查看站点所属线路信息

## 访问地址

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:8127 |
| 后端 API | http://localhost:8137/api |
| MySQL | 127.0.0.1:3353 |
| Redis | 127.0.0.1:6426 |

## 快速开始

### 使用启动脚本

```bash
./start.sh
```

### 手动启动

```bash
# 构建并启动所有服务
docker compose up -d --build

# 查看日志
docker compose logs -f

# 停止服务
docker compose down
```

## 目录结构

```
.
├── .env                    # 环境配置文件
├── .gitignore              # Git 忽略配置
├── .dockerignore           # Docker 忽略配置
├── docker-compose.yml      # Docker Compose 配置
├── start.sh                # 启动脚本
├── README.md               # 项目说明文档
├── backend/                # 后端代码
│   ├── pom.xml             # Maven 配置
│   ├── settings.xml        # Maven 镜像配置
│   ├── Dockerfile          # 后端 Dockerfile
│   └── src/main/java/com/railway/
├── frontend/               # 前端代码
│   ├── package.json        # npm 配置
│   ├── Dockerfile          # 前端 Dockerfile
│   ├── nginx.conf          # Nginx 配置
│   └── src/                # 前端源码
└── data/                   # 数据目录
    └── sql/                # SQL 初始化脚本
```

## 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| APP_NAME | railway-rest-bench | 应用名称 |
| FRONTEND_PORT | 8127 | 前端端口 |
| BACKEND_PORT | 8137 | 后端端口 |
| MYSQL_PORT | 3353 | MySQL 端口 |
| MYSQL_ROOT_PASSWORD | railway2026 | MySQL 密码 |
| MYSQL_DATABASE | railway_db | 数据库名称 |
| REDIS_PORT | 6426 | Redis 端口 |
| REDIS_PASSWORD | railway_redis | Redis 密码 |

## 单独构建验证

### 后端编译

```bash
cd backend
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
mvn compile -q
```

### 前端构建

```bash
cd frontend
npm ci
npm run build
```

## 开发模式

开发模式下可以使用 `docker-compose.yml` 配合 `Dockerfile.dev` 文件进行热更新开发。