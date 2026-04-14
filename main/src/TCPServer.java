import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer {

    private static ServerSocket serverSocket;
    public static void main(String[] args) {
        
        try{

            serverSocket=new ServerSocket(12345);
            System.out.println("Socket olustu baglantı bekleniyor!!!");


            while(true){

                Socket clientSocket=serverSocket.accept();
                System.out.println(clientSocket.toString()+" baglandi");

                ClientHandler clientHandler=new ClientHandler(clientSocket);
                clientHandler.start();

            }


        }catch(IOException e){
            System.out.println(e.getMessage());
            System.exit(1);
        }finally{

            try{
                serverSocket.close();
            }catch(IOException e){
                System.out.println("Hata: "+e.getMessage());
                System.exit(1);
            }
        }
    }
}
