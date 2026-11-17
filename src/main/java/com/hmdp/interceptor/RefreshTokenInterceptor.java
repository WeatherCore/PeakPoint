package com.hmdp.interceptor;

// ═══════════════════════════════════════════════════════════════════
// RefreshTokenInterceptor（Token 刷新拦截器·第一层）—— 登录体系的"装载工"。
// 拦截一切路径（order=0 先于 LoginInterceptor 执行），职责只有三个：
//   ① 从请求头取 token → 查 Redis 还原用户；
//   ② 把用户塞进 ThreadLocal（UserHolder），让后续所有代码随取随用；
//   ③ 顺手把 Redis 会话续期 30 分钟（滑动过期）。
// 设计精髓：无论有没有 token 都 return true 放行——它不负责拦截，拦截是
// 第二层 LoginInterceptor 的事。拆两层的意义：游客路径（/shop/** 等）也能
// 在登录状态下刷新会话有效期，否则只有登录接口能续期，用户刷着店铺就掉线了。
// 线程模型：运行在 Tomcat 请求线程上；afterCompletion 在请求结束后清理
// ThreadLocal——Tomcat 线程会被复用，不清就串号，这是最容易被面试官追问的点。
// ═══════════════════════════════════════════════════════════════════
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// implements HandlerInterceptor：SpringMVC 拦截器规范接口，
// 三个核心方法：`preHandle`、`postHandle`、`afterCompletion`
// - preHandle：Controller 执行之前执行；返回 true = 放行，false = 拦截
// - postHandle：Controller 执行完、渲染视图之前（这个项目没用）
// - afterCompletion：整个请求全部结束，视图渲染完毕之后执行，无论中间有没有异常，都会走这个方法
public class RefreshTokenInterceptor implements HandlerInterceptor {

    // Redis 操作工具：查会话 Hash、续期、所有读写 redis 靠它
    private  StringRedisTemplate stringRedisTemplate;   

    // 构造器注入：本类不是 Spring Bean（MvcConfig 里 new 出来的），拿不到 @Resource，
    // 只能由 MvcConfig 构造时把 RedisTemplate 手动传进来
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate=stringRedisTemplate;
    }

    // preHandle（控制器方法执行前）：装载用户 + 续期，永远放行
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        
        // 1.获取请求头中的token
        // 前端约定把 token 放在 authorization 头里；游客请求这里就是 null
        String token = request.getHeader("authorization");

        // 2.基于token获取redis中的用户
        // StrUtil.isBlank() 是 hutool 工具类：判断 null、空字符串、全空白空格，都算空白
        if (StrUtil.isBlank(token)) {
            // 没 token（游客）：不装载也不拦截，游客可以访问不需要登录的接口（比如查询店铺列表），放行交给第二层判断是否需要登录
            return true;
        }

        // 用 token 反查会话：
        // 拼接后 key 长这样：login:token:{token} → Redis Hash（id/nickName/icon）
        // 为什么用 Hash 存用户信息？
        // 用户信息是多个小字段，hash 适合存储对象，不用序列化成 JSON 字符串，读写单个字段更灵活
        String userKey = RedisConstants.LOGIN_USER_KEY + token;

        // opsForHash()：操作 Redis Hash 类型
        // .entries(key)：取出这个 hash 里面所有的 field-value，封装成 Map
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(userKey);

        // 3.判断用户是否存在
        if (map.isEmpty()) {
            // Hash 为空 = token 无效 或 会话已过期：同样放行，拦截判断交给第二层
            return true;
        }
        
        // 5.将查询到 Hash 数据转换为 userDTO 对象：
        // hutool 工具 BeanUtil.fillBeanWithMap，自动映射填充 UserDTO 对象里面，Hash 里存的就是登录时的脱敏三字段，天然对得上
        // 第三个参数 false：忽略大小写不匹配，map 的 key 和实体字段严格一致才映射；写 true 是忽略大小写
        UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);

        // 6.存在，把封装好的 UserDTO 存入当前线程的 ThreadLocal
        // 塞进当前线程的 ThreadLocal：整个请求链路（Controller/Service/Tool）用 UserHolder.getUser() 直接拿用户信息
        UserHolder.saveUser(userDTO);

        // 7.刷新 token 有效期
        // 滑动续期：只要用户还在活跃（哪怕刷的是游客页面），会话就再续 30 分钟——
        // 这一步是"双层拦截器"存在的全部意义，单层方案在白名单路径上续不了期
        stringRedisTemplate.expire(userKey, 30, TimeUnit.MINUTES);
        
        //放行
        return true;
    }

    // afterCompletion（请求完全结束后）：清理现场—— Tomcat 线程池会复用线程
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        
        // 请求结束后移除 ThreadLocal 用户信息，防止内存泄漏及线程复用导致的串号
        // [必须] 不清的后果：下一个请求复用这条线程时会读到上一个用户的身份——A 用户看到 B 的数据
        UserHolder.removeUser();
    }
}


// RefreshTokenInterceptor 上确实一个 Spring 注解都没有（没有 @Component、没有 @Configuration）
// 但答案是：它照样被 Spring 容器管理着，走的是另一条注册通道。这正是 Spring 的两种“入职方式”之一

// 谁“使用”了它？项目的 MvcConfig.java:44：
// registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);

// RefreshTokenInterceptor 从头到尾不是 Spring Bean —— 它是 MvcConfig 在注册拦截器时用 new 硬造出来的普通 Java 对象。它生效靠的不是 Spring 容器的依赖注入，而是 Spring MVC 的拦截器机制：

// Spring Boot 启动
//      ↓
// 发现 MvcConfig 有 @Configuration + 实现 WebMvcConfigurer  ← "@Configuration 是它唯一的注解，在MvcConfig身上"
//      ↓
// 回调 addInterceptors(InterceptorRegistry registry)   ← 时机：MVC 装配阶段
//      ↓
// 你在里面 new 了两个拦截器对象，交给 registry 登记
//      ↓
// 此后每个 HTTP 请求进来，Spring MVC 按登记的顺序调用它们的 preHandle/afterCompletion