# 📚 PeakPoint（峰点）逐文件精读清单（STUDY_CHECKLIST）

> 本清单覆盖项目**全部 75 个源文件**（73 个 Java + 2 个 Lua），配合 [ZHIDAO.md](ZHIDAO.md) 使用：ZHIDAO 是地图（讲机制），本清单是逐页书签（管进度）。所有源码已带逐行意图级注释，直接打开即读。
> 说明来源：功能说明基于骨架静态分析（出入度/依赖边）+ 类头注释推断，以源码类注释为最终准。

---

## 一、使用说明

### 1.1 四层分级表

| 标记 | 层级 | 判据 | 读法 | 单文件耗时 |
|---|---|---|---|---|
| ⭐⭐ | 必读·核心 | 含核心业务逻辑/并发机制（秒杀编排、缓存模板、MQ 消费、状态计算） | 逐行精读 + 对照 ZHIDAO 第 2 章概念 | 30–60 分钟 |
| ⭐ | 必读 | 入口/契约/机制文件（Controller、拦截器、配置中心、Lua、分布式锁） | 逐行精读 | 15–30 分钟 |
| · | 选读 | 含业务逻辑但非核心（辅助 Service、工具类、上传） | 读类头注释 + 关键方法 | 5–15 分钟 |
| ○ | 薄文件 | 方法名自解释（PO/DTO/Mapper 接口/空壳实现/常量类） | 读类注释一句话 | 1–3 分钟 |

### 1.2 勾选规则

- 每读完一个文件把 `[ ]` 改成 `[x]`——**全量覆盖是本清单的核心承诺**，读完 75 个即通读全项目。
- 路径约定：均相对项目根目录，`src/main/java/com/hmdp/` 下文简写为 `…/`（表格与清单里为可点击的完整路径）。
- ⭐⭐ 文件读完必须能答对当步自检题，答不出回炉重读。

### 1.3 时间估算总表

| 步骤 | 模块 | 文件数 | ⭐⭐ | ⭐ | · | ○ | 估算耗时 |
|---|---|---|---|---|---|---|---|
| 第 0 步 | 全局认知（纯文档） | 0 | — | — | — | — | 1 小时 |
| 第 1 步 | 公共契约层 | 6 | 0 | 1 | 0 | 5 | 0.5 小时 |
| 第 2 步 | 登录与会话 | 11 | 1 | 8 | 0 | 2 | 2.5 小时 |
| 第 3 步 | 缓存工具箱 | 10 | 1 | 5 | 2 | 2 | 2.5 小时 |
| 第 4 步 | 商铺查询与 GEO | 10 | 1 | 1 | 2 | 6 | 2 小时 |
| 第 5 步 | 优惠券秒杀 | 17 | 2 | 4 | 1 | 10 | 4.5 小时 |
| 第 6 步 | 博客与社交互动 | 15 | 0 | 4 | 0 | 11 | 2.5 小时 |
| 第 7 步 | 支撑功能 | 6 | 0 | 0 | 3 | 3 | 1 小时 |
| **合计** | | **75** | **5** | **23** | **8** | **39** | **≈ 16.5 小时** |

### 1.4 十步路线骨架索引（本清单压缩为 7 步，对应 ZHIDAO 章节）

| 步 | 目标 | 对应 ZHIDAO 章节 |
|---|---|---|
| 0 | 建立全局心智：项目是什么、8 大概念、三大流程图 | 第 1、2、4 章 + 附录 B |
| 1 | 认清"营业用纸"：统一返回、DTO、常量表 | 第 5 章 ①② |
| 2 | 吃透登录会话：token + Redis Hash + 双层拦截器 + ThreadLocal | 第 4.2 图 |
| 3 | 吃透缓存工具箱：穿透/击穿/互斥锁/逻辑过期/全局 ID/分布式锁 | 第 4.3 图 + 第 6.1 模式 |
| 4 | 吃透商铺查询：缓存落地、先库后删、GEO 附近搜 | 第 6 章 |
| 5 | 吃透秒杀全链路：Lua 原子校验 → MQ 异步 → 死信兜底 → 幂等落库 | 第 4.1 图（最重要） |
| 6 | 吃透社交互动：点赞 ZSet、关注 Set、Feed 推拉、滚动分页 | 第 2.5/2.7 概念 |
| 7 | 收尾支撑：用户详情、图片上传、密码加密 | 第 5.3 节 |

