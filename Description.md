# Description

## 中文版

黑马点评是一个仿大众点评的 Redis 高并发实战后端，覆盖探店笔记、优惠券秒杀、点赞关注、Feed 流、BitMap 签到与 GEO 附近商铺。核心亮点：Lua 脚本原子校验秒杀资格防超卖与一人一单；RabbitMQ 异步落库削峰，TTL 死信队列延迟重试兜底，消费端以事务、订单号幂等查重、stock>0 条件更新构成三重防线；空值缓存挡缓存穿透，逻辑过期加互斥锁异步重建挡缓存击穿；推模式收件箱与游标滚动分页解决 Feed 流分页错位。基于 Spring Boot 2.3、MyBatis-Plus、Redis、Redisson、RabbitMQ 构建，全部 75 个源文件带逐行意图级中文注释，适合作为 Redis 学习与面试项目。

## English

Hm-DianPing is a Dianping-style store-exploration backend built as an end-to-end Redis high-concurrency playground: voucher flash sales, blog posts, likes, follows, a push-based Feed, BitMap check-ins and GEO-based nearby-shop search. Every hotspot has a named countermeasure — a Lua script validates seckill eligibility atomically (stock plus one-order-per-user) to eliminate overselling; validated orders are pushed to RabbitMQ for peak-shaving writes, where a TTL dead-letter queue retries stragglers and the consumer pairs manual ACK with a programmatic transaction, order-id idempotency and stock>0 conditional updates as a triple safety net; null-value caching absorbs cache penetration, logical expiration with mutex-guarded async rebuilds defuses cache breakdown, and a scrollable max/offset cursor over per-fan ZSet inboxes fixes Feed pagination drift. Built on Spring Boot 2.3, MyBatis-Plus, Redis, Redisson and RabbitMQ, with line-by-line intent-level Chinese comments across all 75 source files — ideal as a Redis learning project and interview showcase.
