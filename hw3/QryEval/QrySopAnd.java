/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The AND operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
	if (r instanceof RetrievalModelIndri)
		return this.docIteratorHasMatchMin(r);
	else
		return this.docIteratorHasMatchAll(r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    } else if (r instanceof RetrievalModelRankedBoolean) {
	  return this.getScoreRankedBoolean (r);
	} else if (r instanceof RetrievalModelIndri) {
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
			if (q.docIteratorHasMatch(r) && q.docIteratorGetMatch() == docid) {
				score *= Math.pow(((QrySop) q).getScore(r), 1.0/this.args.size());
				//System.out.print("o ");
			} else {
				score *= Math.pow(((QrySop) q).getDefaultScore(r, docid), 1.0/this.args.size());
				//System.out.print("x ");
			}
			//System.out.print(score+" ");
		}
		//System.out.print("\n");
		return score; //Math.pow(score, 1.0/this.args.size());
	}

	public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
		double score = 1.0;
		for (int i =0; i<this.args.size(); i++) { 
			Qry q = this.args.get(i);
			score *= ((QrySopScore) q).getDefaultScore(r, docid);
		}

		return score; //Math.pow(score, 1.0/this.args.size());
	}


  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
	if (! this.docIteratorHasMatch(r)) 
		return 0.0;
    int doc_id = this.docIteratorGetMatch();
	double score = 1.0; //Double.MAX_VALUE;
    for (int i =0; i<this.args.size(); i++) {
        Qry q = this.args.get(i);
        if (q instanceof QrySopOr) {
            if (q.args.get(0).docIteratorHasMatch(r) && q.docIteratorGetMatch() == doc_id)
				score = Math.min(score, ((QrySopOr) q).getScore(r) );
        } else if (q instanceof QrySopAnd) {
			if (q.args.get(0).docIteratorHasMatch(r) &&q.docIteratorGetMatch() == doc_id)
				score = Math.min(score, ((QrySopAnd) q).getScore(r) );
		} else if (this.args.get(i) instanceof QrySopScore) {
            if (q.args.get(0).docIteratorHasMatch(r) && q.docIteratorGetMatch() == doc_id)
				score = Math.min( score, ((QrySopScore) q).getScore(r) );
        }
    }
    return score;
  }

  private double getScoreRankedBoolean (RetrievalModel r) throws IOException {


	if (! this.docIteratorHasMatch(r)) 
		return 0.0;
    int doc_id = this.docIteratorGetMatch();
	double score = Double.MAX_VALUE;
    for (int i =0; i<this.args.size(); i++) {
        Qry q = this.args.get(i);
        if (q instanceof QrySopOr) {
            if (q.args.get(0).docIteratorHasMatch(r) && q.docIteratorGetMatch() == doc_id)
				score = Math.min(score, ((QrySopOr) q).getScore(r) );
        } else if (q instanceof QrySopAnd) {
			if (q.args.get(0).docIteratorHasMatch(r) &&q.docIteratorGetMatch() == doc_id)
				score = Math.min(score, ((QrySopAnd) q).getScore(r) );
		} else if (this.args.get(i) instanceof QrySopScore) {
            if (q.args.get(0).docIteratorHasMatch(r) && q.docIteratorGetMatch() == doc_id)
				score = Math.min( score, ((QrySopScore) q).getScore(r) );
        }
    }

	if (score == Double.MAX_VALUE) {
		return 0.0;
	}
    return score;
  }


}