---

## 第 0 步：全局认知（纯文档，零代码门槛）

- [ ] · [ZHIDAO.md](ZHIDAO.md) 第 1 章「这个项目是干什么的」 — 建立"Redis 沙盘"的项目观
- [ ] · [ZHIDAO.md](ZHIDAO.md) 第 2 章「核心概念速览」 — 8 个概念（穿透/击穿/Lua 原子/死信队列/ZSet/BitMap/Feed 推拉/ThreadLocal）逐个过
- [ ] · [ZHIDAO.md](ZHIDAO.md) 第 4 章「运行流程全景图」 — 背下秒杀/登录/缓存三张 ASCII 图
- [ ] · [ZHIDAO.md](ZHIDAO.md) 附录 B「Redis Key 一览表」 — 14 个 key 背熟，后面读代码全靠它

**自检题**：
1. 缓存穿透和缓存击穿的区别是什么？分别对应本项目哪两个方法？
2. 秒杀请求从进入 Controller 到订单落库，一共经过哪 5 站？
3. `login:token:` 和 `seckill:stock:` 分别是什么数据结构、谁写入谁读取？

---

## 第 1 步：公共契约层（6 文件，0.5 小时）

先认识全项目的"通用语言"，后面每一步都会反复用到。

- [ ] ○ [src/main/java/com/hmdp/dto/Result.java](src/main/java/com/hmdp/dto/Result.java) — 统一响应壳：success/errorMsg/data/total 四字段 + 4 个静态工厂，全项目入度最高的类
- [ ] ○ [src/main/java/com/hmdp/dto/UserDTO.java](src/main/java/com/hmdp/dto/UserDTO.java) — 脱敏用户 DTO：只含 id/nickName/icon，Redis 会话与 ThreadLocal 里的用户形态
- [ ] ○ [src/main/java/com/hmdp/dto/LoginFormDTO.java](src/main/java/com/hmdp/dto/LoginFormDTO.java) — 登录入参：phone/code/password 三字段
- [ ] ○ [src/main/java/com/hmdp/dto/ScrollResult.java](src/main/java/com/hmdp/dto/ScrollResult.java) — Feed 滚动分页返回壳：list + minTime 游标 + offset 偏移
- [ ] ⭐ [src/main/java/com/hmdp/utils/RedisConstants.java](src/main/java/com/hmdp/utils/RedisConstants.java) — 全项目 Redis key 前缀与 TTL 总表，读代码的"字典"
- [ ] ○ [src/main/java/com/hmdp/utils/SystemConstants.java](src/main/java/com/hmdp/utils/SystemConstants.java) — 分页大小/上传目录/昵称前缀等系统常量

**自检题**：
1. 前端如何判断一个接口成功还是失败？HTTP 状态码参与判断吗？
2. 为什么存 Redis 会话的是 UserDTO 而不是 User 实体？

---

## 第 2 步：登录与会话（11 文件，2.5 小时）

吃透"token + Redis Hash + 双层拦截器 + ThreadLocal"四件套。

