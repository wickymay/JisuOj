package com.jisuoj.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jisuoj.common.ApiException;
import com.jisuoj.problemimport.ProblemDataStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ProblemDeletionService {
    private static final Logger log = LoggerFactory.getLogger(ProblemDeletionService.class);
    private final JdbcTemplate jdbc;
    private final ProblemDataStorage storage;
    private final ObjectMapper objectMapper;

    public ProblemDeletionService(JdbcTemplate jdbc, ProblemDataStorage storage, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    public void deleteHiddenUnused(int id, String actor, String ip) {
        if (id <= 0) {
            throw ApiException.badRequest("题目编号无效");
        }
        List<ProblemRow> rows = jdbc.query(
                "SELECT title,defunct FROM problem WHERE problem_id=? LIMIT 1",
                (rs, rowNum) -> new ProblemRow(rs.getString("title"), rs.getString("defunct")), id);
        if (rows.isEmpty()) {
            throw ApiException.notFound("题目不存在");
        }
        ProblemRow problem = rows.get(0);
        if (!"Y".equalsIgnoreCase(problem.defunct())) {
            throw ApiException.badRequest("请先隐藏题目，再彻底删除");
        }
        requireUnused(id);

        // The legacy problem table is MyISAM, so a database transaction cannot restore files.
        // Only hidden, unreferenced problems reach this point; keep the row if file removal fails.
        storage.ensureWritable();
        storage.delete(id);
        requireUnused(id);
        jdbc.update("DELETE FROM jisu_problem_source WHERE problem_id=?", id);
        jdbc.update("DELETE FROM privilege WHERE rightstr=?", "p" + id);
        if (jdbc.update("DELETE FROM problem WHERE problem_id=? AND defunct='Y'", id) != 1) {
            throw ApiException.notFound("题目不存在或已恢复公开");
        }
        log.info("管理员 {} 彻底删除题目 #{} ({})", actor, id, problem.title());
        audit(id, problem.title(), actor, ip);
    }

    private void requireUnused(int id) {
        if (exists("SELECT EXISTS(SELECT 1 FROM solution WHERE problem_id=?)", id)) {
            throw new ApiException(HttpStatus.CONFLICT, "该题已有提交记录，不能彻底删除");
        }
        if (exists("SELECT EXISTS(SELECT 1 FROM contest_problem WHERE problem_id=?)", id)) {
            throw new ApiException(HttpStatus.CONFLICT, "该题已加入比赛或作业，不能彻底删除");
        }
        if (exists("SELECT EXISTS(SELECT 1 FROM topic WHERE pid=?)", id)) {
            throw new ApiException(HttpStatus.CONFLICT, "该题已有讨论，不能彻底删除");
        }
        if (exists("SELECT EXISTS(SELECT 1 FROM balloon WHERE pid=?)", id)) {
            throw new ApiException(HttpStatus.CONFLICT, "该题已有气球记录，不能彻底删除");
        }
        if (tableExists("openai_task_queue") &&
                exists("SELECT EXISTS(SELECT 1 FROM openai_task_queue WHERE problem_id=?)", id)) {
            throw new ApiException(HttpStatus.CONFLICT, "该题还有 AI 任务记录，不能彻底删除");
        }
    }

    private boolean exists(String sql, int id) {
        return Integer.valueOf(1).equals(jdbc.queryForObject(sql, Integer.class, id));
    }

    private boolean tableExists(String table) {
        return Integer.valueOf(1).equals(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",
                Integer.class, table));
    }

    private void audit(int id, String title, String actor, String ip) {
        try {
            jdbc.update("INSERT INTO jisu_audit_log(actor_id,action,target_type,target_id,detail,ip,created_at) " +
                            "VALUES(?,?,?,?,CAST(? AS JSON),?,NOW())",
                    actor, "problem.delete", "problem", String.valueOf(id),
                    objectMapper.writeValueAsString(Map.of("title", title)), ip);
        } catch (Exception ex) {
            log.warn("记录题目 #{} 删除审计失败", id, ex);
        }
    }

    private record ProblemRow(String title, String defunct) {
    }
}
