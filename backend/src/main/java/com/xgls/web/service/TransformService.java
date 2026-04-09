package com.xgls.web.service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.TrainArgs;
import com.xgls.web.entity.TrainData;
import com.xgls.web.entity.TrainLabel;
import com.xgls.web.entity.TrainTask;
import com.xgls.web.entity.TrainYoloFile;
import com.xgls.web.mapper.TrainTaskMapper;
import com.xgls.web.mapper.TrainYoloFileMapper;
import com.xgls.web.utils.CocoJsonMerger;
import com.xgls.web.vo.TrainForm;
import com.xgls.web.vo.TrainItem;
import com.xgls.web.vo.ValParams;
import com.xgls.web.wscontroller.WsTrainController;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TransformService {
    @Value("${sys.root-upload}")
    String rootPath;
    @Autowired
    TrainTaskMapper trainTaskMapper;
    @Autowired
    TrainYoloFileMapper trainYoloFileMapper;

    @Async
    public void handleData(TrainForm form, TrainForm oldForm) {

        log.info("~start~transform task id:{},name:{}", form.getId(), form.getName());
        boolean flg = true;
        /** 1.生成新的标注数据 2.生成新的cfg文件 */
        boolean data_change_flg = false;
        boolean cfg_change_flg = false;
        // boolean val_ratio_flg = false;
        if (oldForm == null) {// 新建的,都要更新
            data_change_flg = true;
            cfg_change_flg = true;
        } else {// upadte,要比较
            if (!isDataSame(form, oldForm)) {
                data_change_flg = true;
            }
            if (!isCfgSame(form, oldForm)) {
                cfg_change_flg = true;
            }
        }
        try {
            if (data_change_flg) {
                log.info("annotation rebuild");
                flg &= transAnnotation(form);
            }
            if (cfg_change_flg) {
                log.info("cfg file rebuild");
                flg &= transNetCfg(form);
            }
            log.info("~finish~transfrom task id:{},name:{}", form.getId(), form.getName());
        } catch (Exception e) {
            flg = false;
        }
        /** 3.更新任务的状态为 可运行 */
        TrainTask task = new TrainTask();
        task.setId(form.getId());
        task.setStatus(flg ? CodeMap.TRAIN_TASK_STATUS_READY : CodeMap.TRAIN_TASK_STATUS_CFG_FAIL);
        task.setUpdated_date(LocalDateTime.now());
        task.setObj_num(form.getObj_num());
        trainTaskMapper.updateById(task);
        // 发布消息
        task.setMsg_type(CodeMap.SCRIPT_TYPE_TRAIN);
        WsTrainController.senMsgToAll(JSONUtil.toJsonStr(task));
    }

    // 配置文件重新生成: 1)标签数目变了 2)配置文件的id变了
    private boolean isCfgSame(TrainForm form, TrainForm oldForm) {
        // 标签数目
        if (!form.getCls_num().equals(oldForm.getCls_num())) {
            return false;
        }
        TrainArgs args = form.getArgs();
        TrainArgs old_Args = oldForm.getArgs();
        // 自动上传,不需要重新生成,再之前已经生成了
        return (args.getWeights().equals(old_Args.getWeights()) || args.getWeights() == CodeMap.USE_SELF_ID)
                && args.getCfg().equals(old_Args.getCfg())
                && args.getHyp().equals(old_Args.getHyp());

    }

    // 标注重新生成: 1)标签变了 2)样本集变了 3)标签过滤策略改变
    private boolean isDataSame(TrainForm form, TrainForm oldForm) {
        // 过滤策略改变
        Integer imgz = form.getArgs().getImg_size();
        Integer f_max = form.getArgs().getF_max();
        Integer f_min = form.getArgs().getF_min();
        Integer f_area = form.getArgs().getF_area();

        Integer imgz_old = oldForm.getArgs().getImg_size();
        Integer f_max_old = oldForm.getArgs().getF_max();
        Integer f_min_old = oldForm.getArgs().getF_min();
        Integer f_area_old = oldForm.getArgs().getF_area();
        // 过滤参数改变
        if (!f_max.equals(f_max_old) || !f_min.equals(f_min_old) || !f_area.equals(f_area_old)) {
            log.info("Labels are regenerated, and filter parameters are modified");
            return false;
            // 过滤参数不同时为0,且 img_size改变
        } else if (!imgz.equals(imgz_old) && f_max != 0 || f_min != 0 || f_area != 0) {
            log.info("The tag is regenerated and the imag_size is changed");
            return false;
        }

        // 标签数目
        if (!form.getCls_num().equals(oldForm.getCls_num())) {
            log.info("The label is regenerated and the number of classes is changed");
            return false;
        }
        // 样本数目
        if (!form.getPrj_num().equals(oldForm.getPrj_num()) ||
                !form.getTask_num().equals(oldForm.getTask_num()) ||
                !form.getImg_num().equals(oldForm.getImg_num())) {
            log.info("The label is regenerated and the task set is modified");
            return false;
        }
        TrainData new_t = form.getData();
        TrainData old_t = oldForm.getData();
        // 标签
        if (!StrUtil.equals(new_t.getLabels(), old_t.getLabels())) {
            log.info("The label is regenerated, and the label type is changed");
            return false;
        }
        return StrUtil.equals(new_t.getData(), old_t.getData())
                && StrUtil.equals(new_t.getVal(), old_t.getVal());
    }

    /** 转换标注数据 */
    private boolean transAnnotation(TrainForm form) {
        /**
         * 1.解析标签 ----> 得到标签的映射表
         * [{"id":27,"name":"工程车","nick":"","merge":1,"children":["卡车","吊车","挖掘机"]},
         * {"id":21,"name":"火焰","nick":null,"merge":0,"children":null}]
         */
        form.setObj_num(0L);// 统计obj
        HashMap<String, Integer> map = new HashMap<>();
        List<TrainLabel> lableList = JSONUtil.toList(form.getData().getLabels(), TrainLabel.class);
        form.setCls_num(lableList.size());
        for (int i = 0; i < lableList.size(); i++) {
            TrainLabel one = lableList.get(i);
            Integer merge = one.getMerge();
            if (merge != null && merge == CodeMap.LABEL_IS_MERAGE && one.getChildren() != null) {
                String[] chs = one.getChildren().split(",");
                for (int j = 0; j < chs.length; j++) {
                    map.put(chs[j], i);
                }
            } else {
                map.put(one.getName(), i);
            }
        }

        /**
         * 2.样本集
         * [{"pid":17,"labels":["火焰","烟雾","安全帽","人体","香烟"],"tasks":[47,41,40,39,45,46,43,42]},
         * {"pid":18,"labels":["小汽车","挖掘机","吊车","卡车","火焰"],"tasks":[50,48,49]}]
         * 
         * 
         */
        // 原始样本根目录
        Path srcTaskPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK);
        // 训练根目录
        Path labelsPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, form.getId().toString(),
                CodeMap.DIR_TRAIN_LABELS);

        /**
         * 如果原来的标签存在,先删除
         */
        FileUtil.del(labelsPath);
        labelsPath = FileUtil.mkdir(labelsPath);

        /** 先转换训练集 */
        List<TrainItem> dataList = JSONUtil.toList(form.getData().getData(), TrainItem.class);
        Set<Integer> taskSet = new HashSet<>();// task的集合,
        for (int i = 0; i < dataList.size(); i++) {
            // 转换单个项目
            transformProject(dataList.get(i), srcTaskPath, labelsPath, map, form, taskSet, true);
        }
        /** 再转换测试集 */
        List<TrainItem> valList = JSONUtil.toList(form.getData().getVal(), TrainItem.class);
        for (int i = 0; i < valList.size(); i++) {
            // 转换单个项目
            transformProject(valList.get(i), srcTaskPath, labelsPath, map, form, taskSet, false);
        }
        /**
         * 生成data.yaml文件
         */
        Path filePath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, form.getId().toString(),
                CodeMap.DIR_TRAIN_FILE);
        FileUtil.del(filePath.resolve("data.yaml"));
        FileUtil.mkdir(filePath);

        createDataYml(filePath, srcTaskPath, dataList, valList, lableList);

        return true;
    }

    /**
     * 转换单个project
     * 
     * @param form
     */
    private void transformProject(TrainItem trainItem, Path srcTaskPath, Path labelsPath,
            HashMap<String, Integer> map, TrainForm form, Set<Integer> taskSet, boolean isTrain) {
        // 获取到标注文件地址
        String pid = trainItem.getPid().toString();
        log.info("~start~transform project:{}", pid);
        // 项目的目录
        Path srcProjectPath = srcTaskPath.resolve(pid);
        Path projectPath = labelsPath.resolve(pid);

        List<Integer> list = trainItem.getTasks();
        try {
            if (isTrain) {// 训练集
                // 一个任务一个任务的转换
                for (int j = 0; j < list.size(); j++) {
                    String tid = list.get(j).toString();
                    Path xmlPath = srcProjectPath.resolve(tid).resolve("annotations.xml");
                    Path targetDir = projectPath.resolve(tid);
                    targetDir = FileUtil.mkdir(targetDir);
                    transformTask(xmlPath, targetDir, map, form);
                }
                // 添加所有的taskid ,代表已经转换过了,无需继续转换
                taskSet.addAll(list);
            } else {// 验证集 ,避免重复生成,利用set判断
                for (int j = 0; j < list.size(); j++) {
                    if (taskSet.contains(list.get(j))) {
                        continue;
                    }
                    String tid = list.get(j).toString();
                    Path xmlPath = srcProjectPath.resolve(tid).resolve("annotations.xml");
                    Path targetDir = projectPath.resolve(tid);
                    targetDir = FileUtil.mkdir(targetDir);
                    transformTask(xmlPath, targetDir, map, form);
                }
            }
        } catch (Exception e) {
            log.error("transform project err:{}", e.getMessage());
        }

        log.info("~finish~transfrom project:{}", pid);

    }

    /**
     * 转换单个task
     * 
     * @param form
     */
    private void transformTask(Path xmlPath, Path targetDir, HashMap<String, Integer> map, TrainForm form) {
        Document doc = XmlUtil.readXML(xmlPath.toFile());
        NodeList nodeList = doc.getElementsByTagName("image");
        // 小标签过滤参数
        Integer imgsz = form.getArgs().getImg_size();// 统一尺寸
        Integer f_max = form.getArgs().getF_max();// 长边限制
        Integer f_min = form.getArgs().getF_min();// 短边限制
        Integer f_area = form.getArgs().getF_area();// 面积限制

        for (int i = 0; i < nodeList.getLength(); i++) {
            // 一个节点,就是一张图片
            // <name="0a52d61f_c53f_4f8e_9984_6f903141b174.JPG" width="3024" height="4032">
            Node node = nodeList.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String imgName = element.getAttribute("name");
                String txtName = imgName.substring(0, imgName.lastIndexOf(".")) + ".txt";
                Integer w = Integer.parseInt(element.getAttribute("width"));
                Integer h = Integer.parseInt(element.getAttribute("height"));
                // 缩放比
                int t_max, t_min, t_area;
                if (w > h) {
                    t_max = f_max * w / imgsz;
                    t_min = f_min * w / imgsz;
                    t_area = f_area * w * w / (imgsz * imgsz);
                } else {
                    t_max = f_max * h / imgsz;
                    t_min = f_min * h / imgsz;
                    t_area = f_area * (h * h) / (imgsz * imgsz);
                }
                // <box label="安全帽" source="manual" occluded="0" xtl="2091.48" ytl="2380.01"
                // xbr="2288.76" ybr="2589.62" z_order="0"> </box>
                NodeList boxList = element.getChildNodes();
                // 统计标注对象数目
                form.setObj_num(form.getObj_num() + boxList.getLength());

                Path txtFilePath = targetDir.resolve(txtName);
                try {
                    Files.createDirectories(txtFilePath.getParent());
                    try (BufferedWriter writer = new BufferedWriter(
                            new FileWriter(txtFilePath.toFile()))) {
                        for (int j = 0; j < boxList.getLength(); j++) {
                            Node box = boxList.item(j);
                            if (box.getNodeType() == Node.ELEMENT_NODE) {
                                Element one = (Element) box;
                                Integer idx = map.get(one.getAttribute("label"));
                                if (idx != null) {// 命中才写入
                                    // 支持box以及mask标注
                                    String tagName = one.getTagName();
                                    double x1, x2, y1, y2, m_w, m_h;
                                    switch (tagName) {
                                        case "box":
                                            x1 = Double.parseDouble(one.getAttribute("xtl"));
                                            y1 = Double.parseDouble(one.getAttribute("ytl"));
                                            x2 = Double.parseDouble(one.getAttribute("xbr"));
                                            y2 = Double.parseDouble(one.getAttribute("ybr"));
                                            m_w = Math.abs(x2 - x1);
                                            m_h = Math.abs(y2 - y1);
                                            break;
                                        case "mask":
                                            x1 = Double.parseDouble(one.getAttribute("left"));
                                            y1 = Double.parseDouble(one.getAttribute("top"));
                                            m_w = Double.parseDouble(one.getAttribute("width"));
                                            m_h = Double.parseDouble(one.getAttribute("height"));
                                            x2 = x1 + m_w - 1;
                                            y2 = y1 + m_h - 1;
                                            break;
                                        case "polygon":
                                            double[] arr = convertPolygonPointsToBBox(one.getAttribute("points"));
                                            x1 = arr[0];
                                            y1 = arr[1];
                                            m_w = arr[2];
                                            m_h = arr[3];
                                            x2 = x1 + m_w - 1;
                                            y2 = y1 + m_h - 1;
                                            break;
                                        default:
                                            continue;
                                    }
                                    // 小标签过滤
                                    double h_max = m_w;
                                    double h_min = m_h;
                                    if (m_w < m_h) {
                                        h_max = m_h;
                                        h_min = m_w;
                                    }
                                    if (h_max > t_max && h_min > t_min && (m_w * m_h) > t_area) {
                                        String line = String.format(
                                                "%d %f %f %f %f\n",
                                                idx,
                                                (x2 + x1) / 2 / w,
                                                (y2 + y1) / 2 / h,
                                                m_w / w,
                                                m_h / h);
                                        writer.write(line);
                                    } else {
                                        log.info("del samll labels {},{},[{},{}],[{},{}]", targetDir, txtName, h_max,
                                                h_min, t_max,
                                                t_min);
                                    }
                                }
                            }
                        }
                        writer.flush();
                    } catch (IOException e) {
                        log.warn("transformTask err:{}", e.getMessage());
                    }
                } catch (IOException e2) {
                    log.warn("transformTask err2:{}", e2.getMessage());
                }
            }
        }
    }

    /** 生成data.yaml文件 */
    private void createDataYml(Path filePath, Path srcTaskPath, List<TrainItem> dataList, List<TrainItem> valList,
            List<TrainLabel> lableList) {
        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(filePath.resolve("data.yaml").toString(), false))) {
            /**
             * nc: 3 # number of classes
             * names:
             * - car
             * - person
             * - tank
             * path: /myfiles/src/task # dataset root dir
             * train:
             * - 1/2/images
             * - 1/2/images
             * - 1/3/images
             * val:
             */
            writer.write(String.format("nc: %d\n", lableList.size()));

            writer.write("names:\n");
            for (int k = 0; k < lableList.size(); k++) {
                writer.write(String.format(" - %s\n", lableList.get(k).getName()));
            }

            writer.write(String.format("path: %s\n", srcTaskPath.toString()));

            writer.write("train:\n");
            for (int i = 0; i < dataList.size(); i++) {
                // 转换单个项目
                TrainItem item = dataList.get(i);
                List<Integer> tList = item.getTasks();
                for (int j = 0; j < tList.size(); j++) {
                    String line = String.format(" - %d/%d/images/\n", item.getPid(), tList.get(j));
                    writer.write(line);
                }
            }

            writer.write("val:\n");
            for (int i = 0; i < valList.size(); i++) {
                // 转换单个项目
                TrainItem item = valList.get(i);
                List<Integer> tList = item.getTasks();
                for (int j = 0; j < tList.size(); j++) {
                    String line = String.format(" - %d/%d/images/\n", item.getPid(), tList.get(j));
                    writer.write(line);
                }
            }
            writer.flush();
        } catch (IOException e) {

        }
    }

    /** 拷贝配置文件 */
    private boolean transNetCfg(TrainForm form) {
        log.info("~~~~start copy cfg files");
        TrainArgs args = form.getArgs();
        // weight id 0 代表使用上传权重文件
        if (args.getWeights() != CodeMap.USE_SELF_ID) {
            copyFileById(args.getWeights(), form.getId());
        }
        if (0 != args.getHyp()) {
            copyFileById(args.getHyp(), form.getId());
        }
        if (0 != args.getCfg()) {
            copyAndUpdateYaml(args.getCfg(), form.getId(), form.getCls_num());
        }
        log.info("~~~~finish copy cfg files");
        return true;
    }

    private void copyFileById(Integer id, Integer taskId) {
        TrainYoloFile record = trainYoloFileMapper.selectById(id);
        String fileName = String.format("%s_%d%s", record.getType(), id, record.getPath());
        Path src = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_YOLO, fileName);
        Path target = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, taskId.toString(),
                CodeMap.DIR_TRAIN_FILE,
                fileName);
        FileUtil.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void copyAndUpdateYaml(Integer id, Integer pid, Integer nc) {
        TrainYoloFile record = trainYoloFileMapper.selectById(id);
        String fileName = String.format("%s_%d%s", record.getType(), id, record.getPath());
        Path src = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_YOLO, fileName);
        Path target = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, pid.toString(), CodeMap.DIR_TRAIN_FILE,
                fileName);
        FileUtil.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
        FileUtil.appendString(String.format("\nnc: %d", nc), target.toFile(), Charset.defaultCharset());
    }

    @Async
    public void handleDataMMdet(TrainForm form, TrainForm oldForm) {
        log.info("~start~transform mmdet task id:{},name:{}", form.getId(), form.getName());
        boolean flg = true;
        /** 1.生成新的标注数据 */
        boolean data_change_flg = false;
        boolean cfg_change_flg = false;
        if (oldForm == null) {// 新建的,都要更新
            data_change_flg = true;
            cfg_change_flg = true;
        } else {// upadte,要比较
            if (!isDataSameMMdet(form, oldForm)) {
                data_change_flg = true;
            }
            if (!isCfgSameMMdet(form, oldForm)) {
                cfg_change_flg = true;
            }
        }
        try {
            if (data_change_flg) {
                log.info("annotation rebuild");
                flg &= transAnnotationMMdet(form);
            }
            if (cfg_change_flg) {
                log.info("cfg file rebuild");
                flg &= transNetCfgMMdet(form);
            }
            log.info("~finish~transfrom task id:{},name:{}", form.getId(), form.getName());
        } catch (Exception e) {
            flg = false;
        }
        /** 3.更新任务的状态为 可运行 */
        TrainTask task = new TrainTask();
        task.setId(form.getId());
        task.setStatus(flg ? CodeMap.TRAIN_TASK_STATUS_READY : CodeMap.TRAIN_TASK_STATUS_CFG_FAIL);
        task.setUpdated_date(LocalDateTime.now());
        task.setObj_num(form.getObj_num());
        trainTaskMapper.updateById(task);
        // 发布消息
        task.setMsg_type(CodeMap.SCRIPT_TYPE_TRAIN);
        WsTrainController.senMsgToAll(JSONUtil.toJsonStr(task));
    }

    // 标注重新生成: 1)标签变了 2)样本集变了 3)标签过滤策略改变
    private boolean isDataSameMMdet(TrainForm form, TrainForm oldForm) {
        // 过滤策略改变
        Integer img_w = form.getArgs().getImg_w();
        Integer img_h = form.getArgs().getImg_h();
        Integer f_max = form.getArgs().getF_max();
        Integer f_min = form.getArgs().getF_min();
        Integer f_area = form.getArgs().getF_area();

        Integer img_w_old = oldForm.getArgs().getImg_w();
        Integer img_h_old = oldForm.getArgs().getImg_h();
        Integer f_max_old = oldForm.getArgs().getF_max();
        Integer f_min_old = oldForm.getArgs().getF_min();
        Integer f_area_old = oldForm.getArgs().getF_area();
        // 过滤参数改变
        if (!f_max.equals(f_max_old) || !f_min.equals(f_min_old) || !f_area.equals(f_area_old)) {
            log.info("Labels are regenerated, and filter parameters are modified");
            return false;
            // 过滤参数不同时为0,且 img_size改变
        } else if ((!img_w.equals(img_w_old) || !img_h.equals(img_h_old)) && f_max != 0 || f_min != 0 || f_area != 0) {
            log.info("The tag is regenerated and the imag_w,h is changed");
            return false;
        }

        // 标签数目
        if (!form.getCls_num().equals(oldForm.getCls_num())) {
            log.info("The label is regenerated and the number of classes is changed");
            return false;
        }
        // 样本数目
        if (!form.getPrj_num().equals(oldForm.getPrj_num()) ||
                !form.getTask_num().equals(oldForm.getTask_num()) ||
                !form.getImg_num().equals(oldForm.getImg_num())) {
            log.info("The label is regenerated and the task set is modified");
            return false;
        }
        TrainData new_t = form.getData();
        TrainData old_t = oldForm.getData();
        // 标签
        if (!StrUtil.equals(new_t.getLabels(), old_t.getLabels())) {
            log.info("The label is regenerated, and the label type is changed");
            return false;
        }
        return StrUtil.equals(new_t.getData(), old_t.getData())
                && StrUtil.equals(new_t.getVal(), old_t.getVal());
    }

    // 配置文件重新生成: 1)标签数目变了 2)配置文件的id变了
    private boolean isCfgSameMMdet(TrainForm form, TrainForm oldForm) {
        TrainArgs args = form.getArgs();
        TrainArgs old_Args = oldForm.getArgs();
        // 自动上传,不需要重新生成,再之前已经生成了
        return (args.getWeights().equals(old_Args.getWeights()) || args.getWeights() == CodeMap.USE_SELF_ID);
    }

    private boolean transNetCfgMMdet(TrainForm form) {
        log.info("~~~~start copy cfg files");
        TrainArgs args = form.getArgs();
        // weight id 0 代表使用上传权重文件
        if (args.getWeights() != CodeMap.USE_SELF_ID) {
            copyFileById(args.getWeights(), form.getId());
        }
        log.info("~~~~finish copy cfg files");
        return true;
    }

    /** 转换标注数据 coco格式 */
    private boolean transAnnotationMMdet(TrainForm form) {
        /**
         * 1.解析标签 ----> 得到标签的映射表
         * [{"id":27,"name":"工程车","nick":"","merge":1,"children":["卡车","吊车","挖掘机"]},
         * {"id":21,"name":"火焰","nick":null,"merge":0,"children":null}]
         */
        form.setObj_num(0L);// 统计obj
        HashMap<String, Integer> map = new HashMap<>();
        List<TrainLabel> lableList = JSONUtil.toList(form.getData().getLabels(), TrainLabel.class);
        form.setCls_num(lableList.size());

        for (int i = 0; i < lableList.size(); i++) {
            TrainLabel one = lableList.get(i);
            Integer merge = one.getMerge();
            if (merge != null && merge == CodeMap.LABEL_IS_MERAGE && one.getChildren() != null) {
                String[] chs = one.getChildren().split(",");
                for (int j = 0; j < chs.length; j++) {
                    map.put(chs[j], i);
                }
            } else {
                map.put(one.getName(), i);
            }
        }

        /**
         * 2.样本集
         * [{"pid":17,"labels":["火焰","烟雾","安全帽","人体","香烟"],"tasks":[47,41,40,39,45,46,43,42]},
         * {"pid":18,"labels":["小汽车","挖掘机","吊车","卡车","火焰"],"tasks":[50,48,49]}]
         * 
         * 
         */
        // 原始样本根目录
        Path srcTaskPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK);
        // 训练根目录
        Path labelsPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, form.getId().toString(),
                CodeMap.DIR_TRAIN_LABELS);

        /**
         * 如果原来的标签存在,先删除
         */
        FileUtil.del(labelsPath);
        labelsPath = FileUtil.mkdir(labelsPath);
        Path catePath = labelsPath.resolve("categories.json");
        Path imgsPath = labelsPath.resolve("images.json");
        Path annPath = labelsPath.resolve("annotations.json");
        Path imgsValPath = labelsPath.resolve("images_val.json");
        Path annValPath = labelsPath.resolve("annotations_val.json");
        try (BufferedWriter cate_writer = new BufferedWriter(
                new FileWriter(catePath.toString()));
                BufferedWriter images_writer = new BufferedWriter(
                        new FileWriter(imgsPath.toString()));
                BufferedWriter ann_writer = new BufferedWriter(
                        new FileWriter(annPath.toString()));
                BufferedWriter images_writer_val = new BufferedWriter(
                        new FileWriter(imgsValPath.toString()));
                BufferedWriter ann_writer_val = new BufferedWriter(
                        new FileWriter(annValPath.toString()));) {
            /** 先转换训练集 */
            cate_writer.write("[");
            for (int i = 0; i < lableList.size(); i++) {
                if (i == lableList.size() - 1) {
                    cate_writer.write(String.format("{\"id\":%d,\"name\":\"%s\"}", i, lableList.get(i).getName()));
                } else {
                    cate_writer.write(String.format("{\"id\":%d,\"name\":\"%s\"},", i, lableList.get(i).getName()));
                }
            }
            cate_writer.write("]");
            cate_writer.flush();
            cate_writer.close();
            // 转换训练集
            transAnnData(images_writer, ann_writer, srcTaskPath, form, map, false);
            // 转换验证集
            transAnnData(images_writer_val, ann_writer_val, srcTaskPath, form, map, true);
            // 合并文件
            CocoJsonMerger.merge(
                    labelsPath.resolve("train.json"), catePath, imgsPath, annPath);
            FileUtil.del(imgsPath);
            FileUtil.del(annPath);
            CocoJsonMerger.merge(
                    labelsPath.resolve("val.json"), catePath, imgsValPath, annValPath);
            FileUtil.del(imgsValPath);
            FileUtil.del(annValPath);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void transAnnData(BufferedWriter images_writer, BufferedWriter ann_writer, Path srcTaskPath,
            TrainForm form, HashMap<String, Integer> map, boolean isVal) throws IOException {
        // 转换图片
        images_writer.write("[");
        ann_writer.write("[");
        List<TrainItem> dataList = JSONUtil.toList(isVal ? form.getData().getVal() : form.getData().getData(),
                TrainItem.class);
        if (isVal) {
            form.setImg_val_num(0);// 初始化img,重新编号
            form.setObj_val_num(0L);
        } else {
            form.setImg_num(0);// 初始化img,重新编号
            form.setObj_num(0L);
        }
        for (int i = 0; i < dataList.size(); i++) {
            // 转换单个项目
            transformProjectMMdet(dataList.get(i), srcTaskPath, map, form, images_writer, ann_writer, isVal);
        }
        images_writer.write("]");
        ann_writer.write("]");
        images_writer.flush();
        ann_writer.flush();
        images_writer.close();
        ann_writer.close();
    }

    private void transformProjectMMdet(TrainItem trainItem, Path srcTaskPath, HashMap<String, Integer> map,
            TrainForm form, BufferedWriter images_writer, BufferedWriter ann_writer, boolean isVal) throws IOException {
        // 获取到标注文件地址
        String pid = trainItem.getPid().toString();
        log.info("~start~transform project:{}", pid);
        // 项目的目录
        Path srcProjectPath = srcTaskPath.resolve(pid);
        List<Integer> list = trainItem.getTasks();
        // 一个任务一个任务的转换
        for (int j = 0; j < list.size(); j++) {
            String tid = list.get(j).toString();
            String imgPrefix = pid + "/" + tid + "/images/";
            Path xmlPath = srcProjectPath.resolve(tid).resolve("annotations.xml");
            transformTaskMmdet(xmlPath, map, form, images_writer, ann_writer, imgPrefix, isVal);
        }
        log.info("~finish~transfrom project:{}", pid);
    }

    private void transformTaskMmdet(Path xmlPath, HashMap<String, Integer> map, TrainForm form,
            BufferedWriter images_writer, BufferedWriter ann_writer, String imgPrefix, boolean isVal)
            throws IOException {
        Document doc = XmlUtil.readXML(xmlPath.toFile());
        NodeList nodeList = doc.getElementsByTagName("image");
        // 小标签过滤参数
        Double img_w = form.getArgs().getImg_w().doubleValue();// 缩放宽
        Double img_h = form.getArgs().getImg_h().doubleValue();// 缩放高
        Integer f_max = form.getArgs().getF_max();// 长边限制
        Integer f_min = form.getArgs().getF_min();// 短边限制
        Integer f_area = form.getArgs().getF_area();// 面积限制
        int img_num = isVal ? form.getImg_val_num() : form.getImg_num();
        long obj_num = isVal ? form.getObj_val_num() : form.getObj_num();
        for (int i = 0; i < nodeList.getLength(); i++) {
            img_num++;
            // 一个节点,就是一张图片
            // <name="0a52d61f_c53f_4f8e_9984_6f903141b174.JPG" width="3024" height="4032">
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String imgName = element.getAttribute("name");
                Integer w = Integer.parseInt(element.getAttribute("width"));
                Integer h = Integer.parseInt(element.getAttribute("height"));
                if (img_num != 1) {
                    images_writer.append(",");
                }
                images_writer
                        .write(String.format("{\"id\":%d,\"width\":%d,\"height\":%d,\"file_name\":\"%s\"}", img_num, w,
                                h, imgPrefix + imgName));
                // 缩放比
                double scal_w = img_w / w;
                double scal_h = img_h / h;
                // <box label="安全帽" source="manual" occluded="0" xtl="2091.48" ytl="2380.01"
                // xbr="2288.76" ybr="2589.62" z_order="0"> </box>
                NodeList boxList = element.getChildNodes();
                for (int j = 0; j < boxList.getLength(); j++) {
                    Node box = boxList.item(j);
                    if (box.getNodeType() == Node.ELEMENT_NODE) {
                        Element one = (Element) box;
                        Integer idx = map.get(one.getAttribute("label"));
                        if (idx != null) {// 命中才写入
                            // 支持box以及mask标注
                            String tagName = one.getTagName();
                            double x1, x2, y1, y2, m_w, m_h, m_area;
                            switch (tagName) {
                                case "box":
                                    x1 = Double.parseDouble(one.getAttribute("xtl"));
                                    y1 = Double.parseDouble(one.getAttribute("ytl"));
                                    x2 = Double.parseDouble(one.getAttribute("xbr"));
                                    y2 = Double.parseDouble(one.getAttribute("ybr"));
                                    m_w = Math.abs(x2 - x1);
                                    m_h = Math.abs(y2 - y1);
                                    m_area = m_w * m_h;
                                    break;
                                case "mask":
                                    x1 = Double.parseDouble(one.getAttribute("left"));
                                    y1 = Double.parseDouble(one.getAttribute("top"));
                                    m_w = Double.parseDouble(one.getAttribute("width"));
                                    m_h = Double.parseDouble(one.getAttribute("height"));
                                    // x2 = x1 + m_w - 1;
                                    // y2 = y1 + m_h - 1;
                                    m_area = m_w * m_h;
                                    break;
                                case "polygon":
                                    double[] arr = convertPolygonPointsToBBox(one.getAttribute("points"));
                                    x1 = arr[0];
                                    y1 = arr[1];
                                    m_w = arr[2];
                                    m_h = arr[3];
                                    m_area = m_w * m_h;
                                    break;
                                default:
                                    continue;
                            }
                            // 小标签过滤
                            double w_last = m_w * scal_w;
                            double h_last = m_h * scal_h;
                            double max_s, min_s;
                            if (w_last > h_last) {
                                max_s = w_last;
                                min_s = h_last;
                            } else {
                                min_s = w_last;
                                max_s = h_last;
                            }
                            if (max_s > f_max && min_s > f_min && w_last * h_last > f_area) {
                                obj_num++;
                                if (obj_num != 1) {
                                    ann_writer.write(",");
                                }
                                ann_writer.write(String.format(
                                        "{\"id\":%d,\"image_id\":%d,\"category_id\":%d,\"area\":%.1f,\"bbox\":[%.1f,%.1f,%.1f,%.1f],\"iscrowd\":0}",
                                        obj_num, img_num, idx, m_area, x1, y1, m_w, m_h));
                            } else {
                                log.info("del samll coco labels {},[{},{}],[{},{}],[{}]", imgName, w_last, h_last,
                                        f_max, f_min, f_area);
                            }
                        }
                    }
                }
            }
        }
        if (isVal) {
            form.setImg_val_num(img_num);
            form.setObj_val_num(obj_num);
        } else {
            form.setImg_num(img_num);
            form.setObj_num(obj_num);
        }
    }

    /** poligon转 bbox */
    public static double[] convertPolygonPointsToBBox(String pointsStr) {
        if (pointsStr == null || pointsStr.trim().isEmpty()) {
            return new double[] { 0, 0, 0, 0 };
        }
        // 按分号分割多个点
        List<String> pointPairs = Arrays.asList(pointsStr.split(";"));

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        for (String pair : pointPairs) {
            String[] xy = pair.split(",");
            if (xy.length != 2) {
                // 返回
                return new double[] { 0, 0, 0, 0 };
            }
            try {
                double x = Double.parseDouble(xy[0].trim());
                double y = Double.parseDouble(xy[1].trim());

                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);

            } catch (NumberFormatException e) {
                // 可以选择跳过或者抛出异常
                // 返回
                return new double[] { 0, 0, 0, 0 };
            }
        }
        // 计算宽高
        double width = maxX - minX;
        double height = maxY - minY;
        return new double[] { minX, minY, width, height };
    }

    /** 转换验证集 */
    public void transValAnnotation(List<TrainLabel> lableList, Integer taskId, List<TrainItem> dataList,
            String val_name) {
        /**
         * 1.解析标签 ----> 得到标签的映射表
         * [{"id":27,"name":"工程车","nick":"","merge":1,"children":"卡车,吊车,挖掘机"},
         * {"id":21,"name":"火焰","nick":null,"merge":0,"children":null}]
         */
        HashMap<String, Integer> map = new HashMap<>();
        for (int i = 0; i < lableList.size(); i++) {
            TrainLabel one = lableList.get(i);
            Integer merge = one.getMerge();
            if (merge != null && merge == CodeMap.LABEL_IS_MERAGE && one.getChildren() != null) {
                String[] chs = one.getChildren().split(",");
                for (int j = 0; j < chs.length; j++) {
                    map.put(chs[j], i);
                }
            } else {
                map.put(one.getName(), i);
            }
        }

        /**
         * 2.样本集
         * [{"pid":17,"labels":["火焰","烟雾","安全帽","人体","香烟"],"tasks":[47,41,40,39,45,46,43,42]},
         * {"pid":18,"labels":["小汽车","挖掘机","吊车","卡车","火焰"],"tasks":[50,48,49]}]
         * 
         * 
         */
        // 原始样本根目录
        Path srcTaskPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK);
        // 训练根目录
        Path labelsPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, taskId.toString(),
                CodeMap.DIR_TRAIN_LABELS);

        /** 先转换训练集 */
        for (int i = 0; i < dataList.size(); i++) {
            // 转换单个项目
            transformValProject(dataList.get(i), srcTaskPath, labelsPath, map);
        }
        /**
         * 生成data.yaml文件
         */
        Path valPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, taskId.toString(),
                CodeMap.SCRIPT_TYPE_VAL);
        if (!valPath.toFile().exists()) {
            FileUtil.mkdir(valPath);
        }
        Path filePath = valPath.resolve(val_name);
        if (!filePath.toFile().exists()) {
            FileUtil.mkdir(filePath);
        }
        createDataYml(filePath, srcTaskPath, new ArrayList<>(), dataList, lableList);
        // 保存参数信息
    }

    /**
     * 转换单个task,不过滤
     * 
     * @param form
     */
    private void transformValTask(Path xmlPath, Path targetDir, HashMap<String, Integer> map) {
        Document doc = XmlUtil.readXML(xmlPath.toFile());
        NodeList nodeList = doc.getElementsByTagName("image");
        for (int i = 0; i < nodeList.getLength(); i++) {
            // 一个节点,就是一张图片
            // <name="0a52d61f_c53f_4f8e_9984_6f903141b174.JPG" width="3024" height="4032">
            Node node = nodeList.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String imgName = element.getAttribute("name");
                String txtName = imgName.substring(0, imgName.lastIndexOf(".")) + ".txt";
                Integer w = Integer.parseInt(element.getAttribute("width"));
                Integer h = Integer.parseInt(element.getAttribute("height"));
                // <box label="安全帽" source="manual" occluded="0" xtl="2091.48" ytl="2380.01"
                // xbr="2288.76" ybr="2589.62" z_order="0"> </box>
                NodeList boxList = element.getChildNodes();

                Path txtFilePath = targetDir.resolve(txtName);
                try {
                    Files.createDirectories(txtFilePath.getParent());
                    try (BufferedWriter writer = new BufferedWriter(
                            new FileWriter(txtFilePath.toFile()))) {
                        for (int j = 0; j < boxList.getLength(); j++) {
                            Node box = boxList.item(j);
                            if (box.getNodeType() == Node.ELEMENT_NODE) {
                                Element one = (Element) box;
                                Integer idx = map.get(one.getAttribute("label"));
                                if (idx != null) {// 命中才写入
                                    // 支持box以及mask标注
                                    String tagName = one.getTagName();
                                    double x1, x2, y1, y2, m_w, m_h;
                                    switch (tagName) {
                                        case "box":
                                            x1 = Double.parseDouble(one.getAttribute("xtl"));
                                            y1 = Double.parseDouble(one.getAttribute("ytl"));
                                            x2 = Double.parseDouble(one.getAttribute("xbr"));
                                            y2 = Double.parseDouble(one.getAttribute("ybr"));
                                            m_w = Math.abs(x2 - x1);
                                            m_h = Math.abs(y2 - y1);
                                            break;
                                        case "mask":
                                            x1 = Double.parseDouble(one.getAttribute("left"));
                                            y1 = Double.parseDouble(one.getAttribute("top"));
                                            m_w = Double.parseDouble(one.getAttribute("width"));
                                            m_h = Double.parseDouble(one.getAttribute("height"));
                                            x2 = x1 + m_w - 1;
                                            y2 = y1 + m_h - 1;
                                            break;
                                        case "polygon":
                                            double[] arr = convertPolygonPointsToBBox(one.getAttribute("points"));
                                            x1 = arr[0];
                                            y1 = arr[1];
                                            m_w = arr[2];
                                            m_h = arr[3];
                                            x2 = x1 + m_w - 1;
                                            y2 = y1 + m_h - 1;
                                            break;
                                        default:
                                            continue;
                                    }
                                    String line = String.format(
                                            "%d %f %f %f %f\n",
                                            idx,
                                            (x2 + x1) / 2 / w,
                                            (y2 + y1) / 2 / h,
                                            m_w / w,
                                            m_h / h);
                                    writer.write(line);
                                }
                            }
                        }
                        writer.flush();
                    } catch (IOException e) {
                        log.warn("transformTask val err:{}", e.getMessage());
                    }
                } catch (IOException e2) {
                    log.warn("transformTask val err2:{}", e2.getMessage());
                }
            }
        }
    }

    private void transformValProject(TrainItem trainItem, Path srcTaskPath, Path labelsPath,
            HashMap<String, Integer> map) {
        // 获取到标注文件地址
        String pid = trainItem.getPid().toString();
        log.info("~start~transform val project:{}", pid);
        // 项目的目录
        Path srcProjectPath = srcTaskPath.resolve(pid);
        Path projectPath = labelsPath.resolve(pid);
        List<Integer> list = trainItem.getTasks();
        try {
            // 一个任务一个任务的转换
            for (int j = 0; j < list.size(); j++) {

                String tid = list.get(j).toString();
                Path targetDir = projectPath.resolve(tid);
                // 如果已存在,不重新生成
                if (targetDir.toFile().exists()) {
                    continue;
                }
                Path xmlPath = srcProjectPath.resolve(tid).resolve("annotations.xml");
                targetDir = FileUtil.mkdir(targetDir);
                transformValTask(xmlPath, targetDir, map);
            }
        } catch (Exception e) {
            log.error("transform val project err:{}", e.getMessage());
        }

        log.info("~finish~transfrom val project:{}", pid);

    }

    public void saveValParams(ValParams params) {
        Boolean is_val = params.getIs_val();
        ValParams info = new ValParams();
        info.setId(params.getId());
        info.setRun_name(params.getRun_name());
        info.setWeights(params.getWeights());
        info.setConf_thres(params.getConf_thres());
        info.setImg_size(params.getImg_size());
        info.setDevice(params.getDevice());
        info.setExt_params(params.getExt_params());

        String parentName;
        if (is_val) {
            info.setVal_name(params.getVal_name());
            info.setBatch_size(params.getBatch_size());
            parentName = params.getVal_name();
        } else {
            info.setPredict_name(params.getPredict_name());
            info.setSave_txt(params.getSave_txt());
            parentName = params.getPredict_name();
        }

        Path argsPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, params.getId().toString(),
                is_val ? CodeMap.SCRIPT_TYPE_VAL : CodeMap.SCRIPT_TYPE_PREDICT, parentName, "args.json");
        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(argsPath.toString(), false))) {
            writer.write(JSONUtil.toJsonPrettyStr(info));
        } catch (IOException e) {
            log.warn("save {} args.json err:{}", is_val ? CodeMap.SCRIPT_TYPE_VAL : CodeMap.SCRIPT_TYPE_PREDICT,
                    e.getMessage());
        }
    }

    public void transValParams(ValParams params) {
        // 转换标签
        transValAnnotation(params.getLabels(), params.getId(), params.getData(), params.getVal_name());
        // 保存参数
        saveValParams(params);
    }

    public void transPredictParams(ValParams params, MultipartFile file) {
        // 拷贝图片到指定目录
        Path sPath = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK);
        Path prePath = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, params.getId().toString(),
                CodeMap.SCRIPT_TYPE_PREDICT,
                params.getPredict_name(), "src");
        List<TrainItem> list = params.getData();
        // 拷贝已有的task资源
        if (list != null && !list.isEmpty()) {
            list.forEach(item -> {
                Path projectPath = sPath.resolve(item.getPid().toString());
                item.getTasks().forEach(tid -> {
                    Path taskPath = projectPath.resolve(tid + "/" + CodeMap.DIR_TRAIN_IMAGES + "/");
                    FileUtil.copyContent(taskPath.toFile(), prePath.toFile(), true);
                });
            });
        }
        // 拷贝上传的资源
        if (file != null && !file.isEmpty()) {
            String fileName = file.getOriginalFilename();
            Path filePath = prePath.resolve(fileName);
            FileUtil.mkParentDirs(filePath);
            try {
                file.transferTo(filePath);
            } catch (IllegalStateException | IOException e) {
                log.error("upload predict src err:{}", e.getMessage());
            }
            // zip则解压后删除
            if (fileName.endsWith(".zip")) {
                ZipUtil.unzip(filePath.toFile(), prePath.toFile());
                FileUtil.del(filePath);
            }
        }

        // 保存参数
        saveValParams(params);
    }
}
