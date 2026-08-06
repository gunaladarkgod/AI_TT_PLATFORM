package com.xgls.web.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * 专门处理流的线程，用于记录训练日志
 */
@Slf4j
public class StreamGobbler extends Thread {
    private static final Logger processLogger = LoggerFactory.getLogger(StreamGobbler.class);
    InputStream is;
    String type;
    String msg;

    public StreamGobbler(InputStream is, String type) {
        this.is = is;
        this.type = type;
    }

    public void run() {
        try {
            InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            while ((msg = br.readLine()) != null) {
                // 旧版不同任务类型不再各自创建日志文件，统一写入主日志并保留来源。
                processLogger.info("[{}] {}", type, msg);
            }
        } catch (IOException e) {
            log.error("streamgobble err:{}", e.getMessage());
        }
    }

    public String getMsg() {
        return this.msg;
    }
}
