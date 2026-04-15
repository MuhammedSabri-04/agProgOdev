import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConsoleServer {

    private static ServerSocket serverSocket;
    private static Map<String, PrintWriter> connectedClients = new ConcurrentHashMap<>();

    // Hem ChatHandler hem FileServer kullanabilsin diye static
    private static void broadcast(String mesaj) {
        for (PrintWriter clientOut : connectedClients.values()) {
            clientOut.println(mesaj);
        }
    }

    private static void broadcastUserList() {
        String liste = String.join(",", connectedClients.keySet());
        broadcast("USERLIST|" + liste);
    }

    public static void main(String[] args) {

        new File("uploads").mkdirs(); // dosya klasoru

        new FileServer().start(); // port 12346 dosya sunucusu

        try {
            serverSocket = new ServerSocket(12345);
            System.out.println("Chat  sunucusu baslatildi  (port 12345)");
            System.out.println("Dosya sunucusu baslatildi  (port 12346)");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Yeni baglanti: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket).start();
            }

        } catch (IOException e) {
            System.out.println("Baglanti saglanamadi: " + e.getMessage());
            System.exit(1);
        } finally {
            try { serverSocket.close(); } catch (IOException e) {}
        }
    }

    // ==================== CHAT HANDLER ====================
    private static class ClientHandler extends Thread {

        private Socket clientSocket;
        PrintWriter out;
        BufferedReader in;
        private String userName;

        public ClientHandler(Socket socket) {
            try {
                clientSocket = socket;
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            } catch (IOException e) {
                System.out.println("Hata: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                while (true) {
                    String komut = in.readLine();
                    if (komut == null) break;

                    if (komut.startsWith("LOGIN|")) {
                        userName = komut.substring(6);
                        connectedClients.put(userName, out);
                        System.out.println(userName + " sisteme baglandi");
                        broadcast("SYS|" + userName + " sohbete katildi");
                        broadcastUserList();

                    } else if (komut.startsWith("MSG|")) {
                        String mesaj = komut.substring(4);
                        if (mesaj.startsWith("@")) {
                            int spaceIndex = mesaj.indexOf(" ");
                            if (spaceIndex != -1) {
                                String hedef = mesaj.substring(1, spaceIndex);
                                String ozelMesaj = mesaj.substring(spaceIndex + 1);
                                ozelMesajGonder(hedef, ozelMesaj);
                            } else {
                                out.println("Hatali ozel mesaj formati");
                            }
                        } else {
                            String zaman = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"));
                            broadcast("[" + zaman + "] " + userName + ": " + mesaj);
                        }

                    } else if (komut.startsWith("LOGOUT|")) {
                        break; // finally blogu temizligi yapacak
                    }
                }

            } catch (IOException e) {
                System.out.println(userName + " baglantisi koptu: " + e.getMessage());
            } finally {
                if (userName != null) {
                    connectedClients.remove(userName);
                    broadcast("SYS|" + userName + " sistemden ayrildi");
                    broadcastUserList();
                }
                try { clientSocket.close(); } catch (IOException e) {}
            }
        }

        private void ozelMesajGonder(String hedef, String mesaj) {
            PrintWriter targetOut = connectedClients.get(hedef);
            if (targetOut != null) {
                targetOut.println("[OZEL] " + userName + " -> " + mesaj);
                out.println("[OZEL] Siz -> " + hedef + ": " + mesaj);
            } else {
                out.println(hedef + " isimli kullanici bulunamadi");
            }
        }
    }

    // ==================== DOSYA SUNUCUSU (port 12346) ====================
    private static class FileServer extends Thread {

        @Override
        public void run() {
            try (ServerSocket fs = new ServerSocket(12346)) {
                while (true) {
                    Socket s = fs.accept();
                    new Thread(() -> handle(s)).start();
                }
            } catch (IOException e) {
                System.out.println("Dosya sunucusu hatasi: " + e.getMessage());
            }
        }

        private void handle(Socket s) {
            try (DataInputStream dis = new DataInputStream(s.getInputStream());
                 DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {

                String komut = dis.readUTF(); // "UPLOAD|username|filename" veya "DOWNLOAD|filename"

                if (komut.startsWith("UPLOAD|")) {
                    // --- YUKLEME ---
                    String[] p = komut.split("\\|", 3); // ["UPLOAD", "Ali", "ornek.txt"]
                    String sender = p[1];
                    String filename = p[2];
                    long fileSize = dis.readLong();

                    try (FileOutputStream fos = new FileOutputStream("uploads/" + filename)) {
                        byte[] buf = new byte[4096];
                        long rem = fileSize;
                        while (rem > 0) {
                            int r = dis.read(buf, 0, (int) Math.min(buf.length, rem));
                            if (r == -1) break;
                            fos.write(buf, 0, r);
                            rem -= r;
                        }
                    }
                    dos.writeUTF("OK");
                    dos.flush();
                    broadcast("FILENOTIFY|" + sender + "|" + filename); // herkese bildir
                    System.out.println(sender + " dosya yukledi: " + filename);

                } else if (komut.startsWith("DOWNLOAD|")) {
                    // --- INDIRME ---
                    String filename = komut.substring(9);
                    File file = new File("uploads/" + filename);

                    if (!file.exists()) {
                        dos.writeUTF("ERROR|Dosya bulunamadi: " + filename);
                    } else {
                        dos.writeUTF("OK|" + file.length());
                        try (FileInputStream fis = new FileInputStream(file)) {
                            byte[] buf = new byte[4096];
                            int r;
                            while ((r = fis.read(buf)) != -1) {
                                dos.write(buf, 0, r);
                            }
                        }
                    }
                    dos.flush();
                }

            } catch (IOException e) {
                System.out.println("Dosya aktarim hatasi: " + e.getMessage());
            }
        }
    }
}
