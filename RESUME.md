# 📄 PeakPoint 简历项目描述（秋招投递版）

> 用法：按简历空间三选一；每条 bullet 的追问应答见第二节；投递前先跑一次 JMeter 压测拿到真实量化数据（见第三节）。
> 原则：动作开头 + 机制与效果成对 + 每条都能被仓库代码支撑——面试官追问到任何一条，打开 ZHIDAO.md 对应章节就能画图讲透。

---

## 一、三版文案

### 版本 A：标准版（推荐主投版，6 条 bullet）

**PeakPoint（峰点）—— 高并发点评后端** ｜ 个人项目 ｜ 2026.XX – 至今
技术栈：Spring Boot、MyBatis-Plus、Redis、Redisson、RabbitMQ、MySQL、Lua

- **秒杀链路**：使用 Lua 脚本将「库存校验、一人一单、扣减、登记」合并为 Redis 端原子操作，从源头杜绝超卖与重复下单；引入 RabbitMQ 异步下单削峰，TTL 死信队列实现延迟重试兜底
- **消息可靠性**：消费端手动 ACK + 编程式事务，以「订单号幂等查重 + stock>0 条件更新」双重防线，保证消息不丢不重、数据库零超卖
- **缓存治理**：空值缓存解决缓存穿透；「逻辑过期 + 互斥锁 + 独立线程池异步重建」解决热点 key 击穿；数据更新遵循 Cache Aside 先库后删，保证最终一致性
- **分布式会话**：随机 Token + Redis Hash 替代 Session 实现无状态登录；双层拦截器分离「会话续期」与「登录校验」职责，全路径滑动续期 30 分钟
- **社交与签到**：点赞榜用 ZSet（score=点赞时间戳）实现手速榜、共同关注用 Set 交集一次算出；Feed 流采用推模式收件箱 + 时间戳游标滚动分页，解决新动态插入导致的分页错位；签到基于 BitMap + 位运算统计连续签到天数
- **基础组件**：自研全局唯一 ID 生成器（时间戳<<32 | Redis 按天自增序列，防泄露防溢出）、Redis GEO 附近商铺按距离检索、手写分布式锁（SETNX + Lua 比对线程标识原子释放，防误删他人锁）

### 版本 B：精简版（简历空间紧张，3 行）

基于 Redis 的高并发点评后端：Lua 原子化秒杀资格校验（防超卖/一人一单）+ RabbitMQ 异步落库（TTL 死信重试、事务内订单号幂等 + 条件更新三重防线）；空值缓存与「逻辑过期+互斥锁异步重建」分别解决缓存穿透/击穿，Cache Aside 先库后删保证一致性；Token+Redis Hash 无状态会话（双层拦截器滑动续期）、Feed 流推模式+游标分页、BitMap 签到、GEO 附近搜店、全局 ID 生成器。

### 版本 C：一句话电梯版（HR 面/自我介绍）

"一个把 Redis 高并发场景逐个落地的点评后端——秒杀、缓存、会话、Feed、签到，每个热点问题都有具名解法，全部源码带逐行意图级注释。"

---

## 二、追问弹药表（每条 bullet 的面试深挖点）

