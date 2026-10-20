package com.jisuoj.problem;

import com.jisuoj.common.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class ProblemRepository {
    private final NamedParameterJdbcTemplate namedJdbc;
    private final JdbcTemplate jdbc;

    public ProblemRepository(NamedParameterJdbcTemplate namedJdbc, JdbcTemplate jdbc) {
        this.namedJdbc = namedJdbc;
        this.jdbc = jdbc;
    }

    public PageResponse<ProblemDtos.Summary> findPage(int page, int size, String keyword,
                                                       String source, String userId, boolean includeHidden) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (!includeHidden) {
            where.append(" AND p.defunct='N' ");
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (p.title LIKE :keyword OR CAST(p.problem_id AS CHAR) LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword.trim() + "%");
        }
        if (source != null && !source.isBlank()) {
            where.append(" AND p.source LIKE :source ");
            params.addValue("source", "%" + source.trim() + "%");
        }
        long total = namedJdbc.queryForObject("SELECT COUNT(*) FROM problem p" + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", (page - 1) * size);
        String userStatusSql = userId == null
                ? "NULL AS user_status"
                : "CASE WHEN EXISTS(SELECT 1 FROM solution s WHERE s.problem_id=p.problem_id AND s.user_id=:userId AND s.result=4 AND s.valid=1) THEN 'AC' " +
                "WHEN EXISTS(SELECT 1 FROM solution s WHERE s.problem_id=p.problem_id AND s.user_id=:userId AND s.valid=1) THEN 'TRIED' ELSE NULL END AS user_status";
        if (userId != null) {
            params.addValue("userId", userId);
        }
        List<ProblemDtos.Summary> items = namedJdbc.query(
                "SELECT p.problem_id,p.title,p.source,p.accepted,p.submit," + userStatusSql +
                        " FROM problem p" + where + " ORDER BY p.problem_id DESC LIMIT :limit OFFSET :offset",
                params, (rs, rowNum) -> new ProblemDtos.Summary(
                        rs.getInt("problem_id"), rs.getString("title"), rs.getString("source"),
                        rs.getInt("accepted"), rs.getInt("submit"), ratio(rs.getInt("accepted"), rs.getInt("submit")),
                        rs.getString("user_status")));
        return PageResponse.of(items, page, size, total);
    }

    public PageResponse<ProblemDtos.AdminSummary> findAdminPage(int page, int size, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (p.title LIKE :keyword OR CAST(p.problem_id AS CHAR) LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword.trim() + "%");
        }
        long total = namedJdbc.queryForObject("SELECT COUNT(*) FROM problem p" + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", (page - 1) * size);
        List<ProblemDtos.AdminSummary> items = namedJdbc.query(
                "SELECT p.problem_id,p.title,p.source,p.defunct,p.accepted,p.submit,p.in_date " +
                        "FROM problem p" + where + " ORDER BY p.problem_id DESC LIMIT :limit OFFSET :offset",
                params, (rs, rowNum) -> {
                    java.sql.Timestamp created = rs.getTimestamp("in_date");
                    return new ProblemDtos.AdminSummary(
                            rs.getInt("problem_id"), rs.getString("title"), rs.getString("source"),
                            "Y".equalsIgnoreCase(rs.getString("defunct")), rs.getInt("accepted"),
                            rs.getInt("submit"), created == null ? null : created.toLocalDateTime());
                });
        return PageResponse.of(items, page, size, total);
    }

    public ProblemDtos.Detail findById(int id, String userId, boolean includeHidden) {
        String sql = "SELECT p.* FROM problem p WHERE p.problem_id=?" + (includeHidden ? "" : " AND p.defunct='N'");
        List<ProblemDtos.Detail> rows = jdbc.query(sql, (rs, rowNum) -> mapDetail(rs, userStatus(id, userId)), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public PageResponse<ProblemDtos.AcceptedUser> findAcceptedUsers(int problemId, int page, int size) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT s.user_id) FROM solution s " +
                        "WHERE s.problem_id=? AND s.result=4 AND s.valid=1 AND s.user_id<>''",
                Long.class, problemId);
        List<ProblemDtos.AcceptedUser> items = jdbc.query(
                "SELECT ac.user_id,COALESCE(NULLIF(u.nick,''),ac.user_id) nick," +
                        "COALESCE(u.school,'') school,ac.first_accepted_at " +
                        "FROM (SELECT s.user_id,MIN(s.in_date) first_accepted_at FROM solution s " +
                        "WHERE s.problem_id=? AND s.result=4 AND s.valid=1 AND s.user_id<>'' GROUP BY s.user_id) ac " +
                        "LEFT JOIN users u ON u.user_id=ac.user_id " +
                        "ORDER BY ac.first_accepted_at ASC,ac.user_id ASC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new ProblemDtos.AcceptedUser(
                        rs.getString("user_id"), rs.getString("nick"), rs.getString("school"),
                        rs.getTimestamp("first_accepted_at").toLocalDateTime()),
                problemId, size, (page - 1) * size);
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }

    public List<String> sources() {
        return jdbc.query("SELECT DISTINCT source FROM problem WHERE defunct='N' AND source IS NOT NULL AND source<>'' ORDER BY source LIMIT 200",
                (rs, rowNum) -> rs.getString(1));
    }

    public int create(ProblemDtos.SaveRequest request) {
        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            java.sql.PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO problem(title,description,`input`,`output`,sample_input,sample_output,hint,source,in_date,time_limit,memory_limit,spj,defunct,accepted,submit,solved) " +
                            "VALUES(?,?,?,?,?,?,?,?,NOW(),?,?,?,?,0,0,0)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            bind(statement, request, false);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("题目编号生成失败");
        }
        return key.intValue();
    }

    public boolean update(int id, ProblemDtos.SaveRequest request) {
        return jdbc.update("UPDATE problem SET title=?,description=?,`input`=?,`output`=?,sample_input=?,sample_output=?," +
                        "hint=?,source=?,time_limit=?,memory_limit=?,spj=?,defunct=? WHERE problem_id=?",
                request.title().trim(), safe(request.description()), safe(request.input()), safe(request.output()),
                safe(request.sampleInput()), safe(request.sampleOutput()), safe(request.hint()), safe(request.source()),
                request.timeLimit(), request.memoryLimit(), Boolean.TRUE.equals(request.specialJudge()) ? "1" : "0",
                Boolean.TRUE.equals(request.hidden()) ? "Y" : "N", id) > 0;
    }

    public boolean setHidden(int id, boolean hidden) {
        return jdbc.update("UPDATE problem SET defunct=? WHERE problem_id=?", hidden ? "Y" : "N", id) > 0;
    }

    private void bind(java.sql.PreparedStatement statement, ProblemDtos.SaveRequest request, boolean unused) throws SQLException {
        statement.setString(1, request.title().trim());
        statement.setString(2, safe(request.description()));
        statement.setString(3, safe(request.input()));
        statement.setString(4, safe(request.output()));
        statement.setString(5, safe(request.sampleInput()));
        statement.setString(6, safe(request.sampleOutput()));
        statement.setString(7, safe(request.hint()));
        statement.setString(8, safe(request.source()));
        statement.setBigDecimal(9, request.timeLimit());
        statement.setInt(10, request.memoryLimit());
        statement.setString(11, Boolean.TRUE.equals(request.specialJudge()) ? "1" : "0");
        statement.setString(12, Boolean.TRUE.equals(request.hidden()) ? "Y" : "N");
    }

    private ProblemDtos.Detail mapDetail(ResultSet rs, String userStatus) throws SQLException {
        java.sql.Timestamp created = rs.getTimestamp("in_date");
        return new ProblemDtos.Detail(
                rs.getInt("problem_id"), rs.getString("title"), rs.getString("description"),
                rs.getString("input"), rs.getString("output"), rs.getString("sample_input"),
                rs.getString("sample_output"), rs.getString("hint"), rs.getString("source"),
                rs.getBigDecimal("time_limit"), rs.getInt("memory_limit"), !"0".equals(rs.getString("spj")),
                "Y".equalsIgnoreCase(rs.getString("defunct")), rs.getInt("accepted"), rs.getInt("submit"),
                created == null ? null : created.toLocalDateTime(), userStatus);
    }

    private String userStatus(int problemId, String userId) {
        if (userId == null) {
            return null;
        }
        List<Integer> results = jdbc.query(
                "SELECT result FROM solution WHERE problem_id=? AND user_id=? AND valid=1 ORDER BY solution_id DESC",
                (rs, rowNum) -> rs.getInt(1), problemId, userId);
        if (results.stream().anyMatch(value -> value == 4)) {
            return "AC";
        }
        return results.isEmpty() ? null : "TRIED";
    }

    private static double ratio(int accepted, int submit) {
        return submit <= 0 ? 0 : Math.round(accepted * 10_000.0 / submit) / 100.0;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
