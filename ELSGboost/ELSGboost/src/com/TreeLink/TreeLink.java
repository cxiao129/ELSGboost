package com.TreeLink;

import java.math.BigInteger;

public class TreeLink {
    public TreeLink leftNode;	//左子树
    public TreeLink rightNode;	//右子树
    public String feature = new String();
    public double threshold;
    public double weight; //权重
    public boolean leafnode = false;  //是否是叶子节点
    public TreeLink(String feature, double threshold, TreeLink leftNode,
                    TreeLink rightNode, double weight) {
        this.feature = feature;
        this.threshold = threshold;
        this.leftNode = leftNode;
        this.rightNode = rightNode;
        this.weight = weight;
    }
}
