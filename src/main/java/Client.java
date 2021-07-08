import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;

public class Client extends JFrame {
    private final Socket socket;
    private final DataOutputStream dos;
    private final DataInputStream dis;

    public Client() throws IOException {
        socket = new Socket("localhost", 5678);
        dos = new DataOutputStream(socket.getOutputStream());
        dis = new DataInputStream(socket.getInputStream());

        setSize(300, 300);
        JPanel panel = new JPanel(new GridLayout(2, 1));

        JButton btnSend = new JButton("SEND");
        JTextField textField = new JTextField();

        btnSend.addActionListener(a -> {
            // upload 1.txt
            // download img.png
            String[] cmd = textField.getText().split(" ");
            if ("upload".equals(cmd[0])) {
                sendFile(cmd[1]);
            } else if ("download".equals(cmd[0])) {
                try {
                    getFile(cmd[1]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        panel.add(textField);
        panel.add(btnSend);

        add(panel);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                sendMessage("exit");
            }
        });
        setVisible(true);
    }

    private void getFile(String filename) throws IOException {
        // TODO: 14.06.2021
        try {
            dos.writeUTF("download");
            dos.writeUTF(filename);

            String status = dis.readUTF();
            if (status.equals("READY_TO_SEND")) {
                File file = new File("client" + File.separator + filename);
                if (!file.exists()) {
                    file.createNewFile();
                }

                FileOutputStream fos = new FileOutputStream(file);

                long size = dis.readLong();
                byte[] buffer = new byte[8 * 1024];

                for (int i = 0; i < (size + (buffer.length - 1)) / (buffer.length); i++) {
                    int read = dis.read(buffer);
                    fos.write(read);
                }
                fos.close();
                System.out.println("File: " + filename + "received");
                dos.writeUTF("OK");
            } else if (status.equals("FILE_NOT_FOUND")) {
                System.out.println("File: " + filename + "not found");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendFile(String filename) {
        try {
            File file = new File("client" + File.separator + filename);
            if (!file.exists()) {
                throw new FileNotFoundException();
            }

            long fileLength = file.length();
            FileInputStream fis = new FileInputStream(file);

            dos.writeUTF("upload");
            dos.writeUTF(filename);
            dos.writeLong(fileLength);

            int read = 0;
            byte[] buffer = new byte[8 * 1024];
            while ((read = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, read);
            }
            fis.close();
            dos.flush();

            String status = dis.readUTF();
            System.out.println("Sending status: " + status);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(String message) {
        try {
            dos.writeUTF(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        new Client();
    }
}