/** 
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */
import java.io.*;
import java.util.*;

public abstract class QrySopW extends QrySop {
	public ArrayList<Double> weights;
    public double weightSum;
	
	public void setWeights(ArrayList<Double> weights) {
		this.weights = weights;
		this.weightSum = 0.0;

		for (int i=0; i<weights.size(); i++) {
			this.weightSum += weights.get(i);
			System.out.print(weights.get(i)+" + ");
		}
		System.out.print("= "+weightSum+"\n");
	}

}
