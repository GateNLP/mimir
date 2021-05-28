/*
 *  TermTypeTermsQuery.java
 *
 *  Copyright (c) 2007-2013, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 28 Feb 2013
 *
 *  $Id: TermTypeTermsQuery.java 17253 2014-01-29 12:01:38Z valyt $
 */
package gate.mimir.search.terms;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.AtomicIndex;
import gate.mimir.search.IndexReaderPool;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryEngine.IndexType;

/**
 * A {@link TermsQuery} that enumerates all terms of a given type. The type of
 * a term is the name of a token feature, or an annotation type. 
 */
public class TermTypeTermsQuery implements TermsQuery {

  
  /**
   * Serialization ID
   */
  private static final long serialVersionUID = 2339622263438077062L;

  private static final Logger logger = LoggerFactory.getLogger(TermTypeTermsQuery.class);
  
  /**
   * The term type for this query.  The type of a term is the name of a token 
   * feature, or an annotation type. 
   */
  protected final String termType;
  
  /**
   * The sub-index type in which the terms should be looked up.
   */
  protected final IndexType indexType;
  
  /**
   * If set to true, term strings for annotation mentions are replaced with
   * their description (see
   * {@link SemanticAnnotationHelper#describeMention(String)}.
   */
  protected final boolean describeAnnotations;
  
  /**
   * If set to true, term counts are calculated during the execution of the
   * query.
   */
  protected final boolean countsEnabled;
  
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
   * Constructs a new term-type terms query.
   * @param termType the type of the terms sought. The type of a term is the 
   * name of a token feature, or an annotation type. 
   * @param indexType the type of sub-index used for the query 
   * ({@link IndexType#ANNOTATIONS}, or {@link IndexType#TOKENS}).
   * @param countsEnabled should term counts be calculated and returned?
   * @param describeAnnotations should term descriptions be produced? This is 
   * only relevant for queries against annotation indexes. See 
   * {@link TermsResultSet#termDescriptions} for more details.
   */
  public TermTypeTermsQuery(String termType, IndexType indexType, 
          boolean countsEnabled,
          boolean describeAnnotations) {
    super();
    this.termType = termType;
    this.indexType = indexType;
    this.countsEnabled = countsEnabled;
    this.describeAnnotations = describeAnnotations;
  }

  /**
   * Constructs a new term-type terms query, with the specified term type and 
   * index type. Term counts are collected, and term descriptions are requested
   * if the provided indexType value is {@link IndexType#ANNOTATIONS}.
   * @param termType the type of the terms sought. The type of a term is the 
   * name of a token feature, or an annotation type. 
   * @param indexType the type of sub-index used for the query 
   * ({@link IndexType#ANNOTATIONS}, or {@link IndexType#TOKENS}).
   */
  public TermTypeTermsQuery(String termType, IndexType indexType) {
    this(termType, indexType, true, (indexType == IndexType.ANNOTATIONS));
  }
  
  public boolean isStopWordsBlocked() {
    return stopWordsBlocked;
  }

  /**
   * Sets whether stop words should be filtered out of the results.
   * @param stopWordsBlocked
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

  /**
   * Provides the set of stop words to be used. The actual blocking also needs
   * to be enabled by calling {@link #setStopWordsBlocked(boolean)}.
   * @param stopWords
   */
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

  @Override
  public TermsResultSet execute(QueryEngine engine) throws IOException {
    // prepare local data
    ObjectArrayList<String> termStrings = new ObjectArrayList<String>();
    ObjectArrayList<String> termDescriptions = describeAnnotations ? 
        new ObjectArrayList<String>() : null;
    IntArrayList termCounts = countsEnabled ? new IntArrayList() : null;    
    AtomicIndex atomicIndex = null;
    SemanticAnnotationHelper annotationHelper = null;
    switch(indexType){
      case ANNOTATIONS:
        atomicIndex = engine.getAnnotationIndex(termType);
        annotationHelper = engine.getAnnotationHelper(termType);
        break;
      case TOKENS:
        atomicIndex = engine.getTokenIndex(termType);
        break;
      default:
        throw new IllegalArgumentException("Invalid index type: " +
          indexType.toString());
    }
    
    if(stopWordsBlocked && stopWords == null) {
      setStopWords(AbstractIndexTermsQuery.DEFAULT_STOP_WORDS);
    }
    
    // once we have the index reader, we scan the whole dictionary
    termId:for(long i = 0; i < atomicIndex.getDirectTerms().size64(); i++) {
      String termString = atomicIndex.getDirectTerm(i).toString();
      // check this term should be returned
      if(indexType == IndexType.ANNOTATIONS &&
         !annotationHelper.isMentionUri(termString)) continue termId;
      if(indexType == IndexType.TOKENS && stopWordsBlocked && 
             stopWords.contains(termString))  continue termId;
      
      termStrings.add(termString);
      if(indexType == IndexType.ANNOTATIONS && describeAnnotations) {
        termDescriptions.add(annotationHelper.describeMention(termString));
      }
      if(countsEnabled) {
        long termCount = atomicIndex.getDirectTermOccurenceCount(i);
        if(termCount > Integer.MAX_VALUE) {
          logger.warn("Term count lenght greater than 32 bit. Data was pratially lost!");
          termCounts.add(Integer.MAX_VALUE);
        } else {
          termCounts.add((int)termCount);
        }
      }
      
    }
    TermsResultSet res = new TermsResultSet(
        termStrings.toArray(new String[termStrings.size()]),
        null, 
        countsEnabled ? termCounts.toIntArray() : null,
        describeAnnotations ? 
          termDescriptions.toArray(new String[termDescriptions.size()]) : null);
    if(describeAnnotations) res = TermsResultSet.groupByDescription(res);
    return res;
  }  
}
