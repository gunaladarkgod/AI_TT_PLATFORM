package com.xgls.web.mybatis;

import cn.hutool.json.JSONObject;

public class JSONTypeHandler extends ListTypeHandler<JSONObject> {

    @Override
    protected Class<JSONObject> specificType() {
        return JSONObject.class;
    }

}
