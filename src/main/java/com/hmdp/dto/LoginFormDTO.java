package com.hmdp.dto;

// ═══════════════════════════════════════════════════════════════════
// LoginFormDTO（登录入参）—— POST /user/login 的请求体。
// 本项目只用 phone+code（验证码登录）；password 字段是"密码登录"预留位
// （配合未启用的 PasswordEncoder），当前为 null 不参与校验。
// ═══════════════════════════════════════════════════════════════════
import lombok.Builder;
import lombok.Data;

@Data
@Builder
// [Lombok 注] @Builder：允许链式构造测试用例：LoginFormDTO.builder().phone("...").code("...").build()
public class LoginFormDTO {

    // 手机号：账号主体，正则校验的第一对象
    private String phone; 
    // 验证码：与 Redis 里存的比对
    private String code; 
    // 密码登录预留：当前验证码登录流程不使用
    private String password;   
}
