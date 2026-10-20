package com.jisuoj.mail;

import com.jisuoj.auth.AuthSupport;
import com.jisuoj.auth.UserPrincipal;
import com.jisuoj.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mail")
@PreAuthorize("isAuthenticated()")
public class MailController {
    private final JdbcTemplate jdbc;

    public MailController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<MailDtos.Message> list(@RequestParam(defaultValue = "inbox") String box,
                                       Authentication authentication) {
        String userId = AuthSupport.required(authentication).userId();
        boolean sent = "sent".equalsIgnoreCase(box);
        String column = sent ? "from_user" : "to_user";
        return jdbc.query("SELECT mail_id,to_user,from_user,title,content,new_mail,in_date FROM mail " +
                        "WHERE " + column + "=? AND defunct='N' ORDER BY mail_id DESC LIMIT 100",
                (rs, rowNum) -> new MailDtos.Message(
                        rs.getLong("mail_id"), rs.getString("to_user"), rs.getString("from_user"),
                        rs.getString("title"), rs.getString("content"), rs.getInt("new_mail") == 1,
                        rs.getTimestamp("in_date").toLocalDateTime(), sent), userId);
    }

    @PostMapping
    public Map<String, Long> send(@Valid @RequestBody MailDtos.SendRequest request,
                                  Authentication authentication) {
        UserPrincipal sender = AuthSupport.required(authentication);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE user_id=? AND defunct='N'",
                Integer.class, request.toUser());
        if (exists == null || exists == 0) {
            throw ApiException.notFound("收件人不存在");
        }
        org.springframework.jdbc.support.GeneratedKeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO mail(to_user,from_user,title,content,new_mail,reply,in_date,defunct) VALUES(?,?,?,?,1,?,NOW(),'N')",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.toUser().trim());
            statement.setString(2, sender.userId());
            statement.setString(3, request.title().trim());
            statement.setString(4, request.content());
            statement.setLong(5, request.replyTo() == null ? 0 : request.replyTo());
            return statement;
        }, keys);
        Number key = keys.getKey();
        return Map.of("id", key == null ? 0 : key.longValue());
    }

    @PostMapping("/{id}/read")
    public void read(@PathVariable long id, Authentication authentication) {
        String userId = AuthSupport.required(authentication).userId();
        if (jdbc.update("UPDATE mail SET new_mail=0 WHERE mail_id=? AND to_user=?", id, userId) == 0) {
            throw ApiException.notFound("邮件不存在");
        }
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id, Authentication authentication) {
        String userId = AuthSupport.required(authentication).userId();
        if (jdbc.update("UPDATE mail SET defunct='Y' WHERE mail_id=? AND (to_user=? OR from_user=?)",
                id, userId, userId) == 0) {
            throw ApiException.notFound("邮件不存在");
        }
    }
}
