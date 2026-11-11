package com.hmdp.dto;

// ═══════════════════════════════════════════════════════════════════
// Result（统一响应壳）—— 全项目被依赖最多的类（入度 23）：所有接口的返回契约。
// 四字段固定结构：前端只认 success 布尔值判断成败；HTTP 状态码恒为 200
// （除拦截器 401），"系统错误"与"业务失败"都用本壳承载。
// 4 个静态工厂覆盖全部构造场景，调用方禁止 new Result(...) 直接拼字段。
// [序列化形态] 配合 application.yaml 的 non_null 配置，成功无数据时响应
// 只有 {"success":true}——字段级瘦身，前端解析零负担。
// ═══════════════════════════════════════════════════════════════════
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 三个 Lombok 注解分工：@Data 生成 getter/setter（前端 JSON 映射用）、
// @NoArgsConstructor 反序列化需要（Jackson 默认找无参构造）、
// @AllArgsConstructor 给静态工厂用（四参全构造一行搞定）
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {

    // 业务成败标志：前端判断的第一依据
    private Boolean success;
    // 失败原因话术：成功时为 null（配合 non_null 序列化剔除）
    private String errorMsg;
    // 业务数据载荷：成功时有值（对象/列表/token/订单号皆可）
    private Object data;
    // 分页总数：与 data 列表配套使用，普通接口为 null
    private Long total;

    // 《Effective Java》的第一条（"考虑用静态工厂方法代替构造器”）：
    // ① 有名字：ok(data) 比 new Result(true, null, data, null) 可读得多
    // 工厂版：一眼看懂"成功，带数据"
    // return Result.ok(orderId);
    // 裸 new 版：四个参数全是"裸值"，读者要逐个数位置猜含义
    // return new Result(true,null,orderId,null); // true 是什么？null 是什么？哪个是 data？
    // ② 立规矩：工厂是唯一入口，非法状态根本造不出来（最核心的架构理由）：如果允许大家随便
    // new，迟早有人写出这种“精神分裂”的响应：代码编译完全通过，但都是语义非法状态——问题要到前端联调甚至线上才暴露。而封装成静态工厂后，这 4
    // 种合法形态之外的状态在语法层面就造不出来
    // new Result(false, null, orderList, null) 失败了却带订单数据？
    // ③ 演进零成本：改 Result 类不用动 65 个调用点，假设下个月你要给响应加个字段 timestamp（服务端时间戳，方便排查）
    // ④ 留了后路：工厂方法内部将来可以“加戏”。将来加：失败统一埋点/失败率监控

    // ok（成功·无数据）：纯成功应答——如点赞/关注等只关心成败的写操作
    public static Result ok() {
        // success=true，data/total 均空：序列化后前端只看到 {"success":true}
        return new Result(true, null, null, null);
    }

    // ok（成功·带数据）：最常用的工厂——查询/登录/下单等需要回传数据的接口
    public static Result ok(Object data) {
        // 载荷放 data：token/对象/列表都兼容（Object 类型）
        return new Result(true, null, data, null);
    }

    // ok（成功·列表+总数）：分页查询专用——data 是当前页列表，total 供前端算页码
    public static Result ok(List<?> data, Long total) {
        // 四参全用：分页三件套一次带齐
        return new Result(true, null, data, total);
    }

    // fail（失败）：业务失败统一入口——errorMsg 是给用户看的话术，不含堆栈细节
    public static Result fail(String errorMsg) {
        // success=false + 话术：data 恒空，失败响应无载荷
        return new Result(false, errorMsg, null, null);
    }
}
