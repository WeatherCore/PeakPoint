package com.hmdp.dto;

// ═══════════════════════════════════════════════════════════════════
// ScrollResult（Feed 滚动分页返回壳）—— GET /blog/of/follow 的响应体。
// 三个字段构成完整游标协议：前端下次请求把 minTime 传回 lastId、
// offset 原样传回 offset——无缝续读，不会因收件箱插入新数据而错位。
// [与普通分页的区别] 没有 current/total：动态流没有"总条数"概念，游标即一切。
// ═══════════════════════════════════════════════════════════════════
import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {

    // 本页笔记列表（你这次真正要看的内容）：已补作者信息与点赞状态
    private List<?> list;
    // 本页最小时间戳：下一次请求的 max 游标（首屏传当前时间戳或极大值）  
    private Long minTime;
    // 与 minTime 同分的偏移量：下一次请求的 offset 游标
    private Integer offset;   
}

// 这个类是 Feed 流“滚动分页”的返回包装盒——它是全项目里最抽象的一个 DTO，第一次见完全看不懂是正常的
// 你打开 App 的“关注”页，刷到 5 条动态，手指往下滑，又冒出 5 条新的……服务器凭什么知道第二次该给你第 6~10 条？
// 1.普通分页（你熟悉的写法）会想到页码：
     // 第 1 次请求：GET /blog/of/follow?current=1   → 给你 1~10 条
     // 第 2 次请求：GET /blog/of/follow?current=2   → 给你 11~20 条
   // 但在 Feed 流里这样写是错的。 因为你刷第 1 页的那几分钟里，你关注的人可能刚发了新笔记——新数据会插队到你已经读过的位置前面。页码分页会发生两件恶心事：
// - 重复读：原来第 10 条被挤到第 11 位，你翻页时又看到它一遍
// - 漏读：原来第 11 条被挤到第 12 位，你翻“第 2 页”时它藏在第 2 页末尾外的缝隙里，永远错过
// ScrollResult 就是这个问题的答案：别用页码，用“书签”

  // 三个字段=数据+书签：
    // ScrollResult {
    //     list    → 本页的笔记（你这次真正要看的内容）
    //     minTime → 书签①：本页里"最旧"那条的时间戳（"我读到这了"）
    //     offset  → 书签②：和书签①同一毫秒发布的还有几条（防并列错位）
    // }
  // 它是一个回力镖：后端扔给前端，前端下次请求原样扔回来——
    // 第 1 次：GET /blog/of/follow?lastId=9999999999999&offset=0
    //         （lastId 随便给个大数：从头开始读最新的）
    // 后端返回：{ list: [笔记C, 笔记B], minTime: 1000, offset: 1 }
    //         ↑ 数据             ↑ 书签：最旧的是 1000，和它同毫秒的还有 1 条

    // 第 2 次：GET /blog/of/follow?lastId=1000&offset=1
    //         ↑ 把书签原样带回："从 1000 往旧了找，跳过 1 条同毫秒的"
    // 后端返回：{ list: [笔记A, 笔记X], minTime: 800, offset: 1 }
  // 这就是“滚动分页/游标分页”：每一页的响应里都藏着下一页的地址

// 为什么需要 offset？（最难的部分，值得慢读）
// 光有 minTime 够吗？不够。想想这个坑：
  // 笔记 B 和 C 是同一毫秒发布的（时间戳都是 1000）。这次请求返回了 C 和 B，minTime=1000。下次请求“找 score ≤ 1000 的”，B 又会被查出来——你看到了两遍 B！
// 所以后端在拼装 ScrollResult 时数了一下：本页里有几条的时间戳等于 minTime？有 2 条（C 和 B）。那 offset 就记 1（“除了你刚看的那条，同毫秒的还有 1 条，下次跳过它”）。这段逻辑就是 quertBlogOfFollow 里那段 if(time==minTime) os++ 的循环（BlogServiceImpl L222-234）——现在你再读那几行，是不是每一步都看得懂了？

// 放到全景里：它在 Feed 链路的哪个位置：
    // 你关注的博主发笔记
    //       ↓
    // BlogServiceImpl#saveBlog：把笔记 id 写进【每个粉丝的收件箱】
    //       收件箱 = Redis ZSet：key=feed:{粉丝id}，member=笔记id，score=发布时间戳
    //       ↓
    // 你刷"关注"页
    //       ↓
    // BlogServiceImpl#quertBlogOfFollow：从你的收件箱里，按 score 从新到旧取 2 条
    //       ↓
    // 把笔记 id 回 MySQL 查正文 + 补作者 + 查你有没有点过赞
    //       ↓
    // 装进 ScrollResult{list, minTime, offset}  ← ★ 就是这个类
    //       ↓
    // 前端渲染 + 藏好两个书签，等你下滑