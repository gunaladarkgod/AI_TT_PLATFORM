package com.xgls.web.utils;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

public class HttpUtil {

    public static HttpResponse postJson(String url, String json) {
        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .body(json)
                // .timeout(10000)
                .execute();
        return response;
    }

}
