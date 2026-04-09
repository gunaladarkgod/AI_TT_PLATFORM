package com.xgls.web.license.entity;

import java.io.Serializable;

import lombok.Data;

@Data
public class LicenseRecord implements Serializable {
    private String sig;
    private LicenseInfo info;
}
