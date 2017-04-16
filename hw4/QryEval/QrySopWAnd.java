/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The AND operator for all retrieval models.
 */
public class QrySopWAnd extends QrySopW {
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
		return this.docIteratorHasMatchMin(r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */

  public double getScore (RetrievalModel r) throws IOException {
	if (r instanceof RetrievalModelIndri) {
	  return this.getScoreIndri(r);
	} else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the AND operator.");
    }
  }
 
 
 
  /**
   *  getScore for the UnrankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
	private double getScoreIndri (RetrievalModel r) throws IOException {
		double score = 1.0;
		int docid = this.docIteratorGetMatch();
		//System.out.print(docid+" ");
		for (int i =0; i<this.args.size(); i++) { 
			Qry q = this.args.get(i);
			double weight = this.weights.get(i);
			if (q.docIteratorHasMatch(r) && q.docIteratorGetMatch() == docid) {
				score *= Math.pow(((QrySop) q).getScore(r), weight/this.weightSum);
				//System.out.print(weight+" o ");
			} else {
				score *= Math.pow(((QrySop) q).getDefaultScore(r, docid), weight/this.weightSum);
				//System.out.print(weight+" x ");
			}
		}
		//System.out.print(" "+score+"\n");
		return score;
	}

	public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
		double score = 1.0;	
		for (int i =0; i<this.args.size(); i++) { 
			//System.out.print("\t"+i+"\n");
			Qry q = this.args.get(i);
			double weight = this.weights.get(i);
			score *= ((QrySop) q).getDefaultScore(r, docid);
		}
		return score;
	}

}
