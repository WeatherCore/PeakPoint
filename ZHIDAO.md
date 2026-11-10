# 📖 黑马点评（hm-dianping）项目导读指南

> 本文件是 `黑马点评` 项目的中文导读，帮助你从零开始理解这个 **Redis 高并发实战后端** 的架构、代码和运行方式。
> 目标读者：第一次接触本项目的 Java 学习者 / 准备把它写进简历的求职者。

---

## 目录

- [1. 这个项目是干什么的？](#1-这个项目是干什么的)
- [2. 核心概念速览](#2-核心概念速览)
- [3. 项目目录结构详解](#3-项目目录结构详解)
- [4. 运行流程全景图](#4-运行流程全景图)
- [5. 逐文件代码导读](#5-逐文件代码导读)
- [6. 关键设计模式解析](#6-关键设计模式解析)
- [7. 配置系统详解](#7-配置系统详解)
- [8. 如何运行和测试](#8-如何运行和测试)
- [9. 复刻建议与学习路线](#9-复刻建议与学习路线)
- [10. 常见问题](#10-常见问题)
- [附录 A. 关键术语对照表](#附录-a-关键术语对照表)
- [附录 B. Redis Key 一览表](#附录-b-redis-key-一览表)

---

## 1. 这个项目是干什么的？

**一句话总结**：黑马点评是一个模仿大众点评的探店 App 后端——用户可以查店铺、发探店笔记（Blog）、点赞、关注、抢优惠券秒杀、签到领积分；它是一个**专门为练习 Redis 高并发场景而生的单体 Spring Boot 项目**。

```
用户请求 → Spring Boot(8081)
              │
              ├── MySQL        存"真相"：店铺/用户/订单/笔记（唯一持久层）
              ├── Redis        存"热点"：缓存/会话/秒杀资格/点赞/Feed/签到/GEO
              ├── RabbitMQ     存"排队"：秒杀订单异步落库 + 死信重试
              └── Lua 脚本     存"原子"：秒杀资格校验、分布式锁释放
```

**类比解释**：它不是一个"点评网站"，而是一个 **Redis 八股文的实体沙盘**——教科书里每一道缓存/并发难题（缓存穿透、缓存击穿、超卖、一人一单、分布式锁、Feed 流推送），在这里都有一段真实可跑的代码与之对应。读这个项目 = 沿着一条真实业务链路，把 Redis 核心用法逐个击破。

| 业务模块 | 解决的核心问题 | 一句话机制 |
|---|---|---|
| 短信登录 | Session 在分布式下不共享 | 随机 token + Redis Hash 存用户，双层拦截器刷新有效期 |
| 商铺缓存 | 缓存穿透 / 击穿 | 空值缓存挡穿透；逻辑过期 + 独立线程重建挡击穿 |
| 优惠券秒杀 | 超卖 / 一人一单 / 数据库打爆 | Lua 原子校验资格 → RabbitMQ 异步落库 → 死信队列兜底 |
| 点赞 | 高频点赞打库 | Redis ZSet 记点赞人 + 时间戳，排行榜直接取 Top5 |
| 共同关注 | 社交关系计算 | Redis Set 交集 SINTERSTORE 一次算完 |
| Feed 流 | 关注的人发笔记怎么看到 | 推模式：发笔记时写进每个粉丝的 ZSet 收件箱，滚动分页读 |
| 签到 | 每月 31 天存 31 个布尔值 | BitMap：一个 key 按天置位，位运算数连续签到 |
| 附近商铺 | 按距离排序查店 | Redis GEO 存经纬度，按坐标搜半径 5000m 内的店 |

**要点**：
- 这是单体项目（一个 Spring Boot 进程搞定一切），重点全在 **Redis 的使用姿势**，不在微服务拆分。
- 全项目只有 **73 个 Java 文件 / 3451 行代码**，"庞大幻觉"可以放下——两天足以通读，难的是理解每个 Redis 机制背后的并发问题。
- 秒杀链路（Lua + RabbitMQ + 死信队列）是全项目含金量最高的部分，也是面试最高频的考点。

---

## 2. 核心概念速览

读代码前必须先懂这 8 个概念。每个概念按"是什么 → 把它理解为… → 伪代码"展开。

### 2.1 缓存穿透（Cache Penetration）

- **是什么**：查询一个**数据库里也不存在**的数据（如 id=-1 的店铺），请求每次都穿透缓存打到数据库；恶意攻击时数据库会被打爆。
- **把它理解为**：便利店没有这种货，但每个顾客都非要进仓库翻一遍——仓库（数据库）被无效查询拖垮。
- **本项目的解法**：`CacheClient#queryWithPassThrough`（utils/CacheClient.java L42-70）——数据库也查不到时，往 Redis 写一个**空字符串**并给 2 分钟 TTL，下次请求命中"空值"直接返回，不再碰库。

### 2.2 缓存击穿（Cache Breakdown / 热点失效）

- **是什么**：一个**热点 key** 在过期瞬间，成千上万个请求同时发现缓存没了，全部涌向数据库重建缓存。
- **把它理解为**：演唱会门票开售瞬间，所有黄牛同时冲向售票窗口——窗口（数据库）瞬间挤爆。
- **本项目的解法**：`CacheClient#queryWithLogicalExpire`（L74-122）——缓存里存的数据**永不物理过期**，只是附带一个"逻辑过期时间"字段；发现逻辑过期后，只让**抢到互斥锁的那一个线程**开独立线程池去重建，其余线程先返回旧数据。

### 2.3 Lua 脚本原子性

- **是什么**：Redis 把整个 Lua 脚本当**一条命令**执行，执行期间不会插入其他命令——天然事务。
- **把它理解为**：把"查库存→查名单→扣库存→记名单"四步打包成一个不透光的黑盒，任何人都无法在黑盒执行到一半时插队。
- **本项目的用法**：`seckill.lua`（39 行）在 Redis 里一次性完成**库存判断 + 一人一单判断 + 扣库存 + 记下单人**，返回 0/1/2/-1 四种结果码。这正是秒杀"先判后扣"能防超卖的核心——判断和扣减之间不可能插入并发请求。

### 2.4 异步秒杀 + RabbitMQ 死信队列

- **是什么**：Lua 校验通过只代表"抢到资格"，真正的建单、扣数据库库存被扔进消息队列慢慢做——把同步的数据库写压力削峰成队列消费速度。
- **把它理解为**：抢票成功只给你出票凭证（返回订单号），出票大厅（数据库）按自己的节奏叫号出票；10 秒内没人取号的号牌（消息）会被扔进"死信箱"（死信队列）二次处理。
- **本项目的链路**：`VoucherOrderServiceImpl#seckillVoucher`（service/impl/VoucherOrderServiceImpl.java L78-120）发消息 → `QueueConfig`（config/QueueConfig.java）声明的 QA 队列（带 10s TTL + 死信路由）→ `SeckillVoucherListener#receivedA`（listener/SeckillVoucherListener.java L39-42）消费；QA 里滞留 10 秒的消息自动转入死信队列 QD，由 `receivedD`（L47-50）继续消费。

### 2.5 ZSet（Sorted Set，有序集合）

- **是什么**：每个成员带一个 score（分数），Redis 按分数排序存储，可以按分数区间取值。
- **把它理解为**：一张自动按时间排序的签到表——写名字时自动按签到时间排好，取"最早前 5 个"是 O(logN)。
- **本项目的两处用法**：
  - 点赞榜：`blog:liked:{笔记id}`，member=用户id，score=点赞时间戳 → `queryBlogLikes` 取 Top5（BlogServiceImpl.java L94-116）。
  - Feed 收件箱：`feed:{粉丝id}`，member=笔记id，score=笔记发布时间戳 → 滚动分页（L143-188）。

### 2.6 BitMap（位图）

- **是什么**：把 String 当位数组用，`SETBIT key offset 1` 把第 offset 位设为 1，一个 key 能存 2^32 个位。
- **把它理解为**：一张 31 格的打卡纸，每月一张纸（key=用户id+年月），第几天签到就把第几格涂黑；"连续签到了几天"= 从今天往回数连续涂黑的格子。
- **本项目的用法**：`UserServiceImpl#sign`（service/impl/UserServiceImpl.java L116-129）签到；`signCount`（L132-171）用 `BITFIELD` 取出本月的位图数字，`num & 1` + `num >>>= 1` 位运算数连续签到天数。

### 2.7 推模式 Feed 流 + 滚动分页

- **是什么**：用户发笔记时，**主动**把笔记 id 推进每个粉丝的收件箱（写扩散）；粉丝读收件箱时不能传统分页（新消息插入会让页码错位），要用"上次读到的最小时间戳 max + 同时间戳内的偏移 offset"做游标。
- **把它理解为**：报刊投递员把报纸塞进每家信箱（推），你读信箱时用夹在信堆里的书签（max/offset）记住读到哪，下次从书签处继续——插进来的新信不会打乱你的书签。
- **本项目的用法**：发笔记推送 `BlogServiceImpl#saveBlog`（L119-140）；滚动分页 `quertBlogOfFollow`（L143-188，注意接口名里就有个拼写错误 quert）。

### 2.8 ThreadLocal + 双层拦截器

- **是什么**：`ThreadLocal` 让每个线程独享一份变量；本项目的"当前登录用户"存在 ThreadLocal 里，拦截器负责往里放。
- **把它理解为**：每个服务员（Tomcat 线程）随身带一个记事本（ThreadLocal），记着"这桌客人是谁"；所有业务代码随取随用，不用传参。
- **本项目的双层设计**（这是面试加分点）：
  - 第一层 `RefreshTokenInterceptor`（interceptor/RefreshTokenInterceptor.java）：拦截**一切路径**（order=0），有 token 就刷新 Redis 有效期（30 分钟）并塞 ThreadLocal——即使游客路径也执行，保证"登录状态下刷多久就用多久"。
  - 第二层 `LoginInterceptor`（interceptor/LoginInterceptor.java）：只拦**需要登录的路径**（order=1），检查 ThreadLocal 有没有用户，没有就返回 401。
  - 为什么要分两层？如果只有一层且排除游客路径，游客路径就无法刷新 token 有效期。

**要点**：以上 8 个概念在代码里全部有落点文件，第 5 章逐文件导读时请带着概念回去对照。

---

## 3. 项目目录结构详解

```
黑马点评/
├── pom.xml                          # Maven 构建文件：Spring Boot 2.3.12 + MyBatis-Plus 3.4.3 + Redisson 3.13.6 + spring-boot-starter-amqp
└── src/main/
    ├── resources/
    │   ├── application.yaml         # 全局配置：8081 端口 / MySQL dingping 库 / Redis / RabbitMQ（手动 ACK、并发 5）
    │   ├── seckill.lua              # ⭐ 秒杀资格原子校验脚本（库存+一人一单），被 VoucherOrderServiceImpl 加载
    │   ├── unLock.lua               # ⭐ 分布式锁释放脚本（校验线程标识后删除），被 SimpleRedisLock 加载
    │   ├── db/hmdp.sql              # 建库脚本：11 张表（tb_shop / tb_user / tb_voucher / tb_voucher_order ...）
    │   └── mapper/VoucherMapper.xml # 手写 SQL：查店铺优惠券时 LEFT JOIN 秒杀表带出库存
    └── java/com/hmdp/
        ├── HmDianPingApplication.java   # 🚀 启动类：@MapperScan + @EnableRabbit + exposeProxy=true
        ├── config/                      # ⚙️ 配置层（5 个）
        │   ├── MvcConfig.java           #    注册双层拦截器并排好序（RefreshToken order=0 → Login order=1）
        │   ├── MybatisConfig.java       #    MyBatis-Plus 分页插件
        │   ├── QueueConfig.java         #    ⭐ RabbitMQ 拓扑：X 交换机→QA（10s TTL+死信路由）→Y 死信交换机→QD；JSON 消息转换器
        │   ├── RedissonConfig.java      #    Redisson 客户端（备用分布式锁方案）
        │   └── WebExceptionAdvice.java  #    全局异常兜底：RuntimeException → Result.fail("服务器异常")
        ├── controller/                  # 🚪 控制器层（9 个，前台总服务台，只收单转交不开回执逻辑）
        │   ├── UserController.java      #    /user/code 发码 · /user/login 登录 · /user/sign 签到 · /user/me 我的信息
        │   ├── ShopController.java      #    /shop/{id} 查店（走缓存）· /shop/of/type 类型分页（支持 GEO 坐标）
        │   ├── VoucherController.java   #    /voucher 新增普通券 · /voucher/seckill 新增秒杀券
        │   ├── VoucherOrderController.java  # /voucher-order/seckill/{id} ⭐ 秒杀下单入口
        │   ├── BlogController.java      #    /blog 发笔记 · /blog/like/{id} 点赞 · /blog/of/follow Feed 流
        │   ├── FollowController.java    #    /follow/{id}/{isFollow} 关注取关 · /follow/common/{id} 共同关注
        │   ├── ShopTypeController.java  #    /shop-type/list 店铺分类（Redis List 缓存）
        │   ├── UploadController.java    #    /upload/blog 图片上传（hash 分两级目录存本地）
        │   └── BlogCommentsController.java  # 评论控制器（空壳，预留）
        ├── service/                     # 🧠 业务层（接口 10 + 实现 10，业务经理排兵布阵）
        │   ├── impl/UserServiceImpl.java          # ⭐ 验证码/登录/注册/BitMap 签到
        │   ├── impl/ShopServiceImpl.java          # ⭐ 缓存查询/先库后删更新/GEO 附近商铺
        │   ├── impl/VoucherOrderServiceImpl.java  # ⭐⭐ 秒杀主流程：预热/Lua 校验/发 MQ（全项目最核心）
        │   ├── impl/BlogServiceImpl.java          # ⭐ 笔记/点赞 ZSet/Feed 推拉
        │   ├── impl/FollowServiceImpl.java        # ⭐ 关注 DB+Set 双写/共同关注交集
        │   ├── impl/VoucherServiceImpl.java       # 新增秒杀券时同步预热 Redis 库存
        │   ├── impl/ShopTypeServiceImpl.java      # 分类缓存（List 结构）
        │   └── ...                                # BlogComments / SeckillVoucher / UserInfo 空壳实现
        ├── mapper/                      # 💾 数据访问层（10 个 BaseMapper 接口，仓库管理员只管存取）
        ├── entity/                      # 📦 实体类（10 个 @TableName 对应表，PO）
        ├── dto/                         # 📨 传输对象（4 个：Result 统一响应 / UserDTO 脱敏用户 / LoginFormDTO / ScrollResult）
        ├── interceptor/                 # 🛂 拦截器（2 个，登录鉴权双层设计）
        ├── listener/
        │   └── SeckillVoucherListener.java  # ⭐⭐ MQ 消费者：订单落库+扣库存（事务+幂等）+ 死信兜底
        └── utils/                       # 🧰 工具层（11 个，公共底座）
            ├── CacheClient.java         # ⭐⭐ 缓存客户端：穿透/击穿/互斥锁三种查询模板
            ├── RedisConstants.java      #    全部 Redis Key 前缀与 TTL 常量（读代码先背熟这张表）
            ├── RedisIdWorker.java       #    全局 ID 生成器：时间戳<<32 | Redis 自增序列
            ├── SimpleRedisLock.java     #    手写分布式锁（setnx + Lua 释放）
            ├── ILock.java               #    锁接口
            ├── UserHolder.java          #    ThreadLocal 当前用户存取
            ├── RegexPatterns.java       #    手机号/邮箱/验证码正则
            ├── RegexUtils.java          #    正则校验工具
            ├── RedisData.java           #    逻辑过期包装器（expireTime + data）
            ├── PasswordEncoder.java     #    MD5+盐 加密（本项目暂未使用）
            └── SystemConstants.java     #    分页大小/上传目录/昵称前缀
```

**要点**：
- 分层是标准三层 `controller → service → mapper`，多出来的 `utils / interceptor / listener / config` 四个包才是这个项目的精华所在。
- `dto/Result.java` 是全项目被依赖最多的类（入度 23）：所有接口统一返回 `{success, errorMsg, data, total}` 四字段 JSON。

---

## 4. 运行流程全景图

### 4.1 秒杀下单全链路（最核心，值得背下来）

```
┌─────────────┐  POST /voucher-order/seckill/{id}
│   前端请求    │──────────────────────────────────────────────┐
└─────────────┘                                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│ VoucherOrderController#seckillVoucher        controller 层·收单转交   │
│  职责：解析 voucherId，调用 service；退出条件：返回 Result 即结束        │
└──────────────────────────────┬───────────────────────────────────────┘
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│ VoucherOrderServiceImpl#seckillVoucher       service 层·秒杀总指挥    │
│                                                                      │
│ ① checkTimeWindow    查 DB 校验秒杀时间窗（未开始/已结束直接 fail）    │
│ ② RedisIdWorker      生成全局唯一订单号（时间戳<<32|自增序列）          │
│ ③ 执行 seckill.lua   ── Redis 原子四连 ──┐                            │
│        库存判断 → 一人一单判断 →          │ 返回码:                    │
│        扣库存 decr → 记名单 sadd          │  0=通过 1=库存不足         │
│                                         │  2=重复下单 -1=库存key缺失 │
│ ④ r!=0 → 直接返回失败 Result            │（资格校验到此为止）          │
│ ⑤ r==0 → 组装 VoucherOrder{id,userId,  │                            │
│           voucherId}（尚未落库！）        │                            │
│ ⑥ rabbitTemplate.convertAndSend ────────┼──► 消息进 X 交换机(路由XA)  │
│ ⑦ 返回 Result.ok(orderId) 给前端         │（承诺异步出单）             │
│ 退出条件：返回 Result；MQ 发送失败抛异常   │                            │
└──────────────────────────────────────────┼───────────────────────────┘
                                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│ RabbitMQ 拓扑（QueueConfig 声明）                                      │
│                                                                      │
│  X(Direct) ──路由XA──► QA 普通队列                                    │
│                        │  x-message-ttl = 10s                        │
│                        │  x-dead-letter-exchange = Y                 │
│                        │  x-dead-letter-routing-key = YD             │
│                        ▼ 超过10s未被消费 → 死信                        │
│  Y(Direct) ──路由YD──► QD 死信队列                                    │
└──────────────────────────────┬───────────────────────────────────────┘
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│ SeckillVoucherListener#handleOrder           消费者·出票大厅          │
│                                                                      │
│ ① messageConverter.fromMessage  JSON → VoucherOrder 对象             │
│ ② transactionTemplate.execute ──【事务边界：落库+扣库存同生共死】      │
│     a. getById(orderId) 已存在？→ 幂等返回 true（跳过）               │
│     b. save(order)            订单落库                                │
│     c. update set stock=stock-1                                      │
│            where voucher_id=? and stock>0   ← 条件更新防超卖          │
│ ③ channel.basicAck(deliveryTag)  手动确认：消息真正出队               │
│ ④ 任意异常 → basicNack(requeue=false) 丢弃，防毒消息无限重投          │
│ 退出条件：ACK / NACK；线程模型：RabbitMQ 监听容器线程（并发5）          │
└──────────────────────────────────────────────────────────────────────┘
```

### 4.2 登录鉴权链路

```
请求进入 Tomcat
   │
   ▼
RefreshTokenInterceptor (order=0，拦 /** 一切路径)
   │ ① 取请求头 authorization（token）
   │ ② 有 token → 查 Redis Hash login:token:{token}
   │ ③ 查到用户 → 塞进 ThreadLocal(UserHolder) + 刷新 30min 有效期
   │ ④ 永远放行（它的职责只是"刷新+装载"，不是"拦截"）
   ▼
LoginInterceptor (order=1，只拦需登录路径，白名单见 MvcConfig)
   │ ① ThreadLocal 无用户 → 401 拦截
   │ ② 有用户 → 放行
   ▼
Controller → Service → Mapper（随时 UserHolder.getUser() 取当前用户）
   │
   ▼
请求结束 afterCompletion → UserHolder.removeUser() 清 ThreadLocal（防内存泄漏/线程复用串号）
```

### 4.3 商铺缓存查询链路

```
GET /shop/{id}
   ▼
ShopServiceImpl#queryById → CacheClient#queryWithLogicalExpire
   │
   │ ① Redis 取 cache:shop:{id} → 无数据？直接返回 null（逻辑过期方案
   │    要求热点数据提前预热，冷启动不查库——saveShop2Redis 负责预热）
   │ ② 反序列化 RedisData{expireTime, data(Shop)}
   │ ③ 未过逻辑过期 → 直接返回旧数据（最快路径）
   │ ④ 已过期 → tryLock(setnx lock:shop:{id}, 10s)
   │      ├─ 抢到锁 → 独立线程池(10线程)查库+写回逻辑过期缓存+释放锁；本线程立即返回旧数据
   │      └─ 没抢到 → 直接返回旧数据（别人正在重建）
   ▼
前端拿到数据（可能是旧数据，最终一致性换高可用）
```

**要点**：三张图对应三大高频面试题——"秒杀怎么做""登录态怎么管""缓存三兄弟怎么破"。第 5 章逐文件导读时按图索骥。

---

## 5. 逐文件代码导读

> 本章只标必读与跳过策略。**全量 73 文件 + 每文件打勾的精读清单见 [STUDY_CHECKLIST.md](STUDY_CHECKLIST.md)**（速览/主线/全量三条路径 + 自检题）。

### 5.1 阅读顺序建议（数据驱动）

骨架分析给出的快速上手 3 文件（quick_start_files，按依赖度自动计算）：

| 顺序 | 文件 | 角色 | 数据依据 |
|---|---|---|---|
| 1 | `service/impl/BlogServiceImpl.java` | 入口（出度 13，顶层不被依赖） | 依赖最多其他类，是业务编排的典型样本 |
| 2 | `dto/Result.java` | 中间（入度 23，全项目被依赖最多） | 读懂它=读懂所有接口的返回契约 |
| 3 | `utils/RedisConstants.java` | 补充（入度 10） | 所有 Redis 操作的 key 都从这里出 |

**语义补充**（算法无法建 Controller→Service 边，实际请求入口是 Controller 层）：建议把首读入口定为 `VoucherOrderController.java`（24 行）→ `VoucherOrderServiceImpl.java`（140 行），因为秒杀链路最能代表本项目的技术含量。三者的关系：Controller 是前台收单员，ServiceImpl 是后厨总管，Result/RedisConstants 是全店的"营业用纸"和"货架编号表"。

### 5.2 必读文件清单（核心 13 个，按阅读顺序）

#### ① `utils/RedisConstants.java`（26 行，约 5 分钟）

- **文件作用**：全项目 Redis key 的"地址簿"——14 个 key 前缀常量 + TTL 常量。
- **关键数据结构**：纯常量表，无逻辑。
- **要点**：读任何 Service 前先扫一遍这张表，之后看到 `SECKILL_STOCK_KEY + voucherId` 立刻知道是在操作 `seckill:stock:{id}`。完整 key 表见附录 B。

#### ② `dto/Result.java`（31 行，约 5 分钟）

- **文件作用**：统一响应封装，全部接口的返回契约。
- **关键数据结构**：`success / errorMsg / data / total` 四字段；4 个静态工厂 `ok()/ok(data)/ok(list,total)/fail(msg)`。
- **要点**：前端只认 `success` 布尔值判断成败；`fail` 只传错误消息，HTTP 状态码恒为 200（除拦截器 401）。

#### ③ `interceptor/RefreshTokenInterceptor.java`（61 行，约 15 分钟）

- **文件作用**：第一层拦截器——token 校验、ThreadLocal 装载、Redis 会话续期。
- **阅读顺序**：先读构造器（注入 RedisTemplate，因为它不是 Spring Bean，只能构造器传参）→ `preHandle` 逐行 → `afterCompletion` 清理。
- **要点**：无论 token 有没有，**永远 return true**——真正的拦截在第二层；`afterCompletion` 里 `UserHolder.removeUser()` 是防 Tomcat 线程复用串号的关键一笔。

#### ④ `interceptor/LoginInterceptor.java`（23 行，约 5 分钟）

- **文件作用**：第二层拦截器——只做"ThreadLocal 有没有用户"的守门判断。
- **要点**：为什么它不需要 RedisTemplate？因为它信任第一层已经装载好了 ThreadLocal。

#### ⑤ `config/MvcConfig.java`（35 行，约 5 分钟）

- **文件作用**：把两个拦截器注册进 Spring MVC 并排好执行顺序。
- **要点**：`order(0)` 第一层先执行、`order(1)` 第二层后执行；`excludePathPatterns` 的白名单（/user/login、/shop/** 等）决定了哪些路径游客可访问。

#### ⑥ `service/impl/UserServiceImpl.java`（183 行，约 30 分钟）

- **文件作用**：短信验证码、登录注册、BitMap 签到三合一。
- **关键方法**：
  | 方法 | 机制 | 行号 |
  |---|---|---|
  | `sendCode` | 6 位随机码存 Redis 2 分钟（key: login:code:{phone}） | L49-65 |
  | `login` | 校验码 → 查用户（不存在则注册）→ 随机 token → User 转 Hash 写 Redis 30min | L75-113 |
  | `sign` | `SETBIT sign:{userId}:{yyyyMM} {day-1} 1` | L116-129 |
  | `signCount` | `BITFIELD` 取位图 → `(num&1)` + `num>>>=1` 位运算数连续签到 | L132-171 |
- **要点**：`login` 里 `BeanUtil.beanToMap` 把 UserDTO 转成 Map 才能写 Redis Hash；存 Hash 而不是整个 JSON 字符串，是为了前台可以单独改某个字段（虽然本项目还没用到这个能力）。

#### ⑦ `utils/CacheClient.java`（141 行，约 30 分钟）

- **文件作用**：缓存工具箱——穿透、击穿（互斥锁版）、击穿（逻辑过期版）三种查询模板 + 一个 10 线程重建线程池。
- **阅读顺序**：`set` → `setWithLogicalExpire` → `queryWithPassThrough` → `queryWithLogicalExpire` → `tryLock/unLock`。
- **要点**：泛型 `<R,ID>` + `Function<ID,R> dbFallback` 回调是"模板方法"思想的 Java 函数式写法——调用方只管传"怎么查库"，缓存套路由本类统一封装；`queryWithLogicalExpire` 缓存未命中时**返回 null 而不是查库**（前提：热点数据已预热，见 ShopServiceImpl#saveShop2Redis）。

#### ⑧ `service/impl/ShopServiceImpl.java`（124 行，约 20 分钟）

- **文件作用**：商铺查询（走逻辑过期缓存）、更新（先库后删）、分类分页（数据库版/GEO 版双路径）。
- **关键方法**：`queryById` L41-49 / `saveShop2Redis` L51-60（预热入口）/ `update` L62-74（`@Transactional` + 删缓存）/ `queryShopByType` L77-123（GEO 半径 5000m + `order by field` 保序回查）。
- **要点**：`update` 先改库再删缓存（Cache Aside 标准姿势）；GEO 查询结果里的 shopId 顺序就是距离排序，用 MySQL `order by field(id,...)` 保序拿回完整店铺并回填距离。

#### ⑨ `utils/RedisIdWorker.java`（37 行，约 10 分钟）

- **文件作用**：全局唯一 ID 生成器——`符号位1 + 时间戳31位 + 序列号32位`。
- **要点**：时间戳以 2022-01-01 为起点（`BEGIN_TIMESTAMP=1640995200L`），可用 69 年；序列号用 Redis `INCR` 按天分 key（`icr:order:{yyyy:MM:dd}`），既防溢出又方便按天统计订单量。

#### ⑩ `service/impl/VoucherOrderServiceImpl.java`（140 行，约 40 分钟，全项目最核心）

- **文件作用**：秒杀总指挥——启动预热、Lua 原子校验、MQ 异步下单。
- **阅读顺序**：`SECKILL_SCRIPT` 静态块（L49-54）→ `preloadSeckillVouchers`（@PostConstruct 预热，L60-76）→ `seckillVoucher` 主流程（L78-120）→ `checkTimeWindow`（L126-139）。
- **要点**：预热用 `setIfAbsent`（只在 key 不存在时写入），防止应用重启覆盖 Redis 中已扣减的实时库存；Lua 返回 0/1/2/-1 四个码在 Java 侧翻译成不同错误消息；MQ 发送失败直接抛异常回滚整个"资格"（因为 Redis 库存已被 Lua 扣了，这里是个已知取舍点，见第 10 章 Q4）。

#### ⑪ `config/QueueConfig.java`（116 行，约 20 分钟）

- **文件作用**：RabbitMQ 全部拓扑的声明——两个交换机、两个队列、两条绑定、一个 JSON 消息转换器。
- **关键数据结构**：QA 队列的三个 arguments（死信交换机 Y / 死信路由键 YD / TTL 10s）就是"延迟重试"的全部机关。
- **要点**：`messageConverter` 注册了 JavaTimeModule，让 LocalDateTime 能正确 JSON 序列化——没有它，订单消息在 MQ 里就是一坨解析不了的数字。

#### ⑫ `listener/SeckillVoucherListener.java`（94 行，约 25 分钟）

- **文件作用**：MQ 消费端——订单真正落库、库存真正扣减的地方。
- **关键方法**：`receivedA`（正常队列）与 `receivedD`（死信队列）共用 `handleOrder`；`handleOrder` 内 `transactionTemplate.execute` 把"幂等查询+落库+条件扣库存"包进同一事务。
- **要点**：三个面试高频点——①编程式事务（TransactionTemplate）因为 @Transactional 在监听器里需代理且此处想精确控制边界；②幂等靠"订单 ID 主键查重"；③条件更新 `stock>0` 是数据库层防超卖的最后防线（双保险：Lua 已挡一次，这里再挡一次）。

#### ⑬ `resources/seckill.lua`（39 行，约 15 分钟）

- **文件作用**：秒杀资格的原子裁判——库存、一人一单、扣减、记名四步一气呵成。
- **要点**：返回码契约：0=通过、1=库存不足、2=重复下单、-1=库存 key 不存在（预热失败）；第 37 行的 `xadd stream.orders` 是黑马原版（Redis Stream 方案）的残留，本项目走 RabbitMQ，这条指令会写一条没人消费的 Stream 消息（见第 10 章 Q3）。

### 5.3 选读文件（理解主流程后按需读）

| 文件 | 行数 | 内容 | 读法 |
|---|---|---|---|
| `service/impl/BlogServiceImpl.java` | 252 | 点赞 ZSet / Feed 推拉 / 滚动分页 | 精读 `updateLike`、`saveBlog`、`quertBlogOfFollow` 三方法 |
| `service/impl/FollowServiceImpl.java` | 96 | 关注双写（DB+Set）/ 共同关注交集 | 读 `follow`、`followCommons` |
| `utils/SimpleRedisLock.java` | 67 | 手写分布式锁 + Lua 释放 | 读 `tryLock`/`delLock`，注意 ID_PREFIX 防误删 |
| `config/RedissonConfig.java` | 25 | Redisson 客户端声明（本项目未实际使用其锁） | 1 分钟 |
| `controller/UploadController.java` | 74 | 图片上传 + hash 两级目录 + 防路径穿越删除 | 读 `createNewFileName`、`deleteBlogImg` |
| `service/impl/ShopTypeServiceImpl.java` | 64 | 分类 List 缓存 | 读 `querySort`，注意代码尾部有被注释掉的旧版实现 |
| `controller/*` 其余 | 13-113 | 各入口收单转交 | 读类注释+URL 映射即可 |
| `service/impl/VoucherServiceImpl.java` | 53 | 新增秒杀券双表落库+Redis 预热 | 10 分钟 |

### 5.4 薄文件（读类注释一句话即可）

- `entity/*` 10 个：纯 PO，`@TableName` 对应表，字段即列；注意 `Blog.icon/name/isLike`、`Shop.distance`、`Voucher.stock/beginTime/endTime` 带 `@TableField(exist=false)`——数据库没有这些列，是后端拼装给前端的"展示字段"。
- `dto/*` 4 个：UserDTO 是脱敏版用户（只有 id/nickName/icon，不存 Redis 敏感字段）；ScrollResult 是 Feed 滚动分页的返回壳（list/minTime/offset）。
- `mapper/*` 10 个：9 个空接口继承 BaseMapper 白嫖 MyBatis-Plus CRUD；唯一下手的是 `VoucherMapper#queryVoucherOfShop`，SQL 在 `resources/mapper/VoucherMapper.xml`（LEFT JOIN 秒杀表）。
- `service/I*Service` 10 个：接口即说明书，读方法签名即可。
- 空壳实现 3 个：BlogCommentsServiceImpl / SeckillVoucherServiceImpl / UserInfoServiceImpl。

**要点**：必读 13 个 ≈ 3 小时；选读 8 个 ≈ 2.5 小时；薄文件 1 小时内。全项目通读一天可完成，但每一遍都要问自己"这里的并发问题是什么"。

---

## 6. 关键设计模式解析

### 6.1 模板方法 + 函数式回调（CacheClient）

```
        调用方（ShopServiceImpl）
              │ 传入 dbFallback = this::getById
              ▼
┌─────────────────────────────────────┐
│ CacheClient（缓存套路模板）           │
│  查缓存 → 命中返回                   │
│  未命中 → dbFallback.apply(id) ←──── │── 这一步是"坑"，由调用方填
│  空值缓存 / 互斥锁 / 逻辑过期 套路    │
└─────────────────────────────────────┘
```

- **意图**：缓存套路固定（判空→回源→写回），回源方式千变万化（查店铺/查分类），用 `Function<ID,R>` 把变化点抽出去。
- **代码落点**：`CacheClient.java` L42 泛型签名；调用方 `ShopServiceImpl.java` L43-44。

### 6.2 责任链（双层拦截器）

```
请求 → [RefreshTokenInterceptor order=0] → [LoginInterceptor order=1] → Controller
         职责：装载+续期（永远放行）        职责：守门（无用户 401）
```

- **意图**：把"所有请求都要做的事（续期）"和"只有登录路径要做的事（拦截）"拆开，互不污染。
- **代码落点**：`MvcConfig.java` L18-33。

### 6.3 生产者-消费者 + 死信兜底（异步秒杀）

```
生产者 VoucherOrderServiceImpl ──消息──► [X] ──XA──► [QA](TTL 10s) ──超时──► [Y] ──YD──► [QD]
                                                                │                  │
                                              消费者 receivedA ◄┘   消费者 receivedD ◄┘
                                                        └────────┬────────┘
                                                                 ▼
                                              handleOrder（同一份落库逻辑，幂等兜底）
```

- **意图**：资格校验（快，Redis）与订单落库（慢，MySQL）解耦；QA 滞留 10s 的消息自动降级进死信队列二次消费——相当于自带一层"延迟重试"。
- **代码落点**：`QueueConfig.java` L52-66（QA 的三个 arguments）；`SeckillVoucherListener.java` L39-50。

### 6.4 装饰器思想（RedisData 逻辑过期包装）

- **意图**：不侵入业务实体（Shop），用一个外壳对象包住它并附加 `expireTime` 字段——数据"永不过期"，过期与否由外壳字段说了算。
- **代码落点**：`utils/RedisData.java`（14 行）；写入 `CacheClient#setWithLogicalExpire`；读出 `CacheClient#queryWithLogicalExpire` L87-89 两段式反序列化。

### 6.5 单例线程池异步重建（击穿方案）

- **意图**：重建缓存是慢操作，绝不能占着用户请求线程干——`CACHE_REBUILD_EXECUTOR`（10 线程）专职重建，抢到互斥锁的请求线程立刻返回旧数据。
- **代码落点**：`CacheClient.java` L73（线程池）、L101-117（抢锁→提交任务→返回旧值）。

**要点**：五个模式没有一个是"为了设计而设计"，每个都是被一个具体并发问题逼出来的——这也是面试时讲项目的正确姿势：先讲问题，再讲方案，最后讲代码落点。

---

## 7. 配置系统详解

### 7.1 application.yaml 关键配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | 8081 | 后端服务端口，前端 nginx 反代指向它 |
| `spring.datasource.url` | jdbc:mysql://127.0.0.1:3306/dingping | 数据库名必须是 `dingping`（不是 hmdp） |
| `spring.datasource.username/password` | root / 290390 | 改成你自己的 |
| `spring.redis.host/port` | 127.0.0.1:6379 | 本机 Redis |
| `spring.redis.lettuce.pool.max-active` | 30 | Lettuce 连接池上限，秒杀压测时别太小 |
| `spring.rabbitmq.host/port` | localhost:5672 | 本机 RabbitMQ，账号密码 guest/guest |
| `spring.rabbitmq.listener.simple.acknowledge-mode` | **manual** | ⭐ 手动确认——是 `channel.basicAck` 能生效的前提 |
| `spring.rabbitmq.listener.simple.concurrency` | 5 | 消费者并发线程数 |
| `spring.rabbitmq.listener.simple.prefetch` | 10 | 每个消费者预取消息数，削峰阀门 |
| `spring.jackson.default-property-inclusion` | non_null | JSON 序列化忽略 null 字段（响应瘦身） |
| `mybatis-plus.type-aliases-package` | com.hmdp.entity | 实类别名扫描 |
| `logging.level.com.hmdp` | debug | 业务包日志级别 |

### 7.2 代码内"配置"（常量类）

| 常量类 | 内容 | 改动影响 |
|---|---|---|
| `RedisConstants` | 14 个 key 前缀 + 各 TTL（验证码 2min、缓存空值 2min、店铺缓存 30min、锁 10s） | 改 key 前缀会导致旧缓存全部失配 |
| `SystemConstants` | 分页 5/10、图片上传目录（写死 D 盘 nginx 路径）、昵称前缀 user_ | 上传目录按你机器改 |
| `QueueConfig` | 交换机 X/Y、队列 QA/QD、路由键 XA/YD、TTL 10000ms | 与消费者注解 `@RabbitListener(queues=...)` 强耦合 |
| `RedissonConfig` | Redisson 连接 127.0.0.1:6379（连接池 4，最小空闲 1） | Redisson 在本项目是"备胎"，未实际使用其锁 |

### 7.3 加载优先级

Spring Boot 标准优先级：命令行参数 > 环境变量 > application.yaml。本项目只用 yaml 一处配置，无 profile 区分。

---

## 8. 如何运行和测试

### 8.1 环境准备

| 组件 | 版本要求 | 默认地址 | 用途 |
|---|---|---|---|
| JDK | 1.8 | — | 编译运行 |
| Maven | 3.6+ | — | 构建 |
| MySQL | 8.0 | 127.0.0.1:3306 | 持久层 |
| Redis | 6.x（需支持 GEO/BitMap，5.0+ 即可） | 127.0.0.1:6379 | 缓存/会话/秒杀 |
| RabbitMQ | 3.8+（任意带管理插件的版本） | localhost:5672 / 管理台 15672 | 异步秒杀 |

### 8.2 初始化（命令 100% 可复制）

```bash
# 1. 建库建表 + 灌测试数据（脚本内含 CREATE DATABASE）
mysql -uroot -p你的密码 < src/main/resources/db/hmdp.sql

# 2. Redis、RabbitMQ 起起来之后（Windows 服务或 docker 均可）
#    docker 一把梭示例：
docker run -d --name redis -p 6379:6379 redis:6.2
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.8-management
```

### 8.3 修改配置

打开 `src/main/resources/application.yaml`：把 MySQL 密码 `290390` 改成你的；RabbitMQ 默认 guest/guest 本机不用改。

### 8.4 编译并启动

```bash
# 编译打包（跳过测试，测试类连库可跑但非必需）
mvn clean package -DskipTests

# 启动（方式一：命令行）
java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar

# 启动（方式二：IDE 里直接跑 HmDianPingApplication#main）
```

启动成功的标志（看控制台）：`秒杀优惠券缓存预热完成，共 N 个，实际写入 M 个`。

### 8.5 体验核心链路（按编号调用真实接口）

本项目原本配套 nginx 静态前端（`nginx-1.18.0 dianping`），后端接口也可用 curl/Postman 直接打：

```bash
# ① 发验证码（验证码会打印在后端日志里，本项目没接真实短信）
curl -X POST "http://localhost:8081/user/code?phone=13800138000"

# ② 登录（用上一步日志里的验证码），响应 data 就是 token
curl -X POST "http://localhost:8081/user/login" \
     -H "Content-Type: application/json" \
     -d '{"phone":"13800138000","code":"123456"}'

# ③ 带 token 查店铺详情（缓存链路）
curl "http://localhost:8081/shop/1" -H "authorization: 上一步的token"

# ④ 新增秒杀券（管理员姿态，先查库拿 shopId）
curl -X POST "http://localhost:8081/voucher/seckill" \
     -H "Content-Type: application/json" \
     -d '{"shopId":1,"title":"测试券","subTitle":"2折","rules":"随时退","payValue":100,"actualValue":20,"type":1,"stock":100,"beginTime":"2026-01-01T00:00:00","endTime":"2026-12-31T23:59:59"}'

# ⑤ 秒杀下单（核心链路！连打两次第二次会返回"不能重复下单"）
curl -X POST "http://localhost:8081/voucher-order/seckill/刚返回的券id" \
     -H "authorization: 上一步的token"

# ⑥ 点赞 / 查点赞榜
curl -X PUT "http://localhost:8081/blog/like/1" -H "authorization: 上一步的token"
curl "http://localhost:8081/blog/likes/1" -H "authorization: 上一步的token"

# ⑦ 签到 + 连续签到统计（BitMap）
curl -X POST "http://localhost:8081/user/sign" -H "authorization: 上一步的token"
curl "http://localhost:8081/user/sign/count" -H "authorization: 上一步的token"
```

压测建议：用 JMeter 对 ⑤ 开 200 并发、100 张券，验证**恰好卖出 100 张、每人最多 1 单**——这是本项目最有说服力的实验。

---

## 9. 复刻建议与学习路线

### 9.1 阶段路线（每阶段：做什么 + 耗时估计）

| 阶段 | 做什么 | 产出 | 耗时 |
|---|---|---|---|
| 阶段 1：跑起来 | 环境四件套就位 → 建库 → 改配置 → 启动成功 | 看到预热日志、接口能 curl 通 | 半天 |
| 阶段 2：通读必读 13 件 | 按 5.2 顺序精读，每个机制对照第 2 章概念 | 心里有整张地图 | 1-2 天 |
| 阶段 3：秒杀链路深度实验 | JMeter 压测超卖/一人一单；手动把 RabbitMQ 停掉观察降级；把 prefetch 改 1 观察吞吐 | 实验记录（写进简历的素材！） | 1 天 |
| 阶段 4：复刻改造 | 从零新建工程抄一遍核心 5 类（CacheClient/RedisIdWorker/SimpleRedisLock/VoucherOrderServiceImpl/SeckillVoucherListener），不看原码默写 | 自己的项目 | 2-3 天 |
| 阶段 5：进阶扩展（加分项） | ①把 SimpleRedisLock 换成 Redisson 看门狗；②把 QA 的 TTL 死信换成延迟插件；③补评论功能（BlogComments 空壳已预留） | 面试可讲的"我改进过" | 2-3 天 |

### 9.2 学习资源

| 资源 | 用途 |
|---|---|
| 黑马程序员 Redis 实战课（B 站免费） | 本项目的原始教学配套，遇到读不懂的设计去看对应课时 |
| Redis 官方文档（redis.io） | SETBIT/BITFIELD/GEO/ZSET 命令细节 |
| RabbitMQ 官方教程（TTL + Dead Letter） | 死信队列机制 |
| MyBatis-Plus 官方文档 | BaseMapper/IService/条件构造器速查 |

### 9.3 面试叙事模板（简历项目怎么讲）

1. **先抛问题**："秒杀场景同时面临超卖、一人一单、数据库被打爆三个问题。"
2. **再讲方案分层**："资格校验下沉到 Redis 用 Lua 保证原子；落库削峰进 RabbitMQ；死信队列做延迟重试兜底；消费端用事务 + 订单号幂等 + `stock>0` 条件更新三重防线。"
3. **最后讲证据**："200 并发压测 100 库存，零超卖、零重复单。"
4. 每个模块都按这个"问题→方案→证据"结构讲，全项目可以撑起 20 分钟深挖。

---

## 10. 常见问题

**Q1：为什么登录用 token + Redis 而不是 Session/JWT？**
A：Session 存在单台 Tomcat 内存里，多实例部署时用户在 A 登录、请求落到 B 就掉了；JWT 无状态但无法主动失效（改密码/踢人做不到）。Redis 方案兼得两者：多实例共享（key 都在 Redis），且删 key 即可强制下线，还能滑动续期。

**Q2：`RefreshTokenInterceptor` 为什么拦一切路径还永远放行？**
A：它的职责不是拦人，是"顺便续期"。只要用户带着有效 token 刷了游客页面，会话也续 30 分钟；如果只让登录拦截器做这事，白名单路径就续不了。拆成两层是这个项目最值得学的工程细节之一。

**Q3：`seckill.lua` 第 37 行的 `xadd stream.orders` 是干嘛的？为什么本项目没人消费它？**
A：这是黑马原版方案的残留——原版用 Redis Stream 做消息队列，Java 侧有个线程轮询 `XREADGROUP`。本项目把消息队列换成了 RabbitMQ，但 Lua 里的 XADD 没删。**后果**：每次秒杀会往 `stream.orders` 写一条永远没人读的消息，Redis 内存缓慢增长；**处理建议**：学习时知晓即可，生产化时应删除该行（或删掉 RabbitMQ 用回 Stream，二选一）。`[注意]` 已在源码注释标注。

**Q4：Lua 扣了 Redis 库存，但 MQ 消息发送失败/消费失败，库存岂不是少了？**
A：是的，这是一个已知取舍点。原版用"事务消息/本地消息表"解；本项目的兜底是：发送失败直接抛异常（用户看到失败，不再继续）；消费端死信队列保证最终消费。更严格的做法是把"扣 Redis + 发 MQ"放进 Lua 或用 Redis Stream 事务消息——这正好可以作为你的"改进计划"在面试里讲。

**Q5：逻辑过期方案为什么缓存未命中直接返回 null，不查数据库？**
A：逻辑过期的前提是"热点数据**提前预热**"（`saveShop2Redis`），正常运行时 key 永远存在。未命中意味着这个数据本来就不是热点，按穿透模板（`queryWithPassThrough`）走就行——两种策略服务两种数据形态，别混用。

**Q6：`SimpleRedisLock` 已经有了，为什么还配 Redisson？**
A：SimpleRedisLock 是教学版——手写 setnx + Lua 释放，展示锁的原理（线程标识防误删、原子释放防误删）；但它不可重入、不能自动续期（业务超时锁先过期就两个线程同时持锁）。Redisson 是生产版：看门狗自动续期、可重入、RedLock。本项目 Redisson 只注册未使用，属于"备胎"，你可以作为改造练习把它真正用起来。

**Q7：`unlock.lua` 和 `unLock.lua` 文件名大小写怎么回事？**
A：`SimpleRedisLock.java` L26 引用的是 `unlock.lua`（小写 l），磁盘上的文件名是 `unLock.lua`（大写 L）。Windows 文件系统大小写不敏感能跑，**部署到 Linux 会 ClassPathResource 找不到文件**。建议把文件改名为 `unlock.lua` 与代码一致（本项目未做修改，只在注释里 `[注意]` 标注）。

**Q8：为什么消费端用 `TransactionTemplate` 编程式事务而不是 `@Transactional`？**
A：`@Transactional` 靠 Spring AOP 代理生效，在 `private` 方法或同类内部调用（this.handleOrder）时代理不生效；MQ 监听方法参数是框架反射调用的，编程式事务能精确圈定"幂等查询+落库+扣库存"这个边界，且不依赖代理注入——更直白更稳。

**Q9：`order by field(id,...)` 是什么黑魔法？**
A：MySQL 的 `FIELD()` 函数返回元素在列表中的位置，`ORDER BY FIELD(id,5,1)` 就是按"5 在前 1 在后"的指定顺序排。因为 Redis 返回的 id 顺序（点赞榜按分数、GEO 按距离）在 `WHERE id IN (...)` 回查时会被 MySQL 打乱，必须用它保序。

**Q10：接口名 `quertBlogOfFollow` 拼写错误（quert→query），为什么不改？**
A：这是教学项目常见现象，改名会牵动 Controller/接口/实现三处。作为学习者建议保持原样以便对照视频课，写简历项目时可以顺手改成规范拼写并在提交说明里注明。

---

## 附录 A. 关键术语对照表

| 英文 | 中文 | 一句话说明 |
|---|---|---|
| Cache Penetration | 缓存穿透 | 查不存在的数据，缓存永远不命中，库被打爆 |
| Cache Breakdown | 缓存击穿 | 热点 key 过期瞬间并发全部打到库 |
| Cache Avalanche | 缓存雪崩 | 大量 key 同时过期（本项目未重点涉及） |
| Logical Expiration | 逻辑过期 | 数据不过期，用字段判断新旧，异步重建 |
| Mutual Exclusion Lock | 互斥锁 | 同一时刻只允许一个线程干活（setnx） |
| Idempotency | 幂等 | 同一操作执行多次结果不变（订单号查重） |
| Dead Letter Queue | 死信队列 | 消费失败/过期消息的"收容所"，可做延迟重试 |
| Manual Acknowledge | 手动确认 | 消费成功才 ACK，失败可 NACK，消息不丢 |
| ThreadLocal | 线程局部变量 | 每线程一份的变量，本项目的"当前用户"载体 |
| BitMap | 位图 | 用 bit 存布尔序列，签到场景月省 30 倍空间 |
| GEO | 地理位置索引 | Redis 存经纬度并支持按距离检索 |
| Feed Stream | 内容流 | 关注的人的动态聚合（推模式/拉模式） |
| Scroll Pagination | 滚动分页 | 用游标（max/offset）代替页码，防新数据错位 |
| Cache Aside | 旁路缓存 | 先更新库再删缓存的经典模式 |
| PO / DTO / VO | 实体/传输/视图对象 | 表映射 / 进程间传输 / 前端展示三种形态 |

## 附录 B. Redis Key 一览表（背下来）

| Key 模板 | 类型 | TTL | 写入方 | 读取方 | 用途 |
|---|---|---|---|---|---|
| `login:code:{phone}` | String | 2min | UserServiceImpl#sendCode | UserServiceImpl#login | 短信验证码 |
| `login:token:{token}` | Hash | 30min 滑动 | UserServiceImpl#login | RefreshTokenInterceptor | 登录会话 |
| `cache:shop:{id}` | String(JSON) | 逻辑过期 | CacheClient / saveShop2Redis | ShopServiceImpl#queryById | 店铺缓存 |
| `lock:shop:{id}` | String | 10s | CacheClient#tryLock | 同上 | 缓存重建互斥锁 |
| `seckill:stock:{voucherId}` | String(int) | 永久 | VoucherServiceImpl/VoucherOrderServiceImpl 预热 | seckill.lua | 秒杀实时库存 |
| `seckill:order:{voucherId}` | Set | 永久 | seckill.lua | seckill.lua | 已下单用户名单（一人一单） |
| `blog:liked:{blogId}` | ZSet | 永久 | BlogServiceImpl#updateLike | updateLike/queryBlogLikes | 点赞人+时间戳 |
| `feed:{userId}` | ZSet | 永久 | BlogServiceImpl#saveBlog | quertBlogOfFollow | 粉丝收件箱 |
| `follows:{userId}` | Set | 永久 | FollowServiceImpl#follow | followCommons | 关注列表（求交集） |
| `sign:{userId}:{yyyyMM}` | BitMap | 永久 | UserServiceImpl#sign | signCount | 每月签到位图 |
| `shop:geo:{typeId}` | GEO | 永久 | （测试数据导入） | ShopServiceImpl#queryShopByType | 分类下店铺坐标 |
| `shop_type:` | List(JSON) | 永久 | ShopTypeServiceImpl#querySort | 同上 | 店铺分类缓存（key 即 `SHOP_TYPE_KEY` 本身，带尾冒号无后缀） |
| `icr:order:{yyyy:MM:dd}` | String(int) | 永久 | RedisIdWorker#nextId | 同上 | 全局 ID 序列号（按天分片） |
| `stream.orders` | Stream | — | seckill.lua（残留，无人消费） | 无 | 原版方案残留，见 Q3 |

---

> 📌 配套文档：[README.md](README.md)（项目门面）· [Description.md](Description.md)（中英双版名片）· [STUDY_CHECKLIST.md](STUDY_CHECKLIST.md)（逐文件精读清单）
> 所有源码文件均已带逐行意图级注释，直接打开对照阅读即可。
