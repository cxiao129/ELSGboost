package com.CSP;

import com.TreeLink.TreeLink;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import static com.utils.readDataFromFile;

public class CSP {
    private final int N;                     // 数据拥有者个数
    private final ArrayList<Socket> sockets;
    private double[] global_xMax;
    private double[] global_xMin;
    private final int featureNumber;
    private String[] globaltags;
    private double[] ParamR;
    private int bucketNumber = 32;
    public boolean ifenc = true;
    public int treeNumber;
    public int treeDepth;                    // 树深度 - 1 (不包括叶子节点的层数)
    public int NodeN;
    public int[] padThreshold;
    private LinkedHashMap<String, double[]> threshold;
    private TreeLink[] treelink;
    private double scale = 100.0;
    private double modelLamba = 1.0;

    public CSP(int n, int featureNumber) {
        N = n;
        this.featureNumber = featureNumber;
        this.global_xMax = new double[this.featureNumber];
        this.global_xMin = new double[this.featureNumber];
        this.globaltags = new String[this.featureNumber];
        this.threshold = new LinkedHashMap<>();
        this.sockets = new ArrayList<>();
    }

    public void modelInit(int treeNumber, int treeDepth, int bucketNumber){
        this.treeNumber = treeNumber;
        this.treeDepth = treeDepth;
        this.bucketNumber = bucketNumber;
    }

    public double Decryption(double c, int treeN, int NodeN, int featureN, int bucketN, int flag){
        if(ifenc) {
            int index = treeN * padThreshold[0] + NodeN * padThreshold[1] + featureN * padThreshold[2] + bucketN + 2 - flag;
            return c - ParamR[index];
        }else {
            return c;
        }
    }

    public void receiveParametersFromTA() throws IOException {
        System.out.println("等待TA连接...");
        ServerSocket serverSocket = new ServerSocket();
        //启动端口重用
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress((2001)));
        System.out.println("服务器：" + serverSocket.getLocalSocketAddress());
        System.out.println("准备就绪");
        Socket socketTA = serverSocket.accept();
        System.out.println("TA连接建立：" + socketTA.getLocalAddress() + "端口：" + socketTA.getLocalPort());
        ObjectInputStream objectInputStream = new ObjectInputStream(socketTA.getInputStream());

        new Thread(() -> {
            try {
                ParamR = (double[]) objectInputStream.readObject();

                global_xMax = (double[]) objectInputStream.readObject();
                global_xMin = (double[]) objectInputStream.readObject();

                System.out.println("从TA接收参数:");
                System.out.println("添加扰动后的全局最大值:");
                for (double globalXMax : global_xMax) {
                    System.out.print(globalXMax + " ");
                }
                System.out.println();
                System.out.println("添加扰动后的全局最小值:");
                for (double globalXMin : global_xMin) {
                    System.out.print(globalXMin + " ");
                }
                System.out.println();

                closeSocket(socketTA);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }).start();
        System.out.println("参数接收完成");
    }

    public void startServerSocket() throws IOException, ClassNotFoundException {
        System.out.println("等待DO连接...");
        ServerSocket serverSocket = new ServerSocket();
        //启动端口重用
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress((2002)));
        System.out.println("服务器：" + serverSocket.getLocalSocketAddress());
        System.out.println("准备就绪");

