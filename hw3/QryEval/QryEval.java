/*
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.1.2.
 */
import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 *  This software illustrates the architecture for the portion of a
 *  search engine that evaluates queries.  It is a guide for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 */
public class QryEval {

  //  --------------- Constants and variables ---------------------

  private static final String USAGE =
    "Usage:  java QryEval paramFile\n\n";

  private static final String[] TEXT_FIELDS =
    { "body", "title", "url", "inlink" };



  //  --------------- Methods ---------------------------------------

  /**
   *  @param args The only argument is the parameter file name.
   *  @throws Exception Error accessing the Lucene index.
   */
  public static void main(String[] args) throws Exception {

    //  This is a timer that you may find useful.  It is used here to
    //  time how long the entire program takes, but you can move it
    //  around to time specific parts of your code.
    
    Timer timer = new Timer();
    timer.start ();

    //  Check that a parameter file is included, and that the required
    //  parameters are present.  Just store the parameters.  They get
    //  processed later during initialization of different system
    //  components.

    if (args.length < 1) {
      throw new IllegalArgumentException (USAGE);
    }

    Map<String, String> parameters = readParameterFile (args[0]);

    //  Open the index and initialize the retrieval model.
    Idx.open (parameters.get ("indexPath"));
    RetrievalModel model = initializeRetrievalModel (parameters);
	
    //  Perform experiments.    
    processQueryFile(parameters.get("queryFilePath"), parameters.get("trecEvalOutputPath"), model);

    //  Clean up.
    timer.stop ();
    System.out.println ("Time:  " + timer);
  }

  /**
   *  Allocate the retrieval model and initialize it using parameters
   *  from the parameter file.
   *  @return The initialized retrieval model
   *  @throws IOException Error accessing the Lucene index.
   */
  private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
    throws IOException {

    RetrievalModel model = null;
    String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

    if (modelString.equals("unrankedboolean")) {
      model = new RetrievalModelUnrankedBoolean();
    }
    else if (modelString.equals("rankedboolean")) {
	  model = new RetrievalModelRankedBoolean();
	} else if (modelString.equals("bm25") ) {

      String k_1 = parameters.get("BM25:k_1");
      String b = parameters.get("BM25:b");
      String k_3 = parameters.get("BM25:k_3");
      model = new RetrievalModelBM25(); 
      ((RetrievalModelBM25) model).setParameters(k_1, b, k_3);
    } else if (modelString.equals("indri") ) {
      String mu = parameters.get("Indri:mu");
      String lambda = parameters.get("Indri:lambda");
	  String fbDocs = parameters.get("fbDocs"); 
	  String fbTerms = parameters.get("fbTerms"); 
	  String fbMu = parameters.get("fbMu"); 
	  String fbOrigWeight = parameters.get("fbOrigWeight");
	  String fbExpansionQueryFile = "quries.expansion.txt";
	  if (parameters.containsKey("fbExpansionQueryFile")) {
		fbExpansionQueryFile = parameters.get("fbExpansionQueryFile");	
	  }
	  String fbInitialRankingFile = "";
	  if (parameters.containsKey("fbInitialRankingFile")) {
		fbInitialRankingFile = parameters.get("fbInitialRankingFile");
	  }

      model = new RetrievalModelIndri();
	  ((RetrievalModelIndri) model).setParameters(lambda, mu);
	  if (parameters.containsKey("fb")) {
		((RetrievalModelIndri) model).setFbParameters(fbDocs, fbTerms, fbMu, fbOrigWeight, fbExpansionQueryFile, fbInitialRankingFile);
	  }

	} else { 
      throw new IllegalArgumentException
        ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
    }
      
    return model;
  }

  /**
   * Print a message indicating the amount of memory used. The caller can
   * indicate whether garbage collection should be performed, which slows the
   * program but reduces memory usage.
   * 
   * @param gc
   *          If true, run the garbage collector before reporting.
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc)
      runtime.gc();

    System.out.println("Memory used:  "
        + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
  }

  /**
   * Process one query.
   * @param qString A string that contains a query.
   * @param model The retrieval model determines how matching and scoring is done.
   * @return Search results
   * @throws IOException Error accessing the index
   */
  static ScoreList processQuery(String qString, RetrievalModel model)
    throws IOException {

    String defaultOp = model.defaultQrySopName ();
    qString = defaultOp + "(" + qString + ")";
    Qry q = QryParser.getQuery (qString);

    // Show the query that is evaluated
    
    System.out.println("    --> " + q);
    
    if (q != null) {

      ScoreList r = new ScoreList ();
      
      if (q.args.size () > 0) {		// Ignore empty queries

        q.initialize (model);

        while (q.docIteratorHasMatch (model)) {
          int docid = q.docIteratorGetMatch ();
          double score = ((QrySop) q).getScore (model);
          r.add (docid, score);
          q.docIteratorAdvancePast (docid);
        }
      }

      return r;
    } else
      return null;
		   
  }

  static String processFbQuery(String qid, String qString, ScoreList r, RetrievalModelIndri model) throws IOException {

	HashMap<String, Double> map = new HashMap<String, Double>();

	for (int i=0; i<Math.min(r.size(), model.getFbDocs() ); i++) {
		TermVector tmpVector = new TermVector(r.getDocid(i), "body");
		for (int j=1; j<tmpVector.stemsLength(); j++) {
			String term = tmpVector.stemString(j);
			map.put(term,0.0);
		}
	}


	for (int i=0; i<Math.min(r.size(), model.getFbDocs() ); i++) {
		// indri score of each term in the document.
		TermVector tmpVector = new TermVector(r.getDocid(i), "body");
		double docScore = r.getDocidScore(i);
        double docLen = (double)(Idx.getFieldLength("body", r.getDocid(i)));
		double mu = ((RetrievalModelIndri) model).getFbMu();

		for (Map.Entry<String,Double> entry : map.entrySet()) {
			String term = entry.getKey();
		    double mle = (double)((Idx.getTotalTermFreq("body", term)) / (double)(Idx.getSumOfFieldLengths("body")));
			double idf = Math.log(1.0 / mle);
			double score = docScore * idf * (0.0+mu*mle)/((double)docLen + mu);
			map.put(term, map.get(term)+score);
		}

		for (int j=1; j<tmpVector.stemsLength(); j++) {
			String term = tmpVector.stemString(j);
			double mle = (double)(tmpVector.totalStemFreq(j))/((double)Idx.getSumOfFieldLengths("body"));
			double idf = Math.log(1.0 / mle);
			double score = docScore * idf * ((double)tmpVector.stemFreq(j)+mu*mle)/((double)docLen + mu);
			double mleDefault = (double)((Idx.getTotalTermFreq("body", term)) / (double)(Idx.getSumOfFieldLengths("body")));
			double scoreDefault = docScore * idf * (0.0+mu*mle)/((double)docLen + mu);
			score -= scoreDefault; 
			map.put(term, map.get(term)+score); 
		}

	}


	HashMap<String, Double> sortMap = sortHashMapByValues(map);

	// select top fbTerms terms. 
	int count = 0;
	double origWeight = ((RetrievalModelIndri) model).getFbOrigWeight();
	String expandQuery = "#wand( ";
	for (Map.Entry<String,Double> entry : sortMap.entrySet()) {
		expandQuery += String.format("%.4f",(entry.getValue())) + " " + entry.getKey() + " "; 	
		count += 1;
		if (count >= model.getFbTerms() )
			break;
	}
	expandQuery += " ) ";
	System.out.println(expandQuery);

    File fout = new File(((RetrievalModelIndri)model).getFbExpansionQueryFile());
	FileOutputStream fos = new FileOutputStream(fout);
    OutputStreamWriter output = new OutputStreamWriter(fos);

	output.write(qid+": "+expandQuery+"\n");	
	output.flush();

	expandQuery = String.format("#wand( %.4f #and( %s ) %.4f %s )", origWeight, qString,(1-origWeight), expandQuery);

    return expandQuery;	

  }

	static HashMap<String, ScoreList> readRankingList(String filename) throws Exception {

		if (filename.equals("")) {
			return null;
		}

		HashMap<String, ScoreList> scoreListMap = new HashMap<String, ScoreList>();
        ScoreList scoreList = new ScoreList();

		File documentFile = new File(filename);
		Scanner scan = new Scanner(documentFile);

        String line = null;
        String qid_now = "-1";
		String qid = "";
        while (scan.hasNext()) {
            line = scan.nextLine();
            String[] elements = line.split(" ");
            qid = elements[0];
            if ( !(qid.equals(qid_now)) && !(qid_now.equals("-1"))) {
                scoreListMap.put(qid_now, scoreList);   
                scoreList = new ScoreList();
            }  
            String doc_id = elements[2];
            int rank = Integer.parseInt(elements[3]);
            double score = Double.parseDouble(elements[4]);
            scoreList.add(Idx.getInternalDocid(doc_id), score);
            qid_now = qid; 
        }
		scoreListMap.put(qid_now, scoreList); 

		return scoreListMap;   
    }


  /**
   *  Process the query file.
   *  @param queryFilePath
   *  @param model
   *  @throws IOException Error accessing the Lucene index.
   */
  static void processQueryFile(String queryFilePath, String trecEvalOutputPath,
                               RetrievalModel model)
      throws Exception {

    BufferedReader input = null;

    try {
      String qLine = null;

      input = new BufferedReader(new FileReader(queryFilePath));


	  File fout = new File(trecEvalOutputPath);
	  FileOutputStream fos = new FileOutputStream(fout);
	  OutputStreamWriter output = new OutputStreamWriter(fos);

      //  Each pass of the loop processes one query.

	  // read
	  String initFileName = ""; 
	  if (model instanceof RetrievalModelIndri){
		initFileName = ((RetrievalModelIndri)model).getFbInitialRankingFile(); 
      } 
	  HashMap<String, ScoreList> scoreListMap = readRankingList(initFileName);

      while ((qLine = input.readLine()) != null) {
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
            ("Syntax error:  Missing ':' in query line.");
        }

        printMemoryUsage(false);

        String qid = qLine.substring(0, d);
        String query = qLine.substring(d + 1);

        System.out.println("Query " + qLine);

        ScoreList r = null;

		if ((model instanceof RetrievalModelIndri) && ( ((RetrievalModelIndri)model).getFb()==true) ) {
			// get new query.
			if (((RetrievalModelIndri)model).getFbInitialRankingFile().equals("")) {
				r = processQuery(query, model);
			} else {
				r = scoreListMap.get(qid);
			}
			r.sort(); 
 
			String new_query = processFbQuery(qid, query, r, (RetrievalModelIndri)model);
			// use the query to retrieve again. 
			r = processQuery(new_query, model);
		} else {
			r = processQuery(query, model);
		}

        if (r != null) {
		  writeResults(qid, r, output);
          //printResults(qid, r);
          System.out.println();
        }
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
    }
  }

  /**
   * Print the query results.
   * 
   * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
   * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
   * 
   * QueryID Q0 DocID Rank Score RunID
   * 
   * @param queryName
   *          Original query.
   * @param result
   *          A list of document ids and scores
   * @throws IOException Error accessing the Lucene index.
   */

  /**
   *  Sort the list by score and external document id.
   */

  static void printResults(String queryName, ScoreList result) throws IOException {

	result.sort();

	//System.out.print(result.size());

    System.out.println(queryName + ":  ");
    if (result.size() < 1) {
      System.out.println("\tNo results.");
    } else {
      for (int i = 0; i < Math.min(result.size(),100); i++) {
        System.out.println("\t" + i + ":  " + Idx.getExternalDocid(result.getDocid(i)) + ", "
            + result.getDocidScore(i));
      }
    }
  }

  static void writeResults(String queryName, ScoreList result, OutputStreamWriter output) throws IOException {
	result.sort();
 
    if (result.size() >= 1) {
        for (int i = 0; i < Math.min(100,result.size()); i++) { 
          output.write(String.format("%s Q0 %s %d %f fubar\n",queryName,Idx.getExternalDocid(result.getDocid(i)),i+1,result.getDocidScore(i)));
      }
    }
	output.flush();
  }

  /**
   *  Read the specified parameter file, and confirm that the required
   *  parameters are present.  The parameters are returned in a
   *  HashMap.  The caller (or its minions) are responsible for processing
   *  them.
   *  @return The parameters, in <key, value> format.
   */
  private static HashMap<String, String> readParameterFile (String parameterFileName)
    throws IOException {

    HashMap<String, String> parameters = new HashMap<String, String>();

    File parameterFile = new File (parameterFileName);

    if (! parameterFile.canRead ()) {
      throw new IllegalArgumentException
        ("Can't read " + parameterFileName);
    }

    Scanner scan = new Scanner(parameterFile);
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split ("=");
      parameters.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());

    scan.close();

    if (! (parameters.containsKey ("indexPath") &&
           parameters.containsKey ("queryFilePath") &&
           parameters.containsKey ("trecEvalOutputPath") &&
           parameters.containsKey ("retrievalAlgorithm") )) {
           //parameters.containsKey ("BM25:k_1") &&
           //parameters.containsKey ("BM25:b") && 
           //parameters.containsKey ("BM25:k_3") && 
           //parameters.containsKey ("Indri:mu") && 
           //parameters.containsKey ("Indri:lambda"))) {

      throw new IllegalArgumentException
        ("Required parameters were missing from the parameter file.");
    }

    return parameters;
  }

	public static LinkedHashMap<String, Double> sortHashMapByValues(
			HashMap<String, Double> passedMap) {
		List<String> mapKeys = new ArrayList<>(passedMap.keySet());
		List<Double> mapValues = new ArrayList<>(passedMap.values());
		Collections.sort(mapValues,Collections.reverseOrder());
		Collections.sort(mapKeys,Collections.reverseOrder());

		LinkedHashMap<String, Double> sortedMap =
			new LinkedHashMap<>();

		Iterator<Double> valueIt = mapValues.iterator();
		while (valueIt.hasNext()) {
			double val = valueIt.next();
			Iterator<String> keyIt = mapKeys.iterator();

			while (keyIt.hasNext()) {
				String key = keyIt.next();
				double comp1 = passedMap.get(key);
				double comp2 = val;

				if (comp1==comp2) {
					keyIt.remove();
					sortedMap.put(key, val);
					break;
				}
			}
		}
		return sortedMap;
	}

}


class ValueComparator implements Comparator<String> {
    Map<String, Double> base;

    public ValueComparator(Map<String, Double> base) {
        this.base = base;
    }

    public int compare(String a, String b) {
        if (base.get(a) >= base.get(b)) {
            return -1;
        } else {
            return 1;
        }
    }
}


