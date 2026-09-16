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

## 停运改造与待转运口径

- **站点状态**: `1`=正常运营，`2`=停运改造，`0`=已删除
- **休息台状态**: `1`=在用，`2`=待转运，`0`=已删除
- 站点标记停运改造（`PUT /api/stations/{id}/suspend`）后，该站仍在用的休息台**立即转入待转运**，不再计入线路在用汇总（线路汇总只统计在用台）；允许站点先停、剩台后转
- 待转运台转运（`PUT /api/benches/{id}/transfer`）只能转入**仍在运营且属于所选线路**的站点；转运成功写入变更台账、恢复在用，并同步刷新 Redis 材质参数缓存
- 停运与转运均为事务操作并加行级悲观锁：并发重复停运幂等（待转运只落一套），转运任何一步失败整体回滚（休息台保持待转运，不留半截台账）

## 清扫占台口径

车站清扫班有独立的**清扫占台单**（`cleaning_occupancy` 表），不是口头交代，也不是在休息台旁边加一句“清扫中”：

- **点名占台，只锁脏台**：`POST /api/benches/cleaning/start`，body 必须传点名的具体休息台 id 列表（`benchIds`，不能为空/空元素，自动去重）。**不提供按整条线/整站一把梭的入口**，现场逐张勾选本站脏台。
- **拦新坐、不清人**：占台单生效期间，落座接口 `POST /api/benches/{id}/sit` 直接拒绝新客人（“清扫占台中，暂不接待新客人就座”）；已经在座的客人（`bench_sitting` 表）**一律不清走**，可一直坐到自行调用 `DELETE /api/benches/sittings/{sittingId}` 离开。清扫班不能为了省事提前放人。
- **并发只落一套**：两人同时给同一张台开清扫，服务端对休息台行加悲观写锁串行——先到的落一套“清扫中”占台单，后到的看到已有生效单被拒绝，绝不会一个成功、另一个把座位状态改乱。
- **中途失败整体回滚**：一次点名多张台，只要其中一张不存在/已删除/已在清扫，整单回滚——占台单一条不留，台的编号、所属站、原坐客全部原样。
- **结束清扫**：`PUT /api/benches/cleaning/{occupancyId}/finish`。仍有客人在座时不能结束（等客人自己离开，绝不替清扫班清人）；台子放开后新客人才可重新落座。重复结束幂等。
- 生效占台单与在座名单可分别通过 `GET /api/benches/cleaning/active`、`GET /api/benches/sittings` 核对。

## 急救箱钥匙领用口径

候车厅急救箱钥匙实行**领用登记制**（`first_aid_key` 档案表 + `key_checkout` 领用登记单），不是口头交代，也不是只在钥匙名单上改两个字：

- **领用必落单**：`POST /api/keys/checkout`，body 传 `keyIds`（点名的具体钥匙）、`borrower`（领用人）、`role`、`stationId`（站务必传）。每把钥匙落一份登记单，记清**钥匙编号、所属站、领用人、交出时刻**四要素；登记单把编号、所属站、所属线原样抄进单据留档。
- **未交还只此一份**：同一把钥匙上一份登记未交还前，开不出第二份；拒绝时写明上一份是谁、什么时候领走的。交还走 `PUT /api/keys/checkouts/{checkoutId}/return`，登记单结清、状态字翻回在库，重复交还幂等。
- **值班长领全线，站务只领本站**：`role=SUPERVISOR` 可一次点名全线多把、一次领空；`role=STATION_STAFF` 必须报本人所在站，只能领该站名下那一把，点到别站的钥匙整单拒绝。
- **并发只落一条**：两人同时领同一把，服务端对钥匙行加悲观写锁串行——先到的落登记单，后到的看到未交还登记被拒；未交还登记只有一条，钥匙状态字不会写成两份在外。
- **中途失败整体回滚**：一次点名多把，只要其中一把不存在/已注销/未交还/不属本站，整单回滚——登记单一条不留，钥匙仍在库，编号、所属站原样。
- **单与状态字同落同回**：登记单落库与钥匙「在库/在外」状态字翻转在同一事务完成；只改名单上的状态字不落登记单（或只落单不改状态字）都不算完成，任何一步失败一起回滚。
- 未交还名单与单把钥匙的领用历史可分别通过 `GET /api/keys/checkouts/active`、`GET /api/keys/{keyId}/checkouts` 核对；钥匙建档 `POST /api/keys`、注销 `DELETE /api/keys/{keyId}`（在外的钥匙须先交还）。

## 变更台账冲正口径

变更台账**只进不出**：写错的记录不许改、不许删，只能**冲正**——对它补记一条反向记录把影响抵回来。

- **冲正入口**：`POST /api/benches/records/{recordId}/reverse`，body 可传 `reason`（选填）。成功后台账多一条 `REVERSAL` 类型的冲正单：新旧线路站点与原记录正好对调，记在同一张台名下，并通过 `reversalOfId` 指回原记录；原记录保留，打上 `reversedById` 标记指向冲正单，谁冲了谁一眼可查。
- **位置回退**：冲正成功后休息台回到原记录变更前的线路和站点（只回线路站点，不动台的状态），材质参数缓存同步刷新。
- **只冲最近一条**：每张台只有它最近一条未被冲正的正式变更可冲正，中间隔着别的记录不许跳冲；已被冲正过的记录不能再冲，冲正单本身也不能再被冲正。冲掉最近一条后，前一条才轮得到冲。
- **只动本台**：冲正只标记本台原记录、只往本台名下补冲正单、只调本台位置，牵扯别的台的变更单一条都不碰。
- **并发只成一次**：一张台同一时刻只允许一次冲正。服务端对休息台行加悲观写锁、锁内再锁定读台账，两人同时冲正同一条记录——先到的落冲正单，后到的看到"该变更记录已被冲正"而失败。
- **中途失败整体回滚**：冲正单落库、原记录标记、休息台位置、缓存刷新同一事务完成；任何一步失败（如原线路/站点已不存在、缓存故障），台账不留半条反向记录，休息台保持冲正前的样子。

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