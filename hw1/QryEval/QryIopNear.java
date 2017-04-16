/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */
import java.io.*;
import java.util.*;

/**
 *  The TERM operator for all retrieval models.  The TERM operator stores
 *  information about a query term, for example "apple" in the query
 *  "#AND (apple pie).  Although it may seem odd to use a query
 *  operator to store a term, doing so makes it easy to build
 *  structured queries with nested query operators.
 *
 */
public class QryIopNear extends QryIop {

  private int distance; 
  public QryIopNear(int distance) {
    this.distance = distance;
  }


	// for each arg in this.args, get the matching doc_id:
	// ex. args = a,b,c
	// TODO	
	public void initialize(RetrievalModel r) throws IOException { 

		super.initialize(r);

		if (this.args.size() < 2)
            throw new IllegalArgumentException("Near should have more than one parameters");
		ArrayList<Integer> doc_ids = new ArrayList<Integer>();
		for (int i=0; i<this.args.size(); i++) 
			doc_ids.add( ((QryIop)this.args.get(i)).docIteratorGetMatch());


		// while no more operation can be done. 
		while (true) {

			if (isEqual(doc_ids)) {

				ArrayList<Integer> loc_ids = new ArrayList<Integer>(); 
				for (int i=0; i<this.args.size(); i++)
					loc_ids.add(0);

				// check loc in document:
				// postings: i
				ArrayList<InvList.DocPosting> posting_candidates = new ArrayList<InvList.DocPosting>(); 
				ArrayList<Integer> positions = new ArrayList<Integer> (); 
				for (int i=0; i<this.args.size(); i++) {
					posting_candidates.add(((QryIop)this.args.get(i)).docIteratorGetMatchPosting());
				}

				while (true) {
					boolean meet=true;
					for (int i=1; i<doc_ids.size(); i++) {
						int loc1 = posting_candidates.get(i).positions.get(loc_ids.get(i));
						int loc2 = posting_candidates.get(i-1).positions.get(loc_ids.get(i-1));
						if ( (loc1-loc2 > this.distance) || (loc2>loc1) ) {
							meet = false;
							break;
						}
					}	

					boolean toBreak = false;
					if (meet == true) {
						positions.add( posting_candidates.get(0).positions.get(loc_ids.get(0)) );
						for (int i=0; i<loc_ids.size(); i++) {
							if (loc_ids.get(i)+1 >= posting_candidates.get(i).positions.size() ) {
								toBreak = true;
								break; 
							}
							loc_ids.set(i,loc_ids.get(i)+1) ;
						}
						if ( toBreak == true )
							break;

					} else {
						int min_value = posting_candidates.get(0).positions.get(loc_ids.get(0));
						int min_index = 0;
						for (int i=1; i<loc_ids.size(); i++) {
							if ( posting_candidates.get(i).positions.get(loc_ids.get(i))  <= min_value) {
								min_value = posting_candidates.get(i).positions.get(loc_ids.get(i));
								min_index = i;
							}
						}
						if (loc_ids.get(min_index)+1 >= posting_candidates.get(min_index).positions.size() ) {
							toBreak = true;
							break;
						}
						loc_ids.set(min_index,loc_ids.get(min_index)+1);
					}
					if (toBreak == true)
						break;
				}

				if (positions.size() >= 1) 
				this.invertedList.appendPosting(doc_ids.get(0), new ArrayList<Integer>(positions));


				boolean endOfSet = false;
				for (int i=0; i<doc_ids.size(); i++) {
					this.args.get(i).docIteratorAdvancePast(doc_ids.get(i));

					if (!this.args.get(i).docIteratorHasMatch(r)) {
						endOfSet = true;
						break;
					}

					int ID = ((QryIop)this.args.get(i)).docIteratorGetMatch();
					doc_ids.set(i, ID);
				}

				if (endOfSet == true)
					break;

			} else {
				// doc positions are not equal. The smallest one moves to next doc_id
				int min_index = 0;
				int min_value = doc_ids.get(0); 
				for (int i=0; i<doc_ids.size(); i++) {
					if (doc_ids.get(i) <= min_value) {
						min_value = doc_ids.get(i);
						min_index = i;
					}
				}	
				this.args.get(min_index).docIteratorAdvancePast(doc_ids.get(min_index));
				if (!this.args.get(min_index).docIteratorHasMatch(r)) {
					break;
				}
				doc_ids.set(min_index, this.args.get(min_index).docIteratorGetMatch());
			}
		}

	}

	public boolean isEqual(ArrayList<Integer> doc_ids) {
		for (int i=0; i<doc_ids.size(); i++) {
			if ( doc_ids.get(0).equals(doc_ids.get(i)) == false ) {
				return false;
			}
		}

		return true;
	}


  /**
   *  Evaluate the query operator; the result is an internal inverted
   *  list that may be accessed via the internal iterators.
   *  @throws IOException Error accessing the Lucene index.
   */
  protected void evaluate () throws IOException {
	if (this.args.size() < 2) {
		throw new IllegalArgumentException("Near Operator Error.");
	}
    this.invertedList = new InvList();
  }

  /**
   *  Get a string version of this query operator.  
   *  @return The string version of this query operator.
   */
  public String toString(){
	// TODO 
	return ("#near/"+this.distance);
  }
}
