package com.xgls.web.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.xgls.web.base.AjaxResult;

import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CvatApiUtil {
    private final static String Authorization = "Authorization";
    private static String server = SpringUtil.getProperty("sys.cvat.api-server");
    private static String authHeader = null;

    static {
        String username = SpringUtil.getProperty("sys.cvat.api-username");
        String password = SpringUtil.getProperty("sys.cvat.api-password");
        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        authHeader = "Basic " + new String(encodedAuth);
    }

    private static HttpHeaders baseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Authorization, authHeader);
        headers.set("Accept", "*/*");              // 防止 406
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    public static ResponseEntity<String> doPost(String url, String body) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = baseHeaders();
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        String full = server + url;
        try {
            log.info("post_url:{}", full);
            return restTemplate.exchange(full, HttpMethod.POST, entity, String.class);
        } catch (HttpStatusCodeException e) {
            log.info("{}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("{}", e.getMessage());
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    public static ResponseEntity<String> doGet(String url) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = baseHeaders();
        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        String full = server + url;
        try {
            log.info("get_url:{}", full);
            return restTemplate.exchange(full, HttpMethod.GET, entity, String.class);
        } catch (HttpStatusCodeException e) {
            log.info("{}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("{}", e.getMessage());
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    public static ResponseEntity<String> doPut(String url, String body) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = baseHeaders();
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        String full = server + url;
        try {
            return restTemplate.exchange(full, HttpMethod.PUT, entity, String.class);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    public static AjaxResult doGetFile2(String url, Path tempFilePath) {
        HttpRequest request = HttpRequest.get(url).header(Authorization, authHeader);
        try (InputStream in = request.execute().bodyStream();
             BufferedInputStream bufferedIn = new BufferedInputStream(in);
             FileOutputStream fos = new FileOutputStream(tempFilePath.toFile());
             BufferedOutputStream bufferedOut = new BufferedOutputStream(fos)) {
            log.info("start stream start");
            byte[] buffer = new byte[65536];
            int bytesRead;
            while ((bytesRead = bufferedIn.read(buffer)) != -1) {
                bufferedOut.write(buffer, 0, bytesRead);
            }
            bufferedOut.flush();
            log.info("start stream finish");
            return AjaxResult.success();
        } catch (IOException e) {
            log.info("start stream error");
            e.printStackTrace();
            return AjaxResult.error(e.getMessage());
        }
    }

    public static AjaxResult doGetFile(String url, Path tempFilePath) {
        HttpRequest request = HttpRequest.get(url).header(Authorization, authHeader);
        HttpResponse response = request.execute().sync();
        log.info("res long:{}", response.contentLength());
        try (InputStream in = response.bodyStream();
             FileOutputStream fos = new FileOutputStream(tempFilePath.toFile());
             FileChannel outChannel = fos.getChannel();
             ReadableByteChannel inChannel = Channels.newChannel(in)) {
            long fileSize = response.contentLength();
            long totalBytesRead = 0;
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (fileSize < 0 || totalBytesRead < fileSize) {
                int bytesRead = inChannel.read(buffer);
                if (bytesRead == -1) break;
                buffer.flip();
                outChannel.write(buffer);
                buffer.clear();
                totalBytesRead += bytesRead;
                if (fileSize > 0) log.info("total:{}/{}", totalBytesRead, fileSize);
            }
            log.info("File download completed successfully.");
            return AjaxResult.success();
        } catch (IOException e) {
            log.error("Error during file download", e);
            return AjaxResult.error("File download failed: " + e.getMessage());
        }
    }

    public static ResponseEntity<String> getProject(Integer project_id) {
        String url = String.format("/api/projects/%s", project_id);
        return doGet(url);
    }

    public static ResponseEntity<String> getTask(Integer task_id) {
        String url = String.format("/api/tasks/%s", task_id);
        return doGet(url);
    }

    /** 按 project 列出 tasks（判断图片/视频用） */
    public static ResponseEntity<String> getTasksByProject(Integer project_id) {
        String url = String.format("/api/tasks?project_id=%s&page_size=500&sort=id", project_id);
        return doGet(url);
    }

    public static ResponseEntity<String> getLabelList(Integer project_id) {
        String url = String.format("/api/labels?project_id=%s&page_size=200&sort=id", project_id);
        return doGet(url);
    }

    public static ResponseEntity<String> getFormats() {
        String url = "/api/server/annotation/formats";
        return doGet(url);
    }

    /** ✅ 不要再编码 rq_id，CVAT 允许 ':' '@' 等字符作为 path segment */
    public static ResponseEntity<String> getRequestDetail(String id) {
        String url = String.format("/api/requests/%s", id);
        return doGet(url);
    }

    /** 任务级导出（保留） */
    public static AjaxResult initDatasetExport(Integer task_id, String format, boolean save_image) {
        String fmt = (format == null ? "" : format);
        String url = String.format(
                "/api/tasks/%d/dataset/export?save_images=%s&format=%s",
                task_id, save_image,
                URLEncoder.encode(fmt, StandardCharsets.UTF_8));
        ResponseEntity<String> res = doPost(url, "");
        if (res.getStatusCode().is2xxSuccessful()) {
            return AjaxResult.success(res.getBody());
        } else {
            return AjaxResult.error(res.getBody());
        }
    }

    /** 项目级导出（推荐） */
    public static AjaxResult initProjectDatasetExport(Integer project_id, String format, boolean save_image) {
        String fmt = (format == null ? "" : format);
        String url = String.format(
                "/api/projects/%d/dataset/export?format=%s&save_images=%s",
                project_id,
                URLEncoder.encode(fmt, StandardCharsets.UTF_8),
                String.valueOf(save_image));
        ResponseEntity<String> r = doPost(url, "");
        if (r.getStatusCode().is2xxSuccessful()) {
            return AjaxResult.success(r.getBody());
        } else {
            log.warn("project export (dataset/export) failed: status={}, body={}", r.getStatusCode(), r.getBody());
            return AjaxResult.error(r.getBody());
        }
    }

    /** ✅ 不要再编码 rq_id */
    public static AjaxResult queryRequestStatus(String rq_id) {
        String url = String.format("/api/requests/%s", rq_id);
        ResponseEntity<String> res = doGet(url);
        if (res.getStatusCode().is2xxSuccessful()) {
            return AjaxResult.success(res.getBody());
        } else {
            return AjaxResult.error(res.getBody());
        }
    }

    public static AjaxResult downloadFile(String fileURL, String savePath) {
        URL url;
        try {
            url = new URL(fileURL);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setRequestProperty(Authorization, authHeader);
            try (InputStream inputStream = httpURLConnection.getInputStream();
                 FileOutputStream fileOutputStream = new FileOutputStream(savePath)) {
                byte[] buffer = new byte[1024 * 32];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
                log.info("finish download,savePath: " + savePath);
                return AjaxResult.success();
            } catch (Exception e) {
                e.printStackTrace();
                return AjaxResult.error(e.getMessage());
            } finally {
                httpURLConnection.disconnect();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return AjaxResult.error(e.getMessage());
        } catch (ProtocolException e) {
            e.printStackTrace();
            return AjaxResult.error(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            return AjaxResult.error(e.getMessage());
        }
    }
    public static AjaxResult projectExists(long projectId) {
        try {
            // 直接 GET /api/projects/{id}
            ResponseEntity<String> r = doGet(String.format("/api/projects/%d", projectId));
            int sc = r.getStatusCodeValue();
            if (sc >= 200 && sc < 300) {
                // 存在
                return AjaxResult.success("ok");
            } else if (sc == 404) {
                // 不存在
                return AjaxResult.error("Not found.");
            } else if (sc == 406) {
                // 一般是缺少 Accept: application/json
                return AjaxResult.error("Could not satisfy the request Accept header.");
            } else {
                return AjaxResult.error("unexpected status " + r.getStatusCode() + " for project " + projectId);
            }
        } catch (Exception e) {
            log.warn("projectExists error, projectId={}, ex={}", projectId, e.toString());
            return AjaxResult.error(e.getMessage());
        }
    }
}
