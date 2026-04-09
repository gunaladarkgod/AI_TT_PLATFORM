package com.xgls.web.controller;
import org.springframework.beans.factory.annotation.Autowired;
import com.xgls.web.service.ProjectExportService;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.nio.file.Paths;
import java.util.Arrays;
import cn.hutool.json.JSONArray;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.base.ErrorCode;
import com.xgls.web.entity.EngineLabel;
import com.xgls.web.entity.EngineProject;
import com.xgls.web.entity.EngineTask;
import com.xgls.web.service.EngineLabelService;
import com.xgls.web.service.EngineProjectService;
import com.xgls.web.service.EngineTaskService;
import com.xgls.web.service.RedisService;
import com.xgls.web.service.SyncCvatService;
import com.xgls.web.service.UserProjectService;
import com.xgls.web.utils.CvatApiUtil;
import com.xgls.web.utils.SessionUtil;
import com.xgls.web.vo.query.EngineProjectQuery;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "标注项目管理")
@RestController
@RequestMapping("/engineProject")
@Slf4j
public class EngineProjectController {
    @Value("${sys.root-upload}")
    String rootPath;
    @Autowired
    EngineProjectService engineProjectService;
    @Autowired
    EngineLabelService engineLabelService;
    @Autowired
    EngineTaskService engineTaskService;
    @Autowired
    SyncCvatService syncCvatService;
    @Autowired
    RedisService redisService;
    @Autowired
    UserProjectService userProjectService;
    @Autowired
    private ProjectExportService projectExportService;


//    @PostMapping("/pull/missing")
//    @Operation(summary="按project拉取未下载的数据集（项目级导出）")
//    public AjaxResult pullMissing(@RequestBody(required = false) ProjectPullBody body) {
//        List<Integer> ids = (body == null) ? null : body.getProjectIds();
//        var result = projectExportService.exportMissingProjects(ids, true);
//        return AjaxResult.success(result);
//    }
@PostMapping("/pull/missing")
@Operation(summary="按project拉取未下载的数据集（项目级导出）- 支持自动从CVAT读取全部项目")
public AjaxResult pullMissing(@RequestBody(required = false) ProjectPullBody body) {
    List<Integer> ids = (body == null) ? null : body.getProjectIds();

    // 1) 如果没传 projectIds，则自动从 CVAT 拉取所有 projects
    if (ids == null || ids.isEmpty()) {
        ids = fetchAllCvatProjectIds();
        if (ids.isEmpty()) {
            return AjaxResult.error("CVAT 未返回任何项目：请检查 sys.cvat.api-server / api-username / api-password，以及该账号是否有 projects 可见权限");
        }
    }

    // 2) 确保这些项目存在于 engine_project 表（复用你已有的 add(id) 逻辑）
    List<Integer> imported = new ArrayList<>();
    for (Integer pid : ids) {
        if (pid == null) continue;
        try {
            if (engineProjectService.getById(pid) == null) {
                AjaxResult ar = this.add(pid); // 复用已有接口逻辑：GET项目->save->syncLabels
                if (ar == null || !ar.isSuccess()) {
                    log.warn("import engine_project failed, pid={}, ret={}", pid, ar);
                    continue;
                }
            }
            imported.add(pid);
        } catch (Exception e) {
            log.warn("import engine_project exception, pid={}", pid, e);
        }
    }

    if (imported.isEmpty()) {
        return AjaxResult.error("没有可拉取的项目：导入 engine_project 失败或无权限");
    }

    // 3) 拉取缺失数据（项目级导出 + 落盘 + ingest 入库）
    var result = projectExportService.exportMissingProjects(imported, true);
    return AjaxResult.success(result);
}

    /** 从 CVAT 自动分页读取所有 project id */
    private List<Integer> fetchAllCvatProjectIds() {
        List<Integer> out = new ArrayList<>();
        String url = "/api/projects?page_size=500&sort=id";

        while (true) {
            ResponseEntity<String> r = CvatApiUtil.doGet(url);
            if (r == null || !r.getStatusCode().is2xxSuccessful() || StrUtil.isBlank(r.getBody())) {
                log.warn("fetchAllCvatProjectIds failed, url={}, status={}, body={}",
                        url, (r == null ? "null" : r.getStatusCodeValue()), (r == null ? "" : r.getBody()));
                break;
            }

            JSONObject obj = JSONUtil.parseObj(r.getBody());

            Object resultsObj = obj.get("results");
            if (resultsObj instanceof JSONArray arr) {
                for (int i = 0; i < arr.size(); i++) {
                    Object one = arr.get(i);
                    if (one instanceof JSONObject pj) {
                        Integer id = pj.getInt("id");
                        if (id != null) out.add(id);
                    } else {
                        try {
                            JSONObject pj = JSONUtil.parseObj(one);
                            Integer id = pj.getInt("id");
                            if (id != null) out.add(id);
                        } catch (Exception ignore) {}
                    }
                }
            } else {
                // 兼容某些情况下不是分页结构的返回
                Integer id = obj.getInt("id");
                if (id != null) out.add(id);
            }

            String next = obj.getStr("next");
            if (StrUtil.isBlank(next)) break;

            try {
                URI u = URI.create(next);
                String path = u.getRawPath();
                String query = u.getRawQuery();
                url = path + (query == null ? "" : ("?" + query));
            } catch (Exception e) {
                log.warn("parse next url failed: {}", next, e);
                break;
            }
        }

        // 去重并保持顺序
        return new ArrayList<>(new LinkedHashSet<>(out));
    }


    // 简单 body（也可单独放到 vo 包）
    public static class ProjectPullBody {
        private List<Integer> projectIds;
        public List<Integer> getProjectIds() { return projectIds; }
        public void setProjectIds(List<Integer> projectIds) { this.projectIds = projectIds; }
    }

