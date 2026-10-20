package com.jisuoj.contest;

import com.jisuoj.common.PageResponse;
import com.jisuoj.judge.JudgeCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ContestRepository {
    private final JdbcTemplate jdbc;

    public ContestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<ContestDtos.Summary> findPage(int page, int size, boolean includeHidden) {
        String where = includeHidden ? "" : " WHERE defunct='N'";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM contest" + where, Long.class);
        List<ContestDtos.Summary> items = jdbc.query(
                "SELECT contest_id,title,start_time,end_time,`private`,contest_type,user_id FROM contest" + where +
                        " ORDER BY contest_id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    LocalDateTime start = rs.getTimestamp("start_time").toLocalDateTime();
                    LocalDateTime end = rs.getTimestamp("end_time").toLocalDateTime();
                    return new ContestDtos.Summary(rs.getInt("contest_id"), rs.getString("title"), start, end,
                            status(start, end), rs.getInt("private") == 1, rs.getInt("contest_type"), rs.getString("user_id"));
                }, size, (page - 1) * size);
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }

    public ContestRow find(int id, boolean includeHidden) {
        List<ContestRow> values = jdbc.query(
                "SELECT contest_id,title,description,start_time,end_time,`private`,password,contest_type,user_id,defunct,langmask " +
                        "FROM contest WHERE contest_id=?" + (includeHidden ? "" : " AND defunct='N'"),
                (rs, rowNum) -> new ContestRow(
                        rs.getInt("contest_id"), rs.getString("title"), rs.getString("description"),
                        rs.getTimestamp("start_time").toLocalDateTime(), rs.getTimestamp("end_time").toLocalDateTime(),
                        rs.getInt("private") == 1, rs.getString("password"), rs.getInt("contest_type"),
                        rs.getString("user_id"), rs.getString("defunct"), rs.getInt("langmask")), id);
        return values.isEmpty() ? null : values.get(0);
    }

    public List<ContestDtos.Problem> problems(int contestId) {
        return jdbc.query(
                "SELECT cp.problem_id,cp.num,COALESCE(NULLIF(cp.title,''),p.title) title,cp.c_accepted,cp.c_submit " +
                        "FROM contest_problem cp JOIN problem p ON p.problem_id=cp.problem_id " +
                        "WHERE cp.contest_id=? ORDER BY cp.num",
                (rs, rowNum) -> new ContestDtos.Problem(
                        rs.getInt("problem_id"), rs.getInt("num"), label(rs.getInt("num")),
                        rs.getString("title"), rs.getInt("c_accepted"), rs.getInt("c_submit")), contestId);
    }

    public boolean hasAccess(int contestId, String userId) {
        if (userId == null) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM privilege WHERE user_id=? AND defunct='N' AND rightstr IN (?,?)",
                Integer.class, userId, "c" + contestId, "m" + contestId);
        return count != null && count > 0;
    }

    public void grantAccess(int contestId, String userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM privilege WHERE user_id=? AND rightstr=?",
                Integer.class, userId, "c" + contestId);
        if (count == null || count == 0) {
            jdbc.update("INSERT INTO privilege(user_id,rightstr,valuestr,defunct) VALUES(?,?,'true','N')",
                    userId, "c" + contestId);
        } else {
            jdbc.update("UPDATE privilege SET valuestr='true',defunct='N' WHERE user_id=? AND rightstr=?",
                    userId, "c" + contestId);
        }
    }

    public List<SubmissionRow> submissions(int contestId) {
        return jdbc.query(
                "SELECT s.solution_id,s.user_id,s.nick,s.num,s.result,s.in_date FROM solution s " +
                        "WHERE s.contest_id=? AND s.valid=1 ORDER BY s.solution_id",
                (rs, rowNum) -> new SubmissionRow(
                        rs.getLong("solution_id"), rs.getString("user_id"), rs.getString("nick"),
                        rs.getInt("num"), rs.getInt("result"), rs.getTimestamp("in_date").toLocalDateTime()), contestId);
    }

    public int create(ContestDtos.SaveRequest request, String creator) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO contest(title,start_time,end_time,defunct,description,`private`,langmask,password,contest_type,user_id) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.title().trim());
            statement.setObject(2, request.startTime());
            statement.setObject(3, request.endTime());
            statement.setString(4, Boolean.TRUE.equals(request.hidden()) ? "Y" : "N");
            statement.setString(5, safe(request.description()));
            statement.setInt(6, Boolean.TRUE.equals(request.privateContest()) ? 1 : 0);
            statement.setInt(7, request.languageMask() == null ? 0 : request.languageMask());
            statement.setString(8, safe(request.password()));
            statement.setInt(9, request.type() == null ? 0 : request.type());
            statement.setString(10, creator);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("比赛编号生成失败");
        }
        int id = key.intValue();
        replaceProblems(id, request.problemIds());
        return id;
    }

    public boolean update(int id, ContestDtos.SaveRequest request) {
        int changed = jdbc.update(
                "UPDATE contest SET title=?,start_time=?,end_time=?,defunct=?,description=?,`private`=?,langmask=?,password=?,contest_type=? " +
                        "WHERE contest_id=?",
                request.title().trim(), request.startTime(), request.endTime(),
                Boolean.TRUE.equals(request.hidden()) ? "Y" : "N", safe(request.description()),
                Boolean.TRUE.equals(request.privateContest()) ? 1 : 0,
                request.languageMask() == null ? 0 : request.languageMask(), safe(request.password()),
                request.type() == null ? 0 : request.type(), id);
        if (changed > 0 && request.problemIds() != null) {
            replaceProblems(id, request.problemIds());
        }
        return changed > 0;
    }

    public boolean setHidden(int id, boolean hidden) {
        return jdbc.update("UPDATE contest SET defunct=? WHERE contest_id=?", hidden ? "Y" : "N", id) > 0;
    }

    private void replaceProblems(int contestId, List<Integer> ids) {
        if (ids == null) {
            return;
        }
        jdbc.update("DELETE FROM contest_problem WHERE contest_id=?", contestId);
        int number = 0;
        for (Integer id : ids.stream().distinct().toList()) {
            if (id != null && id > 0) {
                jdbc.update("INSERT INTO contest_problem(problem_id,contest_id,title,num,c_accepted,c_submit) VALUES(?,?,'',?,0,0)",
                        id, contestId, number++);
            }
        }
    }

    static String status(LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now();
        return now.isBefore(start) ? "UPCOMING" : now.isBefore(end) ? "RUNNING" : "ENDED";
    }

    static String label(int number) {
        StringBuilder value = new StringBuilder();
        int current = number;
        do {
            value.insert(0, (char) ('A' + current % 26));
            current = current / 26 - 1;
        } while (current >= 0);
        return value.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record ContestRow(int id, String title, String description, LocalDateTime startTime,
                             LocalDateTime endTime, boolean privateContest, String password, int type,
                             String creator, String defunct, int languageMask) {
    }

    public record SubmissionRow(long id, String userId, String nick, int number, int result,
                                LocalDateTime submittedAt) {
    }
}
