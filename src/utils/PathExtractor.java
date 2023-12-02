package utils;

public class PathExtractor {
    public static void main(String[] args) {
        String fullPath = "/root/home/user/Upload/documents/file.txt你的完整路径"; // 例如："/root/home/user/Upload/documents/file.txt"
        String keyword = "Upload";

        int index = fullPath.indexOf(keyword);

        if (index != -1) {
            String subPath = fullPath.substring(index + keyword.length());
            System.out.println("提取的路径：" + subPath);
        } else {
            System.out.println("未找到关键字：" + keyword);
        }
    }
}
