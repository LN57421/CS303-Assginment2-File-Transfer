import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;


public class Client {
    static String uploadFolder = "Client/Upload";
    static String downloadFolder = "Client/Download";

    static volatile ArrayList<DownloadTask> downloadTasks = new ArrayList<>();

    static volatile ArrayList<UploadTask> uploadTasks = new ArrayList<>();

    static volatile ArrayList<String> delete_folders = new ArrayList<>();
    static volatile ArrayList<String> delete_files = new ArrayList<>();


    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("Please input command:(UPLOAD, DOWNLOAD, LIST_RESOURCES, EXIT)");
            String command = scanner.nextLine();
            switch (command.toUpperCase()) {
                case "UPLOAD":

                    // 获取所有文件夹名
                    Set<String> folderSet = new HashSet<>();
                    File file = new File(uploadFolder);
                    getFolderNamesRequest(file, "", folderSet);

                    // 发送创建文件夹的请求
                    sendCreateFolderRequest(folderSet);

                    ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(10);


                    File directory = new File(uploadFolder);
                    String name = "";

                    uploadRequest(directory, threadPoolExecutor, name);

                    // 进入中场输入
                    try {
                        uploadActions(scanner, threadPoolExecutor);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                case "DOWNLOAD":
                    System.out.println("Enter the file or directory name to download:");
                    String fileNameToDownload = scanner.nextLine();

//                    // 发送获取文件夹结构的请求
//                    Set<String> folderSet_= requestFolderStructure(fileNameToDownload);
//                    File folder = new File(Client.downloadFolder + File.separator + fileNameToDownload);
//                    if (!folder.exists()){
//                        folder.mkdirs();
//                    }
//
//                    // 根据文件夹结构递归创建文件夹
//                    createFolders(folderSet_);

                    // 发送获取文件list的请求
                    Set<String> fileSet = requestFileStructure(fileNameToDownload);

//                    for (String s: fileSet) {
//                        System.out.println(s);
//                    }

                    // 创建线程池，进行下载文件
                    ExecutorService downloadThreadPool = Executors.newFixedThreadPool(10);


                    // 提交任务给线程池
                    downloadRequest(downloadThreadPool, fileSet);

                    // 进入中场输入
                    try {
                        downloadActions(scanner, downloadThreadPool);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    for (int i = 0; i < delete_files.size(); i++) {
                        File delete = new File(delete_files.get(i));
                        delete.delete();
                    }
                    break;
                case "LIST_RESOURCES":
                    resourceListRequest();
                    break;
                case "EXIT":
                    System.out.println("Client exit.");
                    System.exit(0);
                    break;
                default:
                    System.out.println("Nest Loop.");
            }
        }

    }

    // 检查文件夹是否为空
    private static boolean isFolderEmpty(Path path) throws IOException {
        try (var stream = Files.newDirectoryStream(path)) {
            return !stream.iterator().hasNext();
        }
    }