- [ ] ⭐ [src/main/java/com/hmdp/HmDianPingApplication.java](src/main/java/com/hmdp/HmDianPingApplication.java) — 启动类：@MapperScan 扫 mapper、@EnableRabbit 开 MQ、exposeProxy=true 暴露 AopContext 代理
- [ ] ⭐ [src/main/java/com/hmdp/utils/UserHolder.java](src/main/java/com/hmdp/utils/UserHolder.java) — ThreadLocal 工具：当前登录用户的存/取/删，全项目"我是谁"的唯一来源
- [ ] ⭐ [src/main/java/com/hmdp/interceptor/RefreshTokenInterceptor.java](src/main/java/com/hmdp/interceptor/RefreshTokenInterceptor.java) — 第一层拦截器：取 token → 查 Redis → 装 ThreadLocal → 刷新 30min 有效期，永远放行
- [ ] ⭐ [src/main/java/com/hmdp/interceptor/LoginInterceptor.java](src/main/java/com/hmdp/interceptor/LoginInterceptor.java) — 第二层拦截器：ThreadLocal 无用户则 401，只做守门
- [ ] ⭐ [src/main/java/com/hmdp/config/MvcConfig.java](src/main/java/com/hmdp/config/MvcConfig.java) — 注册双层拦截器并排序（order 0→1），白名单决定游客可访问路径
- [ ] ⭐⭐ [src/main/java/com/hmdp/service/impl/UserServiceImpl.java](src/main/java/com/hmdp/service/impl/UserServiceImpl.java) — 发码/登录/注册/BitMap 签到：登录用随机 token + BeanUtil 转 Hash 写 Redis；签到用 SETBIT，连续签到用位运算
- [ ] ⭐ [src/main/java/com/hmdp/controller/UserController.java](src/main/java/com/hmdp/controller/UserController.java) — /user/code、/user/login、/user/me、/user/sign 等入口收单
- [ ] ⭐ [src/main/java/com/hmdp/service/IUserService.java](src/main/java/com/hmdp/service/IUserService.java) — 接口：sendCode/login/sign/signCount 四契约（岗位说明书）
- [ ] ⭐ [src/main/java/com/hmdp/config/WebExceptionAdvice.java](src/main/java/com/hmdp/config/WebExceptionAdvice.java) — 全局异常兜底：RuntimeException 统一转 Result.fail，堆栈不见用户
- [ ] ○ [src/main/java/com/hmdp/entity/User.java](src/main/java/com/hmdp/entity/User.java) — PO：tb_user 表映射（phone/password/nickName/icon）
- [ ] ○ [src/main/java/com/hmdp/mapper/UserMapper.java](src/main/java/com/hmdp/mapper/UserMapper.java) — Mapper：继承 BaseMapper 白嫖 CRUD

**自检题**：
1. 为什么拦截器要拆两层？只留 LoginInterceptor 会丢什么能力？
2. `afterCompletion` 里不调 `UserHolder.removeUser()` 会出什么事？
3. 登录成功后 Redis 里的 Hash 有哪几个 field？30 分钟从什么时候开始倒数？
4. `signCount` 里 `(num & 1)` 和 `num >>>= 1` 合起来在干什么？

---

## 第 3 步：缓存工具箱（10 文件，2.5 小时）

本项目的"公共底座"，第 4、5 步的代码全部踩在它们肩膀上。

- [ ] ⭐⭐ [src/main/java/com/hmdp/utils/CacheClient.java](src/main/java/com/hmdp/utils/CacheClient.java) — 缓存三模板：穿透（空值缓存）/ 逻辑过期（独立线程池+互斥锁重建）/ 通用 set；泛型 + Function 回调的模板方法
- [ ] ⭐ [src/main/java/com/hmdp/utils/RedisData.java](src/main/java/com/hmdp/utils/RedisData.java) — 逻辑过期包装壳：expireTime + data，业务实体不变脏
- [ ] ⭐ [src/main/java/com/hmdp/utils/RedisIdWorker.java](src/main/java/com/hmdp/utils/RedisIdWorker.java) — 全局 ID：时间戳<<32 | Redis INCR 序列（按天分 key 防溢出）
- [ ] ⭐ [src/main/java/com/hmdp/utils/SimpleRedisLock.java](src/main/java/com/hmdp/utils/SimpleRedisLock.java) — 手写分布式锁：setnx 抢锁 + Lua 校验线程标识释放（防误删他人锁）
- [ ] ⭐ [src/main/resources/unLock.lua](src/main/resources/unLock.lua) — 锁释放原子脚本：GET 比对标识一致才 DEL（注意文件名大小写隐患，见 ZHIDAO Q7）
- [ ] ⭐ [src/main/java/com/hmdp/utils/ILock.java](src/main/java/com/hmdp/utils/ILock.java) — 锁接口：tryLock/delLock 两契约
- [ ] · [src/main/java/com/hmdp/config/RedissonConfig.java](src/main/java/com/hmdp/config/RedissonConfig.java) — Redisson 客户端声明（本项目备胎，未实际使用其锁）
- [ ] · [src/main/java/com/hmdp/config/MybatisConfig.java](src/main/java/com/hmdp/config/MybatisConfig.java) — MyBatis-Plus 分页插件注册
- [ ] ○ [src/main/java/com/hmdp/utils/RegexPatterns.java](src/main/java/com/hmdp/utils/RegexPatterns.java) — 手机号/邮箱/验证码正则常量
- [ ] ○ [src/main/java/com/hmdp/utils/RegexUtils.java](src/main/java/com/hmdp/utils/RegexUtils.java) — 正则校验工具（isPhoneInvalid 等）

