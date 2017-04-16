/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the unranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {
    private double lambda; 
    private double mu;

    public void setParameters(String lambda ,String mu) {
        this.lambda = Double.valueOf(lambda);
        this.mu = Double.valueOf(mu);
    }

    public double getLambda() {
        return this.lambda; 
    }

    public double getMu() {
        return this.mu; 
    }

    public String defaultQrySopName () {
        return new String ("#and");
    }

}
