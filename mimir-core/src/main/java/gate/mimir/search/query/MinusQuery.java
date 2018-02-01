/**
 *  Copyright (c) 2007-2012, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 12 11 2012
 *  
 *  $Id*/
package gate.mimir.search.query;

import gate.mimir.search.QueryEngine;
import gate.mimir.search.query.AbstractOverlapQuery.SubQuery;

import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.search.visitor.DocumentIteratorVisitor;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A binary query operator that returns all the hits from the left operand that 
 * are not also returned by the right operand. 
 */
public class MinusQuery implements QueryNode {
  
  public static class MinusQueryExecutor extends AbstractQueryExecutor{

    protected QueryExecutor left;
    
    protected QueryExecutor right;
    
    protected List<Binding> hitsOnCurrentDocument;
    
    public MinusQueryExecutor(QueryEngine engine, MinusQuery qNode) throws IOException {
      super(engine, qNode);
      left = qNode.left.getQueryExecutor(engine);
      right = qNode.right.getQueryExecutor(engine);
      hitsOnCurrentDocument = new ArrayList<Binding>();
    }
    
    @Override
    public void close() throws IOException {
      if(closed) return;
      super.close();
      left.close();
      right.close();
    }

    @Override
    public long nextDocument(long greaterThan) throws IOException {
      if(closed) return latestDocument = -1;
      if(latestDocument == -1) return latestDocument;
      hitsOnCurrentDocument.clear();
      // any more input documents from left?
      latestDocument = left.nextDocument(greaterThan);
      while(hitsOnCurrentDocument.isEmpty() && latestDocument != -1){
        // check if right needs advancing
        long rightNext = right.getLatestDocument();
        if(rightNext != -1 && rightNext < latestDocument){
          rightNext = right.nextDocument(latestDocument - 1);
        }
        if(latestDocument == rightNext) {
          // filter the hits
          Binding leftHit = left.nextHit();
          Binding rightHit = right.nextHit();
          while(leftHit != null) {
            int cmp = rightHit == null ? -1 : leftHit.compareTo(rightHit);
            if(cmp < 0) {
              hitsOnCurrentDocument.add(leftHit);
              leftHit = left.nextHit();
            } else if(cmp == 0) {
              // skip this left hit
              leftHit = left.nextHit();
            } else {
              rightHit = right.nextHit();
            }
          }
        } else {
          // all hits from left are good
          Binding hit = left.nextHit();
          while(hit != null){
            hitsOnCurrentDocument.add(hit);
            hit = left.nextHit();
          }
        }
        // if all hits were vetoed, try the next doc
        if(hitsOnCurrentDocument.isEmpty()){
          latestDocument = left.nextDocument(greaterThan);
        }
      }
      return latestDocument;
    }

    @Override
    public Binding nextHit() throws IOException {
      if(closed) return null;
      return hitsOnCurrentDocument.isEmpty() ?  null : hitsOnCurrentDocument.remove(0);
    }

    @Override
    public ReferenceSet<Index> indices() {
      return left.indices();
    }

    @Override
    public <T> T accept(DocumentIteratorVisitor<T> visitor) throws IOException {
      if ( ! visitor.visitPre( this ) ) return null;
      final T[] a = visitor.newArray( 2 );
      if ( a == null ) {
        if ( left.accept( visitor ) == null ) return null;
        if ( right.accept( visitor ) == null ) return null;
      } else {
        if ( ( a[ 0 ] = left.accept( visitor ) ) == null ) return null;
        if ( ( a[ 1 ] = right.accept( visitor ) ) == null ) return null;
      }
      return visitor.visitPost( this, a );     
    }

    @Override
    public <T> T acceptOnTruePaths(DocumentIteratorVisitor<T> visitor)
            throws IOException {
      
      if ( ! visitor.visitPre( this ) ) return null;
      final T[] a = visitor.newArray( 1 );
      if ( a == null ) {
        if ( left.acceptOnTruePaths( visitor ) == null ) return null;
      }
      else {
        if ( ( a[ 0 ] = left.acceptOnTruePaths( visitor ) ) == null ) return null;
      }
      return visitor.visitPost( this, a );      
    }
  }
  
  /**
   * The left operand.
   */
  protected QueryNode left;
  
  /**
   * The right operand.
   */
  protected QueryNode right;

  /**
   * Construct a new MINUS query.
   * @param left the left operand
   * @param right the right operand
   */
  public MinusQuery(QueryNode left, QueryNode right) {
    this.left = left;
    this.right = right;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.query.QueryNode#getQueryExecutor(gate.mimir.search.QueryEngine)
   */
  @Override
  public QueryExecutor getQueryExecutor(QueryEngine engine) throws IOException {
    return new MinusQueryExecutor(engine, this);
  }

  public String toString(){
    return "MINUS (\nLEFT:" + left.toString() + ",\nRIGHT:" + 
        right.toString() +"\n)";
  }

  public QueryNode getLeft() {
    return left;
  }

  public QueryNode getRight() {
    return right;
  }
}
