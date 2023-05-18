package com.DataOwners;

import com.XGBoost.XGBoost;

import java.io.*;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.utils.readDataFromFile;

public class DataOwners {
    public int dataNumber;
    public int featureNumber;
    public int treeNumber;
    public int treeDepth;
    public int bucketNumber;
    public boolean ifenc = true;
    public String dataOwnerName;
    public String[] localDataTags;
    public double[][] rawDatas;
    public double[] ylabel;
    public int[] padThreshold;
    public LinkedHashMap<String, double[]> threshold;
    public XGBoost XGBoost;
    private double[] pad;
    private Socket socketTA;
    private Socket socketCSP;
    private final int[] NodeNumber;
    private final int serverPortTA = 2000;
    private final String urlCSP = "0.0.0.0";
    private final int serverPortCSP = 2002;
    private final double scale = 100.0;
    private final double modelLamba = 1.0;


    public DataOwners(String DataOwnersName, int dataNumber, int featureNumber) throws IOException {
        this.dataOwnerName = DataOwnersName;
        this.dataNumber = dataNumber;
        this.featureNumber = featureNumber;
        this.NodeNumber = new int[1];
    }


    /**
     * 读取数据并对数据进行预处理
     * @param pathName 文件的路径名字
     */
    public void readDataAndPreprocessing(String pathName){
        System.out.println(this.dataOwnerName + "预处理本地数据......");
        Map<String[], double[][]> map = readDataFromFile(this.dataNumber, this.featureNumber, pathName);
        this.localDataTags = map.keySet().iterator().next();
        double[][] rawData = map.entrySet().iterator().next().getValue();
        NumpyT(rawData);
    }

    /**
     * 转置矩阵
     */
    private void NumpyT(double[][] Datas) {
        double[][] DatasT = new double[featureNumber - 1][dataNumber];
        for (int i = 0; i < featureNumber - 1; i++) {
            for (int j = 0; j < dataNumber; j++) {
                DatasT[i][j] = Datas[j][i];
            }
        }
        this.rawDatas = DatasT;
        this.ylabel = new double[this.dataNumber];
        for (int i = 0; i < dataNumber; i++) {
            this.ylabel[i] = Datas[i][featureNumber - 1];
        }
    }

    private void countLocalMaxMin() throws IOException {
        double[] max = new double[this.featureNumber-1];
        double[] min = new double[this.featureNumber-1];

        for (int i = 0; i < this.featureNumber-1; i++) {
            double tmpMax = 1f / -0f;
            double tmpMin = 1f / 0f;
            for (int j = 0; j < this.dataNumber; j++) {
                if (this.rawDatas[i][j] > tmpMax) {
                    tmpMax = this.rawDatas[i][j];
                }
                if (this.rawDatas[i][j] < tmpMin) {
                    tmpMin = this.rawDatas[i][j];
                }
            }
            max[i] = tmpMax;
            min[i] = tmpMin;
        }
        sendLocalMaxMintoTA(max, min);
    }

    public void createSocketTA() throws IOException {
        String urlTA = "0.0.0.0";
        socketTA = new Socket(urlTA, serverPortTA);

        System.out.println("已发送服务器TA连接");
        System.out.println("客户端:" + socketTA.getLocalAddress() + " Port:" + socketTA.getLocalPort());
        System.out.println("服务器TA:" + socketTA.getInetAddress() + " Port:" + socketTA.getLocalPort());
    }

    public void createSocketCSP() throws IOException {
        socketCSP = new Socket(urlCSP, serverPortCSP);

        System.out.println("已发送服务器CSP连接");
        System.out.println("客户端:" + socketCSP.getLocalAddress() + " Port:" + socketCSP.getLocalPort());
        System.out.println("服务器CSP:" + socketCSP.getLocalPort() + " Port:" + socketCSP.getLocalPort());

    }

    public void closeSocketTA() throws IOException {
        socketTA.shutdownInput();
        socketTA.shutdownOutput();
        socketTA.close();
    }

    public void closeSocketCSP() throws IOException {
        socketCSP.shutdownInput();
        socketCSP.shutdownOutput();
        socketCSP.close();
    }

    public void receivePadsFromTA() throws IOException {
        ObjectInputStream objectInputStream = new ObjectInputStream(this.socketTA.getInputStream());
        try {
            pad = (double[]) objectInputStream.readObject();
            System.out.println("从TA接收参数完成");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void sendLocalMaxMintoTA(double[] xMax, double[] xMin) throws IOException {
        System.out.println(this.dataOwnerName+"发送本地数据最值给TA");
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socketTA.getOutputStream());

        objectOutputStream.writeObject(xMax);
        objectOutputStream.flush();

        objectOutputStream.writeObject(xMin);
        objectOutputStream.flush();

        System.out.println(this.dataOwnerName+"发送TA最大最小值完成");
        try {
            closeSocketTA();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void receiveSamplesFromCSP() throws IOException, ClassNotFoundException {
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socketCSP.getOutputStream());

        // 发送数据特征
        objectOutputStream.writeObject(this.localDataTags);
        objectOutputStream.flush();

        ObjectInputStream objectInputStream = new ObjectInputStream(socketCSP.getInputStream());

        // 接受模型
        this.treeNumber = (int) objectInputStream.readObject();
        this.treeDepth = (int) objectInputStream.readObject();
        this.bucketNumber = (int) objectInputStream.readObject();
        this.threshold = new LinkedHashMap<>();
        this.threshold = (LinkedHashMap<String, double[]>) objectInputStream.readObject();
        this.XGBoost = new XGBoost(rawDatas, threshold, ylabel);
        this.padThreshold = new int[3];
        padThreshold[0] = ((int) Math.pow(2, treeDepth) - 1) * (((featureNumber-1) * bucketNumber) + 1) * 2;
        padThreshold[1] = (((featureNumber-1) * bucketNumber) + 1) * 2;
        padThreshold[2] = bucketNumber * 2;

        System.out.println(this.dataOwnerName+"接收CSP模型参数完成");

    }

    public double Encryption(double m, int treeN, int NodeN, int featureN, int bucketN, int flag){
        if(ifenc) {
            int index = treeN * padThreshold[0] + NodeN * padThreshold[1] + featureN * padThreshold[2] + bucketN + 2 - flag;
            return m + pad[index];
        }else {
            return m;
        }
    }

    public void trainTreeModel() throws IOException, ClassNotFoundException {
        for (int i = 0; i < this.treeNumber; i++) {
            System.out.println("第"+i+"次迭代:");
            int depth = 1;
            NodeNumber[0] = -1;
            //1->计算g和h
            this.XGBoost.caculategh();

            //2->初始化bit
            boolean[] bit = new boolean[this.dataNumber];
            for(int j=0; j<this.dataNumber; j++) {
                bit[j] = true;
            }

            //3->建立一棵树
            splitNode(bit, i, depth);

            //4->更新PredictY值
            this.XGBoost.sigmoid();
        }
    }

    // 分裂结点
    public void splitNode(boolean[] bit, int treeN, int depth) throws IOException, ClassNotFoundException {
        if(depth > this.treeDepth) {
            System.out.println("达到最大深度, 停止分裂");
        }else {
            depth += 1;
            NodeNumber[0] += 1;

            // 1. 计算梯度总值, 并加密
            double[] allGH = new double[2];
            double[] EncallGH = new double[2];
            allGH = this.XGBoost.calulateAllGH(bit);
            for (int i = 0; i < 2; i++) {
                EncallGH[i] = Encryption(allGH[i] * this.scale, treeN, NodeNumber[0], 0, 0, 2-i);
            }

            // 2. 计算每个分桶的梯度求和值, 并加密
            double[][] leftBucketGH = new double[this.featureNumber-1][this.bucketNumber * 2];
            double[][] EncleftBucketGH = new double[this.featureNumber-1][this.bucketNumber * 2];
            int i = 0;
            for (String key : this.XGBoost.initBucket.keySet()) {
                for (int j = 0; j < this.bucketNumber; j++) {
                    leftBucketGH[i][j*2] = 0;
                    leftBucketGH[i][j*2+1] = 0;
                    for (int k = 0; k < dataNumber; k++) {
                        if(bit[k] && this.XGBoost.initBucket.get(key)[j][k]){
                            leftBucketGH[i][j*2] += this.XGBoost.gradient[k];
                            leftBucketGH[i][j*2+1] += this.XGBoost.hessian[k];
                        }
                    }
                    EncleftBucketGH[i][j*2]  = Encryption(leftBucketGH[i][j*2] * this.scale, treeN, NodeNumber[0], i, j*2, 0);
                    EncleftBucketGH[i][j*2+1]  = Encryption(leftBucketGH[i][j*2+1] * this.scale, treeN, NodeNumber[0], i, j*2+1, 0);
                }
                i += 1;
            }

            // 3. 加密梯度求和值，并发送该值 （总值和分桶求和值）
            sendGHtoCSP(EncallGH, EncleftBucketGH);

            // 4. 接受全局最大增益，并计算下一次bit
            boolean[] leftbit = new boolean[this.dataNumber];
            boolean[] rightbit = new boolean[this.dataNumber];
            leftbit = recieveGainfromCSP(bit);
            rightbit = generateBit(bit, leftbit);

            // 5. 选择是否更新梯度
            if(depth == this.treeDepth + 1){
                double[] leafGH = new double[2];
                double[] rightGH = new double[2];
                leafGH = this.XGBoost.calulateAllGH(leftbit);
                rightGH = this.XGBoost.calulateAllGH(rightbit);
                double lweight = 0.0 - leafGH[0] / (leafGH[1] + this.modelLamba);
                double rweight = 0.0 - rightGH[0] / (rightGH[1] + this.modelLamba);
                this.XGBoost.generateWeight(leftbit, lweight);
                this.XGBoost.generateWeight(rightbit, rweight);
            }

            // 6. 继续下一次分裂
            splitNode(leftbit, treeN, depth);
            splitNode(rightbit, treeN, depth);

        }
    }

    public void sendGHtoCSP(double[] EncallGH, double[][] EncleftBucketGH) throws IOException {
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socketCSP.getOutputStream());

        objectOutputStream.writeObject(this.dataOwnerName);
        objectOutputStream.flush();

        objectOutputStream.writeObject(NodeNumber[0]);
        objectOutputStream.flush();

        objectOutputStream.writeObject(EncallGH);
        objectOutputStream.flush();

        objectOutputStream.writeObject(EncleftBucketGH);
        objectOutputStream.flush();

        System.out.println(this.dataOwnerName + "发送梯度完成!");
    }

    public boolean[] recieveGainfromCSP(boolean[] bit) throws IOException, ClassNotFoundException {
        boolean[] leftbit = new boolean[this.dataNumber];

        ObjectInputStream objectInputStream = new ObjectInputStream(socketCSP.getInputStream());

        int featureIndex = (int) objectInputStream.readObject();
        double threshold = (double) objectInputStream.readObject();

        for (int i = 0; i < this.dataNumber; i++) {
            leftbit[i] = this.rawDatas[featureIndex][i] <= threshold && bit[i];
        }
        System.out.println(this.dataOwnerName + "接受CSP最大增益完成");

        return leftbit;
    }

    public boolean[] generateBit(boolean[] bit, boolean[] leftbit){
        boolean[] rightbit = new boolean[this.dataNumber];
        for (int i = 0; i < dataNumber; i++) {
            rightbit[i] = false;
            if(bit[i] && !leftbit[i]){
                rightbit[i] = true;
            }else if(bit[i]  && leftbit[i] ){
                rightbit[i] = false;
            }
        }
        return rightbit;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // 1. 初始化的信息, 参与方个数, 文件路径
        int ParamN = 4;
        String pathName = "ELSGboost/src/com/sources/Credit-4-horizontal/credit_train";

        // String pathName = "ELSGboost/src/com/sources/Bank-4-horizontal/bank_train";
        System.out.println(System.getProperty("user.dir"));

        // 2. 创建对象, 并且读取数据
        DataOwners[] dataOwners = new DataOwners[4];
        for (int i = 0; i < ParamN; i++) {
            // dataOwners[i] = new DataOwners("DataOwners"+i, 9042,17);
            dataOwners[i] = new DataOwners("DataOwners"+i, 6000,24);
            String datapathName = pathName + (i+1) +".csv";
            dataOwners[i].readDataAndPreprocessing(datapathName);
//            dataOwners[i].ModelInit(3,2,32);
        }

        // 3. 统计最大值最小值
        try {
            for (int i = 0; i < ParamN; i++) {
                dataOwners[i].createSocketTA();
                dataOwners[i].receivePadsFromTA();
                dataOwners[i].countLocalMaxMin();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Thread.sleep(100);
        // 4. 初始化XGBoost, 收集全局最大最小值进行本地分桶
        try{
            for (int i = 0; i < ParamN; i++) {
                dataOwners[i].createSocketCSP();
                dataOwners[i].receiveSamplesFromCSP();
            }
        } catch (IOException e) {
        e.printStackTrace();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        // 5. 开始训练
        for (int i = 0; i < ParamN; i++) {
            final int index = i;
            new Thread(()->{
                try {
                    dataOwners[index].trainTreeModel();
                    dataOwners[index].closeSocketCSP();
                } catch (IOException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }



    }



}