**自检题**：
1. `queryWithPassThrough` 里 `json != null` 但 `isNotBlank` 为 false 时返回 null——这一步防的是什么？
2. 逻辑过期方案为什么未命中不查库？谁负责预热？
3. `SimpleRedisLock` 的 `ID_PREFIX`（UUID）解决什么问题？只用 `Thread.currentThread().getId()` 会怎样？
4. RedisIdWorker 的序列号为什么按天分 key 而不是用一个 key 一路自增？

---

## 第 4 步：商铺查询与 GEO（10 文件，2 小时）

- [ ] ⭐⭐ [src/main/java/com/hmdp/service/impl/ShopServiceImpl.java](src/main/java/com/hmdp/service/impl/ShopServiceImpl.java) — 缓存查询落地（逻辑过期）/ 先库后删更新（@Transactional）/ GEO 半径 5000m + order by field 保序回查
- [ ] ⭐ [src/main/java/com/hmdp/controller/ShopController.java](src/main/java/com/hmdp/controller/ShopController.java) — /shop/{id}、/shop/of/type（带可选坐标 x/y）、/shop/of/name 模糊分页
- [ ] · [src/main/java/com/hmdp/service/impl/ShopTypeServiceImpl.java](src/main/java/com/hmdp/service/impl/ShopTypeServiceImpl.java) — 分类 List 缓存：命中直接拆 JSON，未命中查库逐个 rightPush（尾部保留旧版注释实现）
- [ ] · [src/main/java/com/hmdp/controller/ShopTypeController.java](src/main/java/com/hmdp/controller/ShopTypeController.java) — /shop-type/list 入口
- [ ] ○ [src/main/java/com/hmdp/entity/Shop.java](src/main/java/com/hmdp/entity/Shop.java) — PO：tb_shop 表（含经纬度 x/y；distance 为 @TableField(exist=false) 展示字段）
- [ ] ○ [src/main/java/com/hmdp/entity/ShopType.java](src/main/java/com/hmdp/entity/ShopType.java) — PO：tb_shop_type 表（createTime/updateTime 带 @JsonIgnore）
- [ ] ○ [src/main/java/com/hmdp/mapper/ShopMapper.java](src/main/java/com/hmdp/mapper/ShopMapper.java) — Mapper：空接口
- [ ] ○ [src/main/java/com/hmdp/mapper/ShopTypeMapper.java](src/main/java/com/hmdp/mapper/ShopTypeMapper.java) — Mapper：空接口
- [ ] ○ [src/main/java/com/hmdp/service/IShopService.java](src/main/java/com/hmdp/service/IShopService.java) — 接口：queryById/update/queryShopByType 契约
- [ ] ○ [src/main/java/com/hmdp/service/IShopTypeService.java](src/main/java/com/hmdp/service/IShopTypeService.java) — 接口：querySort 契约

**自检题**：
1. `update` 为什么先改库再删缓存？顺序反了会出什么脏数据窗口？
2. GEO 查到的 shopId 列表为什么要用 `ORDER BY FIELD(id,...)` 回查？
3. `saveShop2Redis` 在什么场景被调用？它是谁的前置条件？

---

## 第 5 步：优惠券秒杀（17 源文件 + 1 个 Mapper XML，4.5 小时）⭐⭐ 全项目含金量最高

按"发券 → 校验 → 发消息 → 消费落库"的顺序读，Lua 脚本放中间对照。

