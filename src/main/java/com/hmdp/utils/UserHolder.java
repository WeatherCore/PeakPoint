package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;

// ═══════════════════════════════════════════════════════════════════
// UserHolder（当前用户容器）—— ThreadLocal 的极简封装，全项目"我是谁"的唯一来源。
// 原理：Tomcat 一个请求一条线程，ThreadLocal 让每个线程独享自己的用户副本，
// 各层代码 UserHolder.getUser() 随取随用，不用层层传参。
// 写入方：RefreshTokenInterceptor#preHandle；清理方：同类 afterCompletion
// （线程复用前必须清，否则串号）；删除方：UserController#logout。
// ═══════════════════════════════════════════════════════════════════
public class UserHolder {

    // ThreadLocal 是线程隔离的容器：
    // 一个请求对应一个 Tomcat 线程，这个线程里面存一份当前登录用户信息；别的线程拿不到，线程之间互不干扰。
    // UserHolder 就是对 ThreadLocal 做了一层封装，对外提供静态方法，方便全项目任意地方拿登录用户
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    // saveUser（装载）：拦截器验完 token 后调用 —— 给当前线程"发工牌"
    public static void saveUser(UserDTO user) {
        // 写入的是"本线程"的副本：其他线程完全看不见，天然线程安全
        tl.set(user);
    }

    // getUser（取用）：Service/Controller 里随时调用 —— "看工牌"识别当前用户；游客返回 null
    public static UserDTO getUser() {
        // 读的也是"本线程"的值：整条请求链路共享同一份身份
        return tl.get();
    }

    // removeUser（清理）：请求结束/登出时调用 —— "收回工牌"，防线程复用串号与内存泄漏
    public static void removeUser() {
        // 不清的下场：线程池复用这条线程跑下一个请求时，身份串号
        tl.remove();
    }
}
