import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler extends Thread{

    private Socket clientSocket;
    PrintWriter out;
    BufferedReader in;

    public ClientHandler(Socket socket){

        try{

            clientSocket=socket;
            out=new PrintWriter(clientSocket.getOutputStream(),true);
            in=new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        }catch(IOException e){
            System.out.println("Hata: "+e.getMessage());
        }

    }    

        @Override
        public void run(){

            try{
                
                String gelenVeri;

                do{

                    gelenVeri=in.readLine();
                    System.out.println("Gelen veri: "+gelenVeri);

                    out.println(gelenVeri.toUpperCase());

                }while(true);



            }catch(IOException e){
                System.out.println("Hata: "+e.getMessage());
            }

            if(clientSocket!=null){

                try{
                    System.out.println("Bağlanti kapatiliyor!!!");
                    clientSocket.close();
                }catch(IOException e){
                    System.out.println("Bağlanti kapatilamadi: "+e.getMessage());
                }

            }


        }

    }

