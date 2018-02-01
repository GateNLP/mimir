/*
 *  TermQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 13 Aug 2013
 *
 *  $Id: ConstTermsQuery.java 16782 2013-08-14 08:40:44Z valyt $
 */
package gate.mimir.search.terms;

import gate.mimir.search.QueryEngine;

import java.io.IOException;

/**
 * A terms query that returns a pre-defined (constant) terms result set.
 * 
 * One example usage for this is inserting externally computed terms result 
 * sets as operands into {@link CompoundTermsQuery}s. 
 */
public class ConstTermsQuery implements TermsQuery {
  
  private static final long serialVersionUID = -1993516325057279128L;
  
  /**
   * The {@link TermsResultSet} to be returned upon &quot;execution&quot;. 
   */
  protected TermsResultSet trs;
  
  
  
  /**
   * Constructs a new instance of {@link ConstTermsQuery}.
   * @param trs the terms result set to be returned by the constant terms query.
   */
  public ConstTermsQuery(TermsResultSet trs) {
    this.trs = trs;
  }



  @Override
  /**
   * Returns the terms result set provided at construction time. This method
   * performs no calculation.
   */
  public TermsResultSet execute(QueryEngine engine) throws IOException {
    return trs;
  }
}
