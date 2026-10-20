package com.jisuoj.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UserDtos.Profile profile(String userId, String viewerId) {
        List<BaseProfile> profiles = jdbc.query(
                "SELECT user_id,nick,school,group_name,email,solved,submit,reg_time,accesstime,starred," +
                        "coin_earned,coin_bonus,coin_spent FROM users " +
                        "WHERE user_id=? AND defunct='N'",
                (rs, rowNum) -> new BaseProfile(
                        rs.getString("user_id"), rs.getString("nick"), rs.getString("school"), rs.getString("group_name"),
                        rs.getString("email"), rs.getInt("solved"), rs.getInt("submit"),
                        rs.getTimestamp("reg_time") == null ? null : rs.getTimestamp("reg_time").toLocalDateTime(),
                        rs.getTimestamp("accesstime") == null ? null : rs.getTimestamp("accesstime").toLocalDateTime(),
                        rs.getBoolean("starred"), rs.getInt("coin_earned"), rs.getInt("coin_bonus"),
                        rs.getInt("coin_spent")), userId);
        if (profiles.isEmpty()) {
            return null;
        }
        BaseProfile row = profiles.get(0);
        Long followers = jdbc.queryForObject("SELECT COUNT(*) FROM user_follow WHERE followed_id=?", Long.class, userId);
        Long following = jdbc.queryForObject("SELECT COUNT(*) FROM user_follow WHERE follower_id=?", Long.class, userId);
        boolean followed = viewerId != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COUNT(*)>0 FROM user_follow WHERE follower_id=? AND followed_id=?", Boolean.class, viewerId, userId));
        List<Integer> solved = jdbc.query(
                "SELECT DISTINCT problem_id FROM solution WHERE user_id=? AND result=4 AND valid=1 AND problem_id>0 ORDER BY problem_id",
                (rs, rowNum) -> rs.getInt(1), userId);
        List<Integer> attempted = jdbc.query(
                "SELECT DISTINCT s.problem_id FROM solution s WHERE s.user_id=? AND s.valid=1 AND s.problem_id>0 " +
                        "AND NOT EXISTS (SELECT 1 FROM solution ac WHERE ac.user_id=s.user_id AND ac.problem_id=s.problem_id " +
                        "AND ac.result=4 AND ac.valid=1) ORDER BY s.problem_id",
                (rs, rowNum) -> rs.getInt(1), userId);
        Integer rank = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE solved>? AND defunct='N'", Integer.class, row.solved());
        List<UserDtos.ResultStat> resultStats = jdbc.query(
                "SELECT result,COUNT(*) AS total FROM solution WHERE user_id=? AND valid=1 AND result>=4 " +
                        "GROUP BY result ORDER BY result",
                (rs, rowNum) -> new UserDtos.ResultStat(rs.getInt("result"), rs.getLong("total")), userId);
        List<UserDtos.DailyStat> dailyStats = jdbc.query(
                "SELECT DATE(in_date) AS day,COUNT(*) AS submissions,SUM(CASE WHEN result=4 THEN 1 ELSE 0 END) AS accepted " +
                        "FROM solution WHERE user_id=? AND valid=1 AND in_date>=DATE_SUB(CURDATE(),INTERVAL 1 YEAR) " +
                        "GROUP BY DATE(in_date) ORDER BY day",
                (rs, rowNum) -> new UserDtos.DailyStat(
                        rs.getDate("day").toLocalDate(), rs.getLong("submissions"), rs.getLong("accepted")), userId);
        List<UserDtos.ContestStat> contestStats = jdbc.query(
                "SELECT c.contest_id,c.title,c.start_time,COUNT(DISTINCT s.problem_id) AS tried," +
                        "COUNT(DISTINCT CASE WHEN s.result=4 THEN s.problem_id END) AS accepted " +
                        "FROM contest c JOIN solution s ON s.contest_id=c.contest_id " +
                        "WHERE s.user_id=? AND s.valid=1 AND s.contest_id>0 AND c.defunct='N' " +
                        "GROUP BY c.contest_id,c.title,c.start_time ORDER BY c.contest_id DESC",
                (rs, rowNum) -> new UserDtos.ContestStat(
                        rs.getInt("contest_id"), rs.getString("title"),
                        rs.getTimestamp("start_time") == null ? null : rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getLong("tried"), rs.getLong("accepted")), userId);
        return new UserDtos.Profile(row.userId(), row.nick(), row.school(), row.groupName(), row.email(),
                row.solved(), row.submit(), row.registeredAt(), row.lastActiveAt(),
                followers == null ? 0 : followers, following == null ? 0 : following, followed, solved, attempted,
                (rank == null ? 0 : rank) + 1, row.starred(), row.coinEarned(), row.coinBonus(), row.coinSpent(),
                resultStats, dailyStats, contestStats);
    }

    public void update(String userId, UserDtos.UpdateProfileRequest request) {
        jdbc.update("UPDATE users SET nick=?,school=?,group_name=?,email=? WHERE user_id=?",
                safe(request.nick()), safe(request.school()), safe(request.groupName()), nullable(request.email()), userId);
    }

    public void follow(String follower, String followed) {
        jdbc.update("INSERT IGNORE INTO user_follow(follower_id,followed_id,in_date) VALUES(?,?,NOW())", follower, followed);
    }

    public void unfollow(String follower, String followed) {
        jdbc.update("DELETE FROM user_follow WHERE follower_id=? AND followed_id=?", follower, followed);
    }

    public List<UserDtos.Message> messages(String profileUserId, int limit) {
        return jdbc.query(
                "SELECT message_id,profile_user_id,author_id,content,in_date FROM user_message " +
                        "WHERE profile_user_id=? AND defunct='N' ORDER BY message_id DESC LIMIT ?",
                (rs, rowNum) -> new UserDtos.Message(
                        rs.getLong("message_id"), rs.getString("profile_user_id"), rs.getString("author_id"),
                        rs.getString("content"), rs.getTimestamp("in_date").toLocalDateTime()), profileUserId, limit);
    }

    public void addMessage(String profileUserId, String author, String content, String ip) {
        jdbc.update("INSERT INTO user_message(profile_user_id,author_id,content,in_date,ip,defunct) VALUES(?,?,?,NOW(),?,'N')",
                profileUserId, author, content, ip);
    }

    public boolean deleteMessage(long id, String actor, boolean admin) {
        return jdbc.update("UPDATE user_message SET defunct='Y' WHERE message_id=? AND (profile_user_id=? OR author_id=? OR ?=1)",
                id, actor, actor, admin ? 1 : 0) > 0;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record BaseProfile(String userId, String nick, String school, String groupName, String email,
                               int solved, int submit, java.time.LocalDateTime registeredAt,
                               java.time.LocalDateTime lastActiveAt, boolean starred, int coinEarned,
                               int coinBonus, int coinSpent) {
    }
}
