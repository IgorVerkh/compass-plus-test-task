package org.example;

import noNamespace.MessageDocument;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Клиентская часть тестового задания: консольный TCP-клиент без внешних фреймворков.
 *
 * <p>Реализует требования раздела "Клиент": при запуске устанавливает соединение
 * с сервером, принимает команды {@code -m} и {@code -h}, формирует XML-запрос
 * через классы XmlBeans и отправляет его в сокет.</p>
 *
 * <p>Ответ сервера читается в отдельном потоке, разбирается через XmlBeans и
 * выводится в консоль в виде статуса обработки ({@code code}/{@code reason}).
 * В поле {@code user} используется имя пользователя ОС.</p>
 *
 * <p>Параметры подключения и формат времени берутся из
 * {@code application.properties}.</p>
 */
public class Client {
    private static final Properties CONFIG = loadConfig();
    private static final String HOST = CONFIG.getProperty("app.host", "localhost");
    private static final int PORT = Integer.parseInt(CONFIG.getProperty("app.port", "9090"));
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern(
            CONFIG.getProperty("app.datetime.format", "yyyy-MM-dd HH:mm:ss")
    );

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try (Socket socket = new Socket(HOST, PORT)) {
            System.out.println("Connected to server.");

            // Поток чтения ответов сервера
            pool.submit(() -> readServerResponses(socket));

            // Основной поток чтения консоли
            Scanner scanner = new Scanner(System.in);
            System.out.println("Commands: -m <text> to send | -h to exit");

            while (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();
                if (input.startsWith("-m ")) {
                    sendMessage(socket, input.substring(3));
                } else if (input.equalsIgnoreCase("-h")) {
                    System.out.println("Exiting...");
                    socket.close();
                    pool.shutdown();
                    break;
                } else {
                    System.out.println("Unknown command. Use: -m <text> or -h");
                }
            }
        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    private static void readServerResponses(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    MessageDocument doc = MessageDocument.Factory.parse(line);
                    MessageDocument.Message msg = doc.getMessage();
                    if (msg.isSetResponse()) {
                        int code = msg.getResponse().getStatus().getCode();
                        String reason = msg.getResponse().getStatus().getReason();
                        System.out.println("[SERVER] Code: " + code + " | Reason: " + reason);
                    }
                } catch (Exception e) {
                    System.err.println("Parse response error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Disconnected from server.");
        }
    }

    private static void sendMessage(Socket socket, String text) throws IOException {
        String time = LocalDateTime.now().format(FMT);
        String user = System.getProperty("user.name");

        MessageDocument doc = MessageDocument.Factory.newInstance();
        MessageDocument.Message msg = doc.addNewMessage();
        MessageDocument.Message.Request req = msg.addNewRequest();
        req.setUser(user);
        req.setText(text);
        msg.addNewHeader().setTime(time);

        Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        out.write(doc.xmlText(new org.apache.xmlbeans.XmlOptions()));
        out.write("\n");
        out.flush();
    }

    private static Properties loadConfig() {
        Properties properties = new Properties();
        try (InputStream in = Client.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            System.err.println("Config load error: " + e.getMessage());
        }
        return properties;
    }
}
