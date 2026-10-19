package com.wj.omniagent.modules.ticket.pojo.enums;

/**
 * 工单操作能力：决定某角色"能做什么动作"。
 * <p>与 {@link TicketStatusEnum} 结合使用——同一能力在不同工单状态下可能被禁止
 * （例如 EDIT_CONTENT 仅允许在未处理前由 REPORTER 执行）。
 */
public enum TicketCapability {
    CREATE,        // 创建工单
    VIEW,          // 查看工单（受 DataScope 约束）
    EDIT_CONTENT,  // 修改提交内容（未处理前的报障人）
    PROCESS,       // 处理内容 / 推进状态
    ASSIGN,        // 派单（SUPERVISOR）
    DELETE,        // 硬删除（ADMIN）
    MANAGE         // 用户 / 字典 / 配置（ADMIN，预留）
}
