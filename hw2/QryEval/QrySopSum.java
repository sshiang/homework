/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The AND operator for all retrieval models.
 */
public class QrySopSum extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
	return this.docIteratorHasMatchMin (r);
    //return this.docIteratorHasMatchAll (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */

	public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
		return 0.0;
	}

  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelBM25) {

		if (! this.docIteratorHasMatch(r))  {
			return 0.0;
		}

		int doc_id = this.docIteratorGetMatch();
		double score = 0.0;
		for (int i =0; i<this.args.size(); i++) {
			Qry q = this.args.get(i);
			double s = 0.0;
			if ((q instanceof QrySopOr) || (q instanceof QrySopAnd)) {
				throw new IllegalArgumentException
				   (r.getClass().getName() + " doesn't support the OR/AND operator.");
			} else {
				if (q.args.get(0).docIteratorHasMatch(r) && q.docIteratorGetMatch() == doc_id)
					score += ((QrySop) q).getScore(r);

			}
			//score += s; 
		}
		return score;


	} else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SUM operator.");
    }
  }
}
