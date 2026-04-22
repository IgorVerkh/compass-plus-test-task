package org.example;

import noNamespace.MessageDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Интеграционные тесты по основным сценариям тестового задания.
 *
 * <p>Проверки поднимают реальный сервер, отправляют XML-запросы по TCP и
 * валидируют XML-ответ. Это позволяет автоматически подтвердить поведение,
 * которое обычно демонстрируют вручную: принятие корректного сообщения,
 * отклонение текста с запрещенным словом и корректную обработку длинного
 * сообщения (больше одного типичного TCP-сегмента).</p>
 */
class ClientServerIntegrationTest {
    private static final String HOST = "localhost";
    private static final int PORT = 9090;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static Process serverProcess;

    @BeforeAll
    static void startServer() throws Exception {
        serverProcess = new ProcessBuilder(
                "java",
                "-cp",
                System.getProperty("java.class.path"),
                "org.example.Server"
        ).directory(new File(System.getProperty("user.dir"))).start();

        waitUntilServerIsReady();
    }

    @AfterAll
    static void stopServer() {
        if (serverProcess != null) {
            serverProcess.destroy();
            if (serverProcess.isAlive()) {
                serverProcess.destroyForcibly();
            }
        }
    }

    @Test
    void allowedWordReturnsSuccessStatus() throws Exception {
        MessageDocument.Message.Response.Status status = sendAndReadStatus("hello world");
        assertEquals(0, status.getCode());
        assertEquals("success", status.getReason());
    }

    @Test
    void forbiddenWordReturnsRejectStatus() throws Exception {
        MessageDocument.Message.Response.Status status = sendAndReadStatus("this is spam");
        assertEquals(1, status.getCode());
        assertEquals("used inappropriate language", status.getReason());
    }

    @Test
    void longMessageBiggerThanSingleTcpSegmentReturnsStatus() throws Exception {
        String longText = "ok ".repeat(3000);
        MessageDocument.Message.Response.Status status = sendAndReadStatus(longText);
        assertEquals(0, status.getCode());
        assertEquals("success", status.getReason());
    }

    private static MessageDocument.Message.Response.Status sendAndReadStatus(String text) throws Exception {
        try (Socket socket = new Socket(HOST, PORT);
             Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            MessageDocument doc = MessageDocument.Factory.newInstance();
            MessageDocument.Message msg = doc.addNewMessage();
            msg.addNewHeader().setTime(LocalDateTime.now().format(FMT));
            MessageDocument.Message.Request req = msg.addNewRequest();
            req.setUser("integration-test");
            req.setText(text);

            out.write(doc.xmlText(new org.apache.xmlbeans.XmlOptions()));
            out.write("\n");
            out.flush();

            String responseXml = in.readLine();
            MessageDocument responseDoc = MessageDocument.Factory.parse(responseXml);
            return responseDoc.getMessage().getResponse().getStatus();
        }
    }

    private static void waitUntilServerIsReady() throws Exception {
        Exception lastError = null;
        for (int i = 0; i < 20; i++) {
            try (Socket ignored = new Socket(HOST, PORT)) {
                return;
            } catch (Exception e) {
                lastError = e;
                Thread.sleep(200);
            }
        }
        throw new IllegalStateException("Server did not start in time", lastError);
    }

}
