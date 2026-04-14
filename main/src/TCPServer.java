import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TCPServer {

    private static ServerSocket serverSocket;

    // aktif kullanıcıları tutan bir log defteri ekledik
    private static Map<String, PrintWriter> connectedClients = new ConcurrentHashMap<>();

    public static void main(String[] args) {

        try {

            serverSocket = new ServerSocket(12345);
            System.out.println("Socket olustu baglanti bekleniyor!!!");

            while (true) {

                Socket clientSocket = serverSocket.accept();
                System.out.println("Yeni bağlanti istegi " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clientHandler.start();

            }

        } catch (IOException e) {
            System.out.println("Bağlanti saglanamadi:" + e.getMessage());
            System.exit(1);
        } finally {

            try {
                serverSocket.close();
            } catch (IOException e) {
                System.out.println("Hata: " + e.getMessage());
                System.exit(1);
            }
        }
    }

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
                    if (komut == null)
                        break;

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

                                String hedefKullanici = mesaj.substring(1, spaceIndex);
                                String ozelMesaj = mesaj.substring(spaceIndex + 1);
                                ozelMesajGonder(hedefKullanici, ozelMesaj);

                            } else {
                                // @ ile basladi ama bosluk bırakmadıysa hata mesajı verme yeri
                                out.println("Hatali ozel mesaj formati");

                            }

                        } else {
                            // herkese yolla
                            String zaman = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"));
                            broadcast("[" + zaman + "] " + userName + ": " + mesaj);
                        }

                    } else if (komut.startsWith("LOGOUT|")) {
                        break; // finally blogu zaten temizligi yapacak
                    }

                }

            } catch (IOException e) {
                System.out.println(userName + " username ile baglanti koptu " + e.getMessage());
            } finally {
                if (userName != null) {
                    connectedClients.remove(userName);
                    broadcast("SYS|" + userName + " sistemden ayrildi");
                    broadcastUserList();
                }
                try {
                    clientSocket.close();
                } catch (IOException e) {
                }
            }
        }

        private void broadcast(String mesaj) {
            for (PrintWriter clientOut : connectedClients.values()) {
                clientOut.println(mesaj);
            }
        }

        private void broadcastUserList() {
            String liste = String.join(",", connectedClients.keySet());
            broadcast("USERLIST|" + liste);
        }

        private void ozelMesajGonder(String hedef, String mesaj) {
            PrintWriter targetOut = connectedClients.get(hedef);
            if (targetOut != null) {
                targetOut.println("[OZEL] " + userName + "-> " + mesaj);
                out.println("[OZEL] Siz -> " + hedef + "-> " + mesaj); // gonderene de teyit ettirmek amacli
            } else {
                out.println(hedef + " isimli kullanici bulunamadi: " + hedef);
            }
        }
    }

}
