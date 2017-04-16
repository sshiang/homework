/*
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;


/**
 *  QryParser is an embarrassingly simplistic query parser.  It has
 *  two primary methods:  getQuery and tokenizeString.  getQuery
 *  converts a query string into an optimized Qry tree.  tokenizeString
 *  converts a flat (unstructured) query string into a string array; it
 *  is used for creating learning-to-rank feature vectors.
 *  <p>
 *  Add new operators to the query parser by modifying the following
 *  methods:
 *  </p>
 *  <ul>
 *  <li>createOperator: Use a string (e.g., #and) to create a node
 *      (e.g., QrySopAnd).
 *
 *  <li>parseString:  If the operator supports term weights
 *      (e.g., #wsum (0.5 apple 1 pie)), you must modify this method.
 *      For these operators, two substrings (weight and term) are
 *      popped from the query string at each step, instead of one.
 *  </ul>
 *  <p>
 *  Add new document fields to the parser by modifying createTerms.
 *  </p>
 */

public class QryParser {

  //  --------------- Constants and variables ---------------------

  private static final EnglishAnalyzerConfigurable ANALYZER =
    new EnglishAnalyzerConfigurable(Version.LUCENE_43);

  //  -------------------- Initialization -------------------------

  static {
    ANALYZER.setLowercase(true);
    ANALYZER.setStopwordRemoval(true);
    ANALYZER.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
  }

  //  ----------- Methods, in alphabetical order ------------------

  /**
   *  Count the number of occurrences of character c in string s.
   *  @param c A character.
   *  @param s A string.
   */
  private static int countChars (String s, char c) {

    int count=0;

    for (int i=0; i<s.length(); i++) {
      if (s.charAt (i) == c) {
	count ++;
      }
    }

    return count;
  }


  /**
   *  Create the desired query operator.
   *  @parameter operator The operator name.
   */
  private static Qry createOperator (String operatorName) {

    Qry operator=null;
    int operatorDistance=0;
    String operatorNameLowerCase = (new String (operatorName)).toLowerCase();

    //  Handle the distance argument to proximity operators such as
    //  #near/n and #window/n.

    //  STUDENT HW1 AND HW2 CODE HERE
    
    //  Create the query operator.

	// for near/n 
	int size = operatorNameLowerCase.split("/").length;
	if (size !=1) {
		operatorDistance = Integer.parseInt(operatorNameLowerCase.split("/")[1]);
		operatorNameLowerCase = operatorNameLowerCase.split("/")[0];
	}

    switch (operatorNameLowerCase) {
		case "#or":
		operator = new QrySopOr ();
		break;

		case "#syn":
		operator = new QryIopSyn ();
		break;

		case "#and":
		operator = new QrySopAnd ();
		break;

		case "#near":
		operator = new QryIopNear (operatorDistance);
		break;

		case "#sum":
		operator = new QrySopSum ();
		break;

		case "#window":
		operator = new QryIopWindow (operatorDistance);
		break;

		case "#wsum":
		operator = new QrySopWSum ();
		break;
	
		case "#wand":
		operator = new QrySopWAnd ();
		break;


      default:
	syntaxError ("Unknown query operator " + operatorName);
    }

    operator.setDisplayName (operatorName);

    return operator;
  }
  
  /**
   *  Create one or more terms from a token.  The token may contain
   *  dashes or other punctuation b(e.g., near-death) and/or a field
   *  name (e.g., apple.title).
   *  @parameter token The token consumed from the query string.
   *  @throws IOException Error accessing the Lucene index.
   */
  private static Qry[] createTerms (String token, String defaultField) throws IOException {

    //  Split the token into a term and a field.

    int delimiter = token.indexOf('.');
    String field = null;
    String term = null;

    if (delimiter < 0) {	// .body is the default field
      field = defaultField; //"body";
      term = token;
    } else {			// Remove the field from the token
	  int new_delimiter = token.substring(delimiter+1).indexOf('.');
      field = token.substring(delimiter + 1).toLowerCase();
	  term = token.substring(0, delimiter);
		if ((field.compareTo("url") != 0) &&
		    (field.compareTo("keywords") != 0) &&
		    (field.compareTo("title") != 0) &&
		    (field.compareTo("body") != 0) &&
		    (field.compareTo("inlink") != 0)) {
			field = defaultField;
			term = token;
		}
    }

    //  Confirm that the field is a known field.

    if ((field.compareTo("url") != 0) &&
	(field.compareTo("keywords") != 0) &&
	(field.compareTo("title") != 0) &&
	(field.compareTo("body") != 0) &&
	(field.compareTo("inlink") != 0)) {
		syntaxError ("Unknown field " + token);
    }

    //  Lexical processing, stopwords, stemming.  A loop is used
    //  just in case a term (e.g., "near-death") gets tokenized into
    //  multiple terms (e.g., "near" and "death").

    String t[] = tokenizeString(term);
    Qry terms[] = new Qry[t.length];
    
    for (int j = 0; j < t.length; j++) {
      terms[j] = new QryIopTerm(t [j], field);
    }
    
    return terms;
  }


