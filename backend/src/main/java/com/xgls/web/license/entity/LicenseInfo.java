package com.xgls.web.license.entity;

import java.io.Serializable;

import lombok.Data;

@Data
public class LicenseInfo implements Serializable {
    private String id;
    private Long start;
    private Long end;

    public LicenseInfo(String id, Long start, Long end) {
        this.id = id;
        this.start = start;
        this.end = end;
    }

}
