import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class ServerHandler implements Runnable{

    public Socket socket;
    public DataInputStream in;
    public DataOutputStream out;
    public String filePath = null;

    public static String downloadPath = "Server\\Resources";

    public ServerHandler(Socket socket) {
        this.socket = socket;
    }

    public void run(){
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            String read = in.readUTF();
            if (read.equals("Upload")) {
                Upload();
            } else if (read.equals("Download")) {
                Download();
            } else if (read.equals("List_resources")){
                ListResource();
            } else if (read.equals("askForPaths")){
                responsePaths();
            }

        }catch (Exception e){
            System.out.println(e.getMessage());
        }

    }
    public void responsePaths() throws IOException{
        // 接收文件相对路径
        String subPath;
        subPath = in.readUTF();

        // 拼接文件储存路径
        filePath = "Server\\Resources\\" + subPath;
        File downloadFile = new File(filePath);

        Set<String> fileSet = new HashSet<>();

        // 回传文件夹下面的所有文件绝对路径
        getFileStructure(downloadFile, fileSet);

        OutputStream outputStream = socket.getOutputStream();
        for (String filePath : fileSet) {
            outputStream.write(filePath.getBytes(StandardCharsets.UTF_8));
            outputStream.write('\n');
        }

        outputStream.close();
    }

    public static void getFileStructure(File folder, Set<String> fileSet) {
        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    // 如果是文件，打印文件路径
                    fileSet.add(file.getAbsolutePath());
                } else if (file.isDirectory()) {
                    // 如果是文件夹，递归调用listFiles方法
                    getFileStructure(file, fileSet);
                }
            }
        }
    }

    public void Download() throws IOException{
        // 1. 接收文件相对路径
        String subPath;
        subPath = in.readUTF();

        // 拼接文件储存路径
        filePath = "Server\\Resources\\" + subPath;
        File file = new File(filePath);

        // 2. 发送文件length
        out.writeLong(file.length());
        out.flush();

        // 3.读入文件的流
        FileInputStream fileInputStream = new FileInputStream(filePath);

        byte[] buffer = new byte[1024];
        int bytesRead;

        String cur_status;
        String next_status;
        // 从文件读取内容并通过输出流发送
        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
            cur_status = in.readUTF();

            if (cur_status.equals("Stop")){
                next_status = in.readUTF();
                while (!next_status.equals("Transfer")){
                    next_status = in.readUTF();
                }
            } else if (cur_status.equals("Cancel")){
                // 这个时候说明对面取消了 直接return
                return;
            }

            out.write(buffer, 0, bytesRead);
            out.flush();
        }

        out.flush();

        // 4.等待对方发来finish
        while (!in.readUTF().equals("__finish__")) {

        }

    }

    public void Upload() throws IOException{
        // 接收文件相对路径
        String subPath;
        subPath = in.readUTF();

        // 接收文件长度
        long length;
        length = in.readLong();

        // 拼接文件储存路径
        //filePath = "Server\\Storage\\" + subPath;

        filePath = "Server\\Resources\\" + subPath;

        File file = new File(filePath);
        File directory = file.getParentFile();

        // 如果目录不存在，则创建它
        if (!directory.exists()) {
            if (directory.mkdirs()) System.out.println("create");
        }

        // 接收文件
        long count = 0;

        // 输出文件流
        FileOutputStream fileOutputStream = new FileOutputStream(filePath);

        byte[] buffer = new byte[1024];
        int bytesRead;

        String cur_status;
        String next_status;
        // 从流读取内容并写入文件
        while (count != length) {
            cur_status = in.readUTF();
            if (cur_status.equals("Stop")){
                next_status = in.readUTF();
                while (!next_status.equals("Transfer")){
                    next_status = in.readUTF();
                }
            } else if (cur_status.equals("Cancel")){
                // 这个时候说明对面取消了
                try {
                    fileOutputStream.close();
                    Files.delete(Path.of(filePath));
                } catch (IOException ignored) {

                }
                return;
            }

            bytesRead = in.read(buffer);
            if (bytesRead == -1) {
                System.out.println("出现了读入-1的错误");
                return;
            }
            fileOutputStream.write(buffer, 0, bytesRead);
            fileOutputStream.flush();
            count += bytesRead;
        }

        fileOutputStream.flush();
        fileOutputStream.close();

        // 已经上传完毕 再给Stop信息
        out.writeUTF("__finish__");
        out.flush();

    }


    public void ListResource() throws IOException{

        File folder = new File(downloadPath);

        File[] files = folder.listFiles();
        if (files != null) {
            // 创建一个StringBuilder来存储资源列表
            StringBuilder resourceList = new StringBuilder();

            for (File file : files) {
                if (file.isDirectory()) {
                    resourceList.append("[Directory] ");
                } else {
                    resourceList.append("[File] ");
                }
                resourceList.append(file.getName()).append("\r\n");
            }

            System.out.println(resourceList.length());

            // 获取socket的输出流，发送资源列表给客户端
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(resourceList.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

}
