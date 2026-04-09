package com.xgls.web.license.entity;

import lombok.Data;

@Data
public class LicenseStatus {
    private Boolean status = true;
    private String errMsg;
    private LicenseInfo info;

    public LicenseStatus(Boolean status, String errMsg, LicenseInfo info) {
        this.status = status;
        this.errMsg = errMsg;
        this.info = info;
    }

}
