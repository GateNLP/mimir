/*
 *  AbstractQueryExecutor.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 4 Mar 2009
 *
 *  $Id: AbstractQueryExecutor.java 16667 2013-04-29 16:35:35Z valyt $
 */
package gate.mimir.search.query;

import gate.mimir.search.QueryEngine;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.search.DocumentIterator;
import it.unimi.di.big.mg4j.search.IntervalIterator;

import java.io.IOException;
import java.util.NoSuchElementException;


/**
 * A parent class for all query executors, containing some common functionality.
 */
public abstract class AbstractQueryExecutor implements QueryExecutor {
  
  
  /**
   * The latest document ID returned by a call to nextDocument.  Initially this 
   * value is set to -2, will be -1 if there are no more hits.
   */
  protected long latestDocument;
    
  /**
   * Flag to mark whether the executor has been closed.
   */
  protected boolean closed = false;
  
  /**
   * The {@link QueryEngine} in which we run.
   */
  protected QueryEngine engine;
  
  /**
   * The {@link QueryNode} for the query being executed.
   */
  protected QueryNode queryNode;
  
  
  protected AbstractQueryExecutor(QueryEngine engine, QueryNode qNode){
    this.engine = engine;
    this.queryNode = qNode;
    latestDocument = -2;
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.query.QueryExecutor#getLatestDocument()
   */
  public long getLatestDocument() {
    return latestDocument;
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.query.QueryExecutor#close()
   */
  public void close() throws IOException {
    closed = true;
  }

  public QueryEngine getQueryEngine() {
    return engine;
  }

  
  @Override
  public QueryNode getQueryNode() {
    return queryNode;
  }

  // Implementation for the MG4J DocumentIterator interface (only used for ranking)
  @Override
  public IntervalIterator intervalIterator() throws IOException {
    throw new UnsupportedOperationException("This method is not implemented.");
  }

  @Override
  public IntervalIterator intervalIterator(Index index) throws IOException {
    throw new UnsupportedOperationException("This method is not implemented.");
  }

  @Override
  public Reference2ReferenceMap<Index, IntervalIterator> intervalIterators()
    throws IOException {
    throw new UnsupportedOperationException("This method is not implemented.");
  }

  //@Override
//  public int nextInt() {
//    try {
//      int nextDoc = nextDocument(-1);
//      if(nextDoc < 0) throw new NoSuchElementException();
//      return nextDoc;
//    } catch(IOException e) {
//      throw new RuntimeException(e);
//    }
//  }

  @Override
  public long nextDocument() throws IOException {
    return nextDocument(-1);
  }

  @Override
  public long document() {
    return getLatestDocument();
  }

  @Override
  public long skipTo(long n) throws IOException {
    long nextDoc = nextDocument(n - 1);
    return nextDoc < 0 ? END_OF_LIST : nextDoc;
  }

  @Override
  public double weight() {
    throw new UnsupportedOperationException("This method is not implemented.");
  }

  @Override
  public DocumentIterator weight(double weight) {
    throw new UnsupportedOperationException("This method is not implemented.");
  }

  @Override
  public void dispose() throws IOException {
    close();
  }

  @Override
  public boolean mayHaveNext() {
    throw new UnsupportedOperationException("This method is not implemented.");
  }

  
}