| 简历 bullet | 面试官必追问 | 应答要点 | 代码/文档出处 |
|---|---|---|---|
| Lua 原子校验 | 为什么 Java 写两步不行？ | 查库存与扣减之间存在并发窗口，先查再扣必超卖；Lua 整体在 Redis 单线程原子执行，判断与扣减间无法插队 | ZHIDAO §4.1；seckill.lua |
| 异步削峰 | 为什么不直接同步落库？ | 洪峰直接打库会拖垮 MySQL；资格校验（快）与落库（慢）解耦，DB 按消费节奏写 | VoucherOrderServiceImpl |
| 死信队列 | 消息怎么"死"的？死了去哪？ | 被拒绝且不回队 / 队列 TTL 10s 到期；经 Y 交换机转 QD 二次消费=延迟重试 | QueueConfig 三个 arguments |
| 幂等查重 | 为什么会重复消费？ | MQ 至少一次投递 + 消费超时重投；订单号是主键，事务内 getById 查重天然挡重 | SeckillVoucherListener#handleOrder |
| 条件更新 | 一条防线不够吗？ | Redis 层 Lua 已挡一次，DB 层 stock>0 是最后闸门——双保险覆盖"资格与库存不一致"的极端场景 | handleOrder 内 update 链 |
| 逻辑过期 | 未命中为什么不查库？旧数据能接受？ | 前提是热点已预热（saveShop2Redis）；返回旧数据换可用性，重建完成自动变新——CAP 取舍的落地 | CacheClient#queryWithLogicalExpire |
| 先库后删 | 顺序反了会怎样？ | 先删后更的空窗里并发读会把旧值写回缓存（脏数据复活）；先库后删只多"旧缓存一瞬" | ShopServiceImpl#update 注释块 |
| 双层拦截器 | 一层不行吗？ | 单层白名单路径（/shop/**）不会执行续期，登录态刷游客页会掉线；装载与拦截职责分离 | ZHIDAO §4.2；MvcConfig |
| Token 会话 | 比 JWT 好在哪？ | 可主动失效（删 key 即踢人）、可滑动续期；JWT 无状态但改密/封号无法即时生效 | ZHIDAO Q1 |
| 游标分页 | 页码分页哪里错了？ | 收件箱持续插入新数据，页码会重复/漏读；max（上次最小时间戳）+ offset（同分偏移）精确续读 | BlogServiceImpl#quertBlogOfFollow |
| 全局 ID | 为什么不用自增/UUID？ | 自增泄露销量且分布式冲突；UUID 无序致 B+ 树页分裂；时间戳+序列号趋势递增且不暴露信息 | RedisIdWorker |
| 手写锁 | 有什么局限？怎么升级？ | 不可重入、无自动续期；生产换 Redisson（看门狗+可重入）——项目已配置作演进对照 | SimpleRedisLock/RedissonConfig |

---

## 三、投递前必做：拿到你自己的量化数据（半天）

1. 启动应用（Redis/RabbitMQ/MySQL 就位，控制台出现"秒杀优惠券缓存预热完成"）
2. 新增一张秒杀券：`POST /voucher/seckill`（stock=100，起止时间覆盖当前）
3. JMeter 线程组：200 并发、Ramp-up 1s、每个线程带不同 token（登录 200 个手机号拿 200 个 token，写个循环脚本）
4. 对 `POST /voucher-order/seckill/{券id}` 压测后核对：
   - `tb_voucher_order` 表恰好 100 条、无重复 userId → **零超卖 + 一人一单成立**
   - 记录平均 RT / P99 / QPS
5. 把真实数字写进版本 A 第一条末尾：
   > "…TTL 死信队列实现延迟重试兜底（**JMeter 200 并发压测：零超卖、人均限 1 单、P99 < xx ms**）"

⚠️ 数字必须是你跑出来的——简历上每个字都可能被要求复现。

---

## 四、三条红线提醒

1. **定位写"个人项目"或"独立设计并实现"**，不写任何课程来源；也绝不虚构"公司生产环境"——个人项目在秋招完全正当，编造背景一追问就穿。
2. **每条 bullet 都有真实代码支撑**：75 个文件带逐行意图注释 + ZHIDAO 机制地图，这是你对比"背八股选手"的护城河——面试时主动提出"可以打开源码讲"。
3. **准备 30 秒架构图白板画**：秒杀链路 Controller→Lua→MQ→Listener 四站（ZHIDAO §4.1），被要求"画一下你的项目"时直接开画，效果拉满。

---

## 五、GitHub 仓库门面检查清单（投递前）

- [x] README.md（PeakPoint 品牌叙事 + 架构图 + 快速开始）
- [x] Description.md（About 一句话，中文 320 字符可直贴）
- [x] ZHIDAO.md / STUDY_CHECKLIST.md（展示工程化文档能力）
- [ ] `git commit` 固化当前版本（务必！当前改动未入库）
- [ ] 仓库描述栏填 Description.md 中文版；Topics 加 `redis` `rabbitmq` `spring-boot` `high-concurrency`
- [ ] （可选）录一张 JMeter 压测结果截图放进 README
