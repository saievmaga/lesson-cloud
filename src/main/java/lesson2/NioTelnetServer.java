package lesson2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class NioTelnetServer {
    private static final String LS_COMMAND         = "\tls          view all files from current directory\r\n";
    private static final String MKDIR_COMMAND      = "\tmkdir       create directory, format : mkdir new_directory\r\n";
    private static final String TOUCH_COMMAND      = "\ttouch       create file, format : touch new_file\r\n";
    private static final String CD_COMMAND         = "\tcd          change directory, format : cd ~ | .. | new_path \r\n";
    private static final String RM_COMMAND         = "\trm          remove file / directory, format : rm file | directory\r\n";
    private static final String COPY_COMMAND       = "\tcopy        copy file / directory, format : copy source destination\r\n";
    private static final String CAT_COMMAND        = "\tcat         get textfile context, format : cat file\r\n";
    private static final String CHANGENICK_COMMAND = "\tchangenick  change user nick, format : changenick new_nick\r\n";

    //список поддерживаемых команд
    private final List<String> commands = Arrays.asList(new String[]{"--help", "changenick", "ls", "mkdir", "touch", "cd", "rm", "copy", "cat", "Y", "N"});

    private static String lastCommandWithResponseRequest;
    private static String lastCommandParam;

    private final ByteBuffer buffer = ByteBuffer.allocate(512);

    private final Map<SocketAddress, String> clients = new HashMap<>();

    private final ServerLogic serverLogic;

    public NioTelnetServer() throws Exception {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(5679));
        server.configureBlocking(false);
        Selector selector = Selector.open();

        server.register(selector, SelectionKey.OP_ACCEPT);

        serverLogic = new ServerLogic(Path.of("server")); //На данном этапе реализации для каждого подключенного пользователя
        //не выделяем отдельный каталог, все работают с каталогом SERVER

        System.out.println("Server started");
        while (server.isOpen()) {
            selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                } else if (key.isReadable()) {
                    handleRead(key, selector);
                }
                iterator.remove();
            }
        }
    }

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        System.out.println("Client connected. IP:" + channel.getRemoteAddress());
        clients.put(channel.getRemoteAddress(), "User " + channel.getRemoteAddress()); //Регистрируем подключенного пользователя
        channel.register(selector, SelectionKey.OP_READ);
        channel.write(ByteBuffer.wrap("Hello user!\r\n".getBytes(UTF_8)));
        channel.write(ByteBuffer.wrap("Enter --help for support info\r\n".getBytes(UTF_8)));
    }

    private void handleRead(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        SocketAddress client = channel.getRemoteAddress();
        int readBytes = channel.read(buffer);

        if (readBytes < 0) {
            channel.close();
            return;
        } else  if (readBytes == 0) {
            return;
        }

        buffer.flip();
        StringBuilder sb = new StringBuilder();
        while (buffer.hasRemaining()) {
            sb.append((char) buffer.get());
        }
        buffer.clear();

        if (key.isValid()) {
            String command = sb.toString()
                    .replace("\n", "")
                    .replace("\r", "");

            List<String> response = handleCommand(command, channel);
            if (response.size() > 0) {
                for (String msg : response) {
                    sendMessage(msg, selector, client);
                }
            }

            sendMessage(getUserWelcome(client), selector, client ); //Приглашение пользователю для ввода новой команды
        }
    }

    /**
     * Функция проверяет, что пользователь ввёл правильное количество параметров для команды
     * */
    private boolean checkCommandParams(String[] cmd) {
        if ("ls".equals(cmd[0]) || "--help".equals(cmd[0]) || "Y".equals(cmd[0]) || "N".equals(cmd[0])) { //команда без параметров
            return cmd.length == 1;

        } else if ("copy".equals(cmd[0])) { //в команде должно быть указано два параметра
            return cmd.length == 3;

        } else  { //все остальные команды с одним параметром
            return cmd.length == 2;
        }
    }

    /**
     * Обработчик команд
     * */
    private List<String> handleCommand(String command, SocketChannel channel) {
        String[] cmd = command.split(" ");

        List<String> result = new ArrayList<>();

        if (commands.contains(cmd[0])) { //проверяем, что введена известная серверу команда
            if (!checkCommandParams(cmd)) { //проверяем, что параметры команды указаны корректно
                result.add("unknown parameters\r\n"); //Посылаем сообщение - неправильно указаны параметры
            } else {
                try {
                    switch (cmd[0]) {
                        case "--help":
                            result.add(LS_COMMAND);
                            result.add(MKDIR_COMMAND);
                            result.add(TOUCH_COMMAND);
                            result.add(CD_COMMAND);
                            result.add(RM_COMMAND);
                            result.add(COPY_COMMAND);
                            result.add(CAT_COMMAND);
                            result.add(CHANGENICK_COMMAND);
                            break;

                        case "changenick":
                            clients.put(channel.getRemoteAddress(), cmd[1]);
                            result.add("user nick has been changed\r\n");
                            break;

                        case "ls":
                            result.add(serverLogic.getFilesList());
                            break;

                        case "mkdir":
                            result.add(serverLogic.createDirectory(cmd[1]));
                            break;

                        case "touch":
                            result.add(serverLogic.createFile(cmd[1]));
                            break;

                        case "cd":
                            serverLogic.changeDirectory(cmd[1]);
                            break;

                        case "rm":
                            lastCommandWithResponseRequest = RM_COMMAND; //Последняя команда и её параметры
                            lastCommandParam = cmd[1];                   //Сохраняем, так как будет вопрос пользователю на
                            //удаление непустого каталога
                            result.add(serverLogic.removeFileOrDirectory(cmd[1]));
                            break;

                        case "N": //Ответ пользователя "N", всё обнуляем, ничего не делаем
                            if (lastCommandWithResponseRequest != null) {
                                lastCommandWithResponseRequest = null;
                                lastCommandParam = null;
                            }
                            break;

                        case "Y": //Ответ пользователя "Y". Пока обрабатываем только удаление непустого каталога
                            if (lastCommandWithResponseRequest != null && lastCommandWithResponseRequest.equals(RM_COMMAND)) {
                                serverLogic.deleteNotEmptyDirectory(lastCommandParam);
                                lastCommandWithResponseRequest = null;
                                lastCommandParam = null;
                                result.add("deleted\r\n");
                            }
                            break;

                        case "copy":
                            serverLogic.copy(cmd[1], cmd[2]);
                            result.add("copied\r\n");
                            break;

                        case "cat":
                            result.addAll(serverLogic.viewTextFile(cmd[1]));
                            result.add("\r\n");
                            break;

                    }
/*
                } catch (DirectoryNotEmptyException e) {
                    result.add("directory is not empty, delete anyway? Y/N\r\n"); //Посылаем пользователю вопрос
*/
                } catch (Exception e) {  //Могут быть исключения FileAlreadyExistsException, NoSuchFileException,
                    // IllegalArgumentException
                    //но обработка одна - отправляем пользователю сообщение
                    result.add(e.getMessage());
                }
            }
        } else {
            result.add("unknown command\r\n"); //Посылаем сообщение - неизвестная команда
        }

        return result;
    }

    /**
     * Приглашение пользователю для ввода команды
     * */
    private String getUserWelcome(SocketAddress client) {
        return "\r\n" + clients.get(client) + " " + serverLogic.getUserPath() + " : ";
    }

    private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.channel() instanceof SocketChannel) {
                if (((SocketChannel) key.channel()).getRemoteAddress().equals(client)) {
                    ((SocketChannel) key.channel()).write(ByteBuffer.wrap(message.getBytes(UTF_8)));
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new NioTelnetServer();
    }
}