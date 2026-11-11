package com.hmdp.dto;

// ═══════════════════════════════════════════════════════════════════
// UserDTO（脱敏用户）—— Redis 会话与 ThreadLocal 里的"用户形态"。
// 只有 id/nickName/icon 三个展示字段：密码/手机号等敏感字段绝不进缓存与内存——
// 即使 Redis 被读穿、日志被打印，泄露的也只是公开信息。
// 登录时由 User 实体转换生成（BeanUtil.copyProperties），全程伴随请求生命周期。
// ═══════════════════════════════════════════════════════════════════
import lombok.Data;

@Data
public class UserDTO {

    // 用户主键：订单归属/点赞身份的唯一标识
    private Long id;
    // 昵称：展示用，可改
    private String nickName; 
    // 头像 URL：展示用
    private String icon;        
}