    /**
     *  main方法持续接收的方法
     */
    static void uploadActions(Scanner scanner,  ExecutorService threadPoolExecutor) throws InterruptedException {
        String command;
        StatusMonitor:while(true){
            int cnt = 0;
            for(int i = 0; i < uploadTasks.size();i++){
                if(uploadTasks.get(i).status == Status.Finish|| uploadTasks.get(i).status == Status.Cancel){
                    cnt++;
                }
                if(cnt == uploadTasks.size()){
                    break StatusMonitor;
                }
            }

            System.out.println("检查执行状态请输入check，暂停请输入stop，恢复请输入resume，删除请输入delete.");
            command=scanner.next();
            if(command.equals("check")){
                for (int i = 0; i < uploadTasks.size(); i++) {
                    System.out.println("File Name: " + uploadTasks.get(i).fileName + " Status: " + uploadTasks.get(i).status + " Sent(bytes): " +
                            uploadTasks.get(i).progress);
                }
            }
            if(command.equals("stop")){
                System.out.println("请输入想要暂停传输的文件名，请输入多个相对路径，结束后再输入<<<:");
                ArrayList<String> stop_files = new ArrayList<>();
                while (true){
                    String stop_file =scanner.next();
                    if(stop_file.equals("<<<")){
                        break;
                    }else{
                        stop_files.add(stop_file);
                    }
                }
                for (int i = 0; i < uploadTasks.size(); i++) {
                    for (int j = 0; j < stop_files.size(); j++) {
                        if (uploadTasks.get(i).fileName.equals(stop_files.get(j))){
                            uploadTasks.get(i).status = Status.Stop;
                            System.out.println("File: " + uploadTasks.get(i).fileName + " is stop.") ;
                        }
                    }
                }
            }

            if(command.equals("resume")){
                System.out.println("请输入想要恢复传输的文件，请输入多个相对路径，并在输入结束后输入<<<");
                ArrayList<String> resume_files = new ArrayList<>();
                while (true){
                    String resume_file =scanner.next();
                    if(resume_file.equals("<<<")){
                        break;
                    }else{
                        resume_files.add(resume_file);
                    }
                }
                for (int i = 0; i < uploadTasks.size(); i++) {
                    for (int j = 0; j < resume_files.size(); j++) {
                        if (uploadTasks.get(i).fileName.equals(resume_files.get(j))){
                            uploadTasks.get(i).status = Status.Transfer;
                            System.out.println("File: " + uploadTasks.get(i).fileName + " is resume.") ;
                        }
                    }
                }
            }

            if(command.equals("delete")){
                System.out.println("请输入想要取消传输的文件，请输入多个相对路径，并在输入结束后输入<<<");
                ArrayList<String> delete_files = new ArrayList<>();
                while (true){
                    String delete_file =scanner.next();
                    if(delete_file.equals("<<<")){
                        break;
                    }else{
                        delete_files.add(delete_file);
                    }
                }
                for (int i = 0; i < uploadTasks.size(); i++) {
                    for (int j = 0; j < delete_files.size(); j++) {
                        if (uploadTasks.get(i).fileName.equals(delete_files.get(j))){
                            uploadTasks.get(i).status = Status.Cancel;
                            System.out.println("File: " + uploadTasks.get(i).fileName + " is cancel.") ;
                        }
                    }
                }
            }

            if(command.equals("exit")){
                break;
            }
        }
        // 等待所有任务完成或者某些被取消
        threadPoolExecutor.shutdown();
        try {
            threadPoolExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 关闭线程池
    }


    static void downloadActions(Scanner scanner,ExecutorService threadPoolExecutor) throws InterruptedException {
        String command;
        StatusMonitor:while(true){
            //System.out.println(downloadTasks.size());
            int cnt = 0;
            for(int i = 0; i < downloadTasks.size();i++){
                if(downloadTasks.get(i).status == Status.Finish|| downloadTasks.get(i).status == Status.Cancel){
                    cnt++;
                }
                if(cnt == downloadTasks.size()){
                    break StatusMonitor;
                }
            }

            System.out.println("检查执行状态请输入check，暂停请输入stop，恢复请输入resume，删除请输入delete.");
            command = scanner.next();
            if(command.equals("check")){
                for (int i = 0; i < downloadTasks.size(); i++) {
                    System.out.println("File Name: " + downloadTasks.get(i).fileName + " Status: " + downloadTasks.get(i).status + " Sent(bytes): " +
                            downloadTasks.get(i).progress);
                }
            }
            if(command.equals("stop")){
                System.out.println("请输入想要暂停传输的文件名，请输入多个绝对路径，结束后再输入<<<:");
                ArrayList<String> stop_files = new ArrayList<>();
                while (true){
                    String stop_file =scanner.next();
                    if(stop_file.equals("<<<")){
                        break;
                    }else{
                        stop_files.add(stop_file);
                    }
                }
                for (int i = 0; i < downloadTasks.size(); i++) {
                    for (int j = 0; j < stop_files.size(); j++) {
                        if (downloadTasks.get(i).fileName.equals(stop_files.get(j))){
                            downloadTasks.get(i).status = Status.Stop;
                            System.out.println("File: " + downloadTasks.get(i).fileName + " is stop.") ;
                        }
                    }
                }
            }

            if(command.equals("resume")){
                System.out.println("请输入想要恢复传输的文件，请输入多个绝对路径，并在输入结束后输入<<<");
                ArrayList<String> resume_files = new ArrayList<>();
                while (true){
                    String resume_file = scanner.next();
                    if(resume_file.equals("<<<")){
                        break;
                    }else{
                        resume_files.add(resume_file);
                    }
                }
                for (int i = 0; i < downloadTasks.size(); i++) {
                    for (int j = 0; j < resume_files.size(); j++) {
                        if (downloadTasks.get(i).fileName.equals(resume_files.get(j))){
                            downloadTasks.get(i).status = Status.Transfer;
                            System.out.println("File: " + downloadTasks.get(i).fileName + " is resume.") ;
                        }
                    }
                }
            }

            if(command.equals("delete")){
                System.out.println("请输入想要取消传输的文件，请输入多个绝对路径，并在输入结束后输入<<<");
                ArrayList<String> delete_files = new ArrayList<>();
                while (true){
                    String delete_file = scanner.next();
                    if(delete_file.equals("<<<")){
                        break;
                    }else{
                        delete_files.add(delete_file);
                    }
                }
                for (int i = 0; i < downloadTasks.size(); i++) {
                    for (int j = 0; j < delete_files.size(); j++) {
                        if (downloadTasks.get(i).fileName.equals(delete_files.get(j))){
                            downloadTasks.get(i).status = Status.Cancel;
                            System.out.println("File: " + downloadTasks.get(i).fileName + " is cancel.") ;
                        }
                    }
                }
            }
        }
        // 等待所有任务完成或者某些被取消
        threadPoolExecutor.shutdown();
        try {
            threadPoolExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 关闭线程池
    }

    /**
     *
     * Client发送获取文件结构的请求   Server端需要回传文件的结构
     * */
    private static Set<String> requestFolderStructure(String fileNameToDownload) {
        Set<String> folderSet = new HashSet<>();
        try {
            Socket socket = new Socket("127.0.0.1", 8083);
            OutputStream outputStream = socket.getOutputStream();

            // 发送获取文件夹结构的命令
            String command = "__GET_FOLDER_STRUCTURE__";
            byte[] commandBytes = command.getBytes(StandardCharsets.UTF_8);
            outputStream.write(commandBytes.length);
            outputStream.write(commandBytes);

            // 发送要建立的文件夹
            byte[] fileNameToDownloadBytes = fileNameToDownload.getBytes(StandardCharsets.UTF_8);
            outputStream.write(fileNameToDownloadBytes.length);
            outputStream.write(fileNameToDownloadBytes);


            // 读取文件夹结构
            InputStream inputStream = socket.getInputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;

            StringBuilder folderStructure = new StringBuilder();
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                folderStructure.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }

            // 解析文件夹结构
            String[] folders = folderStructure.toString().split("\n");
            folderSet.addAll(Arrays.asList(folders));

            // 关闭流和socket
            inputStream.close();
            outputStream.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return folderSet;
    }


    private static void createFolders(Set<String> folderSet) {
        for (String folderPath : folderSet) {
            File folder = new File(downloadFolder + File.separator + folderPath);
            if (!folder.exists()) {
                folder.mkdirs();
            }
        }
    }


    /**
     *
     * Client发送获取要下载文件list的请求   Server端需要回传文件的结构
     * */
    private static Set<String> requestFileStructure(String fileNameToDownload) {
        Set<String> files = new HashSet<>();
        try {
            Socket socket = new Socket("127.0.0.1", 8083);
            OutputStream outputStream = socket.getOutputStream();

            // 发送获取文件夹结构的命令
            String command = "__GET_FILE_SET__";
            byte[] commandBytes = command.getBytes(StandardCharsets.UTF_8);
            outputStream.write(commandBytes.length);
            outputStream.write(commandBytes);

            // 发送要建立的文件夹
            byte[] fileNameToDownloadBytes = fileNameToDownload.getBytes(StandardCharsets.UTF_8);
            outputStream.write(fileNameToDownloadBytes.length);
            outputStream.write(fileNameToDownloadBytes);

            // 读取文件list结构
            InputStream inputStream = socket.getInputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;

            StringBuilder folderStructure = new StringBuilder();
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                folderStructure.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }

            // 解析文件夹结构
            String[] folders = folderStructure.toString().split("\n");
            files.addAll(Arrays.asList(folders));

            // 关闭流和socket
            inputStream.close();
            outputStream.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return files;
    }


    /**
     *
     * Client将要发送文件 所以进行Server文件的创建 之后对方再发来文件  ok
     */
    public static void getFolderNamesRequest(File directory, String parentPath, Set<String> folderSet) {
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                String folderPath = parentPath + File.separator + file.getName();
                folderSet.add(folderPath);
                getFolderNamesRequest(file, folderPath, folderSet);
            }
        }
    }

