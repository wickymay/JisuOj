package com.jisuoj.submission;

import com.jisuoj.common.PageResponse;
import com.jisuoj.judge.JudgeCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class SubmissionRepository {
    private final NamedParameterJdbcTemplate namedJdbc;
    private final JdbcTemplate jdbc;

    public SubmissionRepository(NamedParameterJdbcTemplate namedJdbc, JdbcTemplate jdbc) {
        this.namedJdbc = namedJdbc;
        this.jdbc = jdbc;
    }

    public PageResponse<SubmissionDtos.Summary> findPage(int page, int size, Integer problemId, String userId,
                                                          Integer language, Integer result, Integer contestId) {
        StringBuilder where = new StringBuilder(" WHERE s.valid=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (problemId != null) {
            where.append(" AND s.problem_id=:problemId ");
            params.addValue("problemId", problemId);
        }
        if (userId != null && !userId.isBlank()) {
            where.append(" AND s.user_id=:userId ");
            params.addValue("userId", userId.trim());
        }
        if (language != null) {
            where.append(" AND s.language=:language ");
            params.addValue("language", language);
        }
        if (result != null) {
            where.append(" AND s.result=:result ");
            params.addValue("result", result);
        }
        if (contestId != null) {
            where.append(" AND s.contest_id=:contestId ");
            params.addValue("contestId", contestId);
        } else {
            where.append(" AND s.contest_id=0 ");
        }
        long total = namedJdbc.queryForObject("SELECT COUNT(*) FROM solution s" + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", (page - 1) * size);
        List<SubmissionDtos.Summary> items = namedJdbc.query(
                "SELECT s.solution_id,s.problem_id,p.title problem_title,s.user_id,s.nick,s.result,s.memory,s.time," +
                        "s.language,s.code_length,s.in_date,s.judger,s.contest_id FROM solution s " +
                        "LEFT JOIN problem p ON p.problem_id=s.problem_id" + where +
                        " ORDER BY s.solution_id DESC LIMIT :limit OFFSET :offset",
                params, (rs, rowNum) -> {
                    int resultId = rs.getInt("result");
                    int languageId = rs.getInt("language");
                    int cid = rs.getInt("contest_id");
                    return new SubmissionDtos.Summary(
                            rs.getLong("solution_id"), rs.getInt("problem_id"), rs.getString("problem_title"),
                            rs.getString("user_id"), rs.getString("nick"), JudgeCatalog.resultCode(resultId), resultId,
                            rs.getInt("memory"), rs.getInt("time"), JudgeCatalog.languageName(languageId), languageId,
                            rs.getInt("code_length"), rs.getTimestamp("in_date").toLocalDateTime(), rs.getString("judger"),
                            cid == 0 ? null : cid);
                });
        return PageResponse.of(items, page, size, total);
    }

    public List<RecentAttempt> recentForTeaching(int problemId, String userId, int limit) {
        return jdbc.query(
                "SELECT solution_id,result,language,code_length,memory,time,in_date FROM solution " +
                        "WHERE problem_id=? AND user_id=? AND valid=1 ORDER BY solution_id DESC LIMIT ?",
                (rs, rowNum) -> new RecentAttempt(
                        rs.getLong("solution_id"),
                        JudgeCatalog.resultCode(rs.getInt("result")),
                        JudgeCatalog.languageName(rs.getInt("language")),
                        rs.getInt("code_length"), rs.getInt("memory"), rs.getInt("time"),
                        rs.getTimestamp("in_date").toLocalDateTime()),
                problemId, userId, limit);
    }

    public long create(int problemId, String userId, String nick, int language, String source, String ip,
                       Integer contestId, int contestNum) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int length = source.getBytes(StandardCharsets.UTF_8).length;
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO solution(problem_id,user_id,nick,in_date,language,ip,code_length,result,contest_id,num,valid) " +
                            "VALUES(?,?,?,NOW(),?,?,?,0,?,?,1)", Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, problemId);
            statement.setString(2, userId);
            statement.setString(3, nick);
            statement.setInt(4, language);
            statement.setString(5, ip);
            statement.setInt(6, length);
            statement.setInt(7, contestId == null ? 0 : contestId);
            statement.setInt(8, contestId == null ? -1 : contestNum);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("提交编号生成失败");
        }
        long id = key.longValue();
        jdbc.update("INSERT INTO source_code(solution_id,source) VALUES(?,?)", id, source);
        jdbc.update("INSERT INTO source_code_user(solution_id,source) VALUES(?,?)", id, source);
        jdbc.update("UPDATE problem SET submit=submit+1 WHERE problem_id=?", problemId);
        if (contestId != null) {
            jdbc.update("UPDATE contest_problem SET c_submit=c_submit+1 WHERE contest_id=? AND num=?", contestId, contestNum);
        }
        return id;
    }

    public boolean existsVisibleProblem(int problemId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM problem WHERE problem_id=? AND defunct='N'",
                Integer.class, problemId);
        return count != null && count > 0;
    }

    public String userNick(String userId) {
        List<String> values = jdbc.query("SELECT nick FROM users WHERE user_id=?", (rs, rowNum) -> rs.getString(1), userId);
        return values.isEmpty() || values.get(0) == null || values.get(0).isBlank() ? userId : values.get(0);
    }

    public LocalDateTime lastSubmissionAt(String userId) {
        List<LocalDateTime> values = jdbc.query(
                "SELECT in_date FROM solution WHERE user_id=? ORDER BY solution_id DESC LIMIT 1",
                (rs, rowNum) -> rs.getTimestamp(1).toLocalDateTime(), userId);
        return values.isEmpty() ? null : values.get(0);
    }

    public ContestBinding contestBinding(int contestId, int problemId, String userId, boolean admin) {
        List<ContestBinding> values = jdbc.query(
                "SELECT c.start_time,c.end_time,c.private,cp.num," +
                        "EXISTS(SELECT 1 FROM privilege pv WHERE pv.user_id=? AND pv.defunct='N' AND pv.rightstr IN (?,?)) invited " +
                        "FROM contest c JOIN contest_problem cp ON cp.contest_id=c.contest_id " +
                        "WHERE c.contest_id=? AND cp.problem_id=? AND c.defunct='N' LIMIT 1",
                (rs, rowNum) -> new ContestBinding(
                        rs.getTimestamp("start_time").toLocalDateTime(), rs.getTimestamp("end_time").toLocalDateTime(),
                        rs.getInt("private") == 1, rs.getInt("num"), rs.getBoolean("invited")),
                userId, "c" + contestId, "m" + contestId, contestId, problemId);
        if (values.isEmpty()) {
            return null;
        }
        ContestBinding binding = values.get(0);
        return admin || !binding.privateContest() || binding.invited() ? binding : null;
    }

    public SubmissionDtos.Source source(long id) {
        List<SubmissionDtos.Source> values = jdbc.query(
                "SELECT s.solution_id,s.problem_id,s.user_id,s.language,sc.source FROM solution s " +
                        "JOIN source_code_user sc ON sc.solution_id=s.solution_id WHERE s.solution_id=?",
                (rs, rowNum) -> new SubmissionDtos.Source(
                        rs.getLong("solution_id"), rs.getInt("problem_id"), rs.getString("user_id"),
                        JudgeCatalog.languageName(rs.getInt("language")), rs.getString("source")), id);
        return values.isEmpty() ? null : values.get(0);
    }

    public SubmissionDtos.Diagnostics diagnostics(long id) {
        List<SubmissionDtos.Diagnostics> values = jdbc.query(
                "SELECT s.solution_id,ci.error compile_error,ri.error runtime_error FROM solution s " +
                        "LEFT JOIN compileinfo ci ON ci.solution_id=s.solution_id " +
                        "LEFT JOIN runtimeinfo ri ON ri.solution_id=s.solution_id WHERE s.solution_id=?",
                (rs, rowNum) -> new SubmissionDtos.Diagnostics(
                        rs.getLong("solution_id"), rs.getString("compile_error"), rs.getString("runtime_error")), id);
        return values.isEmpty() ? null : values.get(0);
    }

    public record ContestBinding(LocalDateTime startTime, LocalDateTime endTime, boolean privateContest,
                                 int number, boolean invited) {
    }

    public record RecentAttempt(long id, String result, String language, int codeLength,
                                int memory, int time, LocalDateTime submittedAt) {
    }
}