        int index = 0;
        do {
            Socket socket = serverSocket.accept();
            sockets.add(socket);
            System.out.println("DO连接建立：" + socket.getLocalAddress() + "端口：" + socket.getLocalPort());

            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
            this.globaltags = (String[]) objectInputStream.readObject();

            sendModeltoDO(socket);
            index++;
        } while (index != N);
    }

    public void sendModeltoDO(Socket socket) throws IOException {
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());

        objectOutputStream.writeObject(this.treeNumber);
        objectOutputStream.flush();

        objectOutputStream.writeObject(this.treeDepth);
        objectOutputStream.flush();

        objectOutputStream.writeObject(this.bucketNumber);
        objectOutputStream.flush();

        splictThrethold();
        objectOutputStream.writeObject(this.threshold);
        objectOutputStream.flush();

        System.out.println("发送模型参数完成");
    }

    public void splictThrethold(){
        for (int i = 0; i < this.globaltags.length - 1; i++) {
            double[] threshold = new double[this.bucketNumber];
            double tmp = (global_xMax[i] - global_xMin[i]) / (double) bucketNumber;
            for (int j = 0; j < bucketNumber; j++) {
                threshold[j] = global_xMin[i] + tmp * (j + 1);
            }
            this.threshold.put(globaltags[i], threshold);
        }
    }

    public void closeSocket(Socket socket) throws IOException {
        socket.shutdownInput();
        socket.shutdownOutput();
        socket.close();
        System.out.println("关闭连接:" + socket.getPort());
    }


    public TreeLink[] trainTreeModel() throws IOException {
        this.treelink = new TreeLink[this.treeNumber];
        this.padThreshold = new int[3];
        padThreshold[0] = ((int) Math.pow(2, this.treeDepth) - 1) * (((featureNumber-1) * bucketNumber) + 1) * 2;
        padThreshold[1] = (((featureNumber-1) * bucketNumber) + 1) * 2;
        padThreshold[2] = this.bucketNumber * 2;

        for (int i = 0; i < this.treeNumber; i++) {
            int depth = 1;
            treelink[i] = new TreeLink(null, 0, null, null, 0);
            this.splictNode(i, depth, treelink[i]);
        }
        for (Socket socket:sockets){
            closeSocket(socket);
        }
        return treelink;
    }

    public void splictNode(int treeN, int depth, TreeLink treeLink){
        if(depth > this.treeDepth) {
            System.out.println("达到最大深度, 停止分裂");
        }else {
            depth += 1;

            ConcurrentHashMap<String, double[]> DataOwnerEncallGH = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, double[][]> DataOwnerleftBucketGH = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, Integer> DataOwnerNodeNumber = new ConcurrentHashMap<>();

            // 1. 接受加密梯度
            ExecutorService executorService = Executors.newFixedThreadPool(this.N);
            CountDownLatch latch = new CountDownLatch(this.N);
            for(Socket socket:sockets) {
                executorService.execute(new Runnable() {
                    public void run() {
                        try {
                            double[] EncallGH = new double[2];
                            double[][] EncleftBucketGH = new double[featureNumber-1][2*bucketNumber];
                            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());

                            String DataOwnerName = (String) objectInputStream.readObject();
                            NodeN = (Integer) objectInputStream.readObject();
                            EncallGH = (double[]) objectInputStream.readObject();
                            EncleftBucketGH = (double[][]) objectInputStream.readObject();

                            DataOwnerEncallGH.put(DataOwnerName, EncallGH);
                            DataOwnerleftBucketGH.put(DataOwnerName, EncleftBucketGH);
                            DataOwnerNodeNumber.put(DataOwnerName, NodeN);

                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                        latch.countDown();
                        }
                    });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            executorService.shutdown();

            // 2. 聚合增益
            double[] aggregateallGH = new double[2];
            double[][] aggregateallleftBucketGH = new double[this.featureNumber-1][2*this.bucketNumber];

            for (int i = 0; i < 2; i++) {
                aggregateallGH[i] = 0;
            }
            for (int i = 0; i < this.featureNumber-1; i++) {
                for (int j = 0; j < this.bucketNumber*2; j++) {
                    aggregateallleftBucketGH[i][j] = 0;
                }
            }
            for (String key:DataOwnerEncallGH.keySet()) {
                aggregateallGH[0] += DataOwnerEncallGH.get(key)[0];
                aggregateallGH[1] += DataOwnerEncallGH.get(key)[1];
                for (int i = 0; i < this.featureNumber-1; i++) {
                    for (int j = 0; j < this.bucketNumber; j++) {
                        aggregateallleftBucketGH[i][2*j] += DataOwnerleftBucketGH.get(key)[i][2*j];
                        aggregateallleftBucketGH[i][2*j+1] += DataOwnerleftBucketGH.get(key)[i][2*j+1];
                    }
                }
                NodeN = DataOwnerNodeNumber.get(key);
                // System.out.println(NodeN);
            }

            // 3. 解密增益
            double[] aggreGH = new double[2];
            double[][] aggrebucketGH = new double[this.featureNumber-1][2*this.bucketNumber];
            for (int i = 0; i < 2; i++) {
                aggreGH[i] = Decryption(aggregateallGH[i], treeN, NodeN, 0, 0, 2-i) / this.scale;
            }
            for (int i = 0; i < this.featureNumber-1; i++) {
                for (int j = 0; j < this.bucketNumber; j++) {
                    aggrebucketGH[i][2*j] = Decryption(aggregateallleftBucketGH[i][2*j], treeN, NodeN, i, j*2, 0)
                            / this.scale;
                    aggrebucketGH[i][2*j+1] = Decryption(aggregateallleftBucketGH[i][2*j+1], treeN, NodeN, i, j*2+1, 0)
                            / this.scale;
                }
            }

            // 4. 计算最大增益
            int[] index = new int[2];
            index = calculateMaxGain(aggreGH, aggrebucketGH);

            // 5. 发送索引信息
            int featureIndex = index[0];
            double splictThreshold = this.threshold.get(this.globaltags[featureIndex])[index[1]];
            executorService = Executors.newFixedThreadPool(this.N);
            CountDownLatch colatch = new CountDownLatch(this.N);
            for(Socket socket:sockets) {
                executorService.execute(new Runnable() {
                    public void run() {
                        try {
                            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                            objectOutputStream.writeObject(featureIndex);
                            objectOutputStream.writeObject(splictThreshold);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        colatch.countDown();
                    }
                });
            }
            try {
                colatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            executorService.shutdown();

            // 6. 更新模型, 开始下一次分裂
            TreeLink RightLink = new TreeLink(null, 0.0, null, null, 0.0);
            TreeLink LeftLink = new TreeLink(null, 0.0, null, null, 0.0);
            treeLink.feature = this.globaltags[featureIndex];
            treeLink.threshold = splictThreshold;
            if(depth == this.treeDepth+1){
                double[] lrWeight = new double[2];
                lrWeight = calculateWeight(aggreGH, aggrebucketGH[index[0]][index[1]*2], aggrebucketGH[index[0]][index[1]*2+1]);
                LeftLink.leafnode = true;
                RightLink.leafnode = true;
                LeftLink.weight = lrWeight[0];
                RightLink.weight = lrWeight[1];
            }
            treeLink.leftNode = LeftLink;
            treeLink.rightNode = RightLink;
            splictNode(treeN, depth, treeLink.leftNode);
            splictNode(treeN, depth, treeLink.rightNode);
        }
    }

    public int[] calculateMaxGain(double[] aggreGH, double[][] aggrebucketGH){
        double maxGain = 1f / -0f;
        int[] index = new int[2];
        index[0] = -1;
        index[1] = -1;
        for (int i = 0; i < featureNumber - 1; i++) {
            for (int j = 0; j < this.bucketNumber; j++) {
                double gain = calculateGain(aggreGH, aggrebucketGH[i][2*j], aggrebucketGH[i][2*j+1]);
                if(gain > maxGain){
                    index[0] = i;
                    index[1] = j;
                    maxGain = gain;
                }
            }
        }
        return index;
    }

    public double calculateGain(double[] allGH, double leftG, double leftH){
        double leftGH = leftG * leftG/(leftH + this.modelLamba);
        double rightGH = (allGH[0] - leftG) * (allGH[0] - leftG)/(allGH[1] - leftH + 1);
        return  leftGH + rightGH - (allGH[0]) * (allGH[0]) /(allGH[1] + 1);
    }

    public double[] calculateWeight(double[] allGH, double leftg, double lefth){
        double[] lrWeight = new double[2];
        lrWeight[0] = 0.0 - leftg / (lefth + this.modelLamba);
        lrWeight[1] = 0.0 - (allGH[0] - leftg) / (allGH[1] - lefth +this.modelLamba);
        return lrWeight;
    }

    /**
     * 读取数据并对数据进行预处理
     * @param pathName 文件的路径名字
     */
    public void readDataAndPredict(String pathName, int dataNumber, int featureNumber, TreeLink[] treelinks){
        Map<String[], double[][]> map = readDataFromFile(dataNumber, featureNumber, pathName);
        String[] localDataTags = map.keySet().iterator().next();
        double[][] rawData = map.entrySet().iterator().next().getValue();
        double[] trueY = new double[dataNumber];
        for (int i = 0; i < dataNumber; i++) {
            trueY[i] = rawData[i][featureNumber-1];
        }
        double[] predictY = Predict(treelinks, rawData, localDataTags);
        System.out.println(accuracy(predictY, trueY));
    }

    public double[] Predict(TreeLink[] treeLinks, double[][] rawdata, String[] localDataTags){
        double[] predictY = new double[rawdata.length];
        LinkedHashMap<String, Integer> tagIndex = new LinkedHashMap<>();
        for (int i = 0; i < localDataTags.length-1; i++) {
            tagIndex.put(localDataTags[i], i);
        }
        for (int i = 0; i < rawdata.length; i++) {
            for (int j = 0; j < this.treeNumber; j++) {
                TreeLink treeLink = treeLinks[j];
                while(!treeLink.leafnode){
                    int index = tagIndex.get(treeLink.feature);
                    if(rawdata[i][index] > treeLink.threshold){
                        treeLink = treeLink.rightNode;
                    }else{
                        treeLink = treeLink.leftNode;
                    }
                }
                predictY[i] += treeLink.weight;
            }
        }
        return predictY;
    }

    public double accuracy(double[] predictY, double[] trueY){
        double acc = 0.0;
        for (int i = 0; i < trueY.length; i++) {
            if( (predictY[i] > 0 && trueY[i] == 1) || (predictY[i] < 0 && trueY[i] == 0)){
                acc += 1;
            }
        }
        return acc / (double) trueY.length;
    }


    public static void main(String[] args) throws IOException {
        final int N = 4;                // DataOwner数量
        final int featureNumber = 24;
        // final int featureNumber = 17;

        // 1. 创建socket连接, 获得解密密钥
        CSP csp = new CSP(N, featureNumber);
        csp.modelInit(5,2,32);
        // csp.modelInit(6,3,11);
        try {
            csp.receiveParametersFromTA();
            csp.startServerSocket();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        // 2. 与数据拥有者训练全局模型
        long start1 = System.currentTimeMillis();
        TreeLink[] treeLinks = csp.trainTreeModel();
        long end1 = System.currentTimeMillis();

        System.out.println("训练用时间"+(end1-start1)+"ms");

        // 3. 导入预测数据, 开始预测
        String pathtestName = "ELSGboost/src/com/sources/Credit-4-horizontal/credit_test.csv";
        csp.readDataAndPredict(pathtestName, 6000, 24, treeLinks);

        // String pathtestName = "ELSGboost/src/com/sources/Bank-4-horizontal/bank_test.csv";
        // csp.readDataAndPredict(pathtestName, 9042, 17, treeLinks);
    }

}
