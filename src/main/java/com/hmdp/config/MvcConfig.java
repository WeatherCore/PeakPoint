package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

// ═══════════════════════════════════════════════════════════════════
// MvcConfig 是 SpringMVC 的配置类，
// 负责注册我们刚才写的两个拦截器、设置拦截范围、放行路径、执行优先级。登录模块的【装配中心】，前面两个拦截器能不能正常工作、顺序对不对，全靠这个类
// 执行顺序由 order 决定，数字越小越先执行：
//   请求 → [RefreshTokenInterceptor order=0 拦 /** 装载+续期，永远放行]
//        → [LoginInterceptor        order=1 白名单外守门，无用户 401]
//        → Controller
// order(0) 必须小于 order(1)：装载先于拦截，守门员才能看到 ThreadLocal 里的人。
// ═══════════════════════════════════════════════════════════════════

// WebMvcConfigurer：SpringMVC 提供的回调接口，专门用来自定义 MVC 的配置，常见能力：注册拦截器、跨域配置、静态资源映射、消息转换器等
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    // 从 Spring 容器里面注入 Redis 客户端：转发给第一层拦截器用于查会话
    // RefreshTokenInterceptor 没有加 @Component，不会自动注入，只能在配置类手动 new 的时候传进去
    @Resource
    private StringRedisTemplate stringRedisTemplate;   

    // addInterceptors：WebMvcConfigurer 里面的方法，专门用来向 SpringMVC 注册拦截器，参数
    // InterceptorRegistry 是拦截器注册登记器。 我们在这个方法里，把两个拦截器登记到 MVC 体系中，告诉 Spring：这两个拦截器要生效
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 登录拦截器
        // 第二层（守门员）：先注册 LoginInterceptor 实例但 order=1，实际后执行——检查 ThreadLocal 有无用户
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(   // 白名单：这些路径游客可访问，不经过 LoginInterceptor 校验
                        "/user/login",      // 登录接口，登录之前肯定不能要求登录
                        "/upload/**",       // 图片上传/访问
                        "/voucher/**",      // 优惠券查询，游客可以看优惠券列表
                        "/user/code",       // 发验证码（登录流程第一步）
                        "/shop/**",         // 店铺查询接口，首页逛店铺，游客可以看
                        "/shop-type/**",    // 店铺分类查询，首页展示分类
                        "/blog/hot"         // 热门笔记列表，不需要登录也能看
                        ).order(1);
                
        // token刷新的拦截器
        // 第一层（装载工）：order=0 最先执行，拦 /** 一切路径——游客页面也能续期 token；
        // 拦截器不是 Spring Bean，new 出来并手动把 RedisTemplate 递进去，这就是为什么 RefreshToken 不用加
        // @Component，由配置类负责实例化和依赖注入
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")       // 拦截所有请求，所有接口，全部经过 RefreshTokenInterceptor
                .order(0);
    }
}

// ## 完整请求链路（结合 MvcConfig，闭环复盘），举两个典型场景巩固：

// ### 场景 1：访问店铺接口 /shop/1（在 Login 的放行列表）
// 1. RefreshTokenInterceptor（order=0）先执行：读取 token、Redis 查用户、续期、存入 UserHolder
// 2. 匹配 LoginInterceptor 的 excludePathPatterns，**跳过 LoginInterceptor，不登录校验**
// 3. 直接进入 Controller 查询店铺
// 4. 请求结束：先执行 LoginInterceptor 的 afterCompletion（空逻辑），再执行 RefreshToken 的 afterCompletion，执行 UserHolder.removeUser () 清理
// 👉 效果：逛店铺的时候，只要带着有效 token，会话持续续期，不会掉线！

// ### 场景 2：点赞接口（不在 exclude 放行列表，需要登录）
// 1. RefreshTokenInterceptor：解析 token、续期、填充 UserHolder
// 2. LoginInterceptor：UserHolder 不为 null → 放行，进入 controller 点赞
// 3. 请求结束，afterCompletion 清理

// ### 场景 3：游客不带 token，访问点赞接口
// 1. RefreshToken：token 为空，直接放行，UserHolder 是空
// 2. LoginInterceptor：检测 UserHolder=null → 401 拦截，请求终止