import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    public static void main(String[] args) throws IOException {
        ServerSocket socket = new ServerSocket(8083);
        System.out.println("Waiting for client......");
        while (true) {
            Socket clientSocket = socket.accept();
            System.out.println("Client connected.");
            ServerHandler serverHandler = new ServerHandler(clientSocket);
            Thread t = new Thread(serverHandler);
            t.start();
        }
    }
}
