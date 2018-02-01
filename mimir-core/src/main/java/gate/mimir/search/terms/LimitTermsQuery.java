/*
 *  LimitTermsQuery.java
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
 *  $Id: LimitTermsQuery.java 16583 2013-03-12 13:07:53Z valyt $
 */
package gate.mimir.search.terms;


/**
 * A wrapper for another terms query that limit the number of returned terms
 * to a certain value.
 * <br>
 * This is not the same as setting the {@link AbstractTermsQuery#limit} 
 * parameter for an {@link AbstractTermsQuery} implementation because, in this 
 * case,  the limit is applied <strong>after</strong> the execution of the 
 * wrapped query has completed. This allows for example to wrap a query into a
 * {@link SortedTermsQuery} (to change the results order) and 
 * <strong>then</strong> limit the number of results. 
 */
public class LimitTermsQuery extends AbstractCompoundTermsQuery {
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -2853628566995944376L;
  
  protected int limit;
  
  public LimitTermsQuery(TermsQuery query, int limit) {
    super(query);
    this.limit = limit;
  }


  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermsQuery#execute(gate.mimir.search.QueryEngine)
   */
  @Override
  public TermsResultSet combine(TermsResultSet... resSets) {
    if(resSets.length != 1) throw new IllegalArgumentException(
      getClass().getName() + " can only combine arrays of length 1.");
    TermsResultSet trs = resSets[0];
    if(trs.termStrings != null && trs.termStrings.length > limit) {
      
      String[] termStrings = new String[limit];
      System.arraycopy(trs.termStrings, 0, termStrings, 0, limit);
      
      int[] termCounts = null;
      if(trs.termCounts != null) {
        termCounts = new int[limit];
        System.arraycopy(trs.termCounts, 0, termCounts, 0, limit);
      }
      
      int[] termLengths = null;
      if(trs.termLengths != null) {
        termLengths = new int[limit];
        System.arraycopy(trs.termLengths, 0, termLengths, 0, limit);
      }
      
      String[] termDescriptions = null;
      if(trs.termDescriptions != null) {
        termDescriptions = new String[limit];
        System.arraycopy(trs.termDescriptions, 0, termDescriptions, 0, limit);
      }
      
      TermsResultSet res = new TermsResultSet(termStrings, termLengths, termCounts, 
          termDescriptions);
      if(trs.originalTermStrings != null) {
        res.originalTermStrings = new String[limit][][];
        System.arraycopy(trs.originalTermStrings, 0, 
            res.originalTermStrings, 0, limit);
      }
      return res;
    } else {
      return trs;  
    }
  }


  /**
   * This method has no effect, as the number of sub-queries is always 1.
   */
  @Override
  public void setCountsStrategy(AbstractCompoundTermsQuery.CompoundCountsStrategy countsStrategy) {
    super.setCountsStrategy(countsStrategy);
  }
}
