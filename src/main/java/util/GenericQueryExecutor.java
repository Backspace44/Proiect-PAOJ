package main.java.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Singleton utility class for executing generic SQL queries and updates.
 * This class simplifies JDBC operations by providing methods to execute statements,
 * map results to objects using a {@link RowMapper}, and handle parameters.
 * It supports operations that manage their own database connections and operations
 * that use an externally provided connection (useful for transactions).
 */
public class GenericQueryExecutor {
    private static GenericQueryExecutor instance;

    /**
     * Private constructor to enforce the Singleton pattern.
     */
    private GenericQueryExecutor() {
        // Private constructor for Singleton pattern.
    }

    /**
     * Returns the Singleton instance of the GenericQueryExecutor.
     * @return The single instance of GenericQueryExecutor.
     */
    public static synchronized GenericQueryExecutor getInstance() {
        if (instance == null) {
            instance = new GenericQueryExecutor();
        }
        return instance;
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T mapRow(ResultSet rs) throws SQLException;
    }

    private void setParameters(PreparedStatement pstmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param instanceof UUID) {
                pstmt.setString(i + 1, param.toString());
            } else if (param instanceof Enum) {
                pstmt.setString(i + 1, ((Enum<?>) param).name());
            } else if (param instanceof LocalDateTime) {
                pstmt.setTimestamp(i + 1, Timestamp.valueOf((LocalDateTime) param));
            } else {
                pstmt.setObject(i + 1, param);
            }
        }
    }

    // --- Metode care gestionează propria conexiune ---

    public <T> List<T> executeQuery(String sql, RowMapper<T> rowMapper, Object... params) throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setParameters(pstmt, params);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(rowMapper.mapRow(rs));
                }
                return results;
            }
        }
    }

    public <T> T executeQuerySingle(String sql, RowMapper<T> rowMapper, Object... params) throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setParameters(pstmt, params);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rowMapper.mapRow(rs);
                }
            }
        }
        return null;
    }

    public int executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setParameters(pstmt, params);
            return pstmt.executeUpdate();
        }
    }

    // --- Variante care acceptă o conexiune existentă (pentru tranzacții) ---

    public <T> List<T> executeQuery(Connection conn, String sql, RowMapper<T> rowMapper, Object... params) throws SQLException {
        List<T> results = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setParameters(pstmt, params);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(rowMapper.mapRow(rs));
                }
            }
        }
        return results;
    }

    public <T> T executeQuerySingle(Connection conn, String sql, RowMapper<T> rowMapper, Object... params) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setParameters(pstmt, params);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rowMapper.mapRow(rs);
                }
            }
        }
        return null;
    }

    public int executeUpdate(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setParameters(pstmt, params);
            return pstmt.executeUpdate();
        }
    }
}