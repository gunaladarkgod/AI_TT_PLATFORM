package com.xgls.web.vo;

import java.io.Serializable;

import lombok.Data;

@Data
public class TransParams implements Serializable {
    /** 图像尺寸 */
    private Integer imgsz;
    /** 模型类型 */
    private String type;
    /** 模型宽 */
    private Integer model_w;
    /** 模型高 */
    private Integer model_h;
    /** 参数名称量化 1-开 0-关 */
    private Integer quantise;
    /** 图片类型 */
    private String chn;
    /** 转换日期 yyyyMMDD */
    private String date;
    /** 基础校验集 */
    private String base_check;
    /** 拓展验证集 */
    private String ext_check;
    /** 基础验证集 */
    private String base_val;
    /** 拓展验证集 */
    private String ext_val;
    /** yaml 文件名称 */
    private String data;

}