- [ ] ○ [src/main/java/com/hmdp/entity/Voucher.java](src/main/java/com/hmdp/entity/Voucher.java) — PO：tb_voucher 表（stock/beginTime/endTime 为 exist=false，真实值在秒杀表）
- [ ] ○ [src/main/java/com/hmdp/entity/SeckillVoucher.java](src/main/java/com/hmdp/entity/SeckillVoucher.java) — PO：tb_seckill_voucher 表（voucherId 为 IdType.INPUT 主键，与券一对一）
- [ ] ○ [src/main/java/com/hmdp/entity/VoucherOrder.java](src/main/java/com/hmdp/entity/VoucherOrder.java) — PO：tb_voucher_order 表（id 为 IdType.INPUT——订单号由 RedisIdWorker 生成）
- [ ] ○ [src/main/java/com/hmdp/mapper/VoucherMapper.java](src/main/java/com/hmdp/mapper/VoucherMapper.java) — Mapper：唯一手写方法 queryVoucherOfShop（SQL 在 XML）
- [ ] ○ [src/main/resources/mapper/VoucherMapper.xml](src/main/resources/mapper/VoucherMapper.xml) — SQL：LEFT JOIN 秒杀表带出库存/起止时间
- [ ] ○ [src/main/java/com/hmdp/mapper/SeckillVoucherMapper.java](src/main/java/com/hmdp/mapper/SeckillVoucherMapper.java) — Mapper：空接口
- [ ] ○ [src/main/java/com/hmdp/mapper/VoucherOrderMapper.java](src/main/java/com/hmdp/mapper/VoucherOrderMapper.java) — Mapper：空接口
- [ ] ○ [src/main/java/com/hmdp/service/IVoucherService.java](src/main/java/com/hmdp/service/IVoucherService.java) — 接口：queryVoucherOfShop / addSeckillVoucher
- [ ] ○ [src/main/java/com/hmdp/service/ISeckillVoucherService.java](src/main/java/com/hmdp/service/ISeckillVoucherService.java) — 接口：空（继承 IService）
- [ ] ○ [src/main/java/com/hmdp/service/IVoucherOrderService.java](src/main/java/com/hmdp/service/IVoucherOrderService.java) — 接口：seckillVoucher 契约
- [ ] · [src/main/java/com/hmdp/service/impl/VoucherServiceImpl.java](src/main/java/com/hmdp/service/impl/VoucherServiceImpl.java) — 新增秒杀券：双表落库（@Transactional）+ Redis 库存预热
- [ ] ○ [src/main/java/com/hmdp/service/impl/SeckillVoucherServiceImpl.java](src/main/java/com/hmdp/service/impl/SeckillVoucherServiceImpl.java) — 空壳实现
- [ ] ⭐ [src/main/java/com/hmdp/controller/VoucherController.java](src/main/java/com/hmdp/controller/VoucherController.java) — /voucher 新增普通券、/voucher/seckill 新增秒杀券、/voucher/list/{shopId} 查券
- [ ] ⭐ [src/main/java/com/hmdp/controller/VoucherOrderController.java](src/main/java/com/hmdp/controller/VoucherOrderController.java) — /voucher-order/seckill/{id} 秒杀下单唯一入口
- [ ] ⭐⭐ [src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java](src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java) — 秒杀总指挥：@PostConstruct 预热（setIfAbsent 防覆盖）→ Lua 原子校验（0/1/2/-1 码）→ 组装订单发 RabbitMQ
- [ ] ⭐ [src/main/java/com/hmdp/config/QueueConfig.java](src/main/java/com/hmdp/config/QueueConfig.java) — MQ 拓扑：X→QA（10s TTL + 死信路由 Y/YD）→ QD；Jackson JSON 转换器（JavaTimeModule）
- [ ] ⭐⭐ [src/main/java/com/hmdp/listener/SeckillVoucherListener.java](src/main/java/com/hmdp/listener/SeckillVoucherListener.java) — MQ 消费者：TransactionTemplate 事务内幂等查重 + 落库 + `stock>0` 条件扣减；basicAck/basicNack 手动确认
- [ ] ⭐ [src/main/resources/seckill.lua](src/main/resources/seckill.lua) — 秒杀原子裁判：库存判断→一人一单→扣库存→记名单（注意 L37 XADD 为原版残留，无人消费，见 ZHIDAO Q3）