    private static void sendCreateFolderRequest(Set<String> folderSet) {
        for (String folderPath : folderSet) {
            // 发送创建文件夹的请求
            createFolderRequest(folderPath);
        }
    }

    private static void createFolderRequest(String folderPath) {
        try {
            Socket socket = new Socket("127.0.0.1", 8083);
            OutputStream outputStream = socket.getOutputStream();

            // 发送创建文件夹的命令和路径
            String command = "__CREATE_FOLDER__";
            byte[] commandBytes = command.getBytes(StandardCharsets.UTF_8);
            outputStream.write(commandBytes.length);
            outputStream.write(commandBytes);

            byte[] folderPathBytes = folderPath.getBytes(StandardCharsets.UTF_8);
            outputStream.write(folderPathBytes.length);
            outputStream.write(folderPathBytes);

            // 关闭流和socket
            outputStream.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     *  Client端发来展示Resource下面所有文件和文件夹名字的请求
     *
     */

    public static void resourceListRequest() {
        try {
            // 创建一个指定host和port的客户端套接字
            Socket socket = new Socket("127.0.0.1", 8083);

            // 通过socket获取outputStream流
            OutputStream outputStream = socket.getOutputStream();

            // 先发送列举资源的命令
            String command = "__LIST_RESOURCES__";
            byte[] commandBytes = command.getBytes(StandardCharsets.UTF_8);
            outputStream.write(commandBytes.length);
            outputStream.write(commandBytes);

            // 读取服务器返回的资源列表
            InputStream inputStream = socket.getInputStream();
            byte[] buffer = new byte[1048576];
            int bytesRead;

            StringBuilder resourceList = new StringBuilder();
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                resourceList.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }

            // 打印资源列表
            System.out.println("Server Resources:");
            System.out.println(resourceList);


            // 关闭流和socket
            inputStream.close();
            outputStream.close();
            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     *  Client端发送上传文件的请求
     *
     */

    public static void uploadRequest(File directory,  ExecutorService threadPoolExecutor, String name){
//        // 在上传时启动输入监听线程?

        File[] files = directory.listFiles();
        // 创建多个上传任务
        for (File file : files) {
            String temp_name = "";
            // 对于子目录，递归调用
            if (file.isDirectory()) {
                temp_name = name + File.separator + file.getName();
                uploadRequest(file, threadPoolExecutor, temp_name);
            } else {
                // 对于文件，创建上传任务并提交给线程池执行
                String fileName = name + File.separator + file.getName();
                UploadTask uploadTask = new UploadTask(file, fileName);
                threadPoolExecutor.execute(uploadTask);
                uploadTasks.add(uploadTask);
            }
        }
    }

    /**
     *  Client端发送下载文件的请求
     *
     */

    public static void downloadRequest(ExecutorService threadPoolExecutor, Set<String> fileSet) {
        for (String s: fileSet) {
            DownloadTask downloadTask = new DownloadTask(s);
            threadPoolExecutor.execute(downloadTask);
            downloadTasks.add(downloadTask);
        }
    }

}

/**
 *  加入一个状态枚举类
 *
 */

enum Status{
    Transfer,
    Stop,
    Cancel,
    List,
    Finish
}


/**
 *  Runnable封装
 *
 */

class UploadTask implements Runnable {

    // inputStream

    // outputStream   直接在类中


    public String fileName;// 这里其实是已经有相对路径的

    public File file;
    public long progress;

    public volatile Status status;

    public UploadTask(File file, String fileName) {
        this.file = file;
        this.fileName = fileName;
        this.progress = 0;
        this.status = Status.Transfer;
    }

    @Override
    public void run() {
        try {
            //创建一个指定host和port的客户端套接字
            Socket socket = new Socket("127.0.0.1", 8083);

            File file = new File(Client.uploadFolder + File.separator + fileName);
            //创建本地FileInputStream流对象来读取文件
            FileInputStream fileInputStream = new FileInputStream(file);
            //通过socket获取outputStream流
            OutputStream outputStream = socket.getOutputStream();

            // 先发送下载命令和文件名
            String command = "__UPLOAD__";
            byte[] commandBytes = command.getBytes(StandardCharsets.UTF_8);
            outputStream.write(commandBytes.length);
            outputStream.write(commandBytes);

            // 发送文件名
            byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
            outputStream.write(fileNameBytes.length);
            outputStream.write(fileNameBytes);


            // 发送文件内容的长度
            long fileLength = file.length();
            outputStream.write((int) (fileLength & 0xFF));
            outputStream.write((int) ((fileLength >> 8) & 0xFF));
            outputStream.write((int) ((fileLength >> 16) & 0xFF));
            outputStream.write((int) ((fileLength >> 24) & 0xFF));

            //使用outputStream的write方法读取本地文件
            byte[] bytes = new byte[1048576];
            int len = 0;

            // 读取本地文件内容并上传
            //int count = 0;
            ALL:while (true){
                if (status == Status.Transfer){
                    len = fileInputStream.read(bytes);
                    if (len != -1){
                        // 发送暂停命令
                        String stop = "__TRAN__";
                        byte[] stops = stop.getBytes(StandardCharsets.UTF_8);
                        outputStream.write(stops.length);
                        outputStream.write(stops);

                        outputStream.write(bytes, 0, len);
                        this.progress += len;
                    }else{
                        status = Status.Finish;
                        // 再给客户端一个回应。
                        String out = "Uploading " + fileName + " from client to server successfully!\r\n";
                        System.out.println(out);
                        break;
                    }
                }else if (status == Status.Stop){
                    // 发送暂停命令
                    String stop = "__STOP__";
                    byte[] stops = stop.getBytes(StandardCharsets.UTF_8);
                    outputStream.write(stops.length);
                    outputStream.write(stops);

                    while (true){
                        if (status == Status.Transfer){
                            // 发送恢复命令
                            String resume = "__RESU__";
                            byte[] resumes = resume.getBytes(StandardCharsets.UTF_8);
                            outputStream.write(resumes .length);
                            outputStream.write(resumes);
                            break;
                        }else if (status == Status.Cancel){
                            // 发送Cancel命令
                            // 发送取消命令
                            String cancel = "__DELE__";
                            byte[] cancels = cancel.getBytes(StandardCharsets.UTF_8);
                            outputStream.write(cancels.length);
                            outputStream.write(cancels);
                            // 再给客户端一个回应。
                            String out = "Delete transfer " + fileName + " from client to server.\r\n";
                            System.out.println(out);
                            break ALL;
                        }else{
                            // 发送暂停命令
                            String stop2 = "__STOP__";
                            byte[] stop2s = stop2.getBytes(StandardCharsets.UTF_8);
                            outputStream.write(stop2s.length);
                            outputStream.write(stop2s);
                        }
                    }
                }else if (status == Status.Cancel){
                    // 发送取消命令
                    String cancel = "__DELE__";
                    byte[] cancels = cancel.getBytes(StandardCharsets.UTF_8);
                    outputStream.write(cancels.length);
                    outputStream.write(cancels);
                    break;
                }
            }

            // 使用socket的shutdownOutStream()方法禁用此套接字的输出流
            socket.shutdownOutput();

            //关闭流
            fileInputStream.close();
            socket.close();
        } catch (IOException i) {
            System.out.println(i);
        }
    }

}

class DownloadTask implements Runnable {

    public String fileName;//

    // relative path // 这里也封装在方法中了

    public long progress;

    public volatile Status status;

    public DownloadTask(String fileName) {
        this.fileName = fileName;
        this.status = Status.Transfer;
        this.progress = 0;

    }
    @Override
    public void run() {
        try {
            Socket socket = new Socket("127.0.0.1", 8083);

            OutputStream outputStream = socket.getOutputStream();
            InputStream inputStream = socket.getInputStream();

            // 先发送下载命令和文件名
            String command = "__DOWNLOAD__";
            byte[] commandBytes = command.getBytes(StandardCharsets.UTF_8);
            outputStream.write(commandBytes.length);
            outputStream.write(commandBytes);

            // 发送文件路径
            byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
            outputStream.write(fileNameBytes.length);
            outputStream.write(fileNameBytes);
            System.out.println("Send file Path");

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


            File folder = new File(Client.downloadFolder + File.separator + path);

            if (!folder.exists()){
                folder.mkdirs();
            }


            File file = new File(Client.downloadFolder  + File.separator + fileName);
            FileOutputStream fileOutputStream = new FileOutputStream(file);

            // 将上传的文件内容读出来
            byte[] buffer = new byte[1048576];
            int bytesRead = 0;

            // 读取文件内容的长度
            long fileLength = (inputStream.read() & 0xFF) |
                    ((inputStream.read() & 0xFF) << 8) |
                    ((inputStream.read() & 0xFF) << 16) |
                    ((long) (inputStream.read() & 0xFF) << 24);
            System.out.println("fileLength:"  +  fileLength);

            // 读取本地文件内容并上传
            ALL:while (true){
                if (status == Status.Transfer){
                    // 发送传输命令
                    String stop = "__TRAN__";
                    byte[] stops = stop.getBytes(StandardCharsets.UTF_8);
                    outputStream.write(stops.length);
                    outputStream.write(stops);

                    if (fileLength > 0 && (bytesRead = inputStream.read(buffer, 0, (int) Math.min(buffer.length, fileLength))) != -1){
                        // 8.将读出来的数据再写到服务器本地磁盘中
                        fileOutputStream.write(buffer, 0, bytesRead);
                        fileLength -= bytesRead;
                        this.progress += bytesRead;
                    }else{
                        this.status = Status.Finish;
                        String finish = "__FINISH__";
                        byte[] finishes = finish.getBytes(StandardCharsets.UTF_8);
                        outputStream.write(finishes.length);
                        outputStream.write(finishes);
                        // 再给客户端一个回应。
                        String out = "Downloading " + fileName + " from server to client successfully!\r\n";
                        System.out.println(out);
                        break;
                    }
                }else if (status == Status.Stop){
                    // 发送暂停命令
                    String stop = "__STOP__";
                    byte[] stops = stop.getBytes(StandardCharsets.UTF_8);
                    outputStream.write(stops.length);
                    outputStream.write(stops);

                    while (true){
                        if (status == Status.Transfer){
                            // 发送恢复命令
                            String resume = "__RESU__";
                            byte[] resumes = resume.getBytes(StandardCharsets.UTF_8);
                            outputStream.write(resumes .length);
                            outputStream.write(resumes);
                            break;
                        }else if (status == Status.Cancel){
                            // 发送Cancel命令
                            // 发送取消命令
                            String cancel = "__DELE__";
                            byte[] cancels = cancel.getBytes(StandardCharsets.UTF_8);
                            outputStream.write(cancels.length);
                            outputStream.write(cancels);
                            Client.delete_files.add(Client.downloadFolder  + File.separator + fileName);
                            // 再给客户端一个回应。
                            String out = "Delete transfer " + fileName + " from server to client.\r\n";
                            System.out.println(out);
                            break ALL;
                        }else{
                            // 发送暂停命令
                            String stop2 = "__STOP__";
                            byte[] stop2s = stop2.getBytes(StandardCharsets.UTF_8);
                            outputStream.write(stop2s.length);
                            outputStream.write(stop2s);
                        }
                    }
                }else if (status == Status.Cancel){
                    // 发送取消命令
                    String cancel = "__DELE__";
                    byte[] cancels = cancel.getBytes(StandardCharsets.UTF_8);
                    outputStream.write(cancels.length);
                    outputStream.write(cancels);
                    Client.delete_files.add(Client.downloadFolder  + File.separator + fileName);
                    break;
                }
            }
            fileOutputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}