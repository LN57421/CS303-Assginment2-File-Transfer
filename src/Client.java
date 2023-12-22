import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Client {

    static ExecutorService executorUploadService = Executors.newFixedThreadPool(10);
    static ExecutorService executorDownService = Executors.newFixedThreadPool(10);

    static List<DownloadTask> downloadTasks = new ArrayList<>();
    static List<UploadTask> uploadTasks = new ArrayList<>();

    static String uploadPath = "Client\\Upload";


    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);


            label:while (true) {
                System.out.println("请输入：Upload/Download/List_resources/Quit");
                String command = scanner.nextLine();

                switch (command) {
                    case "Upload":
                        // 我们首先获取Upload下面的所有文件
                        Set<String> uploadPaths = new HashSet<>();
                        File folder = new File(uploadPath);

                        if (folder.exists() && folder.isDirectory()) {
                            listFiles(folder, uploadPaths);
                        } else {
                            System.out.println("请求的上传路径有误");
                        }

                        // 然后 对于每一个Path对应的路径进行上传
                        for (String uploadPath: uploadPaths) {
                            UploadTask uploadTask = new UploadTask(uploadPath);
                            executorUploadService.submit(uploadTask);
                            uploadTasks.add(uploadTask);
                        }

                        // 之后 我们进入上传的监听
                        uploadActions(scanner);
                        break;
                    case "Download":
                        System.out.println("请输入你想要下载的文件或者文件夹：");
                        // 选取进行下载的文件/文件夹
                        String path = scanner.nextLine();

                        // 我们首先请求对应文件夹下面的所有文件
                        Set<String> downloadPaths = new HashSet<>();

                        // 这里是请求文件路径的方法 并在后期通过indexOf方法重组本地路径
                        downloadPaths = askForPaths(path);

                        // 然后 对于每一个Path对应的路径进行下载
                        for (String downloadPath: downloadPaths) {
                            DownloadTask downloadTask = new DownloadTask(downloadPath);
                            executorDownService.submit(downloadTask);
                            downloadTasks.add(downloadTask);
                            System.out.println(downloadPath);
                        }

                        // 之后 我们进行下载的监听
                        downloadActions(scanner);
                        break;
                    case "List_resources":
                        // list所有Download文件下面的文件和文件夹名称
                        ListResourcesRequest(scanner);
                        break;
                    case "Quit":
                        break label;
                    default:
                        System.out.println("Invalid commend, please try again.");
                        break;
                }
            }

        }catch (Exception e){
            System.out.println(e.getMessage());
        }

    }

    /**
     *  main方法持续接收的方法
     */
    static void downloadActions(Scanner scanner) throws InterruptedException, IOException {
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

            System.out.println("检查执行状态请输入check，暂停请输入stop，恢复请输入resume，删除请输入delete. 离开请输入exit");
            command = scanner.next();
            if(command.equals("check")){
                for (int i = 0; i < downloadTasks.size(); i++) {
                    System.out.println("File Name: " + downloadTasks.get(i).filePath + " Status: " + downloadTasks.get(i).status + " Sent(bytes): " +
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
                        if (downloadTasks.get(i).filePath.equals(stop_files.get(j))){
                            downloadTasks.get(i).status = Status.Stop;
                            System.out.println("File: " + downloadTasks.get(i).filePath + " is stop.") ;
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
                        if (downloadTasks.get(i).filePath.equals(resume_files.get(j))){
                            downloadTasks.get(i).status = Status.Transfer;
                            System.out.println("File: " + downloadTasks.get(i).filePath + " is resume.") ;
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
                        if (downloadTasks.get(i).filePath.equals(delete_files.get(j))){
                            downloadTasks.get(i).status = Status.Cancel;
                            System.out.println("File: " + downloadTasks.get(i).filePath + " is cancel.") ;
                        }
                    }
                }
            }

            if(command.equals("exit")){
                break;
            }
        }
    }


    static void uploadActions(Scanner scanner) throws InterruptedException {
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

            System.out.println("检查执行状态请输入check，暂停请输入stop，恢复请输入resume，删除请输入delete. 离开请输入exit");
            command=scanner.next();
            if(command.equals("check")){
                for (int i = 0; i < uploadTasks.size(); i++) {
                    System.out.println("File Name: " + uploadTasks.get(i).filePath + " Status: " + uploadTasks.get(i).status + " Sent(bytes): " +
                            uploadTasks.get(i).progress);
                }
            }
            if(command.equals("stop")){
                System.out.println("请输入想要暂停传输的文件名，请输入多个路径，结束后再输入<<<:");
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
                        if (uploadTasks.get(i).filePath.equals(stop_files.get(j))){
                            uploadTasks.get(i).status = Status.Stop;
                            System.out.println("File: " + uploadTasks.get(i).filePath + " is stop.") ;
                        }
                    }
                }
            }

            if(command.equals("resume")){
                System.out.println("请输入想要恢复传输的文件，请输入多个路径，并在输入结束后输入<<<");
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
                        if (uploadTasks.get(i).filePath.equals(resume_files.get(j))){
                            uploadTasks.get(i).status = Status.Transfer;
                            System.out.println("File: " + uploadTasks.get(i).filePath + " is resume.") ;
                        }
                    }
                }
            }

            if(command.equals("delete")){
                System.out.println("请输入想要取消传输的文件，请输入多个路径，并在输入结束后输入<<<");
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
                        if (uploadTasks.get(i).filePath.equals(delete_files.get(j))){
                            uploadTasks.get(i).status = Status.Cancel;
                            System.out.println("File: " + uploadTasks.get(i).filePath + " is cancel.") ;
                        }
                    }
                }
            }

            if(command.equals("exit")){
                break;
            }
        }
    }

    public static void listFiles(File folder, Set<String> uploadPaths) {
        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    // 如果是文件，加入文件路径
                    uploadPaths.add(file.getAbsolutePath());
                } else if (file.isDirectory()) {
                    // 如果是文件夹，递归调用listFiles方法
                    listFiles(file, uploadPaths);
                }
            }
        }
    }

    public static void ListResourcesRequest(Scanner scanner){

        try {
            Socket socket = new Socket("127.0.0.1", 8083);

            DataInputStream in = new DataInputStream(socket.getInputStream());

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // 发送对应的进程命令 Handler进行选择和接收
            out.writeUTF("List_resources");
            out.flush();

            InputStream inputStream = socket.getInputStream();
            byte[] buffer = new byte[1048576];
            int bytesRead;

            StringBuilder resourceList = new StringBuilder();
            bytesRead = inputStream.read(buffer);
            resourceList.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));

            // 打印资源列表
            System.out.println("Server Resources:");
            System.out.println(resourceList);


            // 关闭流和socket
            inputStream.close();
            socket.close();
        }catch (Exception e){
            System.out.println(e.getMessage());
        }

    }

    public static Set<String> askForPaths(String path){
        Set<String> downloadRawPaths = new HashSet<>();
        Set<String> downloadPaths = new HashSet<>();
        try {
            Socket socket = new Socket("127.0.0.1", 8083);

            DataInputStream in = new DataInputStream(socket.getInputStream());

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // 发送对应的进程命令 Handler进行选择和接收
            out.writeUTF("askForPaths");
            out.flush();

            // 发送path 对方需要进行path的拼接
            out.writeUTF(path);
            out.flush();

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
            downloadRawPaths.addAll(Arrays.asList(folders));

            String keyword = "Resources";
            for (String rawPath: downloadRawPaths) {
                int index = rawPath.indexOf(keyword);
                String subPath = rawPath.substring(index + keyword.length());
                downloadPaths.add(subPath);
            }

            // 关闭流和socket
            inputStream.close();
            socket.close();
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
        return downloadPaths;
    }
}
