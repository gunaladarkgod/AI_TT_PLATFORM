package com.xgls.web.vo;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xgls.web.entity.TaskDataset;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@lombok.experimental.Accessors(chain=true)
public class TaskDatasetParams {

    private String id;

    private String name;

    private String sensorType;

    private String targetType;

    private Integer classNum;

    private List<Label> labels;

    private Integer imageNum;

    private Integer sampleNum;

    private String createdBy;

    private String dataPath;

    private String annoPath;

    private List<TaskDatasetParams> children;

    public TaskDatasetParams() {

    }

    public  TaskDatasetParams(TaskDataset taskDataset) {
        this.id = String.valueOf(taskDataset.getId());
        this.name = taskDataset.getName();
        this.sensorType = taskDataset.getSensorType();
        this.targetType = taskDataset.getTargetType();

        this.imageNum = taskDataset.getCoreImgNum()+taskDataset.getSupImgNum();
        this.sampleNum = taskDataset.getCoreAnnoNum()+taskDataset.getSupAnnoNum();
        this.createdBy = taskDataset.getUsername();
        this.children = new ArrayList<TaskDatasetParams>();

        TaskDatasetParams coreTaskDataset = new TaskDatasetParams();

        coreTaskDataset.id = String.valueOf(taskDataset.getId())+"_1";
        coreTaskDataset.name = taskDataset.getName()+"_core";
        coreTaskDataset.sensorType = taskDataset.getSensorType();
        coreTaskDataset.targetType = taskDataset.getCoreTargetType();
        coreTaskDataset.classNum = taskDataset.getCoreClassNum();
        coreTaskDataset.imageNum = taskDataset.getCoreImgNum();
        coreTaskDataset.sampleNum = taskDataset.getCoreAnnoNum();
        coreTaskDataset.createdBy = taskDataset.getUsername();
        coreTaskDataset.labels = convertJsonToLabelList(taskDataset.getCoreClassList());
        System.out.println(coreTaskDataset.labels);
        coreTaskDataset.dataPath = taskDataset.getCoreDataPath();
        coreTaskDataset.annoPath = taskDataset.getCoreAnnoPath();

        this.children.add(coreTaskDataset);


        TaskDatasetParams supTaskDataset = new TaskDatasetParams();

        supTaskDataset.id = String.valueOf(taskDataset.getId())+"_2";
        supTaskDataset.name = taskDataset.getName()+"_sup";
        supTaskDataset.sensorType = taskDataset.getSensorType();
        supTaskDataset.targetType = taskDataset.getSupTargetType();
        supTaskDataset.classNum = taskDataset.getSupClassNum();
        supTaskDataset.imageNum = taskDataset.getSupImgNum();
        supTaskDataset.sampleNum = taskDataset.getSupAnnoNum();
        supTaskDataset.createdBy = taskDataset.getUsername();
        supTaskDataset.labels = convertJsonToLabelList(taskDataset.getSupClassList());
        supTaskDataset.dataPath = taskDataset.getSupDataPath();
        supTaskDataset.annoPath = taskDataset.getSupAnnoPath();

        this.children.add(supTaskDataset);

        Map<String, Label> mergedMap = new LinkedHashMap<>(); // 使用 LinkedHashMap 保持顺序

        // 先添加第一个列表的所有元素
        for (Label label : coreTaskDataset.getLabels()) {
            mergedMap.putIfAbsent(label.getName(), label);
        }

        // 再添加第二个列表的元素，如果已存在则跳过
        for (Label label : supTaskDataset.getLabels()) {
            mergedMap.putIfAbsent(label.getName(), label);
        }

        this.labels = new ArrayList<>(mergedMap.values());

        this.classNum = this.labels.size();




    }

    public void addChildren(TaskDatasetParams taskDatasetParams) {
        children.add(taskDatasetParams);
    }


    public List<Label> convertJsonToLabelList(String jsonString) {
        List<Label> labelList = new ArrayList<>();

        if (jsonString == null || jsonString.trim().isEmpty()) {
            return labelList;
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Integer> labelMap = objectMapper.readValue(
                    jsonString,
                    new TypeReference<Map<String, Integer>>() {}
            );

            for (Map.Entry<String, Integer> entry : labelMap.entrySet()) {
                Label label = new Label();
                label.setName(entry.getKey());
                label.setCount(entry.getValue());
                labelList.add(label);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return labelList;
    }




}
