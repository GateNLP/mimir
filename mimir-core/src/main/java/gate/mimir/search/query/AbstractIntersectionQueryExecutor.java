/*
 *  AbstractIntersectionQueryExecutor.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 8 Jul 2009
 *
 *  $Id: AbstractIntersectionQueryExecutor.java 16667 2013-04-29 16:35:35Z valyt $
 */
package gate.mimir.search.query;


import gate.mimir.search.QueryEngine;

import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.search.visitor.DocumentIteratorVisitor;

import java.io.IOException;
import java.util.Arrays;


/**
 * An abstract query executor that implements the nextDocument() functionality
 * shared between all query executors that combine a set of sub-executors and
 * only need to return results on common documents. 
 */
public abstract class AbstractIntersectionQueryExecutor extends AbstractQueryExecutor {
  
  /**
   * The sub-queries
   */
  protected QueryNode[] nodes;
  
  /**
   * An array of current nextDocumentID values, for all of the sub nodes.
   */
  protected long[] nextDocIDs;
  
  protected ReferenceSet<Index> indices;
  
  /**
   * The {@link QueryExecutor}s for the contained nodes.
   */
  protected QueryExecutor[] executors;
  
  /**
   * Constructor from {@link QueryEngine}.
   * @throws IOException if the index files cannot be accessed.
   */
  public AbstractIntersectionQueryExecutor(QueryEngine engine, QueryNode query,
          QueryNode... subNodes) throws IOException {
    super(engine, query);
    this.nodes = subNodes;
    // prepare all the executors
    this.executors = new QueryExecutor[subNodes.length];
    this.nextDocIDs = new long[executors.length];
    for(int i = 0; i < subNodes.length; i++) {
      executors[i] = subNodes[i].getQueryExecutor(engine);
      nextDocIDs[i] = executors[i].nextDocument(-1);
      if(nextDocIDs[i] < 0) {
        // no results!
        latestDocument = -1;
        // shorten the executors array, to avoid NPEs. 
        executors = Arrays.copyOfRange(executors, 0, i + 1);
        break;
      }
    }
  }

  @Override
  public long nextDocument(long greaterThan) throws IOException {
    if(closed) return latestDocument = -1;
    if(latestDocument == -1) return latestDocument;
    // we want all documentIDs to converge to max, which should be at least
    // greaterThan + 1
    // the max value will only move up!
    // Note that the greterThan value can be anything (e.g.-100), so we need to  
    // force the advance by comparing to latestDocument.
    long max = Math.max(latestDocument, greaterThan)  + 1;
    // move all documentIDs to at or over current max,
    // until they all have the same ID
    boolean doneAdvancing = false;
    while(!doneAdvancing) {
      doneAdvancing = true;
      for(int i = 0; i < executors.length; i++) {
        if(nextDocIDs[i] < max) {
          // this needs to move forward to at least max
          nextDocIDs[i] = executors[i].nextDocument(max - 1);
          if(nextDocIDs[i] == -1) {
            // one executor has run out of documents -> we're done here!
            return latestDocument = -1;
          }
          doneAdvancing = false;
        }
        if(nextDocIDs[i] > max) {
          max = nextDocIDs[i];
          //we need to move all others to the same value
          doneAdvancing = false;
        }
      }
    }
    // If we reached this point, all executors are pointing to the same
    // document (max).
    return latestDocument = max;
  }
  
  
  public <T> T accept( final DocumentIteratorVisitor<T> visitor ) throws IOException {
    if ( ! visitor.visitPre( this ) ) return null;
    int n = executors.length;
    final T[] a = visitor.newArray( n );
    if ( a == null ) {
      for( int i = 0; i < n; i++ ) if ( executors[ i ] != null && executors[ i ].accept( visitor ) == null ) return null;
    }
    else {
      for( int i = 0; i < n; i++ ) if ( executors[ i ] != null && ( a[ i ] = executors[ i ].accept( visitor ) ) == null ) return null;
    }
    return visitor.visitPost( this, a );
  }
  
  public <T> T acceptOnTruePaths( final DocumentIteratorVisitor<T> visitor ) throws IOException {
    if ( ! visitor.visitPre( this ) ) return null;
    int n = executors.length;
    final T[] a = visitor.newArray( n ); 
    if ( a == null ) {
      for( int i = 0; i < n; i++ ) if ( executors[ i ] != null && executors[ i ].acceptOnTruePaths( visitor ) == null ) return null;
    }
    else {
      for( int i = 0; i < n; i++ ) if ( executors[ i ] != null && ( a[ i ] = executors[ i ].acceptOnTruePaths( visitor ) ) == null ) return null;
    }
    return visitor.visitPost( this, a );
  }  
  
  
  @Override
  public ReferenceSet<Index> indices() {
    if(indices == null) {
      indices = new ReferenceArraySet<Index>();
      for(QueryExecutor qExec : executors) {
        indices.addAll(qExec.indices());
      }
    }
    return indices;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.query.AbstractQueryExecutor#close()
   */
  @Override
  public void close() throws IOException {
    if(closed) return;
    super.close();
    for(QueryExecutor anExecutor : executors) {
      if(anExecutor != null) anExecutor.close();
    }
    executors = null;
    nodes = null;
    nextDocIDs = null;
    indices = null;
  }
}
