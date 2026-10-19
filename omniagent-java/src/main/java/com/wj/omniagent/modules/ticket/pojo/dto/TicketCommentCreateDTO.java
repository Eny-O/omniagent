package com.wj.omniagent.modules.ticket.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增工单评论 / 备注请求
 */
@Data
@Schema(description = "新增工单评论请求参数")
public class TicketCommentCreateDTO {

    @NotBlank(message = "评论内容不能为空")
    @Schema(description = "评论内容", example = "已联系用户，预计今天上门处理", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "是否内部备注（TRUE 仅处理人/主管/管理员可见，报障人不可见），默认 false", example = "false")
    private Boolean isInternal = false;
}