    @Operation(summary = "分页获取列表", description = "分页获取列表-支持属性多选")
    @PostMapping("list")
    public AjaxResult queryList(EngineProjectQuery query) {
        /** 查询条件 */
        LambdaQueryWrapper<EngineProject> wrapper = new LambdaQueryWrapper<>();
        Integer id = query.getId();
        if (id != null) {
            wrapper.eq(EngineProject::getId, id);
        }
        /** 是否删除标记 */
        if (query.getDel_flg() != null && query.getDel_flg() != -1) {
            wrapper.eq(EngineProject::getDel_flg, query.getDel_flg());
        }
        String a_l = query.getA_l();
        if (StrUtil.isNotBlank(a_l)) {
            String[] arr_l = a_l.split(",");
            if (arr_l.length > 1) {
                wrapper.in(EngineProject::getA_l, (Object[]) arr_l);
            } else {
                wrapper.eq(EngineProject::getA_l, a_l);
            }
        }
        String a_s = query.getA_s();
        if (StrUtil.isNotBlank(a_s)) {
            String[] arr_s = a_s.split(",");
            if (arr_s.length > 1) {
                wrapper.in(EngineProject::getA_s, (Object[]) arr_s);
            } else {
                wrapper.eq(EngineProject::getA_s, a_s);
            }
        }
        String a_g = query.getA_g();
        if (StrUtil.isNotBlank(a_g)) {
            String[] arr_g = a_g.split(",");
            if (arr_g.length > 1) {
                wrapper.in(EngineProject::getA_g, (Object[]) arr_g);
            } else {
                wrapper.eq(EngineProject::getA_g, a_g);
            }
        }

        if (StrUtil.isNotBlank(query.getA_n())) {
            wrapper.like(EngineProject::getA_n, query.getA_n());
        }

        String label = query.getQuery_label();
        if (StrUtil.isNotBlank(label)) {
            // 标签删选
            List<Integer> ids = engineLabelService.getProjectByLabel(label, id);
            if (ids.isEmpty()) {
                return AjaxResult.success(Page.of(query.getCurrent(), query.getSize(), 0));
            }
            wrapper.in(EngineProject::getId, ids);
        }
        /** project */
        if (!SessionUtil.isAdminOrHeigh()) {
            Long userId = SessionUtil.getCurUserId();
            List<Integer> ids = userProjectService.getProjects(userId);
            if (ids.isEmpty()) {
                return AjaxResult.success(Page.of(query.getCurrent(), query.getSize(), 0));
            }
            wrapper.in(EngineProject::getId, ids);
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
        Page<EngineProject> page = new Page<>(current, size);

        /** 排序信息 */
        List<OrderItem> orders = query.getOrders();
        if (orders != null && !orders.isEmpty()) {
            page.addOrder(orders);
        }
        engineProjectService.page(page, wrapper);
        page.getRecords().forEach(item -> {
            LambdaQueryWrapper<EngineLabel> label_wraper = new LambdaQueryWrapper<>();
            label_wraper.eq(EngineLabel::getProject_id, item.getId()).orderByAsc(EngineLabel::getId);
            item.setLabels(engineLabelService.list(label_wraper));
        });
        return AjaxResult.success(page);
    }

    @Operation(summary = "查询所有,含标签信息", description = "查询所有,含标签信息,不支持属性的多选")
    @PostMapping("all")
    public AjaxResult queryAll(EngineProjectQuery query) {
        String a_l = query.getA_l();
        if (StrUtil.isBlank(a_l)) {
            a_l = null;
        }
        String a_s = query.getA_s();
        if (StrUtil.isBlank(a_s)) {
            a_s = null;
        }
        String a_g = query.getA_g();
        if (StrUtil.isBlank(a_g)) {
            a_g = null;
        }
        Long user_id = null;
        if (!SessionUtil.isAdminOrHeigh()) {
            user_id = SessionUtil.getCurUserId();
        }
        return AjaxResult.success(engineLabelService.queryProjectLabels(a_l, a_s, a_g, user_id));
    }

    @Operation(summary = "查询所有,不含标签信息", description = "查询所有,不含标签信息,支持属性多选")
    @PostMapping("all2")
    public AjaxResult queryAll2(EngineProjectQuery query) {
        /** 查询条件 */
        LambdaQueryWrapper<EngineProject> wrapper = new LambdaQueryWrapper<>();
        Integer id = query.getId();
        if (id != null) {
            wrapper.eq(EngineProject::getId, id);
        }
        /** 是否删除标记 */
        if (query.getDel_flg() != null && query.getDel_flg() != -1) {
            wrapper.eq(EngineProject::getDel_flg, query.getDel_flg());
        }

        String a_l = query.getA_l();
        if (StrUtil.isNotBlank(a_l)) {
            String[] arr_l = a_l.split(",");
            if (arr_l.length > 1) {
                wrapper.in(EngineProject::getA_l, (Object[]) arr_l);
            } else {
                wrapper.eq(EngineProject::getA_l, a_l);
            }
        }
        String a_s = query.getA_s();
        if (StrUtil.isNotBlank(a_s)) {
            String[] arr_s = a_s.split(",");
            if (arr_s.length > 1) {
                wrapper.in(EngineProject::getA_s, (Object[]) arr_s);
            } else {
                wrapper.eq(EngineProject::getA_s, a_s);
            }
        }
        String a_g = query.getA_g();
        if (StrUtil.isNotBlank(a_g)) {
            String[] arr_g = a_g.split(",");
            if (arr_g.length > 1) {
                wrapper.in(EngineProject::getA_g, (Object[]) arr_g);
            } else {
                wrapper.eq(EngineProject::getA_g, a_g);
            }
        }

        if (StrUtil.isNotBlank(query.getA_n())) {
            wrapper.like(EngineProject::getA_n, query.getA_n());
        }

        String label = query.getQuery_label();
        if (StrUtil.isNotBlank(label)) {
            // 标签删选
            List<Integer> ids = engineLabelService.getProjectByLabel(label, id);
            if (ids.isEmpty()) {
                return AjaxResult.success(Page.of(query.getCurrent(), query.getSize(), 0));
            }
            wrapper.in(EngineProject::getId, ids);
        }
        /** project */
        if (!SessionUtil.isAdminOrHeigh()) {
            Long userId = SessionUtil.getCurUserId();
            List<Integer> ids = userProjectService.getProjects(userId);
            if (ids.isEmpty()) {
                return AjaxResult.success(Page.of(query.getCurrent(), query.getSize(), 0));
            }
            wrapper.in(EngineProject::getId, ids);
        }
        return AjaxResult.success(engineProjectService.list(wrapper));
    }

    @Operation(summary = "添加", description = "添加")
    @PostMapping("add")
    public AjaxResult add(Integer id) {
        EngineProject project = engineProjectService.getById(id);
        if (project != null) {
            return AjaxResult.error("项目已存在");
        }
        ResponseEntity<String> res = CvatApiUtil.getProject(id);
        if (res.getStatusCode().is2xxSuccessful()) {
            project = JSONUtil.toBean(res.getBody(), EngineProject.class);
            EngineProject.parsePName(project);
            if (engineProjectService.save(project)) {
                try {
                    syncCvatService.syncLabels(project.getId());
                } catch (Exception e) {
                }
                return AjaxResult.success();
            }
        }
        return AjaxResult.error();
    }

    @Operation(summary = "修改", description = "修改")
    @PostMapping("update")
    public AjaxResult update(EngineProject record) {
        if (record.getId() == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        return engineProjectService.updateById(record) ? AjaxResult.success() : AjaxResult.error();
    }

    @Operation(summary = "删除", description = "删除")
    @PostMapping("del")
    public AjaxResult del(Integer id) {
        log.warn("del project:{},userId:{}", id, SessionUtil.getCurUserId());
        EngineProject project = engineProjectService.getById(id);
        if (project == null) {
            return AjaxResult.success("项目不存在");
        }
        boolean flg = engineProjectService.removeByIdLink(id);
        if (flg) {
            try {
                FileUtil.del(Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK, id.toString()));
            } catch (Exception e) {
                log.warn("del project [{}] err:{}", id, e);
            }
        }
        return flg ? AjaxResult.success() : AjaxResult.error();
    }

    @Operation(summary = "同步全部", description = "同步全部")
    @PostMapping("sync/all")
    public AjaxResult syncall() {
        List<EngineProject> list = engineProjectService.list();
        int success = 0;
        int fail = 0;
        for (int i = 0; i < list.size(); i++) {
            AjaxResult res = syncOne(list.get(i).getId());
            if (res.isSuccess()) {
                success++;
            } else {
                fail++;
            }
        }
        JSONObject json = new JSONObject();
        json.set("success", success);
        json.set("fail", fail);
        return AjaxResult.success(json);
    }

    @Operation(summary = "同步单个", description = "同步单个")
    @PostMapping("sync/one")
    public AjaxResult syncOne(Integer id) {
        ResponseEntity<String> res = CvatApiUtil.getProject(id);
        if (res.getStatusCode().is2xxSuccessful()) {
            EngineProject project = JSONUtil.toBean(res.getBody(), EngineProject.class);
            EngineProject.parsePName(project);
            syncCvatService.syncLabels(id);
            project.setDel_flg(CodeMap.CVAT_EXIST_FLG);
            return engineProjectService.updateById(project) ? AjaxResult.success() : AjaxResult.error();
        } else {
            String body = res.getBody();
            if (StrUtil.equals(body, CodeMap.CVAT_HAS_DEL)) {
                EngineProject project = new EngineProject();
                LambdaUpdateWrapper<EngineProject> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(EngineProject::getId, id).ne(EngineProject::getDel_flg, CodeMap.CVAT_DEL_FLG);
                project.setId(id);
                project.setDel_flg(CodeMap.CVAT_DEL_FLG);
                // 更新了project状态
                if (engineProjectService.update(project, wrapper)) {
                    // 更新task状态
                    LambdaUpdateWrapper<EngineTask> wrapper2 = new LambdaUpdateWrapper<>();
                    wrapper2.eq(EngineTask::getProject_id, id);
                    EngineTask record = new EngineTask();
                    record.setDel_flg(CodeMap.CVAT_DEL_FLG);
                    engineTaskService.update(record, wrapper2);
                    log.info("async project: project has del");
                }
            }
        }
        return AjaxResult.error(res.getBody());
    }

    /**
     * 根据标签查看,包含其中一个即可
     * 
     * @param labels
     * @param project_id
     * @return
     */
    @PostMapping("orLabels")
    public AjaxResult queryOrEngineLabels(String labels, Integer project_id) {
        if (StrUtil.isBlank(labels)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        String[] arr = labels.split(",");
        if (arr.length == 0) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        List<Integer> project_ids = engineLabelService.queryProjectsOrLabels(Arrays.asList(arr));
        return AjaxResult.success(project_ids);
    }

    @PostMapping("distinct")
    public AjaxResult getDistinctFields(String field, Integer project_id) {
        if (StrUtil.isBlank(field)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        List<EngineProject> list = engineProjectService.getDistinctFields(field, project_id);
        return AjaxResult.success(list);
    }
}
