/*
 *  OrQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 05 Mar 2009
 *  
 *  $Id: OrQuery.java 16667 2013-04-29 16:35:35Z valyt $
 */
package gate.mimir.search.query;


import gate.mimir.search.QueryEngine;

import it.unimi.dsi.fastutil.Swapper;
import it.unimi.dsi.fastutil.ints.AbstractIntComparator;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntHeapSemiIndirectPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongHeapSemiIndirectPriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.search.visitor.DocumentIteratorVisitor;

import java.io.IOException;
import java.util.*;

/**
 * Query node for OR queries. It wraps an array of sub-queries and performs a
 * disjunction between them all.
 */
public class OrQuery implements QueryNode {

  private static final long serialVersionUID = 5351173042947382168L;

  /**
   * Executes a disjunction of other queries.
   * 
   * The hit candidates list is sorted by document ID, then by termPosition,
   * and then by hit length.
   */
  public static class OrQueryExecutor extends AbstractQueryExecutor{

    /**
     * @param engine
     * @param query
     * @throws IOException 
     */
    public OrQueryExecutor(OrQuery query, QueryEngine engine) throws IOException {
      super(engine, query);
      this.query = query;
      if(query.getNodes() == null || query.getNodes().length == 0) {
        // empty OR: we're already exhausted
        latestDocument = -1;
      } else {
        //prepare all the executors
        this.executors = new ExecutorsList(engine, query.getNodes());
        currentDoc = new long[executors.size()];
        front = new int[executors.size()];
        this.hitsOnCurrentDocument = new ObjectArrayList<Binding>();
        queue = new LongHeapSemiIndirectPriorityQueue(currentDoc);
        for(int i = 0; i < executors.size(); i++){
          long doc = executors.nextDocument(i, -1);
          if (doc >= 0) {
            currentDoc[i] = doc;
            queue.enqueue(i);
          }
        }        
      }
    }
    
    /**
     * The query being executed.
     */
    protected OrQuery query;
    
    
    
    /**
     * A list of hits (from the executors) on the current document. This value
     * is populated when {@link #nextDocument(int)} is called, and it emptied as
     * hits are consumed by calling {@link #nextHit()}.
     */
    protected ObjectArrayList<Binding> hitsOnCurrentDocument;
    
    protected boolean hitsObtained = false;
    
    /**
     * The {@link QueryExecutor}s for the contained nodes.
     */
    protected ExecutorsList executors;
    
    
    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#close()
     */
    public void close() throws IOException {
      if(closed) return;
      super.close();
      if(executors != null) executors.close();
      executors = null;
      hitsOnCurrentDocument.clear();
    }

    
    public long nextDocument(long greaterThan) throws IOException {
      if(closed || queue.isEmpty()) return latestDocument = -1;
      if(latestDocument == -1) return latestDocument;
      // advance
      int first = queue.first();
      while(currentDoc[first] == latestDocument || 
            currentDoc[first] <= greaterThan) {
        currentDoc[first] = executors.nextDocument(first, greaterThan);
        if(currentDoc[first] < 0) {
          queue.dequeue();
          if(queue.isEmpty()) return latestDocument = -1;
        } else {
          queue.changed();            
        }
        first = queue.first();
      }
      latestDocument = currentDoc[first];
      // collect all the hits from the current document
      frontSize =  queue.front(front);
      hitsOnCurrentDocument.clear();
      hitsObtained = false;
      return latestDocument;
    }
    
    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextDocument(int)
     */
    public long nextDocumentOld(long greaterThan) throws IOException {
      if(closed) return latestDocument = -1;
      if(latestDocument == -1) return latestDocument;
      //find the minimum value for latest document
      if (greaterThan >= latestDocument){
        //we need to advance all executors that are lower than greaterThan
        for(int i = 0 ; i < executors.size(); i++){
          if(executors.latestDocument(i) <= greaterThan){
            executors.nextDocument(i, greaterThan);
          }
        }
      } else {
        for(int i = 0 ; i < executors.size(); i++){
          if(executors.latestDocument(i) == latestDocument) {
            executors.nextDocument(i, -1);
          }
        }
      }
      
      //now find the minimum value from latestDocuments, 
      //and prepare the hitsOnCurrentDocument list
      hitsOnCurrentDocument.clear();
      List<Integer> minExecutors = new LinkedList<Integer>();
      long minDoc = Long.MAX_VALUE;
      for(int i = 0; i < executors.size(); i++){
        if(executors.latestDocument(i) >= 0){
          if(executors.latestDocument(i) < minDoc){
            //we found a smaller minimum
            minExecutors.clear();
            minDoc = executors.latestDocument(i);
            minExecutors.add(i);
          }else if(executors.latestDocument(i) == minDoc){
            //another executor on the current document just add to the list
            minExecutors.add(i);
          }
        }
      }
      if(minExecutors.isEmpty()){
        //all executors are out of documents
        return latestDocument = -1;
      }else{
        //for each executor on the current document
        for(int i : minExecutors){
          //extract all results on the new current document
          Binding aHit = executors.nextHit(i);
          while(aHit != null){
            hitsOnCurrentDocument.add(aHit);
            aHit = executors.nextHit(i);
          }
          //move the executor to its next document
//          executors.nextDocument(i, -1);
        }
        //now sort the list of candidates
        it.unimi.dsi.fastutil.Arrays.quickSort(0, hitsOnCurrentDocument.size(),
                new AbstractIntComparator() {
                  @Override
                  public int compare(int one, int other) {
                    return hitsOnCurrentDocument.get(one).compareTo(
                            hitsOnCurrentDocument.get(other));
                  }
                }, 
                new Swapper() {
                  @Override
                  public void swap(int one, int other) {
                    Binding temp = hitsOnCurrentDocument.get(one);
                    hitsOnCurrentDocument.set(one,
                            hitsOnCurrentDocument.get(other));
                    hitsOnCurrentDocument.set(other, temp);
                  }
                });
        return latestDocument = minDoc;
      }
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextHit()
     */
    public Binding nextHit() throws IOException {
      if(closed) return null;
      if(!hitsObtained) {
        for(int i = 0; i < frontSize; i++){
          //extract all results on the new current document
          Binding aHit = executors.nextHit(front[i]);
          while(aHit != null){
            hitsOnCurrentDocument.add(aHit);
            aHit = executors.nextHit(front[i]);
          }
        }
        //now sort the list of candidates
        it.unimi.dsi.fastutil.objects.ObjectArrays.quickSort(
          (Object[])hitsOnCurrentDocument.elements(), 0, 
          hitsOnCurrentDocument.size());
        hitsObtained = true;
      }
      
      if(hitsOnCurrentDocument.isEmpty()){
        //no more hits
        return null;
      }else{
        //return the first hit from the list
        Binding aHit = hitsOnCurrentDocument.get(0);
        hitsOnCurrentDocument.remove(0);
        //prepare the hit to be returned
        Binding[] containedBindings = null;
        if(engine.isSubBindingsEnabled()){
          Binding[] subBindings = aHit.getContainedBindings();
          containedBindings = subBindings == null ?
                  new Binding[1] : new Binding[subBindings.length + 1];
          if(subBindings != null){
            System.arraycopy(subBindings, 0, containedBindings, 1, 
                    subBindings.length);
            aHit.setContainedBindings(null);
          }
          containedBindings[0] = aHit;
        }        
        return new Binding(query, aHit.getDocumentId(), aHit.getTermPosition(),
                aHit.getLength(), containedBindings); 
      }
    }
    
    @Override
    public ReferenceSet<Index> indices() {
      if(indices == null) {
        indices = new ReferenceArraySet<Index>();
        for(int i = 0; i < executors.size(); i++) {
          try {
            QueryExecutor qExec = executors.getExecutor(i);
            indices.addAll(qExec.indices());
          } catch(IOException e) {
            throw new RuntimeException(e);
          }
        }
      }
      return indices;
    }
   
    public <T> T accept(final DocumentIteratorVisitor<T> visitor)
      throws IOException {
      if(!visitor.visitPre(this)) return null;
      int n = executors.size();
      final T[] a = visitor.newArray(n);
      if(a == null) {
        for(int i = 0; i < n; i++)
          if(executors.latestDocument(i) >= 0 &&
            executors.getExecutor(i).accept(visitor) == null) return null;
      } else {
        for(int i = 0; i < n; i++)
          if(executors.latestDocument(i) >= 0 &&
            (a[i] = executors.getExecutor(i).accept(visitor)) == null) return null;
      }
      return visitor.visitPost(this, a);
    }
    
    public <T> T acceptOnTruePaths(final DocumentIteratorVisitor<T> visitor)
      throws IOException {
      if(!visitor.visitPre(this)) return null;
      final T[] a = visitor.newArray(frontSize);
      if(a == null) {
        for(int i = 0; i < frontSize; i++){
          if(executors.getExecutor(front[i]).acceptOnTruePaths(visitor) == null) return null;
        }
      } else {
        for(int i = 0; i < frontSize; i++){
          if((a[front[i]] = executors.getExecutor(front[i]).acceptOnTruePaths(visitor)) == null)
            return null;
        }
      }
      return visitor.visitPost(this, a);
    }
    
    protected ReferenceSet<Index> indices;
    
    protected long[] currentDoc;
    
    protected int front[];
    
    protected int frontSize;
    
    protected LongHeapSemiIndirectPriorityQueue queue;
    
  }
  
  protected QueryNode[] nodes;
  /**
   * Creates anew OR Query from an array of sub-queries.
   * @param nodes the nodes contained by this query.
   */
  public OrQuery(QueryNode... nodes){
    this.nodes = nodes;
  }
  
  
  /**
   * @return the nodes
   */
  public QueryNode[] getNodes() {
    return nodes;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.query.QueryNode#getQueryExecutor(gate.mimir.search.QueryEngine)
   */
  public QueryExecutor getQueryExecutor(QueryEngine engine) throws IOException {
    return new OrQueryExecutor(this, engine);
  }
  
  public String toString() {
    StringBuilder str = new StringBuilder("OR (");
    if(nodes != null){
      for(int  i = 0; i < nodes.length -1; i++){
        str.append(nodes[i].toString());
        str.append(", ");
      }
      str.append(nodes[nodes.length -1].toString());
    }
    str.append(")");
    return str.toString();
  }

}
