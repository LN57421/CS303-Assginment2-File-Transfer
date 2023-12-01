import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Server {
    static volatile ArrayList<String> delete_folders = new ArrayList<>();
    static volatile ArrayList<String> delete_files = new ArrayList<>();

    public static ServerSocket serverSocket;

    static {
        try {
            serverSocket = new ServerSocket(8083);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        // 1.创建一个服务器对象
        // 2.监听服务器，看是否有客户端连接，这里使用while死循环，一直开启服务器端监听
        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("Client Connected");
            ClientHandler clientHandler = new ClientHandler(socket);
            Thread thread = new Thread(clientHandler);
            thread.start();
        }
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;

    String receiveFolder = "Server/Storage";
    static String resourceFolder = "Server/Resources";

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }


    @Override
    public void run() {
        //虽然已经声明抛出了异常，但是因为run()方法没有抛异常，这里重写之后也不能抛异常，只能捕获异常
        try {
            // 获取网络流inputStream对象
            InputStream inputStream = clientSocket.getInputStream();
            OutputStream outputStream = clientSocket.getOutputStream();

            // 读取命令标识
            int commandLength = inputStream.read();
            byte[] commandBytes = new byte[commandLength];
            inputStream.read(commandBytes);
            String command = new String(commandBytes, StandardCharsets.UTF_8);

            // 根据命令标识处理上传或下载
            switch (command) {
                case "__UPLOAD__":
                    handleUpload(clientSocket, inputStream, receiveFolder);
                    for (int i = 0; i < Server.delete_files.size(); i++) {
                        File delete = new File(Server.delete_files.get(i));
                        delete.delete();
                    }
                    break;
                case "__LIST_RESOURCES__":
                    handleListResources(clientSocket, resourceFolder);
                    break;
                case "__DOWNLOAD__":
                    handleDownload(clientSocket, inputStream, outputStream, resourceFolder);
                    break;
                case "__CREATE_FOLDER__":
                    handleCreateFolder(clientSocket, inputStream, receiveFolder);
                    break;
                case "__GET_FOLDER_STRUCTURE__":
                    handleGetFolderStructure(clientSocket, resourceFolder, inputStream);
                    break;
                case "__GET_FILE_SET__":
                    handleGetFileStructure(clientSocket, resourceFolder, inputStream);
                    break;
                default:
                    System.out.println("Unknown command: " + command);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            // 关闭连接放到finally块中，确保连接总是被关闭
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * Client发送获取文件list的请求   Server端需要回传文件的结构
     */

    private static void handleGetFileStructure(Socket socket, String resourceFolder, InputStream inputStream) {
        try {
            // Read the command identifier
            int fileNameToDownloadLength = inputStream.read();
            byte[] fileNameToDownloadBytes = new byte[fileNameToDownloadLength];
            inputStream.read(fileNameToDownloadBytes);
            String fileName = new String(fileNameToDownloadBytes, StandardCharsets.UTF_8);

            // Retrieve file paths
            Set<String> fileSet = getFileStructure(resourceFolder + File.separator + fileName, fileName);

            // Send file paths to the client
            OutputStream outputStream = socket.getOutputStream();
            for (String filePath : fileSet) {
                outputStream.write(filePath.getBytes(StandardCharsets.UTF_8));
                outputStream.write('\n');
            }

            // Close the output stream
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Set<String> getFileStructure(String resourceFolder, String originFileName) {
        Set<String> fileSet = new HashSet<>();
        File resourceDir = new File(resourceFolder);
        File[] files = resourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    fileSet.add(originFileName + File.separator + file.getName());
                } else if (file.isDirectory()) {
                    getFileStructure(file.getAbsolutePath(), originFileName); // Recursively explore directories
                }
            }
        }
        return fileSet;
    }



    // 检查文件夹是否为空
    private static boolean isFolderEmpty(Path path) throws IOException {
        try (var stream = Files.newDirectoryStream(path)) {
            return !stream.iterator().hasNext();
        }
    }

    /**
     *
     * Client发送获取文件结构的请求   Server端需要回传文件的结构
     */
    private static void handleGetFolderStructure(Socket socket, String resourceFolder, InputStream inputStream) {
        try {
            // 读取命令标识
            int fileNameToDownloadLength = inputStream.read();
            byte[] fileNameToDownloadBytes = new byte[fileNameToDownloadLength];
            inputStream.read(fileNameToDownloadBytes);
            String fileName = new String(fileNameToDownloadBytes, StandardCharsets.UTF_8);

            // 读取文件夹结构
            Set<String> folderSet = getFolderStructure(resourceFolder + File.separator + fileName, fileName);

            // 发送文件夹结构给客户端
            OutputStream outputStream = socket.getOutputStream();
            for (String folderPath : folderSet) {
                outputStream.write(folderPath.getBytes(StandardCharsets.UTF_8));
                outputStream.write('\n');
            }

            // 关闭输出流
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Set<String> getFolderStructure(String resourceFolder, String originFileName) {
        Set<String> folderSet = new HashSet<>();
        File resourceDir = new File(resourceFolder);
        File[] files = resourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    folderSet.add(originFileName + File.separator + file.getName());
                    getSubfolderStructure(file, file.getName(), folderSet, originFileName);
                }
            }
        }
        return folderSet;
    }

    private static void getSubfolderStructure(File directory, String parentPath, Set<String> folderSet, String originFileName) {
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                String folderPath = originFileName + File.separator + parentPath + File.separator + file.getName();
                folderSet.add(folderPath);
                getSubfolderStructure(file, folderPath, folderSet, originFileName);
            }
        }
    }



    /**
     *
     * Client将要发送文件 所以进行Server文件的创建 之后对方再发来文件
     */
    private static void handleCreateFolder(Socket socket, InputStream inputStream, String receiveFolder) {
        try {
            int folderPathLength = inputStream.read();
            byte[] folderPathBytes = new byte[folderPathLength];
            inputStream.read(folderPathBytes);
            String folderPath = new String(folderPathBytes, StandardCharsets.UTF_8);

            // 创建文件夹
            createFolderIfNotExists(receiveFolder + File.separator + folderPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 原子性地创建文件夹
    private static void createFolderIfNotExists(String folderPath) throws IOException {
        File folder = new File(folderPath);
        if (!folder.exists()) {
            try {
                Files.createDirectories(Paths.get(folderPath));
            } catch (FileAlreadyExistsException ignored) {
                // The folder may be created by another thread just before this one
            }
        }
    }


    /**
     *  Client端发来下载文件的请求
     *
     */
    private static void handleDownload(Socket socket, InputStream inputStream, OutputStream outputStream, String receiveFolder) {
        try {
            // 4.读取文件名长度
            int fileNameLength = inputStream.read();
            byte[] fileNameBytes = new byte[fileNameLength];

            // 5.读取文件名内容
            inputStream.read(fileNameBytes);

            String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
            System.out.println(fileName);


            File file = new File( resourceFolder + File.separator + fileName);
            FileInputStream fileInputStream = new FileInputStream(file);

            // 发送文件内容的长度
            long fileLength = file.length();
            outputStream.write((int) (fileLength & 0xFF));
            outputStream.write((int) ((fileLength >> 8) & 0xFF));
            outputStream.write((int) ((fileLength >> 16) & 0xFF));
            outputStream.write((int) ((fileLength >> 24) & 0xFF));

            System.out.println("fileLength:"  +  fileLength);

            //使用outputStream的write方法读取本地文件
            byte[] bytes = new byte[1048576];
            int len = 0;

            ALL:while (true) {
                // 读取读取命令长度
                int commandLength = inputStream.read();
                if (commandLength == -1){
                    break;
                }
                byte[] commandLengthBytes = new byte[commandLength];

                // 读取命令内容
                inputStream.read(commandLengthBytes);
                String command = new String(commandLengthBytes, StandardCharsets.UTF_8);

                System.out.println("Command: " + command);

                if (command.equals("__FINISH__")) {
                    break;
                }else if (command.equals("__TRAN__")){
                    len = fileInputStream.read(bytes);
                    if (len == -1){
                        continue;
                    }
                    outputStream.write(bytes, 0, len);
                }else if (command.equals("__STOP__")){
                    while (true){
                        // 读取读取命令长度
                        int commandLength2 = inputStream.read();
                        byte[] commandLengthBytes2 = new byte[commandLength2];
                        // 读取命令内容
                        inputStream.read(commandLengthBytes2);
                        String command2 = new String(commandLengthBytes2, StandardCharsets.UTF_8);
                        if (command2.equals("__RESU__")){
                            break;
                        }else if (command2.equals("__DELE__")){
                            System.out.println("Client delete");
                            break ALL;
                        }
                    }
                }else if (command.equals("__DELE__")){
                    System.out.println("Client delete");
                    break;
                }
            }

            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     *  Client端发来展示Resource下面所有文件和文件夹名字的请求
     *
     */

    public static void handleListResources(Socket socket, String resourceFolder){
        try {
            File resourceDir = new File(resourceFolder);
            File[] files = resourceDir.listFiles();
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

                // 获取socket的输出流，发送资源列表给客户端
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(resourceList.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     *  Client端发来上传的请求（此时只上传文件）
     *
     */

    public static void handleUpload(Socket socket, InputStream inputStream, String receiveFolder) {
        try {
            FileOutputStream fileOutputStream = null;

            // 4.读取文件名长度
            int fileNameLength = inputStream.read();
            byte[] fileNameBytes = new byte[fileNameLength];

            // 5.读取文件名内容
            inputStream.read(fileNameBytes);

            String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);

            String[] name = fileName.split("\\\\");
            StringBuilder path_ = new StringBuilder();
            if (name.length == 1){
                path_.append("");
            } else {
                for (int i = 0; i < name.length - 1; i++) {
                    path_.append(name[i]);
                    if (i != name.length - 2){
                        path_.append("\\");
                    }
                }
            }
            String path = new String(path_);


            File folder = new File(receiveFolder + File.separator + path);
            File file = new File(folder + File.separator + name[name.length - 1]);
            fileOutputStream = new FileOutputStream(folder + File.separator + name[name.length - 1]);


            // 读取文件内容的长度
            long fileLength = (inputStream.read() & 0xFF) |
                    ((inputStream.read() & 0xFF) << 8) |
                    ((inputStream.read() & 0xFF) << 16) |
                    ((long) (inputStream.read() & 0xFF) << 24);

            // 7.将上传的文件内容读出来
            int len = 0;
            byte[] bytes = new byte[1048576];

            // 这里空文件也可以传输了

            ALL:while (true) {
                // 读取读取命令长度
                int commandLength = inputStream.read();
                if (commandLength == -1){
                    break;
                }
                byte[] commandLengthBytes = new byte[commandLength];

                // 读取命令内容
                inputStream.read(commandLengthBytes);
                String command = new String(commandLengthBytes, StandardCharsets.UTF_8);

                if (command.equals("__TRAN__")){
                    if (fileLength > 0 && (len = inputStream.read(bytes, 0, (int) Math.min(bytes.length, fileLength))) != -1){
                        // 8.将读出来的数据再写到服务器本地磁盘中
                        fileOutputStream.write(bytes, 0, len);
                        fileLength -= len;
                    }else{
                        break;
                    }
                }else if (command.equals("__STOP__")){
                    while (true){
                        // 读取读取命令长度
                        int commandLength2 = inputStream.read();
                        byte[] commandLengthBytes2 = new byte[commandLength2];
                        // 读取命令内容
                        inputStream.read(commandLengthBytes2);
                        String command2 = new String(commandLengthBytes2, StandardCharsets.UTF_8);

                        if (command2.equals("__RESU__")){
                            break;
                        }else if (command2.equals("__DELE__")){
                            // 删除服务器上已经上传的文件
                            System.out.println("Server delete");
                            Server.delete_files.add(folder.toString() + File.separator + name[name.length - 1]);
                            Server.delete_folders.add(receiveFolder + File.separator + path);
                            break ALL;
                        }
                    }
                }else if (command.equals("__DELE__")){
                    //删除服务器上已经上传的文件
                    System.out.println("Server delete");
                    Server.delete_files.add(folder.toString() + File.separator + name[name.length - 1]);
                    Server.delete_folders.add(receiveFolder + File.separator + path);
                    break;
                }
            }

            // 10.释放资源
            fileOutputStream.close();

        }catch(Exception e){
            System.out.println("error: " + e);
        }
    }
}
