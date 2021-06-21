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

    private void getFile(String s) throws IOException {
        // TODO: 14.06.2021
        File file = new File("server" + File.separator + s);
        if (!file.exists()) {
            throw new FileNotFoundException();
        }
        long fileLength = file.length();
        FileInputStream fis = new FileInputStream(file);
        dos.writeUTF("download");
        dos.writeUTF(s);
        dos.writeLong(fileLength);

        int read = 0;
        byte[] buffer = new byte[8 * 1024];
        while ((read = fis.read(buffer)) != -1){
            dis.read(buffer,0,read);
        }


        dos.flush();
        String status = dis.readUTF();
        System.out.println("Sending status: " + status);

    }

    private void sendFile(String filename) {
        try {
            File file = new File("client" + File.separator + filename);
            if (!file.exists()) {
                throw  new FileNotFoundException();
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

            dos.flush();

            String status = dis.readUTF();
            System.out.println("sending status: " + status);

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