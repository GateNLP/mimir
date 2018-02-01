/*
 *  ConstQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 22 Nov 2012
 *
 *  $Id: ConstQuery.java 16782 2013-08-14 08:40:44Z valyt $
 */
package gate.mimir.search.query;

import gate.mimir.search.QueryEngine;

import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.search.visitor.DocumentIteratorVisitor;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

import java.io.IOException;

/**
 * A query that returns a pre-defined (constant) list of document IDs. This 
 * query type does not support positions, so it returns no hits.
 * One example usage for this type of query is as an operand to an AND operator 
 * in order to restrict the results to a given set of documents.
 */
public class ConstQuery implements QueryNode {
  
  /**
   * Serialization ID. 
   */
  private static final long serialVersionUID = 4259330863001338150L;

  /**
   * Executor implementation for {@link ConstQuery}. It returns document IDs
   * from the predefined list, and no positions.
   */
  public static class ConstQueryExecutor extends AbstractQueryExecutor {
    
    public ConstQueryExecutor(ConstQuery qNode, QueryEngine engine) {
      super(engine, qNode);
      this.documentIds = qNode.documentIds;
      latestDocumentPosition = -1;
    }
    
    /**
     * The position in the constant list of document IDs of the latest returned
     * document.
     */
    private int latestDocumentPosition;
    
    /**
     * The predefined list of document IDs.
     */
    private long[] documentIds;
    
    private ReferenceSet<Index> indices;
    
    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextDocument(long)
     */
    @Override
    public long nextDocument(long greaterThan) throws IOException {
      do {
        latestDocumentPosition++;
      } while(latestDocumentPosition < documentIds.length && 
              documentIds[latestDocumentPosition] <= greaterThan);
      if(latestDocumentPosition < documentIds.length) {
        latestDocument = documentIds[latestDocumentPosition];
      } else {
        latestDocument = -1;
      }
      return latestDocument;
    }

    /**
     * This query executor type does not support positions, so it always returns
     * null. 
     * @see gate.mimir.search.query.QueryExecutor#nextHit()
     */
    @Override
    public Binding nextHit() throws IOException {
      return null;
    }

    /**
     * Returns an empty set of indices.
     * @see it.unimi.di.big.mg4j.search.DocumentIterator#indices()
     */
    @Override
    public ReferenceSet<Index> indices() {
      if(indices == null) {
        indices = new ReferenceArraySet<Index>();
      }
      return indices;
    }

    /**
     * Always returns null.
     * @see it.unimi.di.big.mg4j.search.DocumentIterator#accept(it.unimi.di.big.mg4j.search.visitor.DocumentIteratorVisitor)
     */
    @Override
    public <T> T accept(DocumentIteratorVisitor<T> visitor) throws IOException {
      return null;
    }

    /**
     * Always returns null
     * @see it.unimi.di.big.mg4j.search.DocumentIterator#acceptOnTruePaths(it.unimi.di.big.mg4j.search.visitor.DocumentIteratorVisitor)
     */
    @Override
    public <T> T acceptOnTruePaths(DocumentIteratorVisitor<T> visitor)
      throws IOException {
      return null;
    }
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.query.QueryNode#getQueryExecutor(gate.mimir.search.QueryEngine)
   */
  @Override
  public QueryExecutor getQueryExecutor(QueryEngine engine) throws IOException {
    return new ConstQueryExecutor(this, engine);
  }
  
  
  /**
   * Creates a new ConstQuery query. 
   * @param documentIds the document IDs (in ascending order) that should be 
   * returned when this query is executed.
   */
  public ConstQuery(long[] documentIds) {
    this.documentIds = documentIds;
  }

  private long[] documentIds;

  /**
   * @return the documentIds
   */
  public long[] getDocumentIds() {
    return documentIds;
  }
  
}
