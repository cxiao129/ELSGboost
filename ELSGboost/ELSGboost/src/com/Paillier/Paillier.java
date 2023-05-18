package com.Paillier;



import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;


public class Paillier {

    /**
     * n = p*q, where p and q are two large primes.
     */
    public BigInteger n;
    /**
     * nsquare = n*n
     */
    public BigInteger nsquare;
    /**
     * p and q are two large primes. lambda = lcm(p-1, q-1) =
     * (p-1)*(q-1)/gcd(p-1, q-1).
     */
    private BigInteger p, q, lambda;
    /**
     * a random integer in Z*_{n^2} where gcd (L(g^lambda mod n^2), n) = 1.
     */
    private BigInteger g;
    /**
     * number of bits of modulus
     */
    private int bitLength;

    private BigInteger r;
    private BigInteger h;
    private BigInteger RandomR;
    private Queue<BigInteger> DecompH;


    /**
     * Constructs an instance of the Paillier cryptosystem.
     *
     * @param bitLengthVal number of bits of modulus
     * @param certainty    The probability that the new BigInteger represents a prime
     *                     number will exceed (1 - 2^(-certainty)). The execution time of
     *                     this constructor is proportional to the value of this
     *                     parameter.
     */
    public Paillier(int bitLengthVal, int certainty) {
        KeyGeneration(bitLengthVal, certainty);
    }

    /**
     * Constructs an instance of the Paillier cryptosystem with 512 bits of
     * modulus and at least 1-2^(-64) certainty of primes generation.
     */
    public Paillier() {
        KeyGeneration(1024, 64);
    }

    /**
     * main function
     *
     * @param str intput string
     */
    public static void main(String[] str) throws IOException {
//        /* instantiating an object of Paillier cryptosystem */
//        Paillier paillier = new Paillier();
//        /* instantiating two plaintext msgs */
//        BigInteger m1 = new BigInteger("20");
//        BigInteger m2 = new BigInteger("60");
//        /* encryption */
//        BigInteger em1 = paillier.Encryption(m1);
//        BigInteger em2 = paillier.Encryption(m2);
//        /* printout encrypted text */
//        System.out.println(em1);
//        System.out.println(em2);
//        /* printout decrypted text */
//        System.out.println(paillier.Decryption(em1).toString());
//        System.out.println(paillier.Decryption(em2).toString());
//
//        /*
//         * test homomorphic properties -> D(E(m1)*E(m2) mod n^2) = (m1 + m2) mod
//         * n
//         */
//        // m1+m2,求明文数值的和
//        BigInteger sum_m1m2 = m1.add(m2).mod(paillier.n);
//        System.out.println("original sum: " + sum_m1m2.toString());
//        // em1+em2，求密文数值的乘
//        BigInteger product_em1em2 = em1.multiply(em2).mod(paillier.nsquare);
//        System.out.println("encrypted sum: " + product_em1em2.toString());
//        System.out.println("decrypted sum: " + paillier.Decryption(product_em1em2).toString());
//
//        /* test homomorphic properties -> D(E(m1)^m2 mod n^2) = (m1*m2) mod n */
//        // m1*m2,求明文数值的乘
//        BigInteger prod_m1m2 = m1.multiply(m2).mod(paillier.n);
//        System.out.println("original product: " + prod_m1m2.toString());
//        // em1的m2次方，再mod paillier.nsquare
//        BigInteger expo_em1m2 = em1.modPow(m2, paillier.nsquare);
//        System.out.println("encrypted product: " + expo_em1m2.toString());
//        System.out.println("decrypted product: " + paillier.Decryption(expo_em1m2).toString());
//
//        System.out.println("--------------------------------");
//        //Paillier p = new Paillier();
//        BigInteger t0 = new BigInteger("0");
//        System.out.println(t0.toString());
//        BigInteger t1 = new BigInteger("1000");
//        System.out.println(t1.toString());
//        BigInteger t2 = new BigInteger("230");
//        System.out.println(t2.toString());
//        BigInteger t3 = new BigInteger("350");
//        System.out.println(t3.toString());
//
//        BigInteger et0 = paillier.Encryption(t1);
//        System.out.println(et0.toString());
//        BigInteger et1 = paillier.Encryption(t1);
//        System.out.println(et1.toString());
//        BigInteger et2 = paillier.Encryption(t2);
//        System.out.println(et2.toString());
//        BigInteger et3 = paillier.Encryption(t3);
//        System.out.println(et3.toString());
//
//        BigInteger sum = new BigInteger("1");
//        sum = paillier.cipher_add(sum, et1);
//        sum = paillier.cipher_add(sum, et2);
//        sum = paillier.cipher_add(sum, et3);
//        System.out.println("sum: " + sum.toString());
//        System.out.println("decrypted sum: " + paillier.Decryption(sum).toString());
//        System.out.println("--------------------------------");


        // 拆分 paillier 测试
        Paillier paillier = new Paillier(512, 64);
        paillier.splitBigInteger(3, paillier.n);
        BigInteger[] secretKey = new BigInteger[3];
        for (int i = 0; i < 3; i++) {
            BigInteger decompN = paillier.DecompH.poll();
            secretKey[i] = paillier.getRandomR().modPow(decompN, paillier.nsquare);
        }

        BigInteger r1 = new BigInteger(512 / 16, new Random());
        BigInteger c1 =  paillier.g.modPow(new BigInteger("-522"), paillier.nsquare).multiply(paillier.getH().
                modPow(r1, paillier.nsquare)).multiply(secretKey[0]).mod(paillier.nsquare);

        BigInteger r2 = new BigInteger(512 / 16, new Random());
        BigInteger c2 =  paillier.g.modPow(new BigInteger("-5"), paillier.nsquare).multiply(paillier.getH().
                modPow(r2, paillier.nsquare)).multiply(secretKey[1]).mod(paillier.nsquare);

        BigInteger r3 = new BigInteger(512 / 16, new Random());
        BigInteger c3 =  paillier.g.modPow(new BigInteger("-5"), paillier.nsquare).multiply(paillier.getH().
                modPow(r3, paillier.nsquare)).multiply(secretKey[2]).mod(paillier.nsquare);

        BigInteger c = c1.multiply(c2.multiply(c3)).mod(paillier.nsquare);

        BigInteger u1 = c.modPow(paillier.lambda, paillier.nsquare);
        BigInteger u = paillier.g.modPow(paillier.lambda, paillier.nsquare)
                .subtract(BigInteger.ONE).divide(paillier.n).modInverse(paillier.n);

        BigInteger m = (u1.subtract(BigInteger.ONE).divide(paillier.n))
                .multiply(u).mod(paillier.n).mod(paillier.r);

        if(m.multiply(new BigInteger("2")).compareTo(paillier.r)>=0){
            System.out.println( m.subtract(paillier.r) );
        }else{
            System.out.println(m);
        }

    }

