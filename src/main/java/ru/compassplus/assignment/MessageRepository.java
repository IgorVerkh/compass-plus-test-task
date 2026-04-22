package ru.compassplus.assignment;

/**
 * Контракт для слоя логирования сообщений в хранилище.
 *
 * <p>Нужен, чтобы сервер не зависел от конкретной технологии доступа к данным:
 * он передает только бизнес-поля из задания ({@code TIME, USER, TEXT, RESULT}),
 * а реализация решает, как именно сохранить их в БД.</p>
 */
public interface MessageRepository {
    void saveMessage(String time, String user, String text, int code);
}
