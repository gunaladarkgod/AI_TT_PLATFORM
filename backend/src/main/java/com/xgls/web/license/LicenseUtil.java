package com.xgls.web.license;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Base64;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.license.entity.LicenseInfo;
import com.xgls.web.license.entity.LicenseRecord;
import com.xgls.web.license.entity.LicenseStatus;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LicenseUtil {
    private static String public_key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlHCuZ3i2Ih7mCoETG60wK2dlJMG6zWChTZ7FY5BNWyWKQRfzLrkDsqXE/f/oRxJh9m4tHzZCqbdUXtouPDzAt/3KspDqkO3b/iF9OT67VZVgc25ARNLJev66c5uUrvT22mxc5bbxLuCniSXfZ/cu1CbSqvDBwUwdUr7Juk7JpcraxBOcbsHthvLVK9HknwMCFwjmSwnVyN6QwaL2QvFhqjere9MuiNN0xbcn8ORa+n2SKKHyQitxogl551lsWWZH9o5ZOn75U9ESa4x+sdTpR6CxKBXzaDHUIm2/8MU8X6mCGEEuVAIaHdEFMKxwzT7wtMwB/dJPv/BTQ37GF2xxtQIDAQAB";
    public static LicenseStatus LICENSE_STATUS = new LicenseStatus(true, "", null);
    public static String cpu_server = SpringUtil.getProperty("sys.cpu_server",
            "http://host.docker.internal:8099/api/cpu");

    public static boolean checkLiscense(String path) {
        try {
            File file = Paths.get(path).toFile();
            if (!file.exists() || !file.isFile()) {
                log.error("license file not exist:{}", path);
                LICENSE_STATUS.setStatus(false);
                LICENSE_STATUS.setErrMsg("没有发现授权证书");
                return false;
            }
            FileReader fileReader = new FileReader(file);
            String result = fileReader.readString();

            LicenseRecord record = JSONUtil.toBean(decodeBase64(result), LicenseRecord.class);
            LicenseInfo info = record.getInfo();
            LICENSE_STATUS.setInfo(info);
            // http获取cpuId
            String curId = "";
            try {
                AjaxResult res = JSONUtil.toBean(HttpUtil.get(cpu_server), AjaxResult.class);
                if (res.isSuccess()) {
                    curId = res.getData().toString();
                } else {
                    return false;
                }
            } catch (Exception e) {
                log.warn("cpu_server not enable:{}", e);
                return false;
            }

            // String curId = new
            // SystemInfo().getHardware().getProcessor().getProcessorIdentifier().getProcessorID()
            // .toUpperCase();
            log.info("getProcesserId:{}", curId);
            /**
             * 不校验cpuId
             * 特殊版本
             */
            if (info.getId() == null || !StrUtil.equals(curId, info.getId())) {
                log.error("id wrong:{}", info.getId());
                LICENSE_STATUS.setStatus(false);
                LICENSE_STATUS.setErrMsg("授权证书和服务器不匹配");
                return false;
            }
            Long now = DateTime.now().getTime();
            if (info.getStart() == null || now < info.getStart()) {
                log.error("start wrong:{}", DateTime.of(info.getStart()).toString());
                LICENSE_STATUS.setStatus(false);
                LICENSE_STATUS.setErrMsg("证书授权时间和服务器不匹配");
                return false;
            }
            if (info.getEnd() == null || now > info.getEnd()) {
                log.error("end has expire:{}", DateTime.of(info.getEnd()).toString());
                LICENSE_STATUS.setStatus(false);
                LICENSE_STATUS.setErrMsg("证书已过期");
                return false;
            }
            if (StrUtil.isBlank(record.getSig())) {
                log.error("sig is empty");
                LICENSE_STATUS.setStatus(false);
                LICENSE_STATUS.setErrMsg("证书验证失败");
                return false;
            }

            boolean flg = LicenseVerifier.verifyLicense(JSONUtil.toJsonStr(info), record.getSig(),
                    KeyPairGeneratorUtil.base64ToPublicKey(public_key));
            if (flg) {
                LICENSE_STATUS.setErrMsg("");
                log.info("verify success");

            } else {
                LICENSE_STATUS.setErrMsg("证书验证失败");
                log.error("verify fail");
            }
            LICENSE_STATUS.setStatus(flg);
            return flg;
        } catch (Exception e) {
            log.error("verify err:{}", e.getMessage());
            LICENSE_STATUS.setStatus(false);
            LICENSE_STATUS.setErrMsg("证书验证失败");
            return false;
        }
    }

    public static String encodeBase64(String info) {
        return Base64.getEncoder().encodeToString(info.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeBase64(String str) {
        try {
            String msg = new String(Base64.getDecoder().decode(str), StandardCharsets.UTF_8);
            return msg;
        } catch (Exception e) {
            return "{}";
        }
    }
}
