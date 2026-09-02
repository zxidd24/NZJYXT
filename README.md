# 农资现货交易系统

本仓库按《开发手册2.md》和《农资现货交易系统功能清单.xlsx》建设，当前已完成第1阶段至第9阶段：

- 一、开发环境准备：项目目录骨架、环境变量模板、基础工具检查说明。
- 三、数据库设计：`agri_trading` 数据库、28 张核心表及初始化数据。
- 五、第1阶段：Spring Boot 多模块、MyBatis-Plus、Redis/JWT/AES、统一返回和两端 Vue 脚手架。
- 六、第2阶段：用户认证与权限体系。
- 七、第3阶段：商品管理闭环。已完成商品分类树、商品管理、图片上传、上架/下架、推荐位，以及商品上架和量价修改审核闭环。
- 八、第4阶段：订单交易闭环。已完成购物车、实名认证校验、库存扣减与回补、下单幂等令牌、钱包/银行模拟支付、销售+主管两级审核、发货登记、签章附件、客户回传单、电子凭证和待付款超时取消。
- 九、第5阶段：退款与评价。已完成退款申请、审核、钱包原路退款/银行线下退款登记、库存回补、退款通知，以及订单评价和商品/后台评价列表。
- 十、第6阶段：财务管理。已完成账户明细、日/月收支汇总、Excel 对账凭据导出、财务凭证链接、发票抬头维护和开票申请/审核、贷款申请/审核/放款/还款、钱包入金/出金/流水。
- 十一、第7阶段：资讯管理。已完成资讯栏目 CRUD、文章发布/编辑/删除、所有文章和我的发布、门户首页行业资讯、资讯详情及浏览量累加。
- 十二、第8阶段：数据统计看板 + 系统管理。已完成交易看板统计、动态审核流程配置、消息模板配置、业务事件消息渲染和门户消息中心。
- 十三、第9阶段：联调、测试与收尾。已完成核心 Service 单元测试、真实 MySQL/Redis 联调、接口冒烟、前端生产构建、本地 JAR/静态资源部署脚本和安全核查。

中行结算接口仍按开发手册列为后续专题，当前支付使用可替换的本地模拟模式。

## 第2阶段功能

- 后台：真实登录/退出/改密、Redis 单账号令牌、角色与权限配置、后台账户管理、动态权限菜单。
- 用户：门户注册登录、基本资料、实名认证、图片上传、认证状态、收货地址和修改密码。
- 管理：门户用户列表/详情、用户评级与授信、三级商品分类维护。
- 审核：实名认证待办/已办、按角色过滤、审核结果消息落库，形成门户提交到后台审核的闭环。
- 安全：后台与门户 JWT 隔离；退出、改密、停用账户后旧令牌失效；密码 BCrypt；身份证和银行卡 AES 加密存储并脱敏返回。

## 第3阶段功能

- 后台商品：`/api/admin/product` 提供商品新增、编辑、删除、分页查询、上下架和推荐位管理；修改价格/库存使用 `/price-stock` 重新提交审核。
- 商品审核：`/api/admin/task/audit-product` 按 `audit_record` 的 `biz_type=2`（上架）和 `biz_type=3`（量价）处理，审核结果写入站内消息并联动商品状态。
- 图片上传：`POST /api/admin/upload`，仅接受 JPG/JPEG/PNG/WEBP，单文件不超过 5MB，文件名使用 UUID。
- 门户商品：`/api/portal/product/recommend`、`/page`、`/{id}` 和 `/api/portal/category/tree`；H5 已提供首页推荐、分类筛选、关键词搜索和商品详情，首页/分类商品卡片及详情页均支持加入购物车（详情页可选择数量并校验库存）。
- 后台页面新增“商品管理”，任务管理支持商品审核详情和通过/驳回。

## 第4阶段功能

- 门户购物车：`/api/portal/cart` 支持增删改查、选择状态和库存校验；首页、分类页和商品详情页均提供加购入口，加入后可在 `/cart` 选择商品并下单支付。
- 门户订单：`/api/portal/order/token`、`/create`、`/pay`、`/page`、`/{id}`、`/cancel`、`/{id}/rebuy`、`/{id}/delivery`、`/{id}/receipt`、`/{id}/voucher`。
- 订单状态：待付款 → 待审核（销售）→ 待审核（主管）→ 待发货 → 待收货 → 已完成；取消、超时取消和审核驳回会在事务内回补库存。
- 支付：钱包支付扣减 `wallet_account` 并写 `wallet_transaction`；银行转账当前返回模拟支付成功，后续可替换为银行回调实现，订单号保持幂等。
- 后台订单：`/api/admin/order/page`、`/api/admin/task/audit-order`、`/api/admin/order/delivery`、`/confirm`、`/{id}/attachments`，按角色控制销售/主管审核节点。
- 定时任务：`ORDER_TIMEOUT_ENABLED=true` 时每分钟取消创建超过30分钟的待付款订单；阈值和间隔可通过 `ORDER_TIMEOUT_FIXED_DELAY_MS` 配置。

