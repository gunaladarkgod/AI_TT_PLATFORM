package com.xgls.web.controller;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.base.ErrorCode;
import com.xgls.web.entity.TrainYoloFile;
import com.xgls.web.service.TrainYoloFileService;
import com.xgls.web.vo.query.TrainYoloFileQuery;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/trainYolo")
@Slf4j
public class TrainYoloFileController {
    @Value("${sys.root-upload}")
    String rootPath;
    @Autowired
    TrainYoloFileService trainYoloFileService;

    @Operation(summary = "分页获取列表", description = "分页获取列表")
    @PostMapping("list")
    public AjaxResult queryList(TrainYoloFileQuery query) {
        /** 查询条件 */
        LambdaQueryWrapper<TrainYoloFile> wrapper = Wrappers.lambdaQuery(TrainYoloFile.class);
        if (query.getId() != null) {
            wrapper.eq(TrainYoloFile::getId, query.getId());
        }
        if (StrUtil.isNotBlank(query.getType())) {
            wrapper.eq(TrainYoloFile::getType, query.getType());
        }
        /** 用户名 模糊检索 */
        if (StrUtil.isNotBlank(query.getName())) {
            wrapper.like(TrainYoloFile::getName, query.getName());
        }

        /** 分页信息 */
        Long current = query.getCurrent();
        Long size = query.getSize();
        if (current == null) {
            current = CodeMap.PAGE_NO_DEFAULT;
        }
        if (size == null) {
            size = CodeMap.PAGE_SIZE_DEFAULT;
        }
        Page<TrainYoloFile> page = new Page<>(current, size);

        /** 排序信息 */
        List<OrderItem> orders = query.getOrders();
        if (orders != null && !orders.isEmpty()) {
            page.addOrder(orders);
        } else {
            page.addOrder(OrderItem.desc("created_date"));
        }
        Page<TrainYoloFile> res = trainYoloFileService.page(page, wrapper);

        return AjaxResult.success(res);
    }

    @Operation(summary = "添加", description = "添加")
    @PostMapping("add")
    public AjaxResult add(String type, String name, String remark, MultipartFile file)
            throws IllegalStateException, IOException {
        if (StrUtil.isBlank(type) || StrUtil.isBlank(name) || file == null || file.isEmpty()) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        // 获取后缀
        String suff = null;
        if (filename.endsWith(".pt") && type.equals("weights")) {
            suff = ".pt";
        } else if (filename.endsWith(".yaml") && (type.equals("cfg") || type.equals("hyp"))) {
            suff = ".yaml";
        } else {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }

        LambdaQueryWrapper<TrainYoloFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainYoloFile::getName, name);
        if (trainYoloFileService.getOne(wrapper, false) != null) {
            return AjaxResult.error("名称已存在");
        }
        TrainYoloFile record = new TrainYoloFile();
        record.setName(name);
        record.setType(type);
        record.setRemark(remark);
        record.setPath(suff);
        record.setCreated_date(LocalDateTime.now());
        String dir = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_YOLO).toString();
        if (trainYoloFileService.saveLink(record, file, dir, suff)) {
            return AjaxResult.success();
        }
        return AjaxResult.error();

    }

    @Operation(summary = "修改", description = "修改")
    @PostMapping("update")
    public AjaxResult update(TrainYoloFile record) {
        Integer id = record.getId();
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        TrainYoloFile tf = new TrainYoloFile();
        tf.setId(id);
        tf.setName(record.getName());
        tf.setRemark(record.getRemark());
        return trainYoloFileService.updateById(tf) ? AjaxResult.success() : AjaxResult.error();

    }

    @Operation(summary = "删除", description = "删除")
    @PostMapping("del")
    public AjaxResult del(Integer id) {
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        TrainYoloFile tf = trainYoloFileService.getById(id);
        if (tf == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        boolean flg = trainYoloFileService.removeById(id);
        if (flg) {
            FileUtil.del(
                    Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_YOLO, tf.getType() + "_" + id + tf.getPath()));
        }
        return flg ? AjaxResult.success() : AjaxResult.error();
    }
}
