package com.jisuoj.admin;

import com.jisuoj.common.ApiException;
import com.jisuoj.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.jisuoj.common.RequestSupport.page;
import static com.jisuoj.common.RequestSupport.size;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAnyRole('ADMIN','USER_MANAGER')")
public class AdminUserController {
    private final JdbcTemplate jdbc;

    public AdminUserController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public PageResponse<UserRow> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "30") int size,
                                      @RequestParam(required = false) String keyword) {
        int safePage = page(page);
        int safeSize = size(size);
        boolean filtered = keyword != null && !keyword.isBlank();
        String where = filtered ? " WHERE user_id LIKE ? OR nick LIKE ? OR school LIKE ?" : "";
        Object[] filter = filtered ? new Object[]{like(keyword), like(keyword), like(keyword)} : new Object[0];
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM users" + where, Long.class, filter);
        Object[] args = new Object[filter.length + 2];
        System.arraycopy(filter, 0, args, 0, filter.length);
        args[filter.length] = safeSize;
        args[filter.length + 1] = (safePage - 1) * safeSize;
        List<UserRow> rows = jdbc.query(
                "SELECT user_id,nick,email,school,group_name,solved,submit,defunct,reg_time,accesstime,expiry_date " +
                        "FROM users" + where + " ORDER BY reg_time DESC,user_id LIMIT ? OFFSET ?",
                (rs, rowNum) -> new UserRow(
                        rs.getString("user_id"), rs.getString("nick"), rs.getString("email"),
                        rs.getString("school"), rs.getString("group_name"), rs.getInt("solved"),
                        rs.getInt("submit"), "Y".equals(rs.getString("defunct")),
                        rs.getTimestamp("reg_time") == null ? null : rs.getTimestamp("reg_time").toLocalDateTime(),
                        rs.getTimestamp("accesstime") == null ? null : rs.getTimestamp("accesstime").toLocalDateTime(),
                        rs.getDate("expiry_date") == null ? null : rs.getDate("expiry_date").toLocalDate()), args);
        return PageResponse.of(rows, safePage, safeSize, total == null ? 0 : total);
    }

    @PutMapping("/{userId}/state")
    public void state(@PathVariable String userId, @RequestBody StateRequest request,
                      Authentication authentication) {
        requireSuperAdminForAdministratorTarget(userId, authentication);
        if (jdbc.update("UPDATE users SET defunct=? WHERE user_id=?", request.disabled() ? "Y" : "N", userId) == 0) {
            throw ApiException.notFound("用户不存在");
        }
    }

    @GetMapping("/{userId}/roles")
    public List<String> roles(@PathVariable String userId) {
        return jdbc.query("SELECT rightstr FROM privilege WHERE user_id=? AND defunct='N' ORDER BY rightstr",
                (rs, rowNum) -> rs.getString(1), userId);
    }

    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public void addRole(@PathVariable String userId, @Valid @RequestBody RoleRequest request) {
        String role = validateRole(request.role());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM privilege WHERE user_id=? AND rightstr=?",
                Integer.class, userId, role);
        if (count != null && count > 0) {
            jdbc.update("UPDATE privilege SET defunct='N',valuestr='true' WHERE user_id=? AND rightstr=?", userId, role);
        } else {
            jdbc.update("INSERT INTO privilege(user_id,rightstr,valuestr,defunct) VALUES(?,?,'true','N')", userId, role);
        }
    }

    @DeleteMapping("/{userId}/roles/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public void removeRole(@PathVariable String userId, @PathVariable String role) {
        String validatedRole = validateRole(role);
        jdbc.update("UPDATE privilege SET defunct='Y' WHERE user_id=? AND rightstr=?", userId, validatedRole);
    }

    private String validateRole(String role) {
        List<String> allowed = List.of(
                "problem_editor", "contest_creator", "source_browser", "user_adder",
                "password_setter", "balloon", "vip");
        if (!allowed.contains(role)) {
            throw ApiException.badRequest("不支持的系统角色");
        }
        return role;
    }

    private void requireSuperAdminForAdministratorTarget(String userId, Authentication authentication) {
        Integer administrators = jdbc.queryForObject(
                "SELECT COUNT(*) FROM privilege WHERE user_id=? " +
                        "AND rightstr IN ('administrator','super_administrator') AND defunct='N'",
                Integer.class, userId);
        if (administrators != null && administrators > 0 && authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()))) {
            throw ApiException.forbidden("只有超级管理员可以停用或启用管理员账号");
        }
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    public record UserRow(String userId, String nick, String email, String school, String groupName,
                          int solved, int submit, boolean disabled, LocalDateTime registeredAt,
                          LocalDateTime lastActiveAt, LocalDate expiresAt) {
    }

    public record StateRequest(boolean disabled) {
    }

    public record RoleRequest(@NotBlank @Size(max = 30) String role) {
    }
}
