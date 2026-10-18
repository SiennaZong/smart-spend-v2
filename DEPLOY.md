# SmartSpend Web 部署指南

## 一、本地运行（无需安装数据库）

```bash
mvn -DskipTests package
java -jar target/smartspend-web-1.0.0.jar
```

浏览器访问 http://localhost:8080 ，首次使用需注册账号；本地 H2 数据库文件生成在 `data/` 目录。也可直接双击项目根目录的「启动SmartSpend网站.bat」。

## 二、部署到 Sealos（国内访问快，推荐）

Sealos 是国内云平台，控制台访问流畅；新用户有免费体验金，之后按量计费（低配应用一天约几毛钱）。数据库用 Sealos 自带 MySQL（与应用同集群内网直连，速度快）。

1. 到 [cloud.sealos.io](https://cloud.sealos.io) 注册（GitHub 登录或手机号）。
2. 控制台打开「数据库」→ 新建 → **MySQL 8.0** → 资源拉到最低 → 创建。详情页记录：**内网地址**、**root 密码**。
3. 控制台打开「DevBox」→ 新建项目 → 运行环境选 **Java** → 资源最低 → 进入后打开终端执行：
   ```bash
   git clone https://github.com/SiennaZong/smart-spend-v2.git .
   mvn -DskipTests package
   java -jar target/smartspend-web-1.0.0.jar   # 验证能启动后 Ctrl+C 停止
   ```
4. 点 DevBox「发布」→ 填版本号（如 v1.0.0）→ 启动命令 `java -jar target/smartspend-web-1.0.0.jar`，端口 **8080**，开启**外网访问**，环境变量：
   ```
   SPRING_PROFILES_ACTIVE = mysql
   MYSQL_URL = jdbc:mysql://<第2步内网地址>:3306/smartspend?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8&createDatabaseIfNotExist=true
   MYSQLUSER = root
   MYSQLPASSWORD = <第2步密码>
   ```
   （`createDatabaseIfNotExist=true` 会自动建库，无需手工执行 SQL）
5. 确认发布后跳转到「应用管理」，状态变 Running 后，页面上的**外网访问地址**（`https://xxx.cloud.sealos.io`）即公网网址。

> 部署后是全新的云端数据库；每个用户注册后数据互相隔离，AI 接口设置按用户独立保存。

## 三、部署到 Koyeb（免费，无需信用卡，境内访问其控制台可能不畅）

Koyeb Hobby 档：512MB 内存 / 0.1 vCPU，永久免费、无需绑卡；空闲会休眠，下次打开稍等几秒唤醒。数据库用 Aiven 免费 MySQL（1GB，永久免费，无需绑卡）。

1. 把 `smartspend-web` 目录推送到 GitHub 仓库（`Dockerfile` 已备好，Koyeb 会自动识别 Docker 构建）。
2. 到 [aiven.io](https://aiven.io) 注册 → **Create service** → **MySQL** → **Free plan** → 任选区域创建。在服务详情页 **Overview** 找到连接信息（Host / Port / User / Password / Database）。
3. 到 [koyeb.com](https://www.koyeb.com) 注册（可用 GitHub 登录）→ **Create Web Service** → **GitHub** → 选择该仓库，Builder 选 **Dockerfile**。
4. 配置：Instance 选 **Free**（512MB）；Region 选 **Frankfurt** 或 **Washington**（免费档仅欧美节点）；**Ports 把默认 8000 改为 8080**。
5. 在 **Environment Variables** 添加（值来自第 2 步 Aiven 页面）：
   ```
   SPRING_PROFILES_ACTIVE = mysql
   MYSQL_URL = jdbc:mysql://<Host>:<Port>/<Database>?sslMode=REQUIRED&serverTimezone=Asia/Shanghai&characterEncoding=utf8
   MYSQLUSER = <User>
   MYSQLPASSWORD = <Password>
   ```
6. 点 **Deploy**，等待首次构建（约 5~10 分钟）。完成后 Koyeb 会分配 `https://xxx.koyeb.app` 公网网址，发给任何人即可访问。

> 部署后是全新的云端数据库（不含本地数据）；每个用户注册后数据互相隔离，AI 接口设置也按用户独立保存。

## 四、部署到 Render（备选，免费但需绑卡验证）

Render 免费档：512MB 内存，15 分钟无访问会休眠（下次打开约等 30 秒唤醒）。注意：新账号首次部署可能要求绑定 Visa/Mastercard 做 $1 预授权验证（不扣费）；没有国际信用卡请改用 Koyeb。

1. 把 `smartspend-web` 目录推送到 GitHub 仓库（`Dockerfile` 已备好，Render 会自动识别 Docker 构建）。
2. 准备数据库（同 Koyeb 方案第 2 步，Aiven 免费 MySQL）。
3. 到 [render.com](https://render.com) 注册（可用 GitHub 登录）→ **New + → Web Service** → 连接 GitHub 仓库 → Language 选 **Docker**。
4. 在 **Environment Variables** 添加上述同样的 4 个变量。
5. 点 **Create Web Service**，等待首次构建。完成后 Render 会分配 `https://xxx.onrender.com` 公网网址。

## 五、部署到 Railway（备选，付费）

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
