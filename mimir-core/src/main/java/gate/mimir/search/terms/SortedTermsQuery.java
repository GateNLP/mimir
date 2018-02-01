/*
 *  SortedTermsQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 18 Jul 2012
 *
 *  $Id: SortedTermsQuery.java 16583 2013-03-12 13:07:53Z valyt $
 */
package gate.mimir.search.terms;

import gate.mimir.search.terms.AbstractCompoundTermsQuery.CompoundCountsStrategy;
import it.unimi.dsi.fastutil.Arrays;
import it.unimi.dsi.fastutil.ints.IntComparator;

/**
 * A wrapper for another terms query that simply sorts the returned terms based
 * on some criteria.
 */
public class SortedTermsQuery extends AbstractCompoundTermsQuery {
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -4763084996036534582L;
  

  public static enum SortOrder {
    
    /** Sort by counts */
    COUNT,
    
    /** Sort by counts (descending). */
    COUNT_DESC,
    
    /** Sort by term string */
    STRING,
    
    /** Sort by term string (descending) */
    STRING_DESC,
    
    /** Sort by term string */
    DESCRIPTION,
    
    /** Sort by term string (descending) */
    DESCRIPTION_DESC
  }
  
  protected SortOrder[] criteria;
  
  /**
   * The default sort criteria: returned terms are sorted by:
   * <ul>
   *   <li>terms count (descending), then by</li>
   *   <li>term string (ascending), then by</li>
   *   <li>term ID (ascending)</li>
   * </ul> 
   */
  public static final SortOrder[] DEFAULT_SORT_CRITERIA = new SortOrder[]
      { SortOrder.COUNT_DESC, SortOrder.STRING};
  
  /**
   * Creates a new sorted terms query, wrapping the provided query, and using 
   * the given sort criteria.
   * @param query
   * @param criteria
   */
  public SortedTermsQuery(TermsQuery query, SortOrder... criteria) {
    super(query);
    this.criteria = criteria;
  }

  /**
   * Creates a new sorted terms query, wrapping the provided query, and using 
   * the {@link #DEFAULT_SORT_CRITERIA}. 
   * @param query
   */
  public SortedTermsQuery(TermsQuery query) {
   this(query, DEFAULT_SORT_CRITERIA);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermsQuery#execute(gate.mimir.search.QueryEngine)
   */
  @Override
  public TermsResultSet combine(TermsResultSet... resSets) {
    if(resSets.length != 1) throw new IllegalArgumentException(
      getClass().getName() + " can only combine arrays of length 1.");
    final TermsResultSet trs = resSets[0];
    Arrays.quickSort(0, trs.termStrings.length, new IntComparator() {
      @Override
      public int compare(Integer o1, Integer o2) {
        return compare(o1.intValue(), o2.intValue());
      }
      
      @Override
      public int compare(int k1, int k2) {
        int retval = 0;
        for(SortOrder crit: criteria) {
          switch(crit) {
            case COUNT:
              if(trs.termCounts != null){
                retval = trs.termCounts[k1] - trs.termCounts[k2];
              }
              break;
            case COUNT_DESC:
              if(trs.termCounts != null){
                retval = trs.termCounts[k2] - trs.termCounts[k1];
              }
              break;
            case STRING:
              if(trs.termStrings != null &&
                 trs.termStrings[k1] != null &&
                 trs.termStrings[k2] != null) {
                retval = trs.termStrings[k1].compareTo(trs.termStrings[k2]);
              }
              break;
            case STRING_DESC:
              if(trs.termStrings != null &&
                 trs.termStrings[k1] != null &&
                 trs.termStrings[k2] != null) {
                retval = trs.termStrings[k2].compareTo(trs.termStrings[k1]);
              }
              break;
            case DESCRIPTION:
              if(trs.termDescriptions != null &&
                 trs.termDescriptions[k1] != null &&
                 trs.termDescriptions[k2] != null) {
                retval = trs.termDescriptions[k1].compareTo(trs.termDescriptions[k2]);
              }
              break;
            case DESCRIPTION_DESC:
              if(trs.termDescriptions != null &&
                 trs.termDescriptions[k1] != null &&
                 trs.termDescriptions[k2] != null) {
                retval = trs.termDescriptions[k2].compareTo(trs.termDescriptions[k1]);
              }
           break;         
          }
          if(retval != 0) return retval;
        }
        return retval;
      }
    }, new TermsResultSet.Swapper(trs));
    return trs;
  }
  
  /**
   * This method has no effect, as the number of sub-queries is always 1.
   */
  @Override
  public void setCountsStrategy(AbstractCompoundTermsQuery.CompoundCountsStrategy countsStrategy) {
    super.setCountsStrategy(countsStrategy);
  }  
}
