package com.jisuoj.problemimport;

import com.jisuoj.common.ApiException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RemoteStatementFetcher {
    private static final int MAX_BODY_BYTES = 2_000_000;
    private static final Pattern CF_PATH = Pattern.compile("(?i)/(?:contest|problemset/problem)/(\\d+)(?:/problem)?/([A-Z][A-Z0-9]*)");
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            // Do not follow redirects: otherwise an allowed public host could redirect the server to an internal address.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public String fetch(SourceReference reference) {
        int lastStatus = 0;
        String lastError = "";
        for (String url : candidates(reference)) {
            verifyHost(url, reference.type());
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(25))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .header("Cache-Control", "no-cache")
                        .GET().build();
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                lastStatus = response.statusCode();
                if (lastStatus >= 200 && lastStatus < 300 && response.body().length > 0 &&
                        response.body().length <= MAX_BODY_BYTES) {
                    return new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
                }
                if (response.body().length > MAX_BODY_BYTES) {
                    lastError = "远程题面超过 2 MB 限制";
                }
            } catch (Exception ex) {
                lastError = ex.getMessage() == null ? "网络请求失败" : ex.getMessage();
            }
        }
        if (lastStatus == 403) {
            throw ApiException.badRequest("目标站拒绝了服务器请求（HTTP 403），请在浏览器中打开题面后粘贴原始 HTML 或文字");
        }
        if (lastStatus == 429) {
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "目标站暂时限制了请求（HTTP 429），请稍后重试或粘贴题面");
        }
        if (!lastError.isBlank()) {
            throw ApiException.badRequest("拉取题面失败：" + lastError + "；也可以直接粘贴题面");
        }
        throw ApiException.badRequest("拉取题面失败：HTTP " + (lastStatus == 0 ? "未知" : lastStatus) + "；也可以直接粘贴题面");
    }

    private List<String> candidates(SourceReference reference) {
        Set<String> urls = new LinkedHashSet<>();
        urls.add(reference.url());
        if ("codeforces".equals(reference.type())) {
            Matcher matcher = CF_PATH.matcher(URI.create(reference.url()).getPath());
            if (matcher.find()) {
                String contest = matcher.group(1);
                String index = matcher.group(2).toUpperCase();
                urls.add("https://codeforces.com/contest/" + contest + "/problem/" + index);
                urls.add("https://mirror.codeforces.com/problemset/problem/" + contest + "/" + index);
                urls.add("https://mirror.codeforces.com/contest/" + contest + "/problem/" + index);
            }
        }
        return List.copyOf(urls);
    }

    private void verifyHost(String url, String type) {
        String host = URI.create(url).getHost();
        boolean allowed = "codeforces".equals(type)
                ? "codeforces.com".equalsIgnoreCase(host) || "www.codeforces.com".equalsIgnoreCase(host) ||
                "mirror.codeforces.com".equalsIgnoreCase(host)
                : "pintia.cn".equalsIgnoreCase(host) || "www.pintia.cn".equalsIgnoreCase(host);
        if (!allowed) {
            throw ApiException.badRequest("不允许访问该题面地址");
        }
    }
}
