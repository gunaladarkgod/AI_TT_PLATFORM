package com.xgls.web.vo;

import lombok.Data;

@Data
public class MyTask implements Comparable<MyTask> {
    private Integer id;
    private String name;//1-导出图片  0-导出标注
    private Long priority;

    public MyTask(Integer id, String name, Long priority) {
        this.id = id;
        this.name = name;
        this.priority = priority;
    }

    @Override
    public int compareTo(MyTask other) {
        return Long.compare(this.priority, other.priority);
    }

}