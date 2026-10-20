package com.jisuoj.ranking;

import com.jisuoj.common.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static com.jisuoj.common.RequestSupport.page;
import static com.jisuoj.common.RequestSupport.size;

@RestController
@RequestMapping("/api/rankings")
public class RankingController {
    private final JdbcTemplate jdbc;

    public RankingController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public PageResponse<RankRow> ranking(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "50") int size,
                                         @RequestParam(required = false) String keyword) {
        int safePage = page(page);
        int safeSize = size(size);
        String filter = keyword == null || keyword.isBlank()
                ? ""
                : " AND (u.user_id LIKE ? OR u.nick LIKE ? OR u.school LIKE ?)";
        Object[] filterArgs = keyword == null || keyword.isBlank()
                ? new Object[0]
                : new Object[]{like(keyword), like(keyword), like(keyword)};
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM users u WHERE u.defunct='N'" + filter,
                Long.class, filterArgs);
        Object[] args = new Object[filterArgs.length + 2];
        System.arraycopy(filterArgs, 0, args, 0, filterArgs.length);
        args[filterArgs.length] = safeSize;
        args[filterArgs.length + 1] = (safePage - 1) * safeSize;
        List<RankRow> rows = jdbc.query(
                "SELECT u.user_id,u.nick,u.school,u.group_name,u.solved,u.submit,u.accesstime " +
                        "FROM users u WHERE u.defunct='N'" + filter +
                        " ORDER BY u.solved DESC,u.submit ASC,u.user_id ASC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new RankRow(
                        (safePage - 1) * safeSize + rowNum + 1,
                        rs.getString("user_id"), rs.getString("nick"), rs.getString("school"),
                        rs.getString("group_name"), rs.getInt("solved"), rs.getInt("submit"),
                        rs.getInt("submit") == 0 ? 0 : Math.round(rs.getInt("solved") * 10_000.0 / rs.getInt("submit")) / 100.0,
                        rs.getTimestamp("accesstime") == null ? null : rs.getTimestamp("accesstime").toLocalDateTime()),
                args);
        return PageResponse.of(rows, safePage, safeSize, total == null ? 0 : total);
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    public record RankRow(int rank, String userId, String nick, String school, String groupName,
                          int solved, int submit, double ratio, LocalDateTime lastActiveAt) {
    }
}