    /**
     * Split Paillier secret key N to each data owners.
     *
     * @param n   the number of the data owners.
     * @param sum Decomposition value.
     */
    public void splitBigInteger(int n, BigInteger sum) {
        DecompH = new ArrayDeque<>();
        List<BigInteger> list = new ArrayList<>();
        list.add(BigInteger.ZERO);
        list.add(sum);
        for (int i = 0; i < n - 1; i++) {
            list.add(new BigInteger(sum.bitLength(), new SecureRandom()).mod(sum));
        }
        Collections.sort(list);
        for (int i = 0; i < n; i++) {
            this.DecompH.offer(list.get(i + 1).subtract(list.get(i)));
        }
    }

    public BigInteger getRandomR() {
        return RandomR;
    }

    /**
     * Sets up the public key and private key.
     *
     * @param bitLengthVal number of bits of modulus.
     * @param certainty    The probability that the new BigInteger represents a prime
     *                     number will exceed (1 - 2^(-certainty)). The execution time of
     *                     this constructor is proportional to the value of this
     *                     parameter.
     */
    public void KeyGeneration(int bitLengthVal, int certainty) {
        bitLength = bitLengthVal;

        /*
         * Constructs two randomly generated positive BigIntegers that are
         * probably prime, with the specified bitLength and certainty.
         */
        p = new BigInteger(bitLength / 2, certainty, new Random());
        q = new BigInteger(bitLength / 2, certainty, new Random());
        RandomR = new BigInteger(bitLength / 16, certainty, new Random());

        n = p.multiply(q);
        nsquare = n.multiply(n);

        g = new BigInteger("2");
        lambda = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE))
                .divide(p.subtract(BigInteger.ONE).gcd(q.subtract(BigInteger.ONE)));
        /* check whether g is good. */
        if (g.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).gcd(n).intValue() != 1) {
            System.out.println("g is not good. Choose g again.");
            System.exit(1);
        }

        r = new BigInteger(bitLength / 16, certainty, new Random());
        h = g.modPow(r, nsquare);
    }

    public void setNsquare(BigInteger Nsquare) {
        this.nsquare = Nsquare;
    }

    public BigInteger getG() {
        return g;
    }

    public void setG(BigInteger paramG) {
        this.g = paramG;
    }

    public BigInteger getH() {
        return h;
    }

    public void setH(BigInteger paramH) {
        this.h = paramH;
    }

    public BigInteger getLambda() {
        return lambda;
    }

    public BigInteger getR() {
        return r;
    }

    /**
     * Encrypts plaintext m. ciphertext c = g^m * r^n mod n^2. This function
     * explicitly requires random input r to help with encryption.
     *
     * @param m plaintext as a BigInteger
     * @param r random plaintext to help with encryption
     * @return ciphertext as a BigInteger
     */
    public BigInteger Encryption(BigInteger m, BigInteger r) {
        return g.modPow(m, nsquare).multiply(r.modPow(n, nsquare)).mod(nsquare);
    }

    /**
     * Encrypts plaintext m. ciphertext c = g^m * r^n mod n^2. This function
     * automatically generates random input r (to help with encryption).
     *
     * @param m plaintext as a BigInteger
     * @return ciphertext as a BigInteger
     */
    public BigInteger Encryption(BigInteger m) {
        BigInteger r = new BigInteger(bitLength, new Random());
        return g.modPow(m, nsquare).multiply(r.modPow(n, nsquare)).mod(nsquare);
    }

    /**
     * Decrypts ciphertext c. plaintext m = L(c^lambda mod n^2) * u mod n, where
     * u = (L(g^lambda mod n^2))^(-1) mod n.
     *
     * @param c ciphertext as a BigInteger
     * @return plaintext as a BigInteger
     */
    public BigInteger Decryption(BigInteger c) {
        BigInteger u = g.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).modInverse(n);
        return c.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).multiply(u).mod(n);
    }

    /**
     * sum of (cipher) em1 and em2
     *
     * @param em1
     * @param em2
     * @return
     */
    public BigInteger cipher_add(BigInteger em1, BigInteger em2) {
        return em1.multiply(em2).mod(nsquare);
    }
}

