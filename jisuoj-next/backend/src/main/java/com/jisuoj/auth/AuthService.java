package com.jisuoj.auth;

import com.jisuoj.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final LegacyPasswordService legacyPasswordService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JdbcTemplate jdbc,
                       LegacyPasswordService legacyPasswordService,
                       PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.legacyPasswordService = legacyPasswordService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserPrincipal authenticate(String userId, String password) {
        List<AccountRow> accounts = jdbc.query(
                "SELECT user_id,nick,password,defunct,expiry_date FROM users WHERE user_id=? LIMIT 1",
                (rs, rowNum) -> new AccountRow(
                        rs.getString("user_id"), rs.getString("nick"), rs.getString("password"),
                        rs.getString("defunct"), rs.getDate("expiry_date") == null ? null : rs.getDate("expiry_date").toLocalDate()),
                userId
        );
        if (accounts.isEmpty()) {
            throw ApiException.unauthorized("用户名或密码错误");
        }
        AccountRow account = accounts.get(0);
        if ("Y".equalsIgnoreCase(account.defunct())) {
            throw ApiException.forbidden("账号已被停用");
        }
        if (account.expiryDate() != null && account.expiryDate().isBefore(java.time.LocalDate.now())) {
            throw ApiException.forbidden("账号已过期");
        }

        List<String> upgraded = jdbc.query(
                "SELECT password_hash FROM jisu_user_credential WHERE user_id=?",
                (rs, rowNum) -> rs.getString(1), userId);
        boolean matched = !upgraded.isEmpty()
                ? passwordEncoder.matches(password, upgraded.get(0))
                : legacyPasswordService.matches(password, account.password());
        if (!matched) {
            throw ApiException.unauthorized("用户名或密码错误");
        }
        if (upgraded.isEmpty()) {
            jdbc.update("INSERT INTO jisu_user_credential(user_id,password_hash,upgraded_at) VALUES(?,?,NOW()) " +
                            "ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash),upgraded_at=NOW()",
                    userId, passwordEncoder.encode(password));
        }
        jdbc.update("UPDATE users SET accesstime=NOW() WHERE user_id=?", userId);
        return principal(account.userId(), account.nick());
    }

    @Transactional
    public UserPrincipal register(String userId, String password, String nick, String email, String ip) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE user_id=?", Integer.class, userId);
        if (count != null && count > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "用户名已存在");
        }
        String displayName = nick == null || nick.isBlank() ? userId : nick.trim();
        jdbc.update("INSERT INTO users(user_id,email,submit,solved,defunct,ip,accesstime,password,reg_time,nick) " +
                        "VALUES(?,?,0,0,'N',?,NOW(),?,NOW(),?)",
                userId, emptyToNull(email), ip, legacyPasswordService.encode(password), displayName);
        jdbc.update("INSERT INTO jisu_user_credential(user_id,password_hash,upgraded_at) VALUES(?,?,NOW())",
                userId, passwordEncoder.encode(password));
        return principal(userId, displayName);
    }

    public UserPrincipal refresh(String userId) {
        List<String> nicks = jdbc.query("SELECT nick FROM users WHERE user_id=? AND defunct='N' " +
                        "AND (expiry_date IS NULL OR expiry_date>=CURRENT_DATE)",
                (rs, rowNum) -> rs.getString(1), userId);
        if (nicks.isEmpty()) {
            throw ApiException.unauthorized("登录状态已失效");
        }
        return principal(userId, nicks.get(0));
    }

    public UserPrincipal principal(String userId, String nick) {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        List<String> rights = jdbc.query(
                // The legacy site treats every non-defunct privilege row as active,
                // including rows whose valuestr is used for a contest/problem value.
                "SELECT rightstr FROM privilege WHERE user_id=? AND defunct='N'",
                (rs, rowNum) -> rs.getString(1), userId);
        for (String rawRight : rights) {
            String right = rawRight == null ? "" : rawRight.trim().toLowerCase(Locale.ROOT);
            // Contest/problem grants are checked against privilege directly by the
            // relevant repositories; putting every p123/c123 grant into a JWT can
            // make the login cookie unnecessarily large.
            if (right.matches("[cmp]\\d+")) {
                continue;
            }
            if ("super_administrator".equals(right)) {
                // Super administrators inherit every ordinary administrator ability.
                roles.add("ROLE_SUPER_ADMIN");
                roles.add("ROLE_ADMIN");
                continue;
            }
            roles.add(switch (right) {
                case "administrator" -> "ROLE_ADMIN";
                case "problem_editor" -> "ROLE_PROBLEM_EDITOR";
                case "contest_creator" -> "ROLE_CONTEST_CREATOR";
                case "source_browser" -> "ROLE_SOURCE_BROWSER";
                case "user_adder" -> "ROLE_USER_MANAGER";
                case "password_setter" -> "ROLE_PASSWORD_SETTER";
                case "balloon" -> "ROLE_BALLOON";
                case "vip" -> "ROLE_VIP";
                default -> "PRIV_" + right.toUpperCase().replaceAll("[^A-Z0-9_]", "_");
            });
        }
        return new UserPrincipal(userId, nick == null || nick.isBlank() ? userId : nick, Set.copyOf(roles));
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record AccountRow(String userId, String nick, String password, String defunct,
                              java.time.LocalDate expiryDate) {
    }
}
