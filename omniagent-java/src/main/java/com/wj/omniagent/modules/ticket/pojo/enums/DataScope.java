package com.wj.omniagent.modules.ticket.pojo.enums;

/**
 * 工单数据范围：决定某角色能"看到 / 操作"哪些工单。
 * <ul>
 *   <li>SELF —— 仅自己创建的（REPORTER）</li>
 *   <li>ASSIGNED —— 自己创建 + 分配给自己的（HANDLER）</li>
 *   <li>DEPARTMENT —— 本部门（SUPERVISOR）</li>
 *   <li>ALL —— 全部（ADMIN）</li>
 * </ul>
 * 调试期角色为空时按 ALL 处理（宽松）；正式启用鉴权后应收紧为"无角色即无权"。
 */
public enum DataScope {
    SELF,
    ASSIGNED,
    DEPARTMENT,
    ALL
}