**自检题**：
1. 为什么资格校验必须放 Lua？Java 代码"查库存再扣库存"两步会发生什么竞态？
2. QA 队列的三个 arguments 分别是什么？一条消息在 QA 里滞留 10 秒后会去哪？
3. 消费端哪三道防线共同保证"不超卖、不重复"？每一道分别挡住哪类问题？
4. 预热为什么用 `setIfAbsent` 而不是 `set`？
5. Lua 返回 2 是什么含义？这个"名单"存在哪个 key 里？

---

## 第 6 步：博客与社交互动（15 文件，2.5 小时）

- [ ] ⭐ [src/main/java/com/hmdp/service/impl/BlogServiceImpl.java](src/main/java/com/hmdp/service/impl/BlogServiceImpl.java) — 笔记/点赞 ZSet（点赞人+时间戳）/ 点赞榜 Top5 / Feed 推模式（发笔记写粉丝收件箱）/ 滚动分页（max/offset 游标解析）
- [ ] ⭐ [src/main/java/com/hmdp/service/impl/FollowServiceImpl.java](src/main/java/com/hmdp/service/impl/FollowServiceImpl.java) — 关注 DB+Set 双写（先库后缓存）/ 共同关注 Set 交集
- [ ] ⭐ [src/main/java/com/hmdp/controller/BlogController.java](src/main/java/com/hmdp/controller/BlogController.java) — /blog 发笔记、/blog/like/{id} 点赞、/blog/of/follow Feed 流、/blog/hot 热榜
- [ ] ⭐ [src/main/java/com/hmdp/controller/FollowController.java](src/main/java/com/hmdp/controller/FollowController.java) — /follow/{id}/{isFollow} 关注取关、/follow/or/not 查询、/follow/common 共同关注
- [ ] ○ [src/main/java/com/hmdp/entity/Blog.java](src/main/java/com/hmdp/entity/Blog.java) — PO：tb_blog 表（icon/name/isLike 为 exist=false 展示字段）
- [ ] ○ [src/main/java/com/hmdp/entity/Follow.java](src/main/java/com/hmdp/entity/Follow.java) — PO：tb_follow 表（userId 关注者 + followUserId 被关注者）
- [ ] ○ [src/main/java/com/hmdp/entity/BlogComments.java](src/main/java/com/hmdp/entity/BlogComments.java) — PO：tb_blog_comments 表（parentId/answerId 支持楼中楼）
- [ ] ○ [src/main/java/com/hmdp/mapper/BlogMapper.java](src/main/java/com/hmdp/mapper/BlogMapper.java) — Mapper：空接口
- [ ] ○ [src/main/java/com/hmdp/mapper/FollowMapper.java](src/main/java/com/hmdp/mapper/FollowMapper.java) — Mapper：空接口
- [ ] ○ [src/main/java/com/hmdp/mapper/BlogCommentsMapper.java](src/main/java/com/hmdp/mapper/BlogCommentsMapper.java) — Mapper：空接口
- [ ] ○ [src/main/java/com/hmdp/service/IBlogService.java](src/main/java/com/hmdp/service/IBlogService.java) — 接口：6 个博客方法契约（注意 quert 拼写）
- [ ] ○ [src/main/java/com/hmdp/service/IFollowService.java](src/main/java/com/hmdp/service/IFollowService.java) — 接口：follow/isFollow/followCommons
- [ ] ○ [src/main/java/com/hmdp/service/IBlogCommentsService.java](src/main/java/com/hmdp/service/IBlogCommentsService.java) — 接口：空（继承 IService）
- [ ] ○ [src/main/java/com/hmdp/service/impl/BlogCommentsServiceImpl.java](src/main/java/com/hmdp/service/impl/BlogCommentsServiceImpl.java) — 空壳实现（评论功能预留）
- [ ] ○ [src/main/java/com/hmdp/controller/BlogCommentsController.java](src/main/java/com/hmdp/controller/BlogCommentsController.java) — 空壳控制器（预留）

**自检题**：
1. 点赞为什么用 ZSet 而不是 Set？score 存的是什么？
2. 取消点赞的"先减数据库再删 ZSet"与商铺更新的"先库后删"思路一致吗？两处都成功才一致的保证在哪？
3. Feed 滚动分页的 `offset` 什么情况下会大于 1？它解决什么错位问题？
4. 共同关注为什么能用一次 SINTER 算完？DB 里能这样算吗（要写什么 SQL）？

