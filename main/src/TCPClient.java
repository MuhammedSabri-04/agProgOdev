import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import javax.swing.*;
import java.awt.*;

public class TCPClient {

    private static Socket socket;
    private static String serverHost = "localhost"; // sunucu IP adresi

    public static void main(String[] args) {

        // ── Giriş diyaloğu
        JTextField ipField = new JTextField("localhost", 12); // sunucu adresi
        JTextField portField = new JTextField("12345", 6); // port
        JTextField nameField = new JTextField(12); // kullanici adi

        JPanel loginPanel = new JPanel(new GridLayout(0, 2, 6, 6));
        loginPanel.add(new JLabel("Sunucu IP / Adresi:"));
        loginPanel.add(ipField);
        loginPanel.add(new JLabel("Port numarasi:"));
        loginPanel.add(portField);
        loginPanel.add(new JLabel("Kullanici adi:"));
        loginPanel.add(nameField);

        int res = JOptionPane.showConfirmDialog(null, loginPanel,
                "Sunucuya Baglan", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION)
            System.exit(0);

        serverHost = ipField.getText().trim(); // IP adresini kaydet

        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, "Gecersiz port!");
            return;
        }
        String userName = nameField.getText().trim();
        if (userName.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Kullanici adi bos!");
            return;
        }

        // ── Sohbet penceresi
        JFrame frame = new JFrame("TCP Istemcisi — " + userName);
        // DO_NOTHING_ON_CLOSE → X'e basınca biz kontrol edeceğiz (LOGOUT göndereceğiz)
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(640, 460);
        frame.setLayout(new BorderLayout(5, 5));

        JTextArea chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Sohbet"));
        frame.add(scroll, BorderLayout.CENTER);

        JTextField msgField = new JTextField();
        JButton sendBtn = new JButton("Gonder");
        JButton fileBtn = new JButton("📎 Dosya Gonder");
        JButton getFileBtn = new JButton("⬇ Dosya Indir");
        JButton exitBtn = new JButton("🚪 Cikis");

        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        inputRow.add(new JLabel(" Mesaj: "), BorderLayout.WEST);
        inputRow.add(msgField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);

        JPanel btnRow = new JPanel(new GridLayout(1, 3, 4, 0));
        btnRow.add(fileBtn);
        btnRow.add(getFileBtn);
        btnRow.add(exitBtn);

        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.add(inputRow, BorderLayout.CENTER);
        bottom.add(btnRow, BorderLayout.SOUTH);
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        frame.add(bottom, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // ── System.out → chat alanına yönlendir
        PrintStream ps = new PrintStream(new OutputStream() {
            public void write(int b) {
                write(new byte[] { (byte) b }, 0, 1);
            }

            public void write(byte[] b, int off, int len) {
                String text = new String(b, off, len);
                SwingUtilities.invokeLater(() -> {
                    chatArea.append(text);
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                });
            }
        }, true);
        System.setOut(ps);

        // ── Bağlanma ve dinleyici thread
        try {
            socket = new Socket(serverHost, port); // artık IP adresi kullanılıyor
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // ── Pencere X ile kapatılınca LOGOUT gönder
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    out.println("LOGOUT|" + userName); // sunucuya bildir
                    try {
                        socket.close();
                    } catch (IOException ex) {
                    }
                    System.exit(0);
                }
            });

            out.println("LOGIN|" + userName);
            System.out.println("Baglandi! Hosgeldin " + userName);
            System.out.println("Ozel mesaj: @kullaniciAdi mesaj");

            // Dinleyici thread (orijinal kodla aynı)
            Thread listenerThread = new Thread(() -> {
                try {
                    while (true) {
                        String serverResponse = in.readLine();
                        if (serverResponse == null) {
                            System.out.println("Sunucu baglantisi koptu.");
                            System.exit(0);
                        }
                        if (serverResponse.startsWith("SYS|")) {
                            System.out.println(">> Sistem: " + serverResponse.substring(4));
                        } else if (serverResponse.startsWith("USERLIST|")) {
                            String[] kullanicilar = serverResponse.substring(9).split(",");
                            System.out.println("--- Cevrimici (" + kullanicilar.length + ") ---");
                            for (String k : kullanicilar)
                                System.out.println("  * " + k);
                            System.out.println("-----------------------------");
                        } else if (serverResponse.startsWith("FILENOTIFY|")) {
                            String[] p = serverResponse.split("\\|");
                            System.out.println(">> [DOSYA] " + p[1] + " paylasti: " + p[2]);
                        } else {
                            System.out.println(serverResponse);
                        }
                    }
                } catch (IOException ex) {
                    System.out.println("Dinleme hatasi: " + ex.getMessage());
                }
            });
            listenerThread.setDaemon(true);
            listenerThread.start();

            // ── Buton işlemleri
            Runnable sendMsg = () -> {
                String txt = msgField.getText().trim();
                if (!txt.isEmpty()) {
                    out.println("MSG|" + txt);
                    msgField.setText("");
                }
            };
            sendBtn.addActionListener(e -> sendMsg.run());
            msgField.addActionListener(e -> sendMsg.run());

            fileBtn.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    String yol = chooser.getSelectedFile().getAbsolutePath();
                    new Thread(() -> sendFile(yol, userName)).start();
                }
            });

            getFileBtn.addActionListener(e -> {
                String ad = JOptionPane.showInputDialog(frame, "Dosya adi:", "Dosya Indir", JOptionPane.PLAIN_MESSAGE);
                if (ad != null && !ad.trim().isEmpty())
                    new Thread(() -> downloadFile(ad.trim(), userName)).start();
            });

            exitBtn.addActionListener(e -> {
                out.println("LOGOUT|" + userName);
                try {
                    socket.close();
                } catch (IOException ex) {
                }
                System.exit(0);
            });

        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Baglanti hatasi: " + e.getMessage());
        }
    }

    // ---- DOSYA GONDER (port 12346) ----
    private static void sendFile(String dosyaAdi, String userName) {
        File file = new File(dosyaAdi);
        if (!file.exists()) {
            System.out.println("Dosya bulunamadi: " + dosyaAdi);
            System.out.println("Gondermek istediginiz dosyayi su klasore koyun: " + new File(".").getAbsolutePath());
            return;
        }
        try (Socket fs = new Socket(serverHost, 12346);
                DataOutputStream dos = new DataOutputStream(fs.getOutputStream());
                DataInputStream dis = new DataInputStream(fs.getInputStream())) {

            dos.writeUTF("UPLOAD|" + userName + "|" + file.getName());
            dos.writeLong(file.length());

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[4096];
                int r;
                while ((r = fis.read(buf)) != -1) {
                    dos.write(buf, 0, r);
                }
            }
            dos.flush();

            String sonuc = dis.readUTF();
            if (sonuc.equals("OK")) {
                System.out.println("Dosya gonderildi: " + file.getName());
            }
        } catch (IOException e) {
            System.out.println("Dosya gonderme hatasi: " + e.getMessage());
        }
    }

    // ---- DOSYA İNDİR (port 12346) ----
    private static void downloadFile(String dosyaAdi, String userName) {
        String klasor = "downloads/" + userName;
        new File(klasor).mkdirs();
        try (Socket fs = new Socket(serverHost, 12346);
                DataOutputStream dos = new DataOutputStream(fs.getOutputStream());
                DataInputStream dis = new DataInputStream(fs.getInputStream())) {

            dos.writeUTF("DOWNLOAD|" + dosyaAdi);
            dos.flush();

            String sonuc = dis.readUTF();
            if (sonuc.startsWith("ERROR|")) {
                System.out.println("Hata: " + sonuc.substring(6));
                return;
            }
            long fileSize = Long.parseLong(sonuc.split("\\|")[1]);

            try (FileOutputStream fos = new FileOutputStream(klasor + "/" + dosyaAdi)) {
                byte[] buf = new byte[4096];
                long rem = fileSize;
                while (rem > 0) {
                    int r = dis.read(buf, 0, (int) Math.min(buf.length, rem));
                    if (r == -1)
                        break;
                    fos.write(buf, 0, r);
                    rem -= r;
                }
            }
            System.out.println("Dosya indirildi: " + klasor + "/" + dosyaAdi);
        } catch (IOException e) {
            System.out.println("Dosya indirme hatasi: " + e.getMessage());
        }
    }
}
