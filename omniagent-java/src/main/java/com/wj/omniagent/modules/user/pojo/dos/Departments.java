package com.wj.omniagent.modules.user.pojo.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门实体
 */
@Data
@TableName("departments")
@Schema(description = "部门实体")
public class Departments {

    @TableId(type = IdType.AUTO)
    @Schema(description = "部门ID", example = "1")
    private Long id;

    @Schema(description = "部门编码", example = "DEPT_IT")
    private String deptCode;

    @Schema(description = "部门名称", example = "信息技术部")
    private String deptName;

    @Schema(description = "上级部门ID", example = "1")
    private Long parentId;

    @Schema(description = "排序号", example = "0")
    private Integer sortOrder;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
