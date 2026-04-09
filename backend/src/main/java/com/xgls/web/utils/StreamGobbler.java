package com.xgls.web.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xgls.web.base.CodeMap;

import lombok.extern.slf4j.Slf4j;

/**
 * 专门处理流的线程，用于记录训练日志
 */
@Slf4j
public class StreamGobbler extends Thread {
    private static final Logger trainLogger = LoggerFactory.getLogger("trainLogger");
    private static final Logger transLogger = LoggerFactory.getLogger("transLogger");
    private static final Logger valLogger = LoggerFactory.getLogger("valLogger");
    private static final Logger predictLogger = LoggerFactory.getLogger("predictLogger");
    private static final Logger dataLogger = LoggerFactory.getLogger("dataLogger");
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
            switch (type) {
                case CodeMap.SCRIPT_TYPE_TRAIN:
                    while ((msg = br.readLine()) != null) {
                        trainLogger.info(msg);
                    }
                    break;
                case CodeMap.SCRIPT_TYPE_TRANS:
                    while ((msg = br.readLine()) != null) {
                        transLogger.info(msg);
                    }
                    break;
                case CodeMap.SCRIPT_TYPE_VAL:
                    while ((msg = br.readLine()) != null) {
                        valLogger.info(msg);
                    }
                    break;
                case CodeMap.SCRIPT_TYPE_PREDICT:
                    while ((msg = br.readLine()) != null) {
                        predictLogger.info(msg);
                    }
                    break;
                case CodeMap.SCRIPT_TYPE_DATA:
                    while ((msg = br.readLine()) != null) {
                        dataLogger.info(msg);
                    }
                    break;
                default:
                    while ((msg = br.readLine()) != null) {
                        log.info(msg);
                    }
                    break;
            }
        } catch (IOException e) {
            log.error("streamgobble err:{}", e.getMessage());
        }
    }

    public String getMsg() {
        return this.msg;
    }
}