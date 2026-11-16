package com.hmdp.utils;

// ═══════════════════════════════════════════════════════════════════
// SystemConstants（系统常量）—— 分页/上传目录/昵称前缀。
// [注意] IMAGE_UPLOAD_DIR 硬编码了 Windows 绝对路径：换机器部署必须修改，
// 这也是教学项目"图省事"的典型——生产应走配置文件或环境变量。
// ═══════════════════════════════════════════════════════════════════
public class SystemConstants {

    // 图片落盘根目录：nginx 静态资源目录，上传的图可直接通过 URL 访问
    public static final String IMAGE_UPLOAD_DIR = "D:\\tlias\\nginx-1.18.0 dianping\\html\\hmdp\\imgs";
    // 新用户隐式注册的默认昵称前缀：user_ + 10 位随机串
    public static final String USER_NICK_NAME_PREFIX = "user_";
    // 默认分页大小：店铺类型分页用（首页列表较轻），每页 5 条记录
    public static final int DEFAULT_PAGE_SIZE = 5;
    // 最大/常规分页大小：博客热榜、笔记分页用，每页 10 条
    public static final int MAX_PAGE_SIZE = 10;
}
