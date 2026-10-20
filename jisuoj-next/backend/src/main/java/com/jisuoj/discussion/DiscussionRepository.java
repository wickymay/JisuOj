package com.jisuoj.discussion;

import com.jisuoj.common.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.util.List;

@Repository
public class DiscussionRepository {
    private final JdbcTemplate jdbc;

    public DiscussionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<DiscussionDtos.TopicSummary> list(int page, int size, Integer problemId, Integer contestId) {
        StringBuilder where = new StringBuilder(" WHERE t.status=0 ");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (problemId != null) {
            where.append(" AND t.pid=? ");
            args.add(problemId);
        }
        if (contestId != null) {
            where.append(" AND t.cid=? ");
            args.add(contestId);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM topic t" + where, Long.class, args.toArray());
        args.add(size);
        args.add((page - 1) * size);
        List<DiscussionDtos.TopicSummary> rows = jdbc.query(
                "SELECT t.tid,CONVERT(t.title USING utf8mb4) title,t.author_id,t.pid,t.cid,t.top_level," +
                        "COUNT(r.rid) reply_count,MAX(r.time) last_reply_at " +
                        "FROM topic t LEFT JOIN reply r ON r.topic_id=t.tid AND r.status=0" + where +
                        " GROUP BY t.tid,t.title,t.author_id,t.pid,t.cid,t.top_level " +
                        "ORDER BY t.top_level DESC,last_reply_at DESC,t.tid DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    int cid = rs.getInt("cid");
                    boolean cidWasNull = rs.wasNull();
                    return new DiscussionDtos.TopicSummary(
                            rs.getInt("tid"), rs.getString("title"), rs.getString("author_id"), rs.getInt("pid"),
                            cidWasNull || cid == 0 ? null : cid, rs.getInt("reply_count"),
                            rs.getTimestamp("last_reply_at") == null ? null : rs.getTimestamp("last_reply_at").toLocalDateTime(),
                            rs.getInt("top_level"));
                }, args.toArray());
        return PageResponse.of(rows, page, size, total == null ? 0 : total);
    }

    public DiscussionDtos.Topic detail(int id) {
        List<TopicRow> topics = jdbc.query(
                "SELECT t.tid,CONVERT(t.title USING utf8mb4) title,t.author_id,t.pid,t.cid,MIN(r.time) created_at " +
                        "FROM topic t LEFT JOIN reply r ON r.topic_id=t.tid WHERE t.tid=? AND t.status=0 " +
                        "GROUP BY t.tid,t.title,t.author_id,t.pid,t.cid",
                (rs, rowNum) -> {
                    int cid = rs.getInt("cid");
                    boolean cidWasNull = rs.wasNull();
                    return new TopicRow(rs.getInt("tid"), rs.getString("title"), rs.getString("author_id"),
                            rs.getInt("pid"), cidWasNull || cid == 0 ? null : cid,
                            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());
                }, id);
        if (topics.isEmpty()) {
            return null;
        }
        TopicRow topic = topics.get(0);
        List<DiscussionDtos.Reply> replies = jdbc.query(
                "SELECT rid,author_id,content,time FROM reply WHERE topic_id=? AND status=0 ORDER BY rid",
                (rs, rowNum) -> new DiscussionDtos.Reply(
                        rs.getInt("rid"), rs.getString("author_id"), rs.getString("content"),
                        rs.getTimestamp("time").toLocalDateTime()), id);
        return new DiscussionDtos.Topic(topic.id(), topic.title(), topic.author(), topic.problemId(),
                topic.contestId(), topic.createdAt(), replies);
    }

    public int createTopic(DiscussionDtos.CreateTopicRequest request, String author, String ip) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO topic(title,status,top_level,cid,pid,author_id) VALUES(?,0,0,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.title().trim());
            if (request.contestId() == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setInt(2, request.contestId());
            }
            statement.setInt(3, request.problemId() == null ? 0 : request.problemId());
            statement.setString(4, author);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("讨论编号生成失败");
        }
        int id = key.intValue();
        createReply(id, request.content(), author, ip);
        return id;
    }

    public int createReply(int topicId, String content, String author, String ip) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO reply(author_id,time,content,topic_id,status,ip) VALUES(?,NOW(),?,?,0,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, author);
            statement.setString(2, content);
            statement.setInt(3, topicId);
            statement.setString(4, ip);
            return statement;
        }, keys);
        Number key = keys.getKey();
        return key == null ? 0 : key.intValue();
    }

    public boolean exists(int topicId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM topic WHERE tid=? AND status=0", Integer.class, topicId);
        return count != null && count > 0;
    }

    public boolean hideTopic(int id) {
        return jdbc.update("UPDATE topic SET status=1 WHERE tid=?", id) > 0;
    }

    private record TopicRow(int id, String title, String author, int problemId, Integer contestId,
                            java.time.LocalDateTime createdAt) {
    }
}
