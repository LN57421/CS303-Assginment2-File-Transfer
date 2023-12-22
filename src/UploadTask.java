import java.io.*;
import java.net.Socket;

public class UploadTask implements Runnable{

    public Socket socket;
    public DataInputStream in;

    public DataOutputStream out;

    public String filePath;//
    public long progress = 0;

    public volatile Status status = Status.Transfer;

    public UploadTask(String filePath) {
        this.filePath = filePath;
    }


    @Override
    public void run() {
        try {
            socket = new Socket("127.0.0.1", 8083);

            in = new DataInputStream(socket.getInputStream());

            out = new DataOutputStream(socket.getOutputStream());

            out.writeUTF("Upload");
            out.flush();

            // 发送文件相对路径
            String fullPath = filePath; // 例如："/Upload/documents/file.txt"
            String keyword = "Upload";

            int index = fullPath.indexOf(keyword);
            String subPath = fullPath.substring(index + keyword.length());

            out.writeUTF(subPath);
            out.flush();

            // 发送文件长度
            File file = new File(filePath);
            out.writeLong(file.length());
            out.flush();

            // 读入文件的流
            FileInputStream fileInputStream = new FileInputStream(filePath);

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                if (this.status == Status.Transfer){
                    out.writeUTF("Transfer");
                    out.flush();
                } else if (this.status == Status.Stop){
                    out.writeUTF("Stop");
                    out.flush();
                    while (true){
                        if (this.status == Status.Transfer){
                            out.writeUTF("Transfer");
                            out.flush();
                            break;
                        }
                        out.writeUTF("Stop");
                        out.flush();
                        Thread.sleep(1000);
                    }
                } else if (this.status == Status.Cancel){
                    out.writeUTF("Cancel");
                    out.flush();
                    return;
                    // 对方在read的时候也会接收到-1 然后意识到此时任务已经被取消
                }

                out.write(buffer, 0, bytesRead);
                this.progress += bytesRead;
            }
            out.flush();
            fileInputStream.close();

            // 等待服务器传回Stop
            while (!in.readUTF().equals("__finish__")) {

            }

            // 设置状态为完成
            this.status = Status.Finish;
            System.out.println(filePath + " upload successfully");


        } catch (IOException | InterruptedException e) {
            System.out.println(e.getMessage());
        }

    }
}