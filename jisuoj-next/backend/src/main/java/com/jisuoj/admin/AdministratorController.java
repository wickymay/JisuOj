package com.jisuoj.admin;

import com.jisuoj.auth.UserPrincipal;
import com.jisuoj.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/administrators")
public class AdministratorController {
    private static final String ADMINISTRATOR = "administrator";
    private static final String SUPER_ADMINISTRATOR = "super_administrator";

    private final JdbcTemplate jdbc;

    public AdministratorController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<AdministratorRow> list() {
        return jdbc.query("""
                        SELECT u.user_id,u.nick,u.school,u.reg_time,u.accesstime,
                               MAX(CASE WHEN p.rightstr='super_administrator' THEN 1 ELSE 0 END) AS is_super
                        FROM users u
                        JOIN privilege p ON p.user_id=u.user_id AND p.defunct='N'
                        WHERE p.rightstr IN ('administrator','super_administrator')
                        GROUP BY u.user_id,u.nick,u.school,u.reg_time,u.accesstime
                        ORDER BY is_super DESC,u.user_id
                        """,
                (rs, rowNum) -> new AdministratorRow(
                        rs.getString("user_id"), rs.getString("nick"), rs.getString("school"),
                        rs.getInt("is_super") == 1 ? "SUPER_ADMIN" : "ADMIN",
                        rs.getTimestamp("reg_time") == null ? null : rs.getTimestamp("reg_time").toLocalDateTime(),
                        rs.getTimestamp("accesstime") == null ? null : rs.getTimestamp("accesstime").toLocalDateTime()));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public void add(@Valid @RequestBody AddRequest request,
                    @AuthenticationPrincipal UserPrincipal principal) {
        String userId = request.userId().trim();
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE user_id=?", Integer.class, userId);
        if (users == null || users == 0) {
            throw ApiException.notFound("用户不存在");
        }
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM privilege WHERE user_id=? AND rightstr IN (?,?) AND defunct='N'",
                Integer.class, userId, ADMINISTRATOR, SUPER_ADMINISTRATOR);
        if (active != null && active > 0) {
            throw ApiException.badRequest("该用户已经是管理员");
        }
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM privilege WHERE user_id=? AND rightstr=?",
                Integer.class, userId, ADMINISTRATOR);
        if (existing != null && existing > 0) {
            jdbc.update("UPDATE privilege SET defunct='N',valuestr='true' WHERE user_id=? AND rightstr=?",
                    userId, ADMINISTRATOR);
        } else {
            jdbc.update("INSERT INTO privilege(user_id,rightstr,valuestr,defunct) VALUES(?,?,'true','N')",
                    userId, ADMINISTRATOR);
        }
        audit(principal.userId(), "ADMIN_ADD", userId);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public void remove(@PathVariable String userId,
                       @AuthenticationPrincipal UserPrincipal principal) {
        Integer superAdmin = jdbc.queryForObject(
                "SELECT COUNT(*) FROM privilege WHERE user_id=? AND rightstr=? AND defunct='N'",
                Integer.class, userId, SUPER_ADMINISTRATOR);
        if (superAdmin != null && superAdmin > 0) {
            throw ApiException.badRequest("不能从管理员列表中移除超级管理员");
        }
        int changed = jdbc.update(
                "UPDATE privilege SET defunct='Y' WHERE user_id=? AND rightstr=? AND defunct='N'",
                userId, ADMINISTRATOR);
        if (changed == 0) {
            throw ApiException.notFound("管理员不存在");
        }
        audit(principal.userId(), "ADMIN_REMOVE", userId);
    }

    private void audit(String actor, String action, String targetId) {
        jdbc.update("INSERT INTO jisu_audit_log(actor_id,action,target_type,target_id,created_at) " +
                        "VALUES(?,?,'administrator',?,NOW())",
                actor, action, targetId);
    }

    public record AdministratorRow(String userId, String nick, String school, String level,
                                   LocalDateTime registeredAt, LocalDateTime lastActiveAt) {
    }

    public record AddRequest(
            @NotBlank
            @Pattern(regexp = "[A-Za-z0-9_]{3,48}", message = "用户名格式不正确")
            String userId) {
    }
}
