/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the unranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {
    private double k_1; 
    private double k_3;
    private double b;

    //public RetrievalModelBM25(String k_1, String b, String k_3) throws IOException {
    //    this.k_1 = Double.valueOf(k_1);
    //    this.k_3 = Double.valueOf(k_3);
    //    this.b = Double.valueOf(b);
    //}

    public void setParameters(String k_1,String b, String k_3) {
        this.k_1 = Double.valueOf(k_1);
        this.k_3 = Double.valueOf(k_3);
        this.b = Double.valueOf(b);
    }

    public double getk_1() {
        return this.k_1; 
    }
    public double getk_3() {
        return this.k_3; 
    }
    public double getb() {
        return this.b; 
    }

    public String defaultQrySopName () {
        return new String ("#sum");
    }

}
