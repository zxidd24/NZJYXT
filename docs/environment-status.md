# 开发环境状态

检查时间：2026-08-26

| 工具 | 要求 | 当前状态 |
| --- | --- | --- |
| JDK | 17+ | 已安装，20.0.1 |
| Node.js | 18+ | 已安装，v24.9.0 |
| npm | 随 Node.js | 已安装，11.6.0 |
| MySQL | 8.0+ | 已安装并运行，9.3.0 |
| Git | 已安装 | 2.39.5 |
| Maven | 3.9+ | 已安装，3.9.16 |
| Redis | 7+ | 已安装并运行，`redis-cli ping` 返回 `PONG` |

本次已使用本机 MySQL `localhost:3306` 完成 `agri_trading` 初始化和重复执行校验。数据库账号密码仅用于本机命令行连接，未写入仓库；应用配置请复制 `.env.example` 后在本地填写。

建议后续安装完成后执行：

```bash
mvn --version
redis-server --version
```
