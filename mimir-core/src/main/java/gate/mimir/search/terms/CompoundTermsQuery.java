/*
 *  CompoundTermsQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 28 Nov 2012
 *
 *  $Id: CompoundTermsQuery.java 16350 2012-11-28 16:56:17Z valyt $
 */
package gate.mimir.search.terms;

/**
 * A {@link TermsQuery} that combines a group of other terms queries.
 * Apart from the {@link #execute(gate.mimir.search.QueryEngine)} method, these
 * query implementations must be also able to execute their logic by simply 
 * combining a pre-built set of {@link TermsResultSet} values. This is used when
 * the component sub-queries have to be executed externally (possibly on a 
 * remote server), and then combined locally. This functionality is exposed by 
 * the {@link #combine(TermsResultSet...)} method.  
 */
public interface CompoundTermsQuery extends TermsQuery {
  
  /**
   * Gets the sub-queries that are part of this compound query.
   * @return
   */
  public TermsQuery[] getSubQueries();
  
  /**
   * Applies the logic of this query operator to an array of ready-constructed
   * result sets representing the results of the sub-queries. 
   * @param resSets the result sets to be combined according to the logic of 
   * this operator. 
   * @return
   */
  public TermsResultSet combine(TermsResultSet... resSets);
  
}
