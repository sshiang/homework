/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import static java.lang.Math.*;

/**
 *  An Indri DocVector-style interface for the Lucene termvector.
 *  There are three main data structurs:
 *  <pre>
 *    stems:      The field's vocabulary.  The 0'th entry is an empty string.
 *                It indicates a stopword.
 *    stemsFreq:  The frequency (tf) of each entry in stems.
 *    positions:  The index of the stem that occurred at this position. 
 *  </pre>
 */
public class FeatureVector {

	// relMap: answer of the ranking, docid: relevance
	public HashMap<Integer, Integer> relMap = new HashMap<Integer, Integer>(); 
	// feaMap: feature map: docid: 
	//public LinkedHashMap<Integer, LinkedHashMap<String, Double> > feaMap = new LinkedHashMap<Integer, LinkedHashMap<String, Double> >();

    public HashMap<String, ArrayList<Double>> minMap = new HashMap<String, ArrayList<Double>>();
    public HashMap<String, ArrayList<Double>> maxMap = new HashMap<String, ArrayList<Double>>(); 

	public ArrayList<Double> minArray;
	public ArrayList<Double> maxArray;

	/*
	public void setValueMap() {
		this.minMap = new HashMap<String, Double>();
        this.maxMap = new HashMap<String, Double>();
        for (int i=1; i<=18; i++){//TODO better way...
            this.minMap.set(i, Double.MAX_VALUE);
            this.maxMap.set(i, -Double.MAX_VALUE);
        }
	}
	*/

	public double getOverlapScore(String[] qstems, TermVector vec) throws IOException {
		double totalScore = 0;
		int totalMatch = 0;
		for (String stem : qstems) {
			int index = -1;
			try { 
				index = vec.indexOfStem(stem);
			} catch (Exception ex) {}

			if (index != -1) {
				totalMatch++;
			}
		}
		if (qstems.length > 0) {
			totalScore = totalMatch / (double) qstems.length;
		}

		return totalScore;
	}


	public double getBM25Score(RetrievalModelBM25 model, int docid, String field, String[] qstems, TermVector vec) throws IOException {

		double totalScore=0;
		double k_1 = ((RetrievalModelBM25) model).getk_1();
        double b = ((RetrievalModelBM25) model).getb();
        double k_3 = ((RetrievalModelBM25) model).getk_3();
		double doclen = (double)(Idx.getFieldLength(field, docid));
		double avgLength = Idx.getSumOfFieldLengths(field) / (double) Idx.getDocCount(field);
        double userWeight = 1.0; 

		for (String stem : qstems) {
			double score = 0.0;
			int index = -1;		
			try {
				index = vec.indexOfStem(stem);
			} catch (Exception ex) {
				continue;
			}
			if (index == -1) {
				continue;
			}
			int tf = vec.stemFreq(index);
            int df = vec.stemDf(index);
			double idf = Math.log10((Idx.getNumDocs() - (double)df + 0.5) / ((double)df + 0.5));
			double tfWeight = tf*(k_1+1.0) / (tf + k_1 * (1.0-b+b*(doclen/avgLength) ) );

			score = idf * tfWeight * ( (k_3 + 1.0)*userWeight / (k_3+userWeight) );
			totalScore += score; 
		}
		return totalScore;
	}


	public double indriScoreFunc(double mu, double lambda, double tf, double mle, double docLength) {
        return (1-lambda)*(((double)tf+(mu*mle))/(docLength+mu))+lambda*mle;
    }

	public double getIndriScore(RetrievalModelIndri model, int docid, String field, String[] qstems, TermVector vec) throws IOException {
		double totalScore = 1.0;
		double mu = model.getMu(); 
		double lambda = model.getLambda(); 
		boolean isMatch = false;	
		double docLength = (double)(Idx.getFieldLength(field, docid));

		for (String stem : qstems) { 

			int index = -1;		
			int tf=0;
			double mle = (double)(Idx.getTotalTermFreq(field, stem)) / (double)(Idx.getSumOfFieldLengths(field));
			try {
				index = vec.indexOfStem(stem);
			} catch (Exception ex) {
				index = -1;
			}

			if (index == -1) {
				tf = 0;
			} else {
				tf = vec.stemFreq(index);
				isMatch = true;
			}

			double score = indriScoreFunc(mu, lambda, (double)tf, mle, docLength);

			totalScore *= score; 
		}

		return (isMatch ? Math.pow(totalScore, 1.0/(double)(qstems.length)) : 0 );
	}


	public void setMinMaxMap() {
		this.minArray = new ArrayList<Double>();// HashMap<String, Double>();
        this.maxArray = new ArrayList<Double>(); //HashMap<String, Double>();
        for (int i=1; i<=19; i++){//TODO better way...
            this.minArray.add(Double.MAX_VALUE);
            this.maxArray.add(-Double.MAX_VALUE);
        }
	}

	public void setMinMaxValue(int feaid, double score) {
		this.minArray.set(feaid, min(this.minArray.get(feaid), score));
        this.maxArray.set(feaid, max(this.maxArray.get(feaid), score));
	}

	//ArrayList<Integer>
	public LinkedHashMap<Integer, LinkedHashMap<String, Double>> getFeatureVector(String qid, String qString, HashMap<Integer, Integer> relMap, RetrievalModelBM25 model_bm25, RetrievalModelIndri model_indri, HashMap<String, Double> pageRankMap, ArrayList<String> featureDisable) throws Exception {		

		System.out.println("qid: "+qid);
		setMinMaxMap(); 
		LinkedHashMap<Integer, LinkedHashMap<String, Double>> feaMap = new LinkedHashMap<Integer, LinkedHashMap<String, Double>>();

		// get tokens
		//HashMap<String, Boolean> tokenMap = new HashMap<String, Boolean>(); 
		String[] tokens = QryParser.tokenizeString(qString);
		//for (int i=0; i<tokens.length; i++) {
		//	tokenMap.put(tokens[i], true);
		//}

		String[] fields = {"body", "title", "url", "inlink"};
		LinkedHashMap<Integer, Boolean> docMap = new LinkedHashMap<Integer, Boolean>(); 
		// use bm25 body top 100 

		if (relMap == null) { // test phrase
			ScoreList r_bm25 = QryEval.processQuery(qString, "body", model_bm25);
			r_bm25.sort();
			for (int i = 0; i < Math.min(100,r_bm25.size()); i++) {
				int docid = r_bm25.getDocid(i);
				docMap.put(docid, true); 
			}
		} else { // train phrase
			for (int docid : relMap.keySet()) {
				docMap.put(docid, true);
			}
		}

		// start generating features... 
		for (int docid : docMap.keySet()) { 
			LinkedHashMap<String, Double> fMap = new LinkedHashMap<String, Double>();  
	
			// f1: Spam score for d (read from index). 
			// int spamScore = Integer.parseInt (Idx.getAttribute ("score", docid));
			if (!(featureDisable.contains("1"))) {
				int spamScore = Integer.parseInt (Idx.getAttribute("score", docid));
				fMap.put("1", (double)spamScore); 
				setMinMaxValue(1, spamScore);
			}

			// f2: Url depth for d(number of '/' in the rawUrl field). 
			// Hint: The raw URL is stored in your index as the rawUrl attribute. 
			if (!(featureDisable.contains("2"))) {
				String rawUrl = Idx.getAttribute ("rawUrl", docid);
				int urlScore = rawUrl.length() - rawUrl.replace("/", "").length();
				fMap.put("2", (double)urlScore); 
				setMinMaxValue(2, urlScore);
			}

			// f3: FromWikipedia score for d (1 if the rawUrl contains "wikipedia.org", otherwise 0).
			if (!(featureDisable.contains("3"))) {
				int wikiScore = 0;
				String rawUrl = Idx.getAttribute ("rawUrl", docid);
				if (rawUrl.toLowerCase().contains("wikipedia.org".toLowerCase())) {
					wikiScore = 1;
				}
				fMap.put("3", (double)wikiScore);  
				setMinMaxValue(3, wikiScore);	
			}

			// f4: PageRank score for d (read from file).
			if (!(featureDisable.contains("4"))) {
				String ext_doc= Idx.getExternalDocid(docid);
				double pageRankScore = 0.0;

				//pageRankMap.get(ext_doc);
				
				try {  
					pageRankScore=pageRankMap.get(ext_doc); 
				} catch (Exception ex){}

				fMap.put("4", (double)pageRankScore); 
				setMinMaxValue(4, pageRankScore);
			}

			for (int j=0; j<fields.length; j++) {
				TermVector vec = new TermVector(docid, fields[j]);

				// f5: BM25 score for <q, dbody>.
				// f8: BM25 score for <q, dtitle>.
				// f11: BM25 score for <q, durl>.
				// f14: BM25 score for <q, dinlink>.
				if (!(featureDisable.contains(Integer.toString(4+j*3+1)))) {
					double scoreBM25 = getBM25Score(model_bm25, docid, fields[j], tokens, vec); // 0.0;
					fMap.put(Integer.toString(4+j*3+1), (double)scoreBM25); 
					setMinMaxValue(4+j*3+1, scoreBM25);
				}

				// f6: Indri score for <q, dbody>.
				// f9: Indri score for <q, dtitle>.
				// f12: Indri score for <q, durl>.
				// f15: Indri score for <q, dinlink>.
				if (!(featureDisable.contains(Integer.toString(4+j*3+2)))) {
					double scoreIndri = getIndriScore(model_indri, docid, fields[j], tokens, vec); 
					fMap.put(Integer.toString(4+j*3+2), (double)scoreIndri); 
					setMinMaxValue(4+j*3+2, scoreIndri);
				}

				// f7: Term overlap score for <q, dbody>. 
				// f10: Term overlap score for <q, dtitle>.
				// f13: Term overlap score for <q, durl>.
				// Hint: Term overlap is defined as the percentage of query terms that match the document field.
				// f16: Term overlap score for <q, dinlink>.
				if (!(featureDisable.contains(Integer.toString(4+j*3+3)))) {
					double overlapScore = getOverlapScore(tokens, vec); //0.0; 
					fMap.put(Integer.toString(4+j*3+3), overlapScore);
					setMinMaxValue(4+j*3+3, overlapScore);
				}						
			}
		
			// f17: A custom feature - document length
			if (!(featureDisable.contains("17"))) {
				double lengthScore = (double) Idx.getFieldLength("body", docid);
				fMap.put("17", lengthScore);
				setMinMaxValue(17, lengthScore);
			}

			// f18: A custom feature - range of query term 
			if (!(featureDisable.contains("18"))) {
				TermVector vec = new TermVector(docid, "body");
				int minVal = Integer.MAX_VALUE;
				int maxVal = 0;
				for (String term : tokens) {
					int index = 0;
					try {
						index = vec.indexOfStem(term);
						int freq = vec.stemFreq(index);
						minVal = min(minVal, freq);
						maxVal = max(maxVal, freq);	
					} catch (Exception ex) { }
				}
				double rangeScore = (double)(maxVal-minVal);
				fMap.put("18", rangeScore);
				setMinMaxValue(18, rangeScore);
			}

			feaMap.put(docid, fMap);
		}
		// end of a query
		this.minMap.put(qid, this.minArray);
		this.maxMap.put(qid, this.maxArray);	
		return feaMap;
	}
	
}
