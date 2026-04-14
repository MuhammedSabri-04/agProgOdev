import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

            System.out.println("--- Komutlar ---");
            System.out.println("  Genel mesaj  : <mesaj>");
            System.out.println("  Ozel mesaj   : @<kullaniciAdi> <mesaj>");
            System.out.println("  Dosya gonder : SEND <dosyaAdi>");
            System.out.println("  Dosya indir  : GETFILE <dosyaAdi>");
            System.out.println("  Cikis        : EXIT");
            System.out.println("----------------");

            // ---- DİNLEYİCİ THREAD ----
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
                            for (String k : kullanicilar) System.out.println("  * " + k);
                            System.out.println("-----------------------------");
                        } else if (serverResponse.startsWith("FILENOTIFY|")) {
                            String[] p = serverResponse.split("\\|");
                            System.out.println(">> [DOSYA] " + p[1] + " paylasti: " + p[2]);
                            System.out.println("   Indirmek icin: GETFILE " + p[2]);
                        } else {
                            System.out.println(serverResponse);
                        }
                    }
                } catch (IOException ex) {
                    System.out.println("Dinleme hatasi: " + ex.getMessage());
                }
            });
            listenerThread.start();

            // ---- GÖNDERİCİ KISIM ----
            while (true) {
                String mesaj = scanner.nextLine();
                if (mesaj.equals("EXIT")) {
                    out.println("LOGOUT|" + userName);
                    socket.close();
                    break;
                } else if (mesaj.startsWith("SEND ")) {
                    String dosyaAdi = mesaj.substring(5).trim();
                    new Thread(() -> sendFile(dosyaAdi, userName)).start(); // chat bloklanmasin
                } else if (mesaj.startsWith("GETFILE ")) {
                    String dosyaAdi = mesaj.substring(8).trim();
                    new Thread(() -> downloadFile(dosyaAdi, userName)).start(); // chat bloklanmasin
                } else {
                    out.println("MSG|" + mesaj);
                }
            }

        } catch (IOException e) {
            System.out.println(e.getMessage());
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
        try (Socket fs = new Socket("localhost", 12346);
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
        try (Socket fs = new Socket("localhost", 12346);
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
                    if (r == -1) break;
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
