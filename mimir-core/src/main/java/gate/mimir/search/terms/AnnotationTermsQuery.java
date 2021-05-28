/*
 *  AnnotationTermQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *  
 *  Valentin Tablan, 13 Jul 2012
 *
 *  $Id: AnnotationTermsQuery.java 17255 2014-01-29 15:29:10Z valyt $
 */
package gate.mimir.search.terms;

import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.AtomicAnnotationIndex;
import gate.mimir.index.Mention;
import gate.mimir.search.IndexReaderPool;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.query.AnnotationQuery;
import it.unimi.di.big.mg4j.index.IndexIterator;
import it.unimi.di.big.mg4j.search.DocumentIterator;
import it.unimi.di.big.mg4j.index.IndexReader;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Given an {@link AnnotationQuery}, this finds the set of terms that satisfy 
 * it.
 */
public class AnnotationTermsQuery implements TermsQuery {
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 777418229209857720L;
  
  public AnnotationTermsQuery(AnnotationQuery annotationQuery) {
    this(annotationQuery, false, false);
  }

  public AnnotationTermsQuery(AnnotationQuery annotationQuery,
                              boolean countsEnabled,
                              boolean describeAnnotations) {
    super();
    this.annotationQuery = annotationQuery;
    this.countsEnabled = countsEnabled;
    this.describeAnnotations = describeAnnotations;
  }
  
  protected AnnotationQuery annotationQuery;
  
  /**
   * If set to true, term strings for annotation mentions are replaced with
   * their description (see
   * {@link SemanticAnnotationHelper#describeMention(String)}.
   */
  protected final boolean describeAnnotations;
  
  /**
   * If set to true, for each returned term (i.e mention URI) the inverted 
   * index is used to provide the number of occurrences. 
   */
  protected final boolean countsEnabled;
  
  private static final Logger logger = LoggerFactory.getLogger(AnnotationTermsQuery.class);
  
  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermQuery#execute()
   */
  @Override
  public TermsResultSet execute(QueryEngine engine) throws IOException {
    // find the semantic annotation helper for the right annotation type
    SemanticAnnotationHelper helper = 
        engine.getAnnotationHelper(annotationQuery);
    // ask the helper for the mentions that correspond to this query
    long start = System.currentTimeMillis();      
    List<Mention> mentions = helper.getMentions(
        annotationQuery.getAnnotationType(),
        annotationQuery.getConstraints(), engine);
    logger.debug(mentions.size() + " mentions obtained in " + 
      (System.currentTimeMillis() - start) + " ms");
  
    if(mentions.size() > 0) {
      String[] terms = new String[mentions.size()];
      String[] termDescriptions = describeAnnotations ? 
          new String[mentions.size()] : null;
      int[] counts = null;
      AtomicAnnotationIndex atomicAnnIndex = null;
      IndexReader annotationIndexReader = null;
      try {
        if(countsEnabled) {
          counts = new int[mentions.size()];
          atomicAnnIndex = engine.getAnnotationIndex(
            annotationQuery.getAnnotationType());
          annotationIndexReader = atomicAnnIndex.getIndex().getReader();
        }
        
        int[] lengths = new int[mentions.size()];
        int index = 0;
        for(Mention m : mentions) {
          terms[index] = m.getUri();
          lengths[index] = m.getLength();
          if(countsEnabled) {
            counts[index] = 0;
            IndexIterator iIter = annotationIndexReader.documents(terms[index]);
            while(iIter.nextDocument() != DocumentIterator.END_OF_LIST) {
              counts[index] += iIter.count();
            }
          }
          if(describeAnnotations) {
            termDescriptions[index] = helper.describeMention(terms[index]);
          }
          index++;
        }
        return new TermsResultSet(terms, lengths, counts, termDescriptions);
      } finally {
        if(annotationIndexReader != null) {
          annotationIndexReader.close();
        }
      }
    } else {
      return TermsResultSet.EMPTY;
    }
  }
  
}