## 第5阶段功能

- 退款：门户 `POST /api/portal/refund/apply`、`GET /api/portal/refund/{orderId}`；后台 `GET /api/admin/refund/page`、`POST /api/admin/refund/audit`。钱包支付自动入钱包并记录退款流水，银行转账记录待线下原路退款；退款完成事务内回补库存。
- 评价：门户 `POST /api/portal/comment/add`、`GET /api/portal/comment/page`；后台 `GET /api/admin/comment/page`。仅已完成订单可评价，同一订单同一商品唯一评价。

## 第6阶段功能

- 对账：后台 `GET /api/admin/finance/account-detail`、`GET /api/admin/finance/income-expense`、`GET /api/admin/finance/statement`，以及 `GET /api/admin/finance/statement/export` 导出 Excel；流水支持挂接财务凭证链接。
- 发票：门户发票抬头增改删、开票申请和记录；后台开票申请列表、开票和驳回。
- 贷款：门户申请和授信额度查询；后台审核、确认放款、确认还款。贷款资金按受托支付口径只记录额度占用，不进入用户钱包；还款后额度恢复。
- 钱包：门户钱包余额、冻结金额、资金流水、入金和出金。支付、退款、入金、出金均写入 `wallet_transaction`。

## 第7阶段功能

- 后台栏目：`/api/admin/article-category` 提供资讯栏目增删改查。
- 后台文章：`POST /api/admin/article` 发布或编辑文章；`GET /api/admin/article/page` 查询所有文章；`GET /api/admin/article/mine` 按当前发布人查询；支持软删除、草稿和发布状态。
- 门户资讯：`GET /api/portal/article/list` 按栏目查询已发布资讯；`GET /api/portal/article/{id}` 查看详情并原子累加浏览量。门户首页已展示行业资讯入口。

## 第8阶段功能

- 数据看板：`GET /api/admin/dashboard` 返回累计/今日交易概览、年度月度交易、重点商品近30日成交均价、分类销售占比和近7日趋势，管理端使用 ECharts 展示。
- 流程管理：`GET/PUT /api/admin/flow`、`POST/DELETE /api/admin/flow-node` 动态维护六类审核流程、节点顺序和审核角色，后续审核任务运行时读取最新配置。
- 消息配置：`/api/admin/message-config` 提供消息模板增删改查和启停；认证、商品、订单、退款、贷款审核、发货及新待办事件按启用模板渲染写入消息记录。
- 门户消息中心：`GET /api/portal/message/page`、`PUT /api/portal/message/{id}/read`、`PUT /api/portal/message/read-all`、`GET /api/portal/message/unread-count`，个人中心显示未读角标。

## 项目结构

```text
backend/
├── agri-common/
├── agri-service/
├── agri-admin-api/
└── agri-portal-api/
frontend-admin/
frontend-portal/
database/
└── init.sql
```

## 环境准备

要求：JDK 17+、Maven 3.9+、Node.js 18+、MySQL 8+、Redis 7+、Git。

复制 `.env.example` 为本地环境文件并填写数据库、Redis、JWT 和 AES 配置。真实密码和密钥不得提交到 Git。

当前数据库初始化命令（示例）：

```bash
MYSQL_PWD="$DB_PASSWORD" mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" < database/init.sql
```

## 启动

后端启动前在当前终端设置本地环境变量（值只保存在终端，不要提交）：

```bash
export DB_PASSWORD='本机数据库密码'
export JWT_SECRET='至少32字节的本地密钥'
export AGRI_AES_KEY='本地AES密钥'
export ORDER_TIMEOUT_ENABLED='true'       # 开发环境启用待付款超时取消
cd backend
mvn clean install -DskipTests
mvn -pl agri-admin-api spring-boot:run   # 8080
mvn -pl agri-portal-api spring-boot:run  # 8081
```

接口文档地址：`http://localhost:8080/doc.html`、`http://localhost:8081/doc.html`。

前端分别执行 `npm install` 后启动：

```bash
cd frontend-admin && npm run dev   # 3000
cd frontend-portal && npm run dev  # 3001
```

## 数据库

`database/init.sql` 使用 `CREATE DATABASE IF NOT EXISTS` 和 `CREATE TABLE IF NOT EXISTS`，可重复执行。后台初始化账号为 `admin`，初始密码为 `123456`，首次登录后应立即修改。

门户认证图片默认保存在 `UPLOAD_PATH` 指定目录，开发环境默认为 `./uploads`。生产环境应使用持久化绝对路径，并将前端 `VITE_API_BASE_URL`、后台 `VITE_PORTAL_BASE_URL` 配置为实际服务地址。

## 验证

```bash
cd backend && mvn test
cd frontend-admin && npm run build
cd frontend-portal && npm run build
redis-cli ping
./scripts/smoke-test.sh
```

当前自动测试共 17 项，覆盖：初始密码 BCrypt 校验、JWT 后台/门户隔离、令牌撤销、AES 加解密与脱敏、商品分类三级限制、下单令牌与支付幂等、订单/钱包行锁、取消回补库存、电子凭证 PDF 结构、钱包余额原子变更、退款金额边界、已完成订单退款和贷款额度边界。

