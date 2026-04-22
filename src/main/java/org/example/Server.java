package org.example;

import noNamespace.MessageDocument;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;

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
 *
 * <p>Параметры подключения, количество потоков в пуле и пути до базы данных и списка запрещенных слов берутся из
 * {@code application.properties}.</p>
 *
 * <p><b>Обработка сообщений больше одного TCP-пакета (фрейминг):</b>
 * <ul>
 *   <li>Для разделения сообщений используется newline-фрейминг: клиент отправляет
 *       XML + {@code \n}, сервер читает через {@link BufferedReader#readLine()}.</li>
 *   <li>{@code readLine()} автоматически буферизует входной поток и собирает данные,
 *       пока не встретит {@code \n}. Размер сообщения не ограничен размером TCP-сегмента
 *       (~1460 байт) — метод будет читать чанками, пока не получит полный фрейм.</li>
 * </ul>
 */
public class Server {
    private final int port;
    private final int threadPoolSize;
    private final String forbiddenWordsPath;
    private final Set<String> forbiddenWords = new HashSet<>();
    private final MessageRepository repository;

    public Server(int port, int threadPoolSize, String forbiddenWordsPath, MessageRepository repository) {
        this.port = port;
        this.threadPoolSize = threadPoolSize;
        this.forbiddenWordsPath = forbiddenWordsPath;
        this.repository = repository;
    }

    public static void main(String[] args) {
        Properties config = loadConfig();
        int port = Integer.parseInt(config.getProperty("app.port", "9090"));
        int threadPoolSize = Integer.parseInt(config.getProperty("app.server.threads", "10"));
        String dbUrl = config.getProperty("app.db.url", "jdbc:sqlite:messages.db");
        String forbiddenWordsPath = config.getProperty("app.forbidden.words.path", "forbidden_words.txt");
        MessageRepository repository = new SqliteMessageRepository(dbUrl);

        Server server = new Server(port, threadPoolSize, forbiddenWordsPath, repository);
        server.start();
    }

    @SuppressWarnings("InfiniteLoopStatement")
    public void start() {
        loadForbiddenWords();

        try (ExecutorService pool = Executors.newFixedThreadPool(threadPoolSize);
             ServerSocket server = new ServerSocket(port)) {

            System.out.println("Server started on port " + port);
            while (true) {
                Socket client = server.accept();
                pool.submit(() -> handleClient(client));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private void handleClient(Socket client) {
        try (client;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(
                     new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null) {
                processMessage(line, out);
            }

        } catch (IOException | XmlException e) {
            System.err.println("Client handling error: " + e.getMessage());
        }
    }

    private void processMessage(String xml, BufferedWriter out) throws IOException, XmlException {
        MessageDocument doc = MessageDocument.Factory.parse(xml);
        MessageDocument.Message msg = doc.getMessage();
        String time = msg.getHeader().getTime();
        int statusCode;
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

    private int moderationCodeForText(String text) {
        String lowerText = text.toLowerCase();
        for (String word : forbiddenWords) {
            if (lowerText.contains(word)) {
                return 1;
            }
        }
        return 0;
    }

    private void sendResponse(BufferedWriter out, String time, int code, String reason) throws IOException {
        MessageDocument doc = MessageDocument.Factory.newInstance();
        MessageDocument.Message msg = doc.addNewMessage();
        msg.addNewHeader().setTime(time);
        MessageDocument.Message.Response resp = msg.addNewResponse();
        MessageDocument.Message.Response.Status status = resp.addNewStatus();
        status.setCode(code);
        status.setReason(reason);

        out.write(doc.xmlText(new XmlOptions()));
        out.newLine();
        out.flush();
    }

    private void loadForbiddenWords() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(forbiddenWordsPath));
            for (String w : lines) forbiddenWords.add(w.toLowerCase().trim());
            System.out.println("Forbidden words loaded: " + forbiddenWords.size());
        } catch (IOException e) {
            System.err.println("Forbidden words file not found. Creating empty set.");
        }
    }

    private void logToDb(String time, String user, String text, int code) {
        repository.saveMessage(time, user, text, code);
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
