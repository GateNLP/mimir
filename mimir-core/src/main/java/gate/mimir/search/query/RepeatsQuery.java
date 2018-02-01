/*
 *  RepeatsQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 17 Mar 2009
 *    
 *  $Id: RepeatsQuery.java 20208 2017-04-19 08:35:28Z domrout $
 */
package gate.mimir.search.query;


import gate.mimir.search.QueryEngine;

import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.search.visitor.DocumentIteratorVisitor;

import java.io.IOException;
import java.util.*;


/**
 * A query node that wraps another query node. Results for this query are 
 * sequences of consecutive results from the wrapped query,  with lengths 
 * between specified minimum and maximum values.
 * This is similar to a bounded Kleene operator, e.g. {Token}[2,3] in JAPE 
 * notation.
 */
public class RepeatsQuery implements QueryNode {

  private static final long serialVersionUID = 8441324338156901268L;

  /**
   * A {@link QueryExecutor} for repeats queries.
   */
  public static class RepeatsQueryExecutor extends AbstractQueryExecutor{
    
    /**
     * @param engine
     * @param query
     * @throws IOException 
     */
    public RepeatsQueryExecutor(RepeatsQuery query, QueryEngine engine) throws IOException {
      super(engine, query);
      this.query = query;
      this.wrappedExecutor = query.wrappedQuery.getQueryExecutor(engine);
      hitsOnCurrentDocument = new LinkedList<Binding[]>();
    }

    /**
     * Holds the hits on the current document. This data structure is populated
     * by the {@link #nextDocument(int)} method, and is consumed by the 
     * {@link #nextHit()} method.
     * Each entry is a hit (represented as a sequence of consecutive hits from 
     * the wrapped query).
     */
    protected List<Binding[]> hitsOnCurrentDocument;
    
    /**
     * The query being executed.
     */
    protected RepeatsQuery query;
    
    /**
     * The executor for the query wrapped by this RepeastQuery.
     */
    protected QueryExecutor wrappedExecutor;
    
    
    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#close()
     */
    public void close() throws IOException {
      if(closed) return;
      super.close();
      wrappedExecutor.close();
      hitsOnCurrentDocument.clear();
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextDocument(int)
     */
    public long nextDocument(long greaterThan) throws IOException {
      if(closed) return latestDocument = -1;
      if(latestDocument == -1) return latestDocument;
      long nextDoc = -1;
      while(nextDoc < 0){
        nextDoc = wrappedExecutor.nextDocument(greaterThan);
        if(nextDoc < 0){
          //no more docs
          return latestDocument = nextDoc;
        }
        getHitsOnCurrentDocument();
        if(hitsOnCurrentDocument.isEmpty()) nextDoc = -1;
      }
      return latestDocument = nextDoc;
    }

    protected void getHitsOnCurrentDocument() throws IOException{
      //extract all hits on the current document
      List<Binding> hits = new ArrayList<Binding>();
      Binding aHit = wrappedExecutor.nextHit();
      while(aHit != null){
        hits.add(aHit);
        aHit = wrappedExecutor.nextHit();
      }
      //calculate the marks for each hit, at each position in the sequence
      int[][] marks = new int[query.max][];
      for(int i = 0; i < marks.length; i++){
        marks[i] = new int[hits.size()];
      }
      //we mark from right to left, starting from the penultimate list
      //for the last list, all elements are marked as valid
      //VT: next lines removed, as Java initialises arrays with 0
//      for(int i = 0; i < marks[marks.length -1].length; i++){
//        marks[marks.length -1][i] = 0;
//      }
      int currentSlot = marks.length -2;
      while(currentSlot >= 0){
        //Should we only mark an entry if its next slot has a suffix itself?
        boolean suffixCompulsory = currentSlot < query.min -2;
        for(int i = 0 ; i < hits.size(); i++ ){
          //for each candidate hit
          int nextStart = hits.get(i).getTermPosition() + 
              hits.get(i).getLength();
          //find the first hit in the next list and store its pointer
          //search for the first valid next hit
          //we start from the current hit position +1 (hits are ordered, and a 
          //hit cannot follow itself)
          int nextHitIdx = i + 1;
          //first skip the hits too small
          while(nextHitIdx < marks[currentSlot + 1].length && 
                hits.get(nextHitIdx).getTermPosition() < nextStart){
            nextHitIdx++;
          }
          //next process all candidates, until we find a valid one, or we run out
          marks[currentSlot][i] = -1;
          while(nextHitIdx < marks[currentSlot + 1].length &&
                hits.get(nextHitIdx).getTermPosition() == nextStart){
            //A good hit is one that:
            // - starts at nextStart, and
            // - if suffixCompulsory, has a non-negative mark itself
            if(suffixCompulsory){
              if(marks[currentSlot +1][nextHitIdx] >= 0){
                //we found the next hit
                marks[currentSlot][i] = nextHitIdx;
                break;
              }else{
                nextHitIdx++;
              }
            }else{
              //no other conditions required
              marks[currentSlot][i] = nextHitIdx;
              break;
            }
          }
        }//for(int i = 0 ; i < hits.size(); i++ )
        currentSlot--;
      }//while(currentSlot >= 0)
      
      //we finished marking, now we collect results from left to right
      hitsOnCurrentDocument.clear();
      //for each marked starting point on the first slot, start enumerating the 
      //hits
      for(int i = 0; i < marks[0].length; i++){
        if(marks[0][i] >= 0 || query.min == 1){
          Binding[] slots = new Binding[query.max];
          extractHitsRec(query, hitsOnCurrentDocument, hits, marks, 0, i, slots);
        }
      }
      
    }
    
    /**
     * Recursively extracts the hits 
     * @param hits the arrays of hits, one row for each of the {@link #executorsCache}.
     * @param marks the marks for the hits 
     * @param currentSlot which slot to fill at this stage (used for recursion).
     * @param currentHit for the current slot, which candidate hit to start from.
     * @param slots the array of slots that needs filling. 
     * @see #computeMark(int, int, Binding[], int[])
     */
    protected static void extractHitsRec(RepeatsQuery query, 
            List<Binding[]> results, List<Binding> hits, int[][] marks,
            int currentSlot, int currentHit, Binding[] slots){
      slots[currentSlot] = hits.get(currentHit);
      if(currentSlot >= query.min -1){
        //we have a full result -> make a copy of the right length and add it
        Binding[] aResult = new Binding[currentSlot +1];
        for(int i = 0; i<= currentSlot; i++) aResult[i] = slots[i];
        results.add(aResult);
      }
      //if we're not at the end yet, keep recursing
      if(currentSlot < query.max -1){
        if(marks[currentSlot][currentHit] >=0){
          //recursive call for the first next candidate
          extractHitsRec(query, results, hits, marks, currentSlot + 1, 
                  marks[currentSlot][currentHit], slots);
          //now find all other candidates for next slot
          for(int i = marks[currentSlot][currentHit] +1; 
              i < marks[currentSlot + 1].length &&
              slots[currentSlot].getTermPosition() + 
              slots[currentSlot].getLength() == hits.get(i).getTermPosition(); 
              i++){
            //The next candidate starts at the right position.
            //If it is valid, add it to solution and do recursive call.
            //do we need a suffix?
            if(currentSlot < query.min -2){
              //the next hit must have a valid mark itself
              if(marks[currentSlot + 1][i] >= 0){
                //copy the slots array
                Binding[] newSlots = new Binding[slots.length];
                for(int j = 0; j <= currentSlot; j++){
                  newSlots[j] = slots[j];
                }
                extractHitsRec(query, results, hits, marks, currentSlot + 1, i, newSlots);
              }
            }else{
              //we don't need to enforce a suffix -> unconditional recursive call
              Binding[] newSlots = new Binding[slots.length];
              for(int j = 0; j <= currentSlot; j++){
                newSlots[j] = slots[j];
              }
              extractHitsRec(query, results, hits, marks, currentSlot + 1, i, newSlots);
            }
          }
        }else{
          //no furhter advance possible
        }
      }
    }
    
    
    
    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextHit()
     */
    public Binding nextHit() throws IOException {
      if(closed) return null;
      if(hitsOnCurrentDocument.isEmpty()){
        return null;
      }else{
        Binding[] hitSlots = hitsOnCurrentDocument.remove(0);
        int length = hitSlots[hitSlots.length - 1].getTermPosition() +
            hitSlots[hitSlots.length - 1].getLength() - 
            hitSlots[0].getTermPosition();
        return new Binding(query, hitSlots[0].getDocumentId(),
                hitSlots[0].getTermPosition(),length , 
                (engine.isSubBindingsEnabled() ? hitSlots : null));
      }
    }

    @Override
    public ReferenceSet<Index> indices() {
      return wrappedExecutor.indices();
    }
    
    public <T> T accept( final DocumentIteratorVisitor<T> visitor ) throws IOException {
      if ( ! visitor.visitPre( this ) ) return null;
      final T[] a = visitor.newArray( 1 );
      if ( a == null ) {
        if ( wrappedExecutor.accept( visitor ) == null ) return null;
      }
      else {
        if ( ( a[ 0 ] = wrappedExecutor.accept( visitor ) ) == null ) return null;
      }
      return visitor.visitPost( this, a );
    }

    public <T> T acceptOnTruePaths( final DocumentIteratorVisitor<T> visitor ) throws IOException {
      if ( ! visitor.visitPre( this ) ) return null;
      final T[] a = visitor.newArray( 1 );
      if ( a == null ) {
        if ( wrappedExecutor.acceptOnTruePaths( visitor ) == null ) return null;     
      }
      else {
        if ( ( a[ 0 ] = wrappedExecutor.acceptOnTruePaths( visitor ) ) == null ) return null;
      }
      return visitor.visitPost( this, a );
    }
  }
  
  
  /**
   * The minimum number of repeats required.
   */
  protected int min;
  
  /**
   * The maximum number of repeats permitted.
   */
  protected int max;
  
  /**
   * The wrapped query.
   */
  protected QueryNode wrappedQuery;
  
  /**
   * Creates a new repeats query.
   * @param query the query to be wrapped.
   * @param min the minimum number of repeats required. This value needs to be 
   * greater than 0. 
   * @param max the maximum number of repeats permitted. This value needs to be 
   * greater or equal to the value given for <code>min</code>.
   */
  public RepeatsQuery(QueryNode query, int min, int max){
    this.wrappedQuery = query;
    if(min <= 0) throw new IllegalArgumentException(
            "The value provided for minimum (" + min + 
            ") is not strictly positive!");
    if(max < min) throw new IllegalArgumentException(
            "The value provided for maximum repeats (" + max + 
            ") is smaller than thee value for minimum repeats (" + min + ").");
    this.min = min;
    this.max = max;
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.query.QueryNode#getQueryExecutor(gate.mimir.search.QueryEngine)
   */
  public QueryExecutor getQueryExecutor(QueryEngine engine) throws IOException {
    return new RepeatsQueryExecutor(this, engine);
  }
  
  public String toString() {
    return "REPEATS (" + wrappedQuery.toString() + " [" + min + ".." + 
        max + "])";
  }

  public int getMin() {
    return min;
  }

  public int getMax() {
    return max;
  }

  public QueryNode getWrappedQuery() {
    return wrappedQuery;
  }
}
