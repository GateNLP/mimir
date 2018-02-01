/*
 *  AbstractCompoundTermsQuery.java
 *
 *  Copyright (c) 2007-2012, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 27 Nov 2012
 *
 *  $Id: AbstractCompoundTermsQuery.java 16784 2013-08-14 18:06:48Z valyt $
 */
package gate.mimir.search.terms;

import java.io.IOException;

import gate.creole.annic.apache.lucene.search.TermQuery;
import gate.mimir.search.QueryEngine;

/**
 * Abstract base class for {@link TermQuery} implementations that wrap a group
 * of {@link TermQuery} sub-queries.
 */
public abstract class AbstractCompoundTermsQuery implements CompoundTermsQuery{

  /**
   * Enum describing different ways term counts can be calculated for a compound
   * terms query.
   * 
   * A compound terms query produces its result set by combining the result sets
   * from a set of constituent sub-queries. When each of the sub-queries 
   * supplies potentially different counts for the same term, different
   * strategies can be employed for deriving the output term count for the 
   * compound query.
   */
  public static enum CompoundCountsStrategy {
    
    /**
     * The output count is the count from the first sub-query found to supply a 
     * count for each output term.
     */
    FIRST,
  
    /**
     * The output count is the maximum count from all of the sub-queries for 
     * each output term.
     */
    MAX, 
  
    /**
     * The output count is the minimum count from all of the sub-queries for 
     * each output term.
     */    
    MIN,
    
    /**
     * The output count is the sum of the counts from all of the sub-queries for 
     * each output term.
     */
    SUM
  }

  /**
   * The wrapped wrappedQuery
   */
  protected TermsQuery[] subQueries;
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -6582029649819116753L;

  public AbstractCompoundTermsQuery(TermsQuery... subQueries) {
    this.subQueries = subQueries;
  }
  
  /**
   * @return the subQueries
   */
  @Override
  public TermsQuery[] getSubQueries() {
    return subQueries;
  }

  /**
   * Executes each sub-query and then calls {@link #combine(TermsResultSet...)}
   * passing the array of {@link TermsResultSet} values thus produced.
   * This implementation is marked final, forcing the sub-classes to place
   * their logic in {@link #combine(TermsResultSet...)}.  
   */
  @Override
  public final TermsResultSet execute(QueryEngine engine) throws IOException {
    TermsResultSet[] resSets = new TermsResultSet[subQueries.length];
    for(int i = 0; i < subQueries.length; i++) {
      resSets[i] = subQueries[i].execute(engine);
    }
    return combine(resSets);
  }

  protected AbstractCompoundTermsQuery.CompoundCountsStrategy countsStrategy;  
  
  /**
   * Gets the current counts strategy. See 
   * {@link #setCountsStrategy(CompoundCountsStrategy)} for more details.
   * @return the countsStrategy
   */
  public AbstractCompoundTermsQuery.CompoundCountsStrategy getCountsStrategy() {
    return countsStrategy;
  }

  /**
   * A compound terms query produces its result set by combining the result sets
   * from a set of constituent sub-queries. When each of the sub-queries 
   * supplies potentially different counts for the same term, different
   * strategies can be employed for deriving the output term count for the 
   * compound query. This method can be used to set what strategy should be used
   * when generating output counts.
   *  
   * @param countsStrategy the countsStrategy to set
   */
  public void setCountsStrategy(AbstractCompoundTermsQuery.CompoundCountsStrategy countsStrategy) {
    this.countsStrategy = countsStrategy;
  }

  /**
   * Given an array of counts, compute the output count taking into account the
   * provided {@link #countsStrategy}.
   * @param counts an array of count values. Zero and negative values are 
   * ignored (interpreted as count not available).
   * @param countsStrategy the chosen counts strategy.
   * @return the computed output count.
   */
  protected static int computeCompoundCount(final int[] counts,
      final CompoundCountsStrategy countsStrategy) {
    int count = 0;
    counts:for(int aCount : counts) {
      if(aCount > 0) {
        switch(countsStrategy){
          case FIRST:
            if(count <= 0) count = aCount;
            break counts;
          case MAX:
            if(aCount > count) count = aCount;
            break;
          case MIN:
            if(count == 0 || aCount < count) count = aCount;
            break;
          case SUM:
            count += aCount;
            break;
          default:
            throw new IllegalArgumentException("Unknown count strategy " + 
                countsStrategy.toString());
        }
      }
    }
    return count;
  }
  

}
