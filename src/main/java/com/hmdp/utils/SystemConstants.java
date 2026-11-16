package com.hmdp.utils;

// ═══════════════════════════════════════════════════════════════════
// SystemConstants（系统常量）—— 分页/上传目录/昵称前缀。
// [注意] IMAGE_UPLOAD_DIR 硬编码了 Windows 绝对路径：换机器部署必须修改，
// 这也是教学项目"图省事"的典型——生产应走配置文件或环境变量。
// ═══════════════════════════════════════════════════════════════════
public class SystemConstants {

    // 图片落盘根目录：nginx 静态资源目录，上传的图可直接通过 URL 访问
    public static final String IMAGE_UPLOAD_DIR = "D:\\nginx-1.18.0 dianping\\html\\hmdp\\imgs";
    // 新用户隐式注册的默认昵称前缀：user_ + 10 位随机串
    public static final String USER_NICK_NAME_PREFIX = "user_";
    // 默认分页大小：店铺类型分页用（首页列表较轻），每页 5 条记录
    public static final int DEFAULT_PAGE_SIZE = 5;
    // 最大/常规分页大小：博客热榜、笔记分页用，每页 10 条
    public static final int MAX_PAGE_SIZE = 10;
}


// 为什么要有这个类？——消灭“魔法值”
// 假设不用常量类，代码会长这样：
//
// // ShopServiceImpl 里
// .page(new Page<>(current, 5));
// // BlogController 里
// .page(new Page<>(current, 10));
// // UploadController 里（出现 5 次！）
// image.transferTo(new File("D:\\nginx-1.18.0 dianping\\html\\hmdp\\imgs", fileName));
//
// 这些裸写的 5、10、"D:\\..." 就叫魔法值——代码里突然冒出来的、没有解释来历的数字/字符串。三大罪状：
// 1.读不懂：new Page<>(current, 5) 里的 5 是什么？每页条数？为什么是 5？要跳到别处查半天
// 2.改不动：产品经理说“热榜改成每页 20 条”——你得全文搜索 10，然后从 80 个结果里肉眼挑出哪些是分页大小（其他 10 可能是库存、下标、超时秒数……改错一个就是 bug）
// 3.会写岔：UploadController 的路径在 5 个地方各写一遍，某天有人手滑少打一层目录，图片就存丢、删除校验就失效

// 集中到常量类后：改一处 = 全项目生效，且每个值都有名字（MAX_PAGE_SIZE 自解释）