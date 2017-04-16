/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the unranked Boolean
 *  retrieval m (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */

import java.util.*;
import java.io.*;
import static java.lang.Math.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class RetrievalModelLetor extends RetrievalModel {

	private String trainingQrelsFile;
	private String trainingQueryFile;
	private String trainingFeatureVectorsFile;
	private String pageRankFile;
	private String svmRankLearnPath;
	private String svmRankClassifyPath;
	private double svmRankParamC;
	private String svmRankModelFile;
	private String testingFeatureVectorsFile;
	private String testingDocumentScores;
	private ArrayList<String> featureDisable; 

	public HashMap<String, Double> pageRankMap; 
	public HashMap<String, LinkedHashMap<Integer, Integer>> relevanceMap; 

	public RetrievalModelIndri model_indri;
	public RetrievalModelBM25 model_bm25; 

	public HashMap<String, ArrayList<Double>> minMap; //= new HashMap<String, Double>();
    public HashMap<String, ArrayList<Double>> maxMap; // = new HashMap<String, Double>();
	//public ArrayList<Double> minMap;
	//public ArrayList<Double> maxMap;

	public FeatureVector featureVector; // = new FeatureVector();


	public void initializeMap() {
		this.minMap = new HashMap<String, ArrayList<Double>>();
		this.maxMap = new HashMap<String, ArrayList<Double>>();
	}

	public void setModelParameters(RetrievalModelBM25 model_bm25, RetrievalModelIndri model_indri) {
		this.model_bm25 = model_bm25;
		this.model_indri = model_indri;
	}

	// qid: doc: rel
	public void readRelevance() throws Exception {
		//HashMap<String, HashMap<String, Integer>> relevance = new HashMap<String, HashMap<String, Integer>>();
		// answer map qid:docid:rel
		System.out.println("read relevance file for training data...");
		this.relevanceMap = new HashMap<String, LinkedHashMap<Integer, Integer>>();

		BufferedReader input = null;
		try {
			String qLine = null;
			input = new BufferedReader(new FileReader(this.trainingQrelsFile));
			LinkedHashMap<Integer, Integer> relMap = new LinkedHashMap<Integer, Integer>();
			
			String qid_prev = ""; 
			while ((qLine = input.readLine()) != null) {
				try {
					String eles[] = qLine.trim().split(" ");
					String qid = eles[0];
					String ext_docid = eles[2].trim();
					//System.out.println(ext_docid);
					int docid = Idx.getInternalDocid(ext_docid); 
					int rel = Integer.parseInt(eles[3]);
					if (!(qid.equals(qid_prev)) && !(qid_prev.equals(""))){
						this.relevanceMap.put(qid_prev, relMap);
						relMap = new LinkedHashMap<Integer, Integer>();
					}
					relMap.put(docid,rel);
					qid_prev = qid; 
				} catch(Exception ex) {
					continue;
				}
			}
			this.relevanceMap.put(qid_prev, relMap);
			//return relevance;

		} catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
        } 
	}


	public void readPageRank() throws Exception {
		// [line:] external_id	score
		// HashMap<String, Double> sMap = new HashMap<String, Double>(); 
		System.out.println("read pagerank ... ");
		this.pageRankMap = new HashMap<String, Double>();
		BufferedReader input = null;
		try {
			String qLine = null;
			input = new BufferedReader(new FileReader(this.pageRankFile));
			while ((qLine = input.readLine()) != null) {
				//try{ 
				String[] parts = qLine.split("\t");
				String doc = parts[0]; 
				//int docid = Idx.getInternalDocid(doc);
				double score = (double)(Float.parseFloat(parts[1]));
				this.pageRankMap.put(doc, score); 
				//} catch (Exception ex) {
				//	continue;
				//}
			}
		} catch (IOException ioex) {
			ioex.printStackTrace();
		} finally {
			input.close();
		}
		//return sMap; 
	}

	//ArrayList<Integer>
	public  LinkedHashMap<Integer, LinkedHashMap<String, Double>> constructFeatureVector(String qid, String qString, String phrase) throws Exception {
		//FeatureVector featureVector = new FeatureVector(); 
		ArrayList<Integer> docList = new ArrayList<Integer>();
		// qid: docid: feaid: fea;
		LinkedHashMap<Integer, LinkedHashMap<String, Double>> feaMap = new LinkedHashMap<Integer, LinkedHashMap<String, Double>>();  
		// output feature vector 
		if (phrase.equals("train")) {
			feaMap = this.featureVector.getFeatureVector(qid, qString, this.relevanceMap.get(qid), this.model_bm25, this.model_indri, this.pageRankMap, this.featureDisable);
		} else {
			feaMap = this.featureVector.getFeatureVector(qid, qString, null, this.model_bm25, this.model_indri, this.pageRankMap, this.featureDisable);
		}

		return feaMap; 
	}

	public ArrayList<Double> readReuslt() throws Exception { // ArrayList<String> docList) {
		//ScoreList r = new ScoreList(); 
		String qLine = null;
		BufferedReader input = null;
		ArrayList<Double> scoreList = new ArrayList<Double>(); 
		try {
			input = new BufferedReader(new FileReader(this.testingDocumentScores));
			//int count = 0;
			while ((qLine = input.readLine()) != null) {
				double score = Double.parseDouble(qLine); 
				scoreList.add(score);
				//String doc_id= docList.get(count);
				//r.put(Idx.getInternalDocid(doc_id), score);
				//count += 1;	
			}
			// TODO check doc size matching the length of scores			
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			input.close();
		}
		return scoreList; 
	}


	public void writeFeatures(String qid, int docid, OutputStreamWriter output, int rel, HashMap<String, Double>fMap) throws Exception {
			
		String res = String.format("%d qid:%s ", rel, qid); 
		for (String feaid : fMap.keySet()) {
			res += String.format("%s:%f ", feaid, fMap.get(feaid));
		}
		res += String.format("#%s\n", Idx.getExternalDocid(docid));
		//System.out.println(res);
		output.write(res); 
		output.flush();
	}

	public void train() throws Exception {
		// read label of training data:
		readRelevance();
		readPageRank(); 
		// extract training features: 
		this.featureVector = new FeatureVector();
		//this.featureVector.setMinMaxMap();

		System.out.println("extracting features...");

		File fout = new File(this.trainingFeatureVectorsFile);
		FileOutputStream fos = new FileOutputStream(fout);
		OutputStreamWriter output = new OutputStreamWriter(fos);

		LinkedHashMap<String, LinkedHashMap<Integer, LinkedHashMap<String, Double>>> featureMap = new LinkedHashMap<String, LinkedHashMap<Integer, LinkedHashMap<String, Double>>>(); 

		// for normalization: remember min and max of each feature...
		//this.minMap = new HashMap<String, ArrayList<Double>>();
		//this.maxMap = new HashMap<String, ArrayList<Double>>();
		initializeMap();

		//this.minArray = new ArrayList<Double>();
		//this.maxArray = new ArrayList<Double>(); 
		//for (int i=1; i<=18; i++){//TODO better way...
		//	this.minMap.put(Integer.toString(i), Double.MAX_VALUE);
		//	this.maxMap.put(Integer.toString(i), -Double.MAX_VALUE);
		//}

		// read query per line
		BufferedReader input = null;
		try { 
			String qLine = null;
			input = new BufferedReader(new FileReader(this.trainingQueryFile));
			while ((qLine = input.readLine()) != null) { 
				int d = qLine.indexOf(':');

				if (d < 0) {
					throw new IllegalArgumentException
					("Syntax error:  Missing ':' in query line.");
				}

				String qid = qLine.substring(0, d);
				String qString = qLine.substring(d + 1);

				//this.minArray = new ArrayList<Double>();
				//this.maxArray = new ArrayList<Double>();

				LinkedHashMap<Integer, LinkedHashMap<String, Double>> feaMap = constructFeatureVector(qid, qString, "train");
				featureMap.put(qid, feaMap);

				ArrayList<Double> minList = new ArrayList<Double>();
				ArrayList<Double> maxList = new ArrayList<Double>();
				for (int i=1; i<=19; i++){
					minList.add(Double.MAX_VALUE);
					maxList.add(-Double.MAX_VALUE);
				}

				for (int docid : feaMap.keySet()) {
					for (String feaid: feaMap.get(docid).keySet()) {
						int feaid_int = Integer.parseInt(feaid); 
						minList.set(feaid_int, min(minList.get(feaid_int), feaMap.get(docid).get(feaid)));
						maxList.set(feaid_int, max(maxList.get(feaid_int), feaMap.get(docid).get(feaid)));
						//this.minMap.put(feaid, min(this.minMap.get(feaid), feaMap.get(docid).get(feaid)));
						//this.maxMap.put(feaid, max(this.maxMap.get(feaid), feaMap.get(docid).get(feaid)));
					}
				}
				
				this.minMap.put(qid, minList); //p = this.featureVector.minMap;
				this.maxMap.put(qid, maxList); // = this.featureVector.maxMap;
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			input.close();
		}

		// normalization
		// write training data for rank svm 
		for (String qid: featureMap.keySet()) {
			for (int docid : featureMap.get(qid).keySet()) {
				for (String feaid: featureMap.get(qid).get(docid).keySet()) {
					double score = featureMap.get(qid).get(docid).get(feaid); 
					double normalizedScore = 0.0;
					int feaid_int = Integer.parseInt(feaid);
					if ((this.maxMap.get(qid).get(feaid_int)!=this.minMap.get(qid).get(feaid_int))) {
						normalizedScore = (score-this.minMap.get(qid).get(feaid_int))/(this.maxMap.get(qid).get(feaid_int)-this.minMap.get(qid).get(feaid_int));
					}
					featureMap.get(qid).get(docid).put(feaid, normalizedScore);
				}
				// write 
				writeFeatures(qid, docid, output, relevanceMap.get(qid).get(docid), featureMap.get(qid).get(docid));
			}
		}


		// run rank svm script
		String command = String.format("%s -c %f %s %s", this.svmRankLearnPath, this.svmRankParamC, this.trainingFeatureVectorsFile, this.svmRankModelFile);
		execute(command);
	}

	public void test() throws Exception {
		// run rank svm script
		String command = String.format("%s %s %s %s", this.svmRankClassifyPath, this.testingFeatureVectorsFile, this.svmRankModelFile, this.testingDocumentScores);
		System.out.println(command);

		execute(command);
	}
	

	public void execute(String command) {
		StringBuffer output = new StringBuffer();
        Process p;
        try {
            p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader =
                            new BufferedReader(new InputStreamReader(p.getInputStream()));

                        String line = "";
            while ((line = reader.readLine())!= null) {
                System.out.println(line);
                output.append(line + "\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
	}


    public void setParameters(String trainingQrelsFile, String trainingQueryFile, String trainingFeatureVectorsFile, String featureDisable, String pageRankFile, String svmRankLearnPath, String svmRankClassifyPath, String svmRankParamC, String svmRankModelFile, String testingFeatureVectorsFile, String testingDocumentScores) throws Exception {
		this.trainingQrelsFile = trainingQrelsFile;
		this.trainingQueryFile = trainingQueryFile;
		this.trainingFeatureVectorsFile = trainingFeatureVectorsFile;
		this.pageRankFile = pageRankFile;
		this.svmRankLearnPath = svmRankLearnPath;
		this.svmRankClassifyPath = svmRankClassifyPath;
		this.svmRankParamC = Double.valueOf(svmRankParamC);
		this.svmRankModelFile = svmRankModelFile;
		this.testingFeatureVectorsFile = testingFeatureVectorsFile;
		this.testingDocumentScores = testingDocumentScores;
		this.featureDisable = new ArrayList<String>(Arrays.asList(featureDisable.split(","))); //featureDisable.split(","); 
    }

    public String getTrainingQrelsFile() {
        return this.trainingQrelsFile; 
    }

    public String getTrainingQueryFile() {
        return this.trainingQueryFile;
    }

    public String getTrainingFeatureVectorsFile() {
        return this.trainingFeatureVectorsFile;
    }

    public String getPageRankFile() {
        return this.pageRankFile;
    }

    public String getSvmRankLearnPath() {
        return this.svmRankLearnPath;
    }

    public String getSvmRankClassifyPath() {
        return this.svmRankClassifyPath;
    }

    public double getSvmRankParamC() {
        return this.svmRankParamC;
    }

    public String getSvmRankModelFile() {
        return this.svmRankModelFile;
    }

    public String getTestingFeatureVectorsFile() {
        return this.testingFeatureVectorsFile;
    }

    public String getTestingDocumentScores() {
        return this.testingDocumentScores;
    }

	public ArrayList<String> getFeatureDisable() {
		return this.featureDisable;
	}

    public String defaultQrySopName () {
        return new String ("#and");
    }

}
