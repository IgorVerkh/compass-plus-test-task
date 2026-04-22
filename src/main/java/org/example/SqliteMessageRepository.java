package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * JDBC-реализация {@link MessageRepository} для SQLite.
 *
 * <p>Закрывает требования задания по работе с БД: используется чистый JDBC,
 * при инициализации создается таблица {@code MESSAGES} с нужными полями, а при
 * каждом запросе сохраняется запись с кодом результата обработки.</p>
 *
 * <p>Запись синхронизирована через внутренний lock, чтобы обращения из разных
 * серверных потоков не конфликтовали при работе с одним соединением.</p>
 */
public class SqliteMessageRepository implements MessageRepository {
    private final String dbUrl;
    private final Object lock = new Object();
    private Connection connection;

    public SqliteMessageRepository(String dbUrl) {
        this.dbUrl = dbUrl;
        initialize();
    }

    @Override
    public void saveMessage(String time, String user, String text, int code) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO MESSAGES (TIME, USER, TEXT, RESULT) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, time);
                ps.setString(2, user);
                ps.setString(3, text);
                ps.setInt(4, code);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("DB insert error: " + e.getMessage());
            }
        }
    }

    private void initialize() {
        try {
            connection = DriverManager.getConnection(dbUrl);
            connection.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS MESSAGES (ID INTEGER PRIMARY KEY AUTOINCREMENT, TIME TEXT, USER TEXT, TEXT TEXT, RESULT INTEGER)");
            System.out.println("✅ DB initialized");
        } catch (SQLException e) {
            System.err.println("DB init error: " + e.getMessage());
        }
    }
}
