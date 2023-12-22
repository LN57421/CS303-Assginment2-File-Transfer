import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

public class DownloadTask implements Runnable{

    public Socket socket;
    public DataInputStream in;

    public DataOutputStream out;

    public String filePath;//
    public long progress = 0;

    public volatile Status status = Status.Transfer;

    public DownloadTask(String filePath) {
        this.filePath = filePath;
    }


    @Override
    public void run() {
        try {
            socket = new Socket("127.0.0.1", 8083);

            in = new DataInputStream(socket.getInputStream());

            out = new DataOutputStream(socket.getOutputStream());

            out.writeUTF("Download");
            out.flush();

            // 1. 发送文件相对路径
            out.writeUTF(filePath); // 例如："\35085e43596735e2819ca3888b8db64e\2a16720e3611b014f3df55df135e1e83"
            out.flush();

            // 2. 接收文件length
            long length;
            length = in.readLong();

            // 拼接得到本地绝对路径
            filePath = "Client\\Download" + filePath;

            File file = new File(filePath);
            File directory = file.getParentFile();

            // 如果目录不存在，则创建它
            if (!directory.exists()) {
                if (directory.mkdirs()) System.out.println("create");
            }

            // 3.接收文件
            long count = 0;

            // 输出文件流
            FileOutputStream fileOutputStream = new FileOutputStream(filePath);

            byte[] buffer = new byte[1024];
            int bytesRead;

            // 从输入流读取内容并写入文件
            while (count != length) {

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
                    // 删除掉本地的绝对路径对应文件
                    fileOutputStream.close();
                    Files.delete(Path.of("C:\\Users\\nian5\\Desktop\\JAVA2\\Final\\"  + filePath));
                    return;
                }


                bytesRead = in.read(buffer);
                if (bytesRead == -1){
                    System.out.println("出现了读入-1");
                }
                fileOutputStream.write(buffer, 0, bytesRead);
                fileOutputStream.flush();

                count += bytesRead;
                this.progress += bytesRead;
            }

            // 设置状态为完成
            this.status = Status.Finish;
            System.out.println("File received: " + filePath);

            fileOutputStream.flush();
            fileOutputStream.close();

            //4. 发送接收完毕的消息
            out.writeUTF("__finish__");
            out.flush();


        } catch (IOException | InterruptedException e) {
            System.out.println(e.getMessage());
        }


    }
}
