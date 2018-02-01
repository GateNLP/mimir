/*
 *  ContainsQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 20 Mar 2009
 *  $Id: ContainsQuery.java 14541 2011-11-14 19:31:23Z ian_roberts $
 */
package gate.mimir.search.query;


import gate.mimir.search.QueryEngine;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;


/**
 * Filtering query that matches hits from the target query that
 * contain a hit of the filter query, i.e. any
 * X that has a Y within it.
 */
public class ContainsQuery extends AbstractOverlapQuery {

  private static final long serialVersionUID = -3152202241528149456L;

  public ContainsQuery(QueryNode outerQuery, QueryNode innerQuery) {
    super(innerQuery,  outerQuery);
  }

  public QueryExecutor getQueryExecutor(QueryEngine engine) throws IOException {
    return new AbstractOverlapQuery.OverlapQueryExecutor(this, engine, 
            SubQuery.OUTER);
  }
  
  public String toString(){
    return "CONTAINS (\nOUTER:" + outerQuery.toString() + ",\nINNER:" + 
        innerQuery.toString() +"\n)";
  }
}
