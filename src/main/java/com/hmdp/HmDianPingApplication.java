package com.hmdp;

import javax.swing.Spring;

// ═══════════════════════════════════════════════════════════════════
// HmDianPingApplication（启动类）—— 整个应用的点火开关，三个注解各管一件事：
//   @SpringBootApplication：组件扫描 + 自动装配（com.hmdp 包下所有 @Component/@Service/@Configuration）
//   @MapperScan("com.hmdp.mapper")：把 mapper 包下 10 个 BaseMapper 接口注册成 MyBatis 代理 Bean
//   @EnableRabbit：激活 RabbitMQ 监听容器——没有它，@RabbitListener 全部装聋
//   @EnableAspectJAutoProxy(exposeProxy = true)：AOP 代理暴露到 AopContext，
//     支持代码里 AopContext.currentProxy() 拿到代理对象（事务/切面场景备用）
// ═══════════════════════════════════════════════════════════════════
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

// 暴露 AOP 代理对象，开启 Spring AOP（面向切面编程）：同类内部方法调用也能走代理（事务生效的前提之一）
@EnableAspectJAutoProxy(exposeProxy = true)
// 扫描 Mapper 接口：10 个 BaseMapper 在此注册为可注入的代理 Bean
@MapperScan("com.hmdp.mapper")
// 开启 RabbitMQ 的注解驱动，让 Spring 识别 `@RabbitListener`、`@RabbitHandler` 消费者注解
@EnableRabbit
// @SpringBootApplication 三合一：@Configuration + @EnableAutoConfiguration +
// @ComponentScan
@SpringBootApplication
public class HmDianPingApplication {

    // Java 程序入口，也是 SpringBoot 的启动入口
    public static void main(String[] args) {

        // 一行启动：自动装配 + 内嵌容器 + 激活全部 @Enable* 开关
        SpringApplication.run(HmDianPingApplication.class, args);
        // `SpringApplication.run(启动类.class, args)` 做了一整套事情：
        // 1. 初始化 Spring 上下文（IoC 容器）
        // 2. 加载配置文件（application.yml）
        // 3. 触发自动装配
        // 4. 启动内嵌 Tomcat 服务器，监听端口，接收前端发来的 HTTP 请求
        // 5. 扫描所有组件、注册 Bean
        // args：是命令行启动参数，比如 `--server.port=8081`，可以在启动的时候临时覆盖配置文件
    }

}
