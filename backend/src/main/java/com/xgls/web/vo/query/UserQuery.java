package com.xgls.web.vo.query;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.xgls.web.entity.User;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "UserQuery", description = "用户查询类")
public class UserQuery extends User {
    @Schema(description = "分页大小")
    private Long size;
    @Schema(description = "当前页号")
    private Long current;
    @Schema(description = "排序条件")
    private List<OrderItem> orders;
}
