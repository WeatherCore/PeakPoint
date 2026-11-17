package com.hmdp.interceptor;


import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// ═══════════════════════════════════════════════════════════════════
// LoginInterceptor（登录校验拦截器·第二层）—— 登录体系的"守门员"。
// 只拦需要登录的路径（MvcConfig 白名单排除游客路径），判断依据是 ThreadLocal：
// 第一层 RefreshTokenInterceptor 已把用户装好，这里只查不装——所以本类不需要注入 RedisTemplate，干净得只剩一个判断。
// 两层分工：第一层"装载+续期"（拦 /** 永远放行），本层"拦截"（白名单外无用户即 401）。
// 一句话：保证游客访问不需要登录的接口时，只要携带有效 token，照样可以续期登录状态
// ═══════════════════════════════════════════════════════════════════
public class LoginInterceptor implements HandlerInterceptor {

    // preHandle（控制器方法执行前）：守门判断——ThreadLocal 里没用户就拦下
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 1.判断是否需要拦截（ThreadLocal）中是否有用户
        // 只看 ThreadLocal：第一层没装上（游客/无效 token）这里就是 null
        if (UserHolder.getUser() == null) {

            // 没有需要拦截，设置状态码 401
            // 401 Unauthorized：前端收到后跳登录页（本框架约定，不用 JSON body）
            response.setStatus(401);

            // 拦截
            // return false = 请求到此为止，Controller 根本不会执行
            return false;
        }

        //由用户放行
        // ThreadLocal 里有用户 = 已登录，放行进 Controller
        return true;
    }
}


// 拦截器的执行顺序：
// 前置：RefreshTokenInterceptor.preHandle → LoginInterceptor.preHandle
// 后置（afterCompletion）：RefreshTokenInterceptor.afterCompletion → LoginInterceptor.afterCompletion
// 
// 不管 LoginInterceptor 是放行还是拦截，RefreshTokenInterceptor 的 afterCompletion 一定会执行，负责清理 ThreadLocal。所以 LoginInterceptor 不需要再写 removeUser，避免重复调用（当然重复调用 remove 也不会报错，只是没必要）