package com.jisuoj.problemimport;

import com.jisuoj.common.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class TestDataZipService {
    private static final long MAX_UPLOAD = 64L * 1024 * 1024;
    private static final long MAX_EXTRACTED = 256L * 1024 * 1024;
    private static final int MAX_ENTRIES = 300;
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._\\-\\[\\]]*\\.(?i:in|out|ans)");

    public Map<String, byte[]> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Map.of();
        }
        if (file.getSize() <= 0 || file.getSize() > MAX_UPLOAD) {
            throw ApiException.badRequest("测试数据 ZIP 必须大于 0 且不超过 64 MiB");
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        int entries = 0;
        try (InputStream input = file.getInputStream(); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw ApiException.badRequest("测试数据 ZIP 文件数量过多（最多 300 个）");
                }
                String path = entry.getName().replace('\\', '/');
                if (entry.isDirectory()) {
                    continue;
                }
                if (path.startsWith("/") || path.matches("^[A-Za-z]:/.*") || path.contains("../") || path.contains("/..")) {
                    throw ApiException.badRequest("测试数据 ZIP 包含不安全路径：" + path);
                }
                String name = path.substring(path.lastIndexOf('/') + 1);
                if (!SAFE_NAME.matcher(name).matches()) {
                    continue;
                }
                String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                if ("ans".equals(extension)) {
                    name = name.substring(0, name.length() - 4) + ".out";
                }
                if (files.containsKey(name)) {
                    throw ApiException.badRequest("测试数据 ZIP 存在重复文件：" + name);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_EXTRACTED) {
                        throw ApiException.badRequest("测试数据解压后超过 256 MiB");
                    }
                    output.write(buffer, 0, read);
                }
                files.put(name, output.toByteArray());
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw ApiException.badRequest("无法读取测试数据 ZIP：" + ex.getMessage());
        }
        if (files.isEmpty()) {
            throw ApiException.badRequest("测试数据 ZIP 中没有 .in/.out/.ans 文件");
        }
        for (String name : files.keySet()) {
            int dot = name.lastIndexOf('.');
            String stem = name.substring(0, dot);
            String pair = name.endsWith(".in") ? stem + ".out" : stem + ".in";
            if (!files.containsKey(pair)) {
                throw ApiException.badRequest("测试数据缺少配对文件：" + pair);
            }
        }
        return files;
    }
}
