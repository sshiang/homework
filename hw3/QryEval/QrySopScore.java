/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
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
    } else if (r instanceof RetrievalModelBM25) {
       return this.getScoreBM25 (r); 
    } else if (r instanceof RetrievalModelIndri) {
		return this.getScoreIndri (r); 
	} 
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }

  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
	double score = 0.0; 
	Qry q = this.args.get(0);	
	if (q.docIteratorHasMatch(r)) {
		score =  ((QryIop) q).docIteratorGetMatchPosting().tf;
	}
	return score;
  }

  public double getScoreBM25 (RetrievalModel r) throws IOException {
	double score = 0.0; 
	Qry q = this.args.get(0);	
	if (q.docIteratorHasMatch(r)) {


		int df = ((QryIop) q).getDf(); //this.invertedList.df;
		double idf = Math.log10((Idx.getNumDocs() - (double)df + 0.5) / ((double)df + 0.5));
		double avgLength = Idx.getSumOfFieldLengths(((QryIop) q).field) / (double) Idx.getDocCount(((QryIop) q).field);

        //double idf = ((QryIop) q).getIdf();
        double tf = (double)(((QryIop) q).docIteratorGetMatchPosting().tf);
        double k_1 = ((RetrievalModelBM25) r).getk_1();
        double b = ((RetrievalModelBM25) r).getb();
        double k_3 = ((RetrievalModelBM25) r).getk_3();
        double doclen = (double)(Idx.getFieldLength(((QryIop) q).field, q.docIteratorGetMatch()));
		double userWeight = 1.0; // when to set it? 
        double tfWeight = tf*(k_1+1.0) / (tf + k_1 * (1.0-b+b*(doclen/avgLength) ) );

		score = idf * tfWeight * ( (k_3 + 1.0)*userWeight / (k_3+userWeight) );
	}
	return score;
  }

  public double getScoreIndri (RetrievalModel r) throws IOException {
	double score = 1.0; 
	Qry q = this.args.get(0);
    if (q.docIteratorHasMatch(r)) {
		double mu = ((RetrievalModelIndri) r).getMu();
		double lambda = ((RetrievalModelIndri) r).getLambda();
		double mle = (double)(((QryIop) q).getCtf()) / (double)(Idx.getSumOfFieldLengths(((QryIop) q).field));
		double docLength = (double)(Idx.getFieldLength(((QryIop) q).field, q.docIteratorGetMatch()));
		double tf = (double)(((QryIop) q).docIteratorGetMatchPosting().tf);
		score = indriScoreFunc(mu, lambda, tf, mle, docLength);
	} else {
		System.out.print("here!!!\n");
	}

	return score; 
  }

	public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
		Qry q = this.args.get(0);
		double mu = ((RetrievalModelIndri) r).getMu();
        double lambda = ((RetrievalModelIndri) r).getLambda();	
		double mle = (double)(((QryIop) q).getCtf()) / (double)(Idx.getSumOfFieldLengths(((QryIop) q).field));
		double docLength = (double)(Idx.getFieldLength(((QryIop) q).field, docid));

		return indriScoreFunc(mu, lambda, 0.0, mle, docLength);
	}


	public double indriScoreFunc(double mu, double lambda, double tf, double mle, double docLength) {
		return (1-lambda)*(((double)tf+(mu*mle))/(docLength+mu))+lambda*mle;
	}
		

  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get (0);
    q.initialize (r);
  }

}
