import java.io.IOException;
import java.net.Socket;

public class TCPClient {

    private static Socket socket;
    public static void main(String[] args) {
        
        try{

            socket=new Socket("localhost",12345);

        }catch(IOException e){

            System.out.println(e.getMessage());
        } 

    }

}