### P0 修复说明（2026-09-02）

- **超时取消事务**：订单超时扫描改为单事务执行，取消状态使用“读取时状态”条件更新，避免取消任务误伤刚完成支付的订单；库存回补失败会抛出异常触发事务回滚，不再静默吞掉异常。
- **并发支付安全**：支付使用数据库行锁读取订单和钱包账户，避免并发请求重复支付或写入错误的余额快照；钱包扣款仍使用余额条件更新防止透支。
- **后台订单分类分页**：分类筛选下推到 SQL 的 `EXISTS` 条件，在数据库分页后返回准确 `total`，不再以内存过滤造成分页失真。
- **电子凭证 PDF**：补全 PDF 交叉引用表和尾部索引，使用 `STSong-Light` + `UniGB-UCS2-H` 编码，支持中文订单信息显示。

P0 修复后的后端测试共 17 项全部通过；前端构建与接口冒烟仍按下方命令执行。

补充修复：退款申请现在允许已完成订单（仍要求订单已支付），与“完成→评价→退款”的业务流程一致；退款审核通过后按原支付渠道退款并回补库存。

## 第9阶段：联调、测试与收尾

### 测试与联调

- 后端：`cd backend && mvn test`，当前 17 项通过。
- 前端：`frontend-admin` 和 `frontend-portal` 均执行 `npm run build`，生产构建通过。
- 基础设施：`mysqladmin ping -h localhost -P 3306 -u root`、`redis-cli -h localhost -p 6379 ping` 已验证连通。
- 接口冒烟：`./scripts/smoke-test.sh` 验证未登录 401、管理员登录、看板查询和门户注册。
- 回归主链路：注册→实名认证→商品审核→下单支付→销售/主管审核→发货→客户回传→完成→评价→退款→开票→贷款；业务状态和越权校验由 Service 单测及接口鉴权共同覆盖。

### 真实全流程联调记录（2026-09-02）

使用本机 MySQL `localhost:3306`（数据库 `agri_trading`）和 Redis `localhost:6379` 启动服务，按真实 HTTP 请求完成以下链路并核验返回状态：

1. 门户注册、登录、实名认证提交；后台审核通过后认证状态为“已认证”。
2. 后台设置用户信用等级和授信额度，门户新增默认收货地址、钱包入金、加入购物车。
3. 获取下单令牌并创建订单，钱包支付成功；后台销售、主管两级审核均通过。
4. 后台登记物流，门户上传客户盖章回传单，后台确认收货，订单进入“已完成”。
5. 门户提交五星评价；提交已完成订单退款，后台审核通过，钱包收到原路退款且库存回补。
6. 门户维护发票抬头并申请开票，后台开票成功；门户申请贷款，后台审核、放款、还款完成。
7. 电子凭证下载结果通过 PDF 文件校验（`PDF 1.4`，单页）。

本次联调过程中发现并修复了“已完成订单无法申请退款”的状态判断缺陷，并增加对应回归测试。

### 本地部署

先准备一次本地环境文件（`.env` 已被 Git 忽略，真实密码和密钥不会写入仓库）：

```bash
cp .env.example .env
# 编辑 .env，至少填写 DB_PASSWORD、JWT_SECRET、AGRI_AES_KEY
```

也可以直接在当前终端导出环境变量：

```bash
export DB_HOST=localhost DB_PORT=3306 DB_NAME=agri_trading DB_USER=root
export DB_PASSWORD='本机数据库密码'
export REDIS_HOST=localhost REDIS_PORT=6379
export JWT_SECRET='至少32字节的本地密钥'
export AGRI_AES_KEY='本地AES密钥'
```

`local-deploy.sh` 和 `start-local.sh` 会自动读取项目根目录的 `.env`；使用 `.env` 后，每次启动无需重复 `export`。

执行打包和启动：

```bash
./scripts/local-deploy.sh   # Maven 打包两个后端、构建两个前端 dist
./scripts/start-local.sh    # API: 8080/8081，静态页: 3000/3001
./scripts/smoke-test.sh
./scripts/stop-local.sh
```

运行日志和 PID 保存在 `.local/`（已加入 `.gitignore`）。当前本地部署入口：

- 后台：`http://localhost:3000`，API 文档：`http://localhost:8080/doc.html`
- 门户：`http://localhost:3001`，API 文档：`http://localhost:8081/doc.html`

### 安全核查

- JWT 过期、后台/门户隔离、退出/改密撤销旧令牌：已由拦截器和单测核查。
- 配置无明文口令，生产密钥通过环境变量注入：已核查。
- 身份证/银行卡 AES 入库和脱敏返回：已核查并有单测。
- 上传扩展名、Content-Type、文件头和大小白名单，随机文件名及非执行目录：已核查。
- 门户资源按当前用户隔离，后台接口按角色权限拦截：已核查并通过接口冒烟。
