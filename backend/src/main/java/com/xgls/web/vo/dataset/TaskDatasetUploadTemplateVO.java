package com.xgls.web.vo.dataset;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "模板上传结果")
public class TaskDatasetUploadTemplateVO {

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "相对路径")
    private String relativePath;

    @Schema(description = "绝对路径")
    private String absolutePath;
}