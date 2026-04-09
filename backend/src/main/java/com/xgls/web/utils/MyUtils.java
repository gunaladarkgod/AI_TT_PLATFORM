package com.xgls.web.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.xgls.web.base.CodeMap;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ReUtil;

public class MyUtils {
    private static SimpleDateFormat df2 = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");

    public static String parseTime(Long time) {
        if (time == null) {
            return DateTime.now().toStringDefaultTimeZone();
        }
        return DateTime.of(time).toStringDefaultTimeZone();
    }

    public static String parseTime(DateTime time) {
        if (time == null) {
            return DateTime.now().toStringDefaultTimeZone();
        }
        return time.toStringDefaultTimeZone();
    }

    /**
     * yyyy_MM_dd_HH_mm_ss
     * 
     * @param time
     * @return
     */
    public static String parseTime2(Long time) {
        if (time == null) {
            return DateTime.now().toString(df2);
        }
        return DateTime.of(time).toString(df2);
    }

    public static String parseTime2(DateTime time) {
        if (time == null) {
            return DateTime.now().toString(df2);
        }
        return time.toString(df2);
    }

    public static boolean isIds(String str) {
        return ReUtil.isMatch(CodeMap.RE_IDS, str);
    }

    public static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String getFirstFileName(Path dir) {
        try {
            Optional<Path> file = Files.walk(dir)
                    .filter(Files::isRegularFile)
                    .findFirst();
            if (file.isPresent()) {
                return dir.relativize(file.get()).toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<Integer> convertStringToList(String input) {
        return Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }
}
