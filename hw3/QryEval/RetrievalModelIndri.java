/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the unranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */

import java.util.*;
import java.io.*;


public class RetrievalModelIndri extends RetrievalModel {
    private double lambda; 
    private double mu;
	private Boolean fb = false;
	private int fbDocs=0;
	private int fbTerms=0;
	private double fbMu=0.0;
	private double fbOrigWeight=0.0;
	private String fbExpansionQueryFile="";
	private String fbInitialRankingFile="";
	private HashMap<String, ScoreList> scoreListMap;

	/*
	public void readRankingList(String filename) {
		File documentFile = new File(filename);
		ScoreList scoreList = new ScoreList();
		this.scoreListMap = new HashMap<>();		
		Scanner scan = new Scanner(documentFile);

		String line = null;
		String qid_now = "-1";
		while (scan.hasNext()) {
			line = scan.nextLine();
			String[] elements = line.split(" ");
			String qid = elements[0];
			if ( !(qid.equals(qid_now)) && !(qid_now.equals("-1"))) {
				this.scoreListMap.put(qid, scoreList);   
				scoreList = new ScoreList();
			}  
			String doc_id = elements[2];
			int rank = Integer.parseInt(elements[3]);
			double score = Double.parseDouble(elements[4]);
			scoreList.add(Idx.getInternalDocid(doc_id), score);
			qid_now = qid; 
		}   
	}
	*/

	public void setFbParameters(String fbDocs, String fbTerms, String fbMu, String fbOrigWeight, String fbExpansionQueryFile, String fbInitialRankingFile) {
		this.fb = true; 
		this.fbDocs = Integer.parseInt(fbDocs);
		this.fbTerms = Integer.parseInt(fbTerms);
		this.fbMu = Double.parseDouble(fbMu);
		this.fbOrigWeight = Double.parseDouble(fbOrigWeight);
		this.fbExpansionQueryFile = fbExpansionQueryFile; 
		this.fbInitialRankingFile = fbInitialRankingFile;
		//if (!fbExpansionQueryFile.equals("")){
		//	readRankingList(fbExpansionQueryFile);
		//}
	}
 
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

	public boolean getFb() {
		return this.fb;
	}

	public int getFbDocs() {
		return this.fbDocs;
	}

	public int getFbTerms() {
		return this.fbTerms;
	}

	public double getFbMu() {
		return this.fbMu;
	}

	public double getFbOrigWeight() {
		return this.fbOrigWeight;
	}
	
	public String getFbExpansionQueryFile() {
		return this.fbExpansionQueryFile; 
	}
	
	public String getFbInitialRankingFile() {
		return this.fbInitialRankingFile; 
	}
}
