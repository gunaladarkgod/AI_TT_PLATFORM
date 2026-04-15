package com.xgls.platform.v2.auth.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountRepository {

    private final JdbcTemplate jdbc;

    public UserAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserAccount> findByUsername(String username) {
        String sql = """
                SELECT id, username, password_hash, status
                FROM usr_user
                WHERE username = ?
                """;
        List<UserAccount> rows = jdbc.query(sql, ROW, username);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        UserAccount u = rows.get(0);
        String rolesSql = """
                SELECT r.code
                FROM usr_role r
                INNER JOIN usr_user_role ur ON r.id = ur.role_id
                WHERE ur.user_id = ?
                """;
        List<String> roles = jdbc.query(rolesSql, (rs, n) -> rs.getString(1), u.id());
        return Optional.of(new UserAccount(u.id(), u.username(), u.passwordHash(), u.status(), roles));
    }

    private static final RowMapper<UserAccount> ROW = (rs, rowNum) -> mapRow(rs);

    private static UserAccount mapRow(ResultSet rs) throws SQLException {
        return new UserAccount(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getInt("status"),
                List.of());
    }

    public record UserAccount(long id, String username, String passwordHash, int status, List<String> roleCodes) {
        /** Legacy JWT claim {@code type}: 1 = system admin, 3 = normal user (matches old CodeMap). */
        public int legacyUserType() {
            return roleCodes.stream().anyMatch(c -> "ADMIN".equalsIgnoreCase(c)) ? 1 : 3;
        }

        public boolean active() {
            return status == 1;
        }
    }
}