---

## 第 7 步：支撑功能（7 文件，1 小时）

- [ ] · [src/main/java/com/hmdp/controller/UploadController.java](src/main/java/com/hmdp/controller/UploadController.java) — 图片上传（UUID+hash 两级目录）/ 删除接口防路径穿越（前缀校验 + normalize 比对）
- [ ] · [src/main/java/com/hmdp/service/impl/UserInfoServiceImpl.java](src/main/java/com/hmdp/service/impl/UserInfoServiceImpl.java) — 空壳实现（用户详情）
- [ ] · [src/main/java/com/hmdp/utils/PasswordEncoder.java](src/main/java/com/hmdp/utils/PasswordEncoder.java) — MD5+随机盐加密工具（本项目当前未使用，备用）
- [ ] ○ [src/main/java/com/hmdp/entity/UserInfo.java](src/main/java/com/hmdp/entity/UserInfo.java) — PO：tb_user_info 表（城市/介绍/粉丝数/积分等）
- [ ] ○ [src/main/java/com/hmdp/mapper/UserInfoMapper.java](src/main/java/com/hmdp/mapper/UserInfoMapper.java) — Mapper：空接口
- [ ] ○ [src/main/java/com/hmdp/service/IUserInfoService.java](src/main/java/com/hmdp/service/IUserInfoService.java) — 接口：空（继承 IService）

**自检题**：
1. `deleteBlogImg` 防路径穿越用了哪两道校验？
2. `createNewFileName` 为什么用 hash 取两级目录而不是全塞一个目录？

---

## 附录 A：配置与数据文件（不计入 75 源文件，部署时用）

- [ ] · [src/main/resources/application.yaml](src/main/resources/application.yaml) — 8081 端口 / MySQL dingping 库 / Redis / RabbitMQ（**manual ACK 是手动确认的前提**，并发 5、prefetch 10）
- [ ] · [src/main/resources/db/hmdp.sql](src/main/resources/db/hmdp.sql) — 建库脚本：11 张表 + 测试数据（约 1284 行）
- [ ] · [pom.xml](pom.xml) — Spring Boot 2.3.12 + MyBatis-Plus 3.4.3 + Redisson 3.13.6 + starter-amqp + hutool

## 附录 B：全量文件核对表（75/75）

<details><summary><b>📋 点开核对：你的阅读进度覆盖了全部文件吗</b>（点击展开）</summary>

