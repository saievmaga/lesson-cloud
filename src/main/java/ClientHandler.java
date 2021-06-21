import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }


    @Override
    public void run() {
        try (
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                DataInputStream dis = new DataInputStream(socket.getInputStream())
        ) {
            System.out.printf("Client %s connected\n", socket.getInetAddress());
            while (true) {
                String command = dis.readUTF();
                if ("upload".equals(command)) {
                    try {
                        File file = new File("server"  + File.separator + dis.readUTF());
                        if (!file.exists()) {
                            file.createNewFile();
                        }
                        FileOutputStream fos = new FileOutputStream(file);

                        long size = dis.readLong();

                        byte[] buffer = new byte[8 * 1024];

                        for (int i = 0; i < (size + (buffer.length - 1)) / (buffer.length); i++) {
                            int read = dis.read(buffer);
                            fos.write(buffer, 0, read);
                        }
                        fos.close();
                        dos.writeUTF("OK");
                    } catch (Exception e) {
                        dos.writeUTF("FATAL ERROR");
                    }
                }

                if ("download".equals(command)) {
                    // TODO: 14.06.2021
                    try {
                        File file = new File("client" + File.separator + dis.readUTF());
                        if (!file.exists()) {
                            file.createNewFile();
                        }

                        FileInputStream fis = new FileInputStream(file);

                        long size = dis.readLong();

                        byte[] buffer = new byte[8 * 1024];
                        for (int i = 0; i < (size + (buffer.length - 1)) / (buffer.length); i++) {
                            int read = dis.read(buffer);
                            fis.read(buffer, 0, read);
                        }
                        fis.close();
                        dos.writeUTF("OK");

                    } catch (Exception e) {
                        dos.writeUTF("ERROR");
                    }
                }
                if ("exit".equals(command)) {
                    System.out.printf("Client %s disconnected correctly\n", socket.getInetAddress());
                    break;
                }

                System.out.println(command);
                dos.writeUTF(command);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}