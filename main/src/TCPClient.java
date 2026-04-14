import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class TCPClient {

    private static Socket socket;

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.print("Port numarasi: ");
        int port = Integer.parseInt(scanner.nextLine());

        try {

            socket = new Socket("localhost", port);

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("Sisteme giris icin lutfen kullanici adi giriniz !!!");
            String userName = scanner.nextLine();
            out.println("LOGIN|" + userName);

            System.out.println(
                    "Baglanti kuruldu genel mesaj yazabilirsiniz ozel mesaj icin @<kullaniciAdi> formatini kullanin.");

            /// dinleyici thread gelen veriyi
            Thread listenerThread = new Thread(() -> {
                try {
                    while (true) {
                        String serverResponse = in.readLine();
                        if (serverResponse == null) {
                            System.out.println("Sunucu baglantisi koptu ");
                            System.exit(0);
                        }

                        if (serverResponse.startsWith("SYS|")) {
                            System.out.println(">> Sistem: " + serverResponse.substring(4));
                        } else if (serverResponse.startsWith("USERLIST|")) {
                            String[] kullanicilar = serverResponse.substring(9).split(",");
                            System.out.println("--- Cevrimici Kullanicilar (" + kullanicilar.length + ") ---");
                            for (String k : kullanicilar) System.out.println("  * " + k);
                            System.out.println("--------------------------------");
                        } else {
                            System.out.println(serverResponse);
                        }
                    }

                } catch (IOException ex) {
                    System.out.println("Dinleme hatasi: " + ex.getMessage());
                }
            });

            listenerThread.start();

            /// GONDERICI KISMI
            while (true) {
                String mesaj = scanner.nextLine();
                if (mesaj.equals("EXIT")) {
                    out.println("LOGOUT|" + userName);
                    socket.close();
                    break;
                }
                out.println("MSG|" + mesaj);
            }

        } catch (IOException e) {

            System.out.println(e.getMessage());
        }

    }

}
