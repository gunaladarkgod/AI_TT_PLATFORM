package com.xgls.web.utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LogFileReader {

    // 获取日志文件的最后n行,按字节读取
    public static List<String> getLastLines(String logFilePath, Integer n) throws IOException {
        // 使用 try-with-resources 自动关闭 RandomAccessFile
        try (RandomAccessFile file = new RandomAccessFile(logFilePath, "r")) {
            long fileLength = file.length();
            long pointer = fileLength - 1;
            List<String> lines = new ArrayList<>();
            StringBuilder currentLine = new StringBuilder();

            while (pointer >= 0 && lines.size() < n) {
                file.seek(pointer); // 定位到当前位置
                char c = (char) file.readByte(); // 读取字符

                if (c == '\n' && currentLine.length() > 0) {
                    // 当前行结束时，将行内容加入到列表中并反转
                    lines.add(currentLine.reverse().toString());
                    currentLine.setLength(0); // 清空 StringBuilder，准备读取下一行
                } else {
                    // 如果是普通字符，加入当前行
                    currentLine.append(c);
                }
                pointer--; // 向前移动指针
            }

            // 如果最后一行没有换行符，添加最后一行
            if (currentLine.length() > 0) {
                lines.add(currentLine.reverse().toString());
            }

            // 反转列表，以便按时间顺序排列
            Collections.reverse(lines);
            return lines;
        }
    }
    /** 全读,按字符 */
    public static List<String> getLastLines2(String logFilePath, int n) throws IOException {
        // 参数校验
        if (n < 0) {
            return new ArrayList<>();
        }

        List<String> allLines = new ArrayList<>();

        // 使用 UTF-8 编码读取文件
        try (InputStream fis = new FileInputStream(logFilePath);
                InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isr)) {

            String line;
            while ((line = reader.readLine()) != null) {
                allLines.add(line);
            }
        }
        if (allLines.size() <= n) {
            return allLines;
        } else {
            int startIndex = allLines.size() - n;
            return allLines.subList(startIndex, allLines.size());
        }
    }
}
