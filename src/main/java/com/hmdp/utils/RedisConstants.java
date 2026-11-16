package com.hmdp.utils;

// ═══════════════════════════════════════════════════════════════════
// RedisConstants（Redis 常量表）—— 全项目 Redis key 的"地址簿"：
// 集中管理项目里所有 Redis 的 key 前缀、过期时长，整个项目所有操作 Redis 的地方，全部引用这个类里的常量，禁止代码里硬编码字符串、数字
// 14 个 key 前缀对应 7 大业务场景（会话/缓存/秒杀/点赞/Feed/关注/签到/GEO），全部带尾冒号：拼接时直接 + 业务 id 即可。读任何 Service 前先背熟这张表，完整的 key→写入方→读取方映射见 ZHIDAO.md 附录 B。
// [命名规范] 三段式 {业务}:{对象}:{维度}——login:token:123 是"登录业务的token 会话，属于用户 123"；看到 key 就能反推归属与用途。
// [分组浏览] 常量按业务域分了三组（登录体系/商铺缓存/秒杀与社交/分类缓存）：改任何一处前先确认该 key 的写入方与读取方都同步更新——key 是两端契约。
// [TTL 常量说明] 部分 TTL 常量（如 LOGIN_USER_TTL）当前未被引用：实现里直接写了数字——保留常量作为"单一事实来源"的意图声明。
// ═══════════════════════════════════════════════════════════════════
public class RedisConstants {

    // ───────── 登录体系 ─────────
    // 短信验证码：String，值=6位数字
    public static final String LOGIN_CODE_KEY = "login:code:";
    // 验证码有效期 2 分钟（sendCode 里直接写死 2，此常量备用）
    public static final Long LOGIN_CODE_TTL = 2L;
    // 登录会话：Hash（id/nickName/icon），key 里的 token 由前端请求头携带
    public static final String LOGIN_USER_KEY = "login:token:";
    // 会话 TTL 备用常量（实际登录写 30 分钟、拦截器续期 30 分钟）
    public static final Long LOGIN_USER_TTL = 36000L;

    // ───────── 商铺缓存 ─────────
    // 空值缓存 TTL：2 分钟——挡缓存穿透的"假数据"存活期
    public static final Long CACHE_NULL_TTL = 2L;
    // 店铺缓存有效期：30 分钟（逻辑过期方案里是"逻辑有效期"）
    public static final Long CACHE_SHOP_TTL = 30L;
    // 店铺缓存：String(JSON)，值为 RedisData 包装的 Shop
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    // 店铺缓存重建互斥锁：String("1")，10 秒自动过期防死锁
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    // 锁 TTL：10 秒（tryLock 里直接写死 10，此常量备用）
    public static final Long LOCK_SHOP_TTL = 10L;

    // ───────── 秒杀与社交 ─────────
    // 秒杀实时库存：String(数字)，Lua 脚本读它判断并扣减
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    // 笔记点赞榜：ZSet，member=用户id，score=点赞时间戳
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    // 粉丝收件箱：ZSet，member=笔记id，score=发布时间戳
    public static final String FEED_KEY = "feed:";
    // 关注列表：Set，member=被关注用户id（求共同关注交集用）
    public static final String FOLLOW_KEY = "follows:";
    // 店铺地理坐标：GEO，按 typeId 分组存经纬度
    public static final String SHOP_GEO_KEY = "shop:geo:";
    // 签到位图：BitMap，key 后缀 :userId:yyyyMM
    public static final String USER_SIGN_KEY = "sign:";

    // ───────── 分类缓存 ─────────
    // 店铺分类缓存：List(JSON)，key 即常量本身（不带后缀）
    public static final String SHOP_TYPE_KEY = "shop_type:";
    // 分类缓存 TTL 备用常量（当前 List 版未设过期）
    public static final Long SHOP_TYPE_LONG = 10L;
}
