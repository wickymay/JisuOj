package com.jisuoj.news;

import com.jisuoj.common.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.util.List;

@Repository
public class NewsRepository {
    private final JdbcTemplate jdbc;

    public NewsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<NewsDtos.Summary> list(int page, int size, boolean includeHidden) {
        String where = includeHidden ? "" : " WHERE defunct='N'";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM news" + where, Long.class);
        List<NewsDtos.Summary> rows = jdbc.query(
                "SELECT news_id,title,user_id,time,importance FROM news" + where +
                        " ORDER BY importance ASC,time DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new NewsDtos.Summary(
                        rs.getInt("news_id"), rs.getString("title"), rs.getString("user_id"),
                        rs.getTimestamp("time").toLocalDateTime(), rs.getInt("importance")),
                size, (page - 1) * size);
        return PageResponse.of(rows, page, size, total == null ? 0 : total);
    }

    public NewsDtos.Detail detail(int id, boolean includeHidden) {
        List<NewsDtos.Detail> rows = jdbc.query(
                "SELECT news_id,title,content,user_id,time,importance,menu,defunct FROM news WHERE news_id=?" +
                        (includeHidden ? "" : " AND defunct='N'"),
                (rs, rowNum) -> new NewsDtos.Detail(
                        rs.getInt("news_id"), rs.getString("title"), rs.getString("content"),
                        rs.getString("user_id"), rs.getTimestamp("time").toLocalDateTime(),
                        rs.getInt("importance"), rs.getInt("menu") == 1,
                        "Y".equalsIgnoreCase(rs.getString("defunct"))), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int create(NewsDtos.SaveRequest request, String userId) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO news(user_id,title,content,time,importance,menu,defunct) VALUES(?,?,?,NOW(),?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, userId);
            statement.setString(2, request.title().trim());
            statement.setString(3, request.content());
            statement.setInt(4, request.importance() == null ? 0 : request.importance());
            statement.setInt(5, Boolean.TRUE.equals(request.menu()) ? 1 : 0);
            statement.setString(6, Boolean.TRUE.equals(request.hidden()) ? "Y" : "N");
            return statement;
        }, keys);
        Number key = keys.getKey();
        return key == null ? 0 : key.intValue();
    }

    public boolean update(int id, NewsDtos.SaveRequest request) {
        return jdbc.update("UPDATE news SET title=?,content=?,importance=?,menu=?,defunct=? WHERE news_id=?",
                request.title().trim(), request.content(), request.importance() == null ? 0 : request.importance(),
                Boolean.TRUE.equals(request.menu()) ? 1 : 0, Boolean.TRUE.equals(request.hidden()) ? "Y" : "N", id) > 0;
    }

    public boolean hide(int id) {
        return jdbc.update("UPDATE news SET defunct='Y' WHERE news_id=?", id) > 0;
    }
}
