# SmartSpend Web 部署指南

## 一、本地运行（无需安装数据库）

```bash
mvn -DskipTests package
java -jar target/smartspend-web-1.0.0.jar
```

浏览器访问 http://localhost:8080 ，首次使用需注册账号；本地 H2 数据库文件生成在 `data/` 目录。也可直接双击项目根目录的「启动SmartSpend网站.bat」。

## 二、部署到 Render（免费，推荐）

Render 免费档：512MB 内存，15 分钟无访问会休眠（下次打开约等 30 秒唤醒）。数据库用 Aiven 免费 MySQL（1GB，永久免费，无需绑卡）。

1. 把 `smartspend-web` 目录推送到 GitHub 仓库（`Dockerfile` 已备好，Render 会自动识别 Docker 构建）。
2. 到 [aiven.io](https://aiven.io) 注册 → **Create service** → **MySQL** → **Free plan** → 任选区域创建。在服务详情页 **Overview** 找到连接信息（Host / Port / User / Password / Database）。
3. 到 [render.com](https://render.com) 注册（可用 GitHub 登录）→ **New + → Web Service** → 连接 GitHub 仓库 → Language 选 **Docker**。
4. 在 **Environment Variables** 添加（值来自第 2 步 Aiven 页面）：
   ```
   SPRING_PROFILES_ACTIVE = mysql
   MYSQL_URL = jdbc:mysql://<Host>:<Port>/<Database>?sslMode=REQUIRED&serverTimezone=Asia/Shanghai&characterEncoding=utf8
   MYSQLUSER = <User>
   MYSQLPASSWORD = <Password>
   ```
5. 点 **Create Web Service**，等待首次构建（约 5~10 分钟）。完成后 Render 会分配 `https://xxx.onrender.com` 公网网址，发给任何人即可访问。

> 部署后是全新的云端数据库（不含本地数据）；每个用户注册后数据互相隔离，AI 接口设置也按用户独立保存。

## 三、部署到 Railway（备选，付费）

1. 把 `smartspend-web` 目录推送到 GitHub 仓库（`.gitignore` 已排除构建产物与本地数据）。
2. 登录 [railway.app](https://railway.app) → **New Project** → **Deploy from GitHub repo**，选择该仓库，Railway 会按 `railway.json` 自动构建（Nixpacks + Maven，Java 17 由 `system.properties` 指定）。
3. 在项目里点 **+ New → Database → MySQL**，添加一个 MySQL 服务。
4. 回到应用服务 → **Variables**，添加环境变量：
   ```
   SPRING_PROFILES_ACTIVE=mysql
   ```
   Railway 的 MySQL 服务会自动注入 `MYSQLHOST / MYSQLPORT / MYSQLUSER / MYSQLPASSWORD / MYSQLDATABASE`，`application.yml` 的 mysql profile 会自动读取，无需手工填写。
5. 应用服务 → **Settings → Networking → Generate Domain**，即可获得公网访问地址。

> 部署后是全新的云端数据库（不含本地数据）；每个用户注册后数据互相隔离，AI 接口设置也按用户独立保存。

## 三、常见问题

- **构建失败**：确认仓库根目录包含 `pom.xml` 与 `railway.json`；Railway 构建日志可在服务页 **Deployments** 查看。
- **启动后立即退出**：检查是否已添加 `SPRING_PROFILES_ACTIVE=mysql` 且 MySQL 服务已启动（首次启动需要等 MySQL 就绪，失败会自动重启，最多 3 次）。
- **端口**：应用自动读取 Railway 注入的 `PORT` 环境变量，无需配置。