| # | 文件 | 步 |
|---|---|---|
| 1 | HmDianPingApplication.java | 2 |
| 2 | config/MvcConfig.java | 2 |
| 3 | config/MybatisConfig.java | 3 |
| 4 | config/QueueConfig.java | 5 |
| 5 | config/RedissonConfig.java | 3 |
| 6 | config/WebExceptionAdvice.java | 2 |
| 7 | controller/BlogCommentsController.java | 6 |
| 8 | controller/BlogController.java | 6 |
| 9 | controller/FollowController.java | 6 |
| 10 | controller/ShopController.java | 4 |
| 11 | controller/ShopTypeController.java | 4 |
| 12 | controller/UploadController.java | 7 |
| 13 | controller/UserController.java | 2 |
| 14 | controller/VoucherController.java | 5 |
| 15 | controller/VoucherOrderController.java | 5 |
| 16 | dto/LoginFormDTO.java | 1 |
| 17 | dto/Result.java | 1 |
| 18 | dto/ScrollResult.java | 1 |
| 19 | dto/UserDTO.java | 1 |
| 20 | entity/Blog.java | 6 |
| 21 | entity/BlogComments.java | 6 |
| 22 | entity/Follow.java | 6 |
| 23 | entity/SeckillVoucher.java | 5 |
| 24 | entity/Shop.java | 4 |
| 25 | entity/ShopType.java | 4 |
| 26 | entity/User.java | 2 |
| 27 | entity/UserInfo.java | 7 |
| 28 | entity/Voucher.java | 5 |
| 29 | entity/VoucherOrder.java | 5 |
| 30 | interceptor/LoginInterceptor.java | 2 |
| 31 | interceptor/RefreshTokenInterceptor.java | 2 |
| 32 | listener/SeckillVoucherListener.java | 5 |
| 33 | mapper/BlogCommentsMapper.java | 6 |
| 34 | mapper/BlogMapper.java | 6 |
| 35 | mapper/FollowMapper.java | 6 |
| 36 | mapper/SeckillVoucherMapper.java | 5 |
| 37 | mapper/ShopMapper.java | 4 |
| 38 | mapper/ShopTypeMapper.java | 4 |
| 39 | mapper/UserInfoMapper.java | 7 |
| 40 | mapper/UserMapper.java | 2 |
| 41 | mapper/VoucherMapper.java | 5 |
| 42 | mapper/VoucherOrderMapper.java | 5 |
| 43 | service/IBlogCommentsService.java | 6 |
| 44 | service/IBlogService.java | 6 |
| 45 | service/IFollowService.java | 6 |
| 46 | service/ISeckillVoucherService.java | 5 |
| 47 | service/IShopService.java | 4 |
| 48 | service/IShopTypeService.java | 4 |
| 49 | service/IUserInfoService.java | 7 |
| 50 | service/IUserService.java | 2 |
| 51 | service/IVoucherOrderService.java | 5 |
| 52 | service/IVoucherService.java | 5 |
| 53 | service/impl/BlogCommentsServiceImpl.java | 6 |
| 54 | service/impl/BlogServiceImpl.java | 6 |
| 55 | service/impl/FollowServiceImpl.java | 6 |
| 56 | service/impl/SeckillVoucherServiceImpl.java | 5 |
| 57 | service/impl/ShopServiceImpl.java | 4 |
| 58 | service/impl/ShopTypeServiceImpl.java | 4 |
| 59 | service/impl/UserInfoServiceImpl.java | 7 |
| 60 | service/impl/UserServiceImpl.java | 2 |
| 61 | service/impl/VoucherOrderServiceImpl.java | 5 |
| 62 | service/impl/VoucherServiceImpl.java | 5 |
| 63 | utils/CacheClient.java | 3 |
| 64 | utils/ILock.java | 3 |
| 65 | utils/PasswordEncoder.java | 7 |
| 66 | utils/RedisConstants.java | 1 |
| 67 | utils/RedisData.java | 3 |
| 68 | utils/RedisIdWorker.java | 3 |
| 69 | utils/RegexPatterns.java | 3 |
| 70 | utils/RegexUtils.java | 3 |
| 71 | utils/SimpleRedisLock.java | 3 |
| 72 | utils/SystemConstants.java | 1 |
| 73 | utils/UserHolder.java | 2 |
| 74 | resources/seckill.lua | 5 |
| 75 | resources/unLock.lua | 3 |

</details>

## 附录 C：三条精读路径（按目标选）

### 🚀 速览路径（面试前 3-4 天）

> 第 0 步（1h）→ 第 1 步（0.5h）→ 第 2 步只读 ⭐⭐ UserServiceImpl + 两拦截器（1.5h）→ 第 3 步只读 ⭐⭐ CacheClient + SimpleRedisLock + RedisIdWorker（2h）→ 第 5 步全读（4.5h，秒杀是面试重灾区）
> **合计 ≈ 10 小时**，覆盖全部 ⭐⭐ 与核心 ⭐，能撑住 80% 的项目深挖提问。

### 🛤️ 主线路径（7-8 天，推荐求职者走这条）

> 第 0→7 步全部 ⭐⭐ 与 ⭐ 逐行精读，· 选读按需，○ 薄文件快速翻过。
> **合计 ≈ 16 小时**，达成"每个机制能画图、能答自检题"。

### 🏔️ 全量路径（约 14 天，彻底吃透）

> 75 个文件全部按清单读完打勾 + 配置文件 + hmdp.sql 建表语句逐表看一遍字段设计 + JMeter 压测实验（超卖/一人一单验证）+ 三个改造练习（Redisson 看门狗替换 SimpleRedisLock / 延迟插件替换 TTL 死信 / 补全评论功能）。
> **合计 ≈ 30 小时**，产出：压测数据 + 三处改进，简历与面试叙事全部有据可依。
