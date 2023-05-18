package com;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class utils {
    /**
     * End the socket connection as the client side
     * @param socket A socket connection
     */
    public static void closeSocketClient(Socket socket) throws IOException {
        socket.shutdownInput();
        socket.shutdownOutput();
        socket.close();
    }

    /**
     * End the socket connection as the server side
     * @param socket A socket connection
     */
    public static void closeSocketServer(Socket socket) throws IOException {
        InputStream inputStream = socket.getInputStream();
        byte[] buf = new byte[1024];
        while (inputStream.read(buf) != -1) {
            System.out.println(new String(buf));
        }
        socket.shutdownInput();
        socket.shutdownOutput();
        socket.close();
        System.out.println("关闭连接:" + socket.getPort());
    }

    public static List<Double> countNumber(double[] data) {
        Set<Double> set = new TreeSet<Double>();
        for (int i = 0; i < data.length; i++) {
            set.add(data[i]);
        }
        List<Double> list = new ArrayList(set);
        return list;
    }


    public static Integer[] sort(double[] arr) {
        Integer[] index = new Integer[arr.length];
        for (int i = 0; i < arr.length; i ++) {
            index[i] = i;
        }
        Arrays.sort(index, new Comparator<Integer>() {

            @Override
            public int compare(Integer o1, Integer o2) {
                if(arr[o1] > arr[o2]) {
                    return 1;
                }else if(arr[o1] < arr[o2]) {
                    return -1;
                }
                return 0;
            }
        });
        Integer[] a = new Integer[index.length];
        for(int i = 0;i < index.length;i ++) {
            a[index[i]] = i;
        }
        return a;
    }

    public static Map<String[], double[][]> readDataFromFile(int dataNums, int dataDims, String pathName) {
        double[][] dataSets = new double[dataNums][dataDims];
        String[] dataTags = new String[dataDims];
        Map<String[],double[][]> map =new HashMap<String[] ,double[][]>();
        //第一步：先获取csv文件的路径，通过BufferedReader类去读该路径中的文件
        File csv = new File(pathName);
        try {
            //第二步：从字符输入流读取文本，缓冲各个字符，从而实现字符、数组和行（文本的行数通过回车符来进行判定）的高效读取。
            BufferedReader textFile = new BufferedReader(new FileReader(csv));
            String lineDta = "";
            lineDta = textFile.readLine();
            dataTags = lineDta.split(",");
            int index = 0;
            //第三步：将文档的下一行数据赋值给lineData，并判断是否为空，若不为空则输出
            while (index < dataNums) {
                lineDta = textFile.readLine();
                String[] arr = lineDta.split(",");
                for (int j = 0; j < dataDims; j++) {
//					System.out.println(arr[j]);
                    dataSets[index][j] = Double.parseDouble(arr[j]);
                }
                index++;
            }
        } catch (FileNotFoundException e) {
            System.out.println("没有找到指定文件");
        } catch (IOException e) {
            System.out.println("文件读写出错");
        }
        map.put(dataTags, dataSets);
        return map;
    }

}