  /**
   *  Parse a query string into a query tree.
   *  @param queryString The query string, in an Indri-style
   *  query language.
   *  @return Qry The query tree for the parsed query.
   *  @throws IOException Error accessing the Lucene index.
   *  @throws IllegalArgumentException Query syntax error.
   */
  public static Qry getQuery (String queryString, String defaultField)
      throws IOException, IllegalArgumentException {

	// sshaing: if there is no operator in the input string , use OR as default

    Qry q = parseString (queryString, defaultField);		// An exact parse
    q = optimizeQuery (q);			// An optimized parse
    return q;
  }

  /**
   *  Get the index of the right parenenthesis that balances the
   *  left-most parenthesis.  Return -1 if it doesn't exist.
   *  @param s A string containing a query.
   */
  private static int indexOfBalencingParen (String s) {

    int depth = 0;

    for (int i=0; i< s.length(); i++) {
      if (s.charAt(i) == '(') {
	depth ++;
      } else if (s.charAt(i) == ')') {
	depth --;
            
	if (depth == 0) {
	  return i;
	}
      }
    }

    return -1;
  }


  /**
   *  Optimize the query by removing degenerate nodes produced during
   *  query parsing, for example '#NEAR/1 (of the)' which turns into
   *  '#NEAR/1 ()' after stopwords are removed; and unnecessary nodes
   *  or subtrees, such as #AND (#AND (a)), which can be replaced by
   *  'a'.
   */
  private static Qry optimizeQuery(Qry q) {

    //  Term operators don't benefit from optimization.

    if (q instanceof QryIopTerm) {
      return q;
    }

    //  Optimization is a depth-first task, so recurse on query
    //  arguments.  This is done in reverse to simplify deleting
    //  query arguments that become null.
    
    for (int i = q.args.size() - 1; i >= 0; i--) {

      Qry q_i_before = q.args.get(i);
      Qry q_i_after = optimizeQuery (q_i_before);

      if (q_i_after == null) {
        q.removeArg(i);			// optimization deleted the arg
      } else {
        if (q_i_before != q_i_after) {
          q.args.set (i, q_i_after);	// optimization changed the arg
        }
      }
    }

    //  If the operator now has no arguments, it is deleted.

    if (q.args.size () == 0) {
      return null;
    }

    //  Only SCORE operators can have a single argument.  Other
    //  query operators that have just one argument are deleted.

    if ((q.args.size() == 1) &&
        (! (q instanceof QrySopScore))) {
      q = q.args.get (0);
    }

    return q;

  }


  /**
   *  Parse a query string into a query tree.
   *  @param queryString The query string, in an Indri-style query
   *  language.
   *  @return Qry The query tree for the parsed query.
   *  @throws IOException Error accessing the Lucene index.
   *  @throws IllegalArgumentException Query syntax error.
   */
  private static Qry parseString (String queryString, String defaultField)
      throws IOException, IllegalArgumentException {

    //  This simple parser is sensitive to parenthensis placement, so
    //  check for basic errors first.

    queryString = queryString.trim ();	// The last character should be ')'

    if ((countChars (queryString, '(') == 0) ||
	(countChars (queryString, '(') != countChars (queryString, ')')) ||
	(indexOfBalencingParen (queryString) != (queryString.length() - 1))) {
      syntaxError ("Missing, unbalanced, or misplaced parentheses");
    }

    //  The query language is prefix-oriented, so the query string can
    //  be processed left to right.  At each step, a substring is
    //  popped from the head (left) of the string, and is converted to
    //  a Qry object that is added to the query tree.  Subqueries are
    //  handled via recursion.

    //  Find the left-most query operator and start the query tree.

    String[] substrings = queryString.split("[(]", 2);
    Qry queryTree = createOperator (substrings[0].trim());

	String queryName = queryTree.getDisplayName().toLowerCase() ;

    //  Start consuming queryString by removing the query operator and
    //  its terminating ')'.  queryString is always the part of the
    //  query that hasn't been processed yet.
    
    queryString = substrings[1];
    queryString =
      queryString.substring (0, queryString.lastIndexOf(")")).trim();
    
    //  Each pass below handles one argument to the query operator.
    //  Note: An argument can be a token that produces multiple terms
    //  (e.g., "near-death") or a subquery (e.g., "#and (a b c)").
    //  Recurse on subqueries.


	ArrayList<Double> weights = new ArrayList<Double>();
	


    while (queryString.length() > 0) {
	
      //  If the operator uses weighted query arguments, each pass of
      //  this loop must handle "weight arg".  Handle the weight first.

      //  STUDENT HW2 CODE GOES HERE

      //  Now handle the argument (which could be a subquery).

      Qry[] qargs = null;
      PopData<String,String> p;
		double w = 0.0;

	  if ((queryName.equals("#wsum")) || (queryName.equals("#wand"))) {
		//weights.add(Double.parseDouble(p.getPopped()));
		p = popTerm (queryString);
		w = Double.parseDouble(p.getPopped());
		//weights.add(Double.parseDouble(p.getPopped()));
		queryString = p.getRemaining().trim();
      }
	  

      if (queryString.charAt(0) == '#') {	// Subquery
		p = popSubquery (queryString);
		qargs = new Qry[1];
		qargs[0] = parseString (p.getPopped(), defaultField);
      } else {					// Term
		p = popTerm (queryString);	
		qargs = createTerms (p.getPopped(),  defaultField);
      }


		if ((queryName.equals("#wsum")) || (queryName.equals("#wand"))) {
			if (qargs.length!=0) {
				weights.add(w);
			}
		}


      queryString = p.getRemaining().trim();	// Consume the arg

      //  Add the argument(s) to the query tree.

	//System.out.print("xxx "+queryString+" "+qargs.length+"\n");

      for (int i=0; i<qargs.length; i++) {
		//  STUDENTS WILL NEED TO ADJUST THIS BLOCK TO HANDLE WEIGHTS IN HW2
		queryTree.appendArg (qargs[i]);
   }

	}

		if ((queryName.equals("#wsum")) || (queryName.equals("#wand"))) {
			((QrySopW)queryTree).setWeights(weights); 
		}
	

    return queryTree;
  }  

    
  /**
   *  Remove a subQuery from an argument string.  Return the subquery
   *  and the modified argument string.
   *  @param String A partial query argument string, e.g., "#and(a b)
   *  c d".
   *  @return PopData<String,String> The subquery string and the
   *  modified argString (e.g., "#and(a b)" and "c d".
   */
  static private PopData<String,String> popSubquery (String argString) {
	
    int i = indexOfBalencingParen (argString);
	  
    if (i < 0) {		// Query syntax error.  The parser
      i = argString.length();	// handles it.  Here, just don't fail.
    }
    
    String subquery = argString.substring(0, i+1);
    argString = argString.substring(i+1);

    return new PopData<String,String>(subquery, argString);
  }

    
  /**
   *  Remove a term from an argument string.  Return the term and
   *  the modified argument string.
   *  @param String A partial query argument string, e.g., "a b c d".
   *  @return PopData<String,String>
   *  The term string and the modified argString (e.g., "a" and
   *  "b c d".
   */
  static private PopData<String,String> popTerm (String argString) {
	
    String[] substrings = argString.split ("[ \t\n\r]+", 2);
    String token = substrings[0];

    if (substrings.length < 2) {	//  Is this the last argument?
      argString = "";
    } else {
      argString = substrings[1];
    }

    return new PopData<String,String>(substrings[0], argString);
  }

    
  /**
   *  Throw an error specialized for query parsing syntax errors.
   *  @param errorString The string "Syntax
   *  @throws IllegalArgumentException The query contained a syntax
   *  error
   */
  static private void syntaxError (String errorString) throws IllegalArgumentException {
    throw new IllegalArgumentException ("Syntax Error: " + errorString);
  }


  /**
   *  Given part of a query string, returns an array of terms with
   *  stopwords removed and the terms stemmed using the Krovetz
   *  stemmer.  Use this method to process raw query terms.
   *  @param query String containing query. 
   *  @return Array of query tokens
   *  @throws IOException Error accessing the Lucene index.
   */
  public static String[] tokenizeString(String query) throws IOException {

    TokenStreamComponents comp =
      ANALYZER.createComponents("dummy", new StringReader(query));
    TokenStream tokenStream = comp.getTokenStream();

    CharTermAttribute charTermAttribute =
      tokenStream.addAttribute(CharTermAttribute.class);
    tokenStream.reset();

    List<String> tokens = new ArrayList<String>();

    while (tokenStream.incrementToken()) {
      String term = charTermAttribute.toString();
      tokens.add(term);
    }

    return tokens.toArray (new String[tokens.size()]);
  }


}
