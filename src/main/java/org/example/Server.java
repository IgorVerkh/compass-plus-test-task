package org.example;

import noNamespace.MessageDocument;
import org.apache.xmlbeans.XmlException;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Серверная часть тестового задания: принимает XML-запросы, обрабатывает текст
 * и возвращает XML-ответ по заданному протоколу.
 *
 * <p>Реализует ключевые требования раздела "Сервер": открывает серверный порт,
 * принимает клиентские подключения и передает обработку в пул потоков, чтобы
 * несколько сообщений могли обрабатываться параллельно.</p>
 *
 * <p>Каждое сообщение разбирается через XmlBeans. Если в тексте найдено слово
 * из локального словаря запрещенных слов, формируется ответ
 * {@code code=1, reason=used inappropriate language}; иначе
 * {@code code=0, reason=success}.</p>
 *
 * <p>Результат обработки записывается в БД через {@link MessageRepository}.
 * Таким образом в классе остается прикладная логика протокола, а детали JDBC
 * вынесены в отдельную реализацию репозитория.</p>
 */
public class Server {
    private static final Properties CONFIG = loadConfig();
    private static final int PORT = Integer.parseInt(CONFIG.getProperty("app.port", "9090"));
    private static final String DB_URL = CONFIG.getProperty("app.db.url", "jdbc:sqlite:messages.db");
    private static final String FORBIDDEN_WORDS_PATH = CONFIG.getProperty("app.forbidden.words.path", "forbidden_words.txt");
    private static final Set<String> FORBIDDEN_WORDS = new HashSet<>();
    private static final MessageRepository REPOSITORY = new SqliteMessageRepository(DB_URL);

    public static void main(String[] args) {
        loadForbiddenWords();
        ExecutorService pool = Executors.newFixedThreadPool(10);

        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("🟢 Server started on port " + PORT);
            while (true) {
                Socket client = server.accept();
                pool.submit(() -> handleClient(client));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }

    private static void handleClient(Socket client) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null) {
                processMessage(line, out);
            }
        } catch (IOException | XmlException e) {
            System.err.println("Client handling error: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private static void processMessage(String xml, BufferedWriter out) throws IOException, XmlException {
        MessageDocument doc = MessageDocument.Factory.parse(xml);
        MessageDocument.Message msg = doc.getMessage();
        String time = msg.getHeader().getTime();
        int statusCode = 0;
        String reason = "success";

        if (msg.isSetRequest()) {
            String user = msg.getRequest().getUser();
            String text = msg.getRequest().getText();

            statusCode = moderationCodeForText(text);
            if (statusCode == 1) {
                reason = "used inappropriate language";
            }

            logToDb(time, user, text, statusCode);
            sendResponse(out, time, statusCode, reason);
        }
    }

    private static int moderationCodeForText(String text) {
        String lowerText = text.toLowerCase();
        for (String word : Server.FORBIDDEN_WORDS) {
            if (lowerText.contains(word)) {
                return 1;
            }
        }
        return 0;
    }

    private static void sendResponse(BufferedWriter out, String time, int code, String reason) throws IOException {
        MessageDocument doc = MessageDocument.Factory.newInstance();
        MessageDocument.Message msg = doc.addNewMessage();
        msg.addNewHeader().setTime(time);
        MessageDocument.Message.Response resp = msg.addNewResponse();
        MessageDocument.Message.Response.Status status = resp.addNewStatus();
        status.setCode(code);
        status.setReason(reason);

        out.write(doc.xmlText(new org.apache.xmlbeans.XmlOptions()));
        out.newLine(); // Обязательно для readLine() на клиенте
        out.flush();
    }

    private static void loadForbiddenWords() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(FORBIDDEN_WORDS_PATH));
            for (String w : lines) FORBIDDEN_WORDS.add(w.toLowerCase().trim());
            System.out.println("✅ Forbidden words loaded: " + FORBIDDEN_WORDS.size());
        } catch (IOException e) {
            System.err.println("⚠️ Forbidden words file not found. Creating empty set.");
        }
    }

    private static void logToDb(String time, String user, String text, int code) {
        REPOSITORY.saveMessage(time, user, text, code);
    }

    private static Properties loadConfig() {
        Properties properties = new Properties();
        try (InputStream in = Server.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            System.err.println("Config load error: " + e.getMessage());
        }
        return properties;
    }
}
