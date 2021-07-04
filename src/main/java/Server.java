import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    // TODO: 14.06.2021
    // организовать корректный вывод статуса
    // подумать почему так реализован цикл в ClientHandler

    /* Необходимо определить сколько раз должен выполняться цикл. А именно, сколько раз объём информации в байтах количеством
     *  size поместится в буфере размером length, причём в последнюю итерацию буфер будет заполнен не полностью. Данная формула
     *  вычисляет необходимое количество итераций.
     * */

    public Server() {
        ExecutorService service = Executors.newFixedThreadPool(4);
        try (ServerSocket server = new ServerSocket(5678)) {
            System.out.println("Server started");
            while (true) {
                service.execute(new ClientHandler(server.accept()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new Server();
    }
}