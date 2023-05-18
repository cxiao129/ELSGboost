package com.TrustAuthority;

import com.TreeLink.TreeLink;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.*;

public class TrustAuthority {
    public static ServerSocket serverSocket;
    public final int treeNumber = 5;                // 树数目
    public final int treeDepth = 2;                 // 树深度 - 1 (不包括叶子节点的层数)
    public final int bucketNumber = 32;             // 分桶数
    private double[][] pads;
    private double[] ParamR;
    private final int maskingParam = 1000;
    private final int featureNumber;                 // 数据向量的维度
    private final int ParamN;                        // 数据拥有者个数
    private final ArrayList<double[]> Max;
    private final ArrayList<double[]> Min;
    private final ArrayList<Socket> sockets;

    private final double[] global_xMax;
    private final double[] global_xMin;
    private Socket socketCSP;

    private final String urlCSP = "0.0.0.0";
    private final int portCSP = 2001;

    public TrustAuthority(int N, int featureNumber) {
        this.ParamN = N;
        this.featureNumber = featureNumber;
        this.Max = new ArrayList<double[]>();
        this.Min = new ArrayList<double[]>();
        this.sockets = new ArrayList<>();
        this.global_xMax = new double[this.featureNumber-1];
        this.global_xMin = new double[this.featureNumber-1];
        GenerateMask(ParamN, this.featureNumber);
    }

    public void GenerateMask(int n, int featureNumber) {
        SecureRandom random = new SecureRandom();
        int padNumber = treeNumber * ((int) Math.pow(2, treeDepth) - 1) * (((featureNumber-1) * bucketNumber) + 1) * 2;
        pads = new double[n][padNumber];
        ParamR = new double[padNumber];

        for (int j = 0; j < padNumber; j++) {
            double R = 0.0;
            for (int i = 0; i < n; i++) {
                pads[i][j] = random.nextInt() % maskingParam / (double) maskingParam + random.nextInt() % maskingParam;
                R += pads[i][j];
            }
            ParamR[j] = R;
        }
    }

    public void createServerSocket() throws IOException {
        serverSocket = new ServerSocket();
        //启动端口重用
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(2000));
        // System.out.println("服务器：" + serverSocket.getLocalSocketAddress());
        // System.out.println("准备就绪");
    }

    public void startServerSocket() {
        // System.out.println("服务器启动......");
        int index = 0;
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                sockets.add(socket);
                // System.out.println("DO创建新的连接:" + socket.getPort());

                // Send one-time pads to DOi
                long start2 = System.currentTimeMillis();
                sendPadsToDataOwners(socket, pads[index]);
                // System.out.println("向DO密钥发送完成！");
                long end2 = System.currentTimeMillis();
                System.out.println("向DO发送掩码用时"+(end2-start2)+"ms");

                ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());

                new Thread(() -> {
                    try {
                        double[] xMax = (double[]) objectInputStream.readObject();
                        double[] xMin = (double[]) objectInputStream.readObject();
                        collectDataFromDataOwners(xMax, xMin);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }).start();

                index++;
                if (index == this.ParamN) {
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public void sendPadsToDataOwners(Socket socket, double[] pad) throws IOException {
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());

        objectOutputStream.writeObject(pad);
        objectOutputStream.flush();
    }


    /**
     * Trusted authority get data from Data Owners.
     *
     * @param xMax receive the max feature value from each data owner.
     * @param xMin receive the min feature value from each data owner.
     */
    public void collectDataFromDataOwners(double[] xMax, double[] xMin) {
        Max.add(xMax);
        Min.add(xMin);

        if (Max.size() == ParamN) {
            System.out.println("从DO接收数据完成");
            extractFeature();
        }
    }

    /**
     * Extract the maximum value and the minimum value of each data owner.
     */
    public void extractFeature() {
        assert (this.ParamN == Max.size());

        int colNum = Max.get(0).length;
        int rowNum = Max.size();
        for (int i = 0; i < colNum; i++) {
            double tmpMax = 1f / -0f;
            double tmpMin = 1f / 0f;
            for (int j = 0; j < rowNum; j++) {
                if (Max.get(j)[i] > tmpMax) {
                    tmpMax = Max.get(j)[i];
                }
                if (Min.get(j)[i] < tmpMin) {
                    tmpMin = Min.get(j)[i];
                }
            }
            this.global_xMax[i] = tmpMax;
            this.global_xMin[i] = tmpMin;
        }

        // Send back to DOi
//        sendSamplesToDataOwners(this.global_xMax, this.global_xMin);
    }



    /**
     * Generate a random normal distribution.
     *
     * @return a random normal distribution
     */
    private static double standardNormalDistribution() {
        Random random = new Random();
        double noise = random.nextGaussian();
        return noise > 0 ? 10 * noise : -noise * 10;
    }

    public void sendSamplesToDataOwners(double[] global_xMax, double[] global_xMin) {

        System.out.println("将处理后的数据返回给DO");
        for (Socket socket : sockets) {
            try {
                ObjectOutputStream dataOutputStream = new ObjectOutputStream(socket.getOutputStream());

                dataOutputStream.writeObject(global_xMax);
                dataOutputStream.flush();

                dataOutputStream.writeObject(global_xMin);
                dataOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                closeSocket(socket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendParametersToCSP() throws IOException {
        this.socketCSP = new Socket(urlCSP, portCSP);
        System.out.println("开启CSP服务器");

        // send decryption parameters
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(this.socketCSP.getOutputStream());
        objectOutputStream.writeObject(ParamR);
        objectOutputStream.flush();

        // send global max value
        objectOutputStream.writeObject(global_xMax);
        objectOutputStream.flush();

        // send global min value
        objectOutputStream.writeObject(global_xMin);
        objectOutputStream.flush();

        System.out.println("向CSP发送解密密钥完成!");

    }


    public void closeSocket(Socket socket) throws IOException {
//        InputStream inputStream = socket.getInputStream();
//        byte[] buf = new byte[1024];
//        while (inputStream.read() != -1) {
//            System.out.println(new String(buf));
//        }
        socket.shutdownInput();
        socket.shutdownOutput();
        socket.close();
        System.out.println("关闭连接:" + socket.getPort());
    }

    public void closeSocketCSP() throws IOException {
        this.socketCSP.shutdownInput();
        this.socketCSP.shutdownOutput();
        this.socketCSP.close();
    }

    public static void main(String[] args) {
        final int N = 4;                 // DataOwner数量
        final int DATADIMENSION = 24;    // 数据集维度，包括标签
        // final int DATADIMENSION = 17;

        // 1. 初始化TA
        long start1 = System.currentTimeMillis();
        TrustAuthority trustedAuthority = new TrustAuthority(N, DATADIMENSION);
        long end1 = System.currentTimeMillis();
        System.out.println("TA生成掩码用时"+(end1-start1)+"ms");

        // 2. 创建socket连接, 计算DataOwner最大最小值
        try {
            trustedAuthority.createServerSocket();
            trustedAuthority.startServerSocket();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 3. 创建socket连接, 发送CSP密钥参数
        try {
            long start3 = System.currentTimeMillis();
            trustedAuthority.sendParametersToCSP();
            long end3 = System.currentTimeMillis();
            System.out.println("向CSP发送掩码用时"+(end3-start3)+"ms");
            trustedAuthority.closeSocketCSP();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
