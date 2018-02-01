/*
 * AbstractIndexTermsQuery.java
 * 
 * Copyright (c) 2007-2011, The University of Sheffield.
 * 
 * This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html),
 * and is free software, licenced under the GNU Lesser General Public License,
 * Version 3, June 2007 (also included with this distribution as file
 * LICENCE-LGPL3.html).
 * 
 * Valentin Tablan, 17 Jul 2012
 * 
 * $Id: AbstractIndexTermsQuery.java 17255 2014-01-29 15:29:10Z valyt $
 */
package gate.mimir.search.terms;

import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.AtomicIndex;
import gate.mimir.search.IndexReaderPool;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryEngine.IndexType;
import it.unimi.di.big.mg4j.search.DocumentIterator;
import it.unimi.di.big.mg4j.search.visitor.CounterCollectionVisitor;
import it.unimi.di.big.mg4j.search.visitor.CounterSetupVisitor;
import it.unimi.di.big.mg4j.search.visitor.TermCollectionVisitor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

/**
 * Base class for terms queries that use an MG4J direct index for their search.
 */
public abstract class AbstractIndexTermsQuery extends
  AbstractDocumentsBasedTermsQuery {
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 8382919427152317859L;

  private static final Logger logger = Logger
    .getLogger(AbstractIndexTermsQuery.class);

  /**
   * The name of the subindex in which the terms are sought. Each Mímir index
   * includes multiple sub-indexes (some storing tokens, other storing
   * annotations), identified by a name. For token indexes, the index name is
   * the name of the token feature being indexed; for annotation indexes, the
   * index name is the annotation type.
   */
  protected final String indexName;

  /**
   * The type of index being searched (tokens or annotations).
   */
  protected final IndexType indexType;

  /**
   * The atomic index used for executing the query. This includes both the 
   * inverted and the direct index (if configured).
   */
  protected transient AtomicIndex atomicIndex;

  /**
   * The semantic annotation helper for the correct annotation type (as given by
   * {@link #indexName}), if {@link #indexType} is {@link IndexType#ANNOTATIONS}
   * , <code>null</code> otherwise.
   */
  protected transient SemanticAnnotationHelper annotationHelper;

  /**
   * Should stop words be filtered out of the results?
   */
  protected boolean stopWordsBlocked = false;

  /**
   * Stop words set used for filtering out stop words. See
   * {@link #stopWordsBlocked}.
   */
  protected Set<String> stopWords = null;

  /**
   * If set to true, term strings for annotation mentions are replaced with
   * their description (see
   * {@link SemanticAnnotationHelper#describeMention(String)}.
   */
  protected final boolean describeAnnotations;

  /**
   * The query engine used to execute this query.
   */
  protected transient QueryEngine engine;

  protected final boolean countsEnabled;

  /**
   * @return the countsEnabled
   */
  public boolean isCountsEnabled() {
    return countsEnabled;
  }

  /**
   * The default set of stop words.
   */
  public static final String[] DEFAULT_STOP_WORDS = new String[]{",", ".", "?",
    "!", ":", ";", "#", "~", "^", "@", "%", "&", "(", ")", "[", "]", "{", "}",
    "|", "\\", "<", ">", "-", "+", "*", "/", "=", "'", "\"", "'s", "1", "2",
    "3", "4", "5", "6", "7", "8", "9", "0", "a", "about", "above", "above",
    "across", "after", "afterwards", "again", "against", "all", "almost",
    "alone", "along", "already", "also", "although", "always", "am", "among",
    "amongst", "amoungst", "amount", "an", "and", "another", "any", "anyhow",
    "anyone", "anything", "anyway", "anywhere", "are", "around", "as", "at",
    "b", "back", "be", "became", "because", "become", "becomes", "becoming",
    "been", "before", "beforehand", "behind", "being", "below", "beside",
    "besides", "between", "beyond", "bill", "both", "bottom", "but", "by", "c",
    "call", "can", "cannot", "cant", "co", "con", "could", "couldnt", "cry",
    "d", "de", "describe", "detail", "do", "done", "down", "due", "during",
    "e", "each", "eg", "eight", "either", "eleven", "else", "elsewhere",
    "empty", "enough", "etc", "even", "ever", "every", "everyone",
    "everything", "everywhere", "except", "f", "few", "fifteen", "fify",
    "fill", "find", "fire", "first", "five", "for", "former", "formerly",
    "forty", "found", "four", "from", "front", "full", "further", "g", "get",
    "give", "go", "h", "had", "has", "hasnt", "have", "he", "hence", "her",
    "here", "hereafter", "hereby", "herein", "hereupon", "hers", "herself",
    "him", "himself", "his", "how", "however", "hundred", "i", "ie", "if",
    "in", "inc", "indeed", "interest", "into", "is", "it", "its", "itself",
    "j", "k", "keep", "l", "last", "latter", "latterly", "least", "less",
    "ltd", "m", "made", "many", "may", "me", "meanwhile", "might", "mill",
    "mine", "more", "moreover", "most", "mostly", "move", "much", "must", "my",
    "myself", "n", "name", "namely", "neither", "never", "nevertheless",
    "next", "nine", "no", "nobody", "none", "noone", "nor", "not", "nothing",
    "now", "nowhere", "o", "of", "off", "often", "on", "once", "one", "only",
    "onto", "or", "other", "others", "otherwise", "our", "ours", "ourselves",
    "out", "over", "own", "p", "part", "per", "perhaps", "please", "put", "q",
    "r", "rather", "re", "s", "same", "see", "seem", "seemed", "seeming",
    "seems", "serious", "several", "she", "should", "show", "side", "since",
    "sincere", "six", "sixty", "so", "some", "somehow", "someone", "something",
    "sometime", "sometimes", "somewhere", "still", "such", "system", "t",
    "take", "ten", "than", "that", "the", "their", "them", "themselves",
    "then", "thence", "there", "thereafter", "thereby", "therefore", "therein",
    "thereupon", "these", "they", "thickv", "thin", "third", "this", "those",
    "though", "three", "through", "throughout", "thru", "thus", "to",
    "together", "too", "top", "toward", "towards", "twelve", "twenty", "two",
    "u", "un", "under", "until", "up", "upon", "us", "v", "very", "via", "w",
    "was", "we", "well", "were", "what", "whatever", "when", "whence",
    "whenever", "where", "whereafter", "whereas", "whereby", "wherein",
    "whereupon", "wherever", "whether", "which", "while", "whither", "who",
    "whoever", "whole", "whom", "whose", "why", "will", "with", "within",
    "without", "would", "x", "y", "yet", "you", "your", "yours", "yourself",
    "yourselves", "z"};

  /**
   * @param indexName
   *          The name of the subindex in which the terms are sought. Each Mímir
   *          index includes multiple sub-indexes (some storing tokens, other
   *          storing annotations), identified by a name. For token indexes, the
   *          index name is the name of the token feature being indexed; for
   *          annotation indexes, the index name is the annotation type.
   * @param indexType
   *          The type of index to be searched (tokens or annotations).
   * @param countsEnabled
   *          should term counts be obtained?
   * @param describeAnnotations
   *          If the index being interrogated is of type
   *          {@link IndexType#ANNOTATIONS} then the indexed term strings are
   *          URIs whose format depends on the actual implementation of the
   *          index. These strings make little sense outside of the index. If
   *          this is set to <code>true</code>, then term descriptions are also
   *          included in the results set. See
   *          {@link TermsResultSet#termDescriptions} and
   *          {@link SemanticAnnotationHelper#describeMention(String)}. Setting
   *          this to <code>true</code> has no effect if the index being
   *          interrogated is a {@link IndexType#TOKENS} index.
   */
  public AbstractIndexTermsQuery(String indexName, IndexType indexType,
                                 boolean countsEnabled,
                                 boolean describeAnnotations,
                                 long... documentIDs) {
    super(documentIDs);
    this.indexName = indexName;
    this.indexType = indexType;
    this.countsEnabled = countsEnabled;
    this.describeAnnotations =
      describeAnnotations && (indexType == IndexType.ANNOTATIONS);
  }

  /**
   * Populates the internal state by obtaining references to the direct and
   * indirect indexes from the {@link QueryEngine}.
   * 
   * @param engine
   *          the {@link QueryEngine} used to execute this query.
   * @throws IllegalArgumentException
   *           if the index represented by the provided query engine does not
   *           have a direct index for the given sub-index (as specified by
   *           {@link #indexType} and {@link #indexName}).
   */
  protected void prepare(QueryEngine engine) {
    this.engine = engine;
    switch(indexType){
      case ANNOTATIONS:
        atomicIndex = engine.getAnnotationIndex(indexName);
        annotationHelper = engine.getAnnotationHelper(indexName);
        break;
      case TOKENS:
        atomicIndex = engine.getTokenIndex(indexName);
        break;
      default:
        throw new IllegalArgumentException("Invalid index type: " +
          indexType.toString());
    }
    if(!atomicIndex.hasDirectIndex()) { throw new IllegalArgumentException(
      "This type of query requires a " +
        "direct index, but one was not found for (" +
        indexType.toString().toLowerCase() + ") sub-index \"" + indexName +
        "\""); }
  }

  protected TermsResultSet buildResultSet(DocumentIterator documentIterator)
    throws IOException {
    // prepare local data
    ObjectArrayList<String> termStrings = new ObjectArrayList<String>();
    ObjectArrayList<String> termDescriptions =
      describeAnnotations ? new ObjectArrayList<String>() : null;
    IntArrayList termCounts = countsEnabled ? new IntArrayList() : null;
    TermCollectionVisitor termCollectionVisitor = null;
    CounterSetupVisitor counterSetupVisitor = null;
    CounterCollectionVisitor counterCollectionVisitor = null;
    if(countsEnabled) {
      termCollectionVisitor = new TermCollectionVisitor();
      counterSetupVisitor = new CounterSetupVisitor(termCollectionVisitor);
      counterCollectionVisitor =
        new CounterCollectionVisitor(counterSetupVisitor);
      termCollectionVisitor.prepare();
      documentIterator.accept(termCollectionVisitor);
      counterSetupVisitor.prepare();
      documentIterator.accept(counterSetupVisitor);
    }
    if(stopWordsBlocked) {
      // use the default list if no custom one was set
      if(stopWords == null) setStopWords(DEFAULT_STOP_WORDS);
    }
    long termId = documentIterator.nextDocument();
    terms: while(termId != DocumentIterator.END_OF_LIST && termId != -1) {
      int termCount = -1;
      if(countsEnabled) {
        counterSetupVisitor.clear();
        documentIterator.acceptOnTruePaths(counterCollectionVisitor);
        termCount = 0;
        for(int aCount : counterSetupVisitor.count)
          termCount += aCount;
      }
      String termString = null;
      // get the term string
      try {
        termString = atomicIndex.getDirectTerm(termId).toString();
      } catch(Exception e) {
        System.err.println("Error reading indirect index term with ID " +
          termId);
        e.printStackTrace();
        termId = documentIterator.nextDocument();
        continue terms;
      }
      if(stopWordsBlocked && stopWords.contains(termString)) {
        // skip this term
        termId = documentIterator.nextDocument();
        continue terms;
      }
      if(indexType == IndexType.ANNOTATIONS) {
        if(!annotationHelper.isMentionUri(termString)) {
          // skip this term (not produced by our helper)
          termId = documentIterator.nextDocument();
          continue terms;
        }
        if(describeAnnotations) {
          termDescriptions.add(annotationHelper.describeMention(termString));
        }
      }
      termStrings.add(termString);
      if(countsEnabled) {
        termCounts.add(termCount);
      }
      termId = documentIterator.nextDocument();
    }
    // construct the result
    TermsResultSet res =
      new TermsResultSet(termStrings.toArray(new String[termStrings.size()]),
        null, countsEnabled ? termCounts.toIntArray() : null,
        describeAnnotations
          ? termDescriptions.toArray(new String[termDescriptions.size()])
          : null);
    if(describeAnnotations) res = TermsResultSet.groupByDescription(res);
    return res;
  }

  /**
   * Should stop words be filtered out from the results? Defaults to
   * <code>false</code>.
   * 
   * @return the stopWordsBlocked
   */
  public boolean isStopWordsBlocked() {
    return stopWordsBlocked;
  }

  /**
   * Enables or disables the filtering of stop words from the results. If a
   * custom list of stop words has been set (by calling
   * {@link #setStopWords(String[])}) then it is used, otherwise the
   * {@link #DEFAULT_STOP_WORDS} list is used.
   * 
   * @param stopWordsBlocked
   *          the stopWordsBlocked to set
   */
  public void setStopWordsBlocked(boolean stopWordsBlocked) {
    this.stopWordsBlocked = stopWordsBlocked;
  }

  /**
   * Gets the current custom list of stop words.
   * 
   * @return the stopWords
   */
  public Set<String> getStopWords() {
    return stopWords;
  }

  public void setStopWords(Set<String> stopWords) {
    this.stopWords = new HashSet<String>(stopWords);
  }

  /**
   * Sets the custom list of stop words that should be blocked from query
   * results. The actual blocking also needs to be enabled by calling
   * {@link #setStopWordsBlocked(boolean)}. If this array is set to
   * <code>null<code>, then the 
   * {@link #DEFAULT_STOP_WORDS} are used.
   * 
   * @param stopWords
   *          the stopWords to set
   */
  public void setStopWords(String[] stopWords) {
    this.stopWords = new HashSet<String>(stopWords.length);
    for(String sw : stopWords)
      this.stopWords.add(sw);
  }
}
