package com.xgls.web.wscontroller;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;

/**
 * websocket连接点, userId_envId,进入工作台的监控连接
 */
@Slf4j
@Component
@ServerEndpoint("/working/train/{uuid}")
@Tag(name = "训练任务WS")
public class WsTrainController {
    public static ConcurrentHashMap<String, WsTrainController> clients = new ConcurrentHashMap<>();
    private Session session;
    private String uuid;

    /**
     * 建立连接回调
     * 
     * @param uuid    连接标识符
     * @param session 会话
     * @throws IOException 异常
     */
    @OnOpen
    public void onOpen(@PathParam("uuid") String uuid, Session session)
            throws IOException {
        session.setMaxIdleTimeout(30000);
        if (StrUtil.isBlank(uuid)) {
            session.close();
        }
        WsTrainController client = clients.remove(uuid);
        if (client != null) {
            client.close();
            client = null;
        }
        this.uuid = uuid;
        this.session = session;
        clients.put(uuid, this);
    }

    /**
     * 关闭连接回调
     * 
     * @throws IOException 异常
     */
    @OnClose
    public void onClose() throws IOException {
        clients.remove(uuid);
    }

    /**
     * 获取到消息回调
     * 
     * @param message 消息内容
     * @throws IOException 异常
     */
    @OnMessage
    public void onMessage(String message) throws IOException {
        // log.info("{}", message);
    }

    /**
     * 出错回调
     * 
     * @param session 会话
     * @param error   出错信息
     */
    @OnError
    public void onError(Session session, Throwable error) {
        try {
            session.close();
            log.info("ws onerr,close");
        } catch (IOException e) {
            log.error("ws err:{}", e.getMessage());
        }
    }

    public void close() {
        if (session != null) {
            try {
                session.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 发送消息接口
     * 
     * @param message 消息内容
     * @throws IOException 异常
     */
    public void sendMessage(String message) throws IOException {
        this.session.getAsyncRemote().sendText(message);
    }

    public static void senMsgToAll(String message) {
        clients.values().stream().forEach(item -> {
            try {
                item.sendMessage(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}