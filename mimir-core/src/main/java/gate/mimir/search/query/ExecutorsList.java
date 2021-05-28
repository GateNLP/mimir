/*
 *  ExecutorsList.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 27 Aug 2009
 *  
 *  $Id: ExecutorsList.java 15767 2012-05-11 15:45:23Z valyt $
 */
package gate.mimir.search.query;

import gate.mimir.search.QueryEngine;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import java.util.Arrays;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Class for managing a large list of {@link QueryExecutor}s. This class is 
 * responsible for accessing a large set of executors while only keeping a 
 * limited number of them loaded. It automatically manages the closing and 
 * reopening of executors as needed.  
 * 
 */
public class ExecutorsList {

  
  /**
   * Constructor.
   * 
   * @param maxLiveExecutors how many executor should maximally be kept in 
   * memory.
   * @param engine the {@link QueryEngine} used to create executors.
   * @param nodes the {@link QueryNode} for which the executors are created.
   */
  public ExecutorsList(int maxLiveExecutors, QueryEngine engine, 
          QueryNode[] nodes) {
    this.maxLiveExecutors = maxLiveExecutors;
    this.engine = engine;
    this.nodes = nodes;
    this.closed = false;
    
    latestDocuments = new long[nodes.length];
    Arrays.fill(latestDocuments, EXECUTOR_NOT_STARTED);
    hitsOnLatestDocument = new Binding[nodes.length][];
    hitsReturned = new int[nodes.length];
    
    executorsOpened = 0;
    executorsClosed = 0;
    
    // alternative implementation
    executors = new QueryExecutor[nodes.length];
    executorsNext = new int[nodes.length];
    executorsPrev = new int[nodes.length];
    executorsFirst = -1;
    executorsLast = -1;
    executorsSize = 0;
  }

  /**
   * Returns the number of nodes/executors managed by this list.
   * @return
   */
  public int size(){
    return nodes == null ? 0 : nodes.length;
  }
  
  /**
   * Constructor that uses the default maximum number of live executors. 
   * @param engine the {@link QueryEngine} used to create executors.
   * @param nodes the {@link QueryNode}s for which the executors are created.
   */
  public ExecutorsList(QueryEngine engine, QueryNode[] nodes) {
    this(DEFAULT_MAX_LIVE_EXECUTORS, engine, nodes);
  }


  public QueryExecutor getExecutor(int nodeId) throws IOException{
    if(executors[nodeId] == null) { // we need to create a new executor
      executorsSize++;
      if(executorsSize > maxLiveExecutors) { // about to go over: remove last
        executors[executorsLast].close();
        executorsClosed++;
        executors[executorsLast] = null;
        int newLast = executorsPrev[executorsLast];
        executorsNext[newLast] = -1;
        executorsPrev[executorsLast] = -1;
        executorsNext[executorsLast] = -1;
        executorsLast = newLast;
      }
      // open the new executor
      executors[nodeId] = nodes[nodeId].getQueryExecutor(engine);
      executorsOpened++;
      // add first to the list
      executorsNext[nodeId] = executorsFirst;
      executorsPrev[nodeId] = -1;
      if(executorsFirst != -1) { // old first becomes second
        executorsPrev[executorsFirst] = nodeId;
      } else { // there was no first -> list was empty -> first = last
        executorsLast = nodeId;
      }
      //nodeId is the new first
      executorsFirst = nodeId;
    } else { // move to front
      int prev = executorsPrev[nodeId];
      int next = executorsNext[nodeId];
      if(prev >= 0) executorsNext[prev] = next;
      if(next >= 0) executorsPrev[next] = prev;
      
      executorsPrev[nodeId] = -1;
      executorsNext[nodeId] = executorsFirst;
      executorsPrev[executorsFirst] = nodeId;
      executorsFirst = nodeId;
    }
    return executors[nodeId];
  }
  
  public long nextDocument(int nodeId, long greaterThan) throws IOException{
    if(latestDocuments[nodeId] == -1){
      //executor already exhausted
      return -1;
    }
    QueryExecutor executor = getExecutor(nodeId);
    if(executor.getLatestDocument() < 0) {
      // newly recreated executor, so we need to skip ahead
      greaterThan = Math.max(greaterThan, latestDocuments[nodeId]);
    }
    latestDocuments[nodeId] = executor.nextDocument(greaterThan);
    hitsReturned[nodeId] = 0;
    hitsOnLatestDocument[nodeId] = null;
    return latestDocuments[nodeId];
  }
  
  public Binding nextHit(int nodeId) throws IOException{
    if(latestDocuments[nodeId] == -1){
      //executor already exhausted
      return null;
    }
    
    if(hitsReturned[nodeId] == 0) {
      // we're asking for the first hit on this document: build the cache
      QueryExecutor executor = getExecutor(nodeId);
      if(executor.getLatestDocument() < 0) {
        // newly (re)created executor, so we need to skip ahead
        long oldLatest = latestDocuments[nodeId];
        latestDocuments[nodeId] = executor.nextDocument(latestDocuments[nodeId] - 1);
        if(oldLatest != latestDocuments[nodeId]){
          throw new RuntimeException("Malfunction in " + 
                  this.getClass().getName() + 
                  ": executor scrolled to a different document after reload!");
        }      
      }
      List<Binding> hits = new LinkedList<Binding>();
      Binding aHit = executor.nextHit();
      while(aHit != null) {
        hits.add(aHit);
        aHit = executor.nextHit();
      }
      hitsOnLatestDocument[nodeId] = hits.toArray(new Binding[hits.size()]);
    }
    // now return directly from cache
    Binding aHhit = 
      (hitsReturned[nodeId] < hitsOnLatestDocument[nodeId].length) ?
      hitsOnLatestDocument[nodeId][hitsReturned[nodeId]] :
      null;
    if(aHhit != null){
      hitsReturned[nodeId]++;
    }
    return aHhit;
  }
  
  public long latestDocument(int nodeId){
    return latestDocuments[nodeId];
  }
  
  
  
  /**
   * Closes all executors still live, and releases all memory resources.
   * @throws IOException 
   */
  public void close() throws IOException{
    closed = true;
    for(int i = 0; i< executors.length; i++) {
      if(executors[i] != null) {
        executors[i].close();
        executors[i] = null;
        executorsClosed++;
      }
    }
    engine = null;
    hitsReturned = null;
    latestDocuments = null;
    nodes = null;
    logger.debug("Closing executors list. Operations (open/close): " + 
            executorsOpened +"/" + executorsClosed);
  }
  
  
  /**
   * The default maximum number of executor to be kept live.
   */
  public static final int DEFAULT_MAX_LIVE_EXECUTORS = 200000;
  
  /**
   * The load factor used when none specified in constructor.
   */
  protected static final float DEFAULT_LOAD_FACTOR = 0.75f;
  
  /**
   * Value returned when {@link #latestDocument(int)} is called for an executor
   * that was not started yet (i.e. nextDocument was not called yet).
   */
  public static final int EXECUTOR_NOT_STARTED = -2;
  
  /**
   * The maximum number of executors that should be kept in memory at any one 
   * time.
   */
  protected int maxLiveExecutors;
  
  /**
   * The {@link QueryEngine} used to create executors.
   */
  protected QueryEngine engine;
  
  
  /**
   * Has {@link #close()} been called?
   */
  protected boolean closed;
  
  private long executorsClosed;
  
  private long executorsOpened;
  
  protected static Logger logger = LoggerFactory.getLogger(ExecutorsList.class);
  
  /**
   * The {@link QueryNode} used to create executors.
   */
  protected QueryNode[] nodes;
  
  /**
   * Array that holds the latest document ID returned by each executor.
   */
  protected long[] latestDocuments;
  
  /**
   * The number of hits already returned from the latest document, for each 
   * executor. This is used to skip already-returned hits when the executor 
   * needs to be re-loaded.  
   */
  protected int[] hitsReturned;
  
  /**
   * A cache storing all the hits on the latest document for each executor. 
   * First array index selects the executor, second array index selects the hit. 
   */
  protected Binding[][] hitsOnLatestDocument;
  
  
  /**
   * An array contining the executors (some position may be null, if the 
   * executor on that location has been dropped from RAM).
   */
  protected QueryExecutor[] executors;
  
  protected int[] executorsNext;
  protected int[] executorsPrev;
  protected int executorsFirst;
  protected int executorsLast;
  protected int executorsSize;
}
