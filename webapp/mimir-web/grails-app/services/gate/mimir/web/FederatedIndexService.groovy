/*
 *  FederatedIndexService.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Ian Roberts, 06 Jan 2010
 *  
 *  $Id$
 */
package gate.mimir.web

import gate.mimir.index.DocumentData
import gate.mimir.search.QueryRunner
import gate.mimir.search.FederatedQueryRunner
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.parser.ParseException
import gate.mimir.search.terms.CompoundTermsQuery;
import gate.mimir.search.terms.DocumentsBasedTermsQuery;
import gate.mimir.search.terms.OrTermsQuery;
import gate.mimir.search.terms.TermsQuery;
import gate.mimir.search.terms.TermsResultSet;
import gate.mimir.web.FederatedIndex
import gate.mimir.web.Index

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger

import org.apache.log4j.Logger;

/**
 * Service for operations on federated indexes.
 */
class FederatedIndexService {

  /**
   * Map holding the index into the list of indexes that should be used next by
   * each federated index.  Federated indexes dispatch documents for indexing
   * to their sub-indexes in round-robin fashion, the first to sub-index 0, the
   * next to 1, and so on up to <code>indexes.size()</code>, then starting
   * again from 0.  But we can't store this next index number in the database
   * because at high request volumes we'll either get loads of optimistic
   * locking failures, or will need to synchronize access to the database.
   * Neither of these would be acceptable, so instead we store a map in this
   * service from the FederatedIndex id to an AtomicInteger which we can
   * getAndIncrement without locking.
   */
  private Map nextIndex = [:]
  
  private Map<String, FederatedIndexProxy> proxies = [:];
  
  public synchronized FederatedIndexProxy findProxy(FederatedIndex index) {
    FederatedIndexProxy p = proxies[index.id]
    if(!p) {
      p = new FederatedIndexProxy(index)
      proxies[index.id] = p
    }
    return p
  }
  
  public void indexDeleted(id){
    proxies.remove(id)?.close()
  }
  
  /**
   * Register a FederatedIndex in the nextIndex map.  This should be called
   * when the index is opened for indexing (i.e. at BootStrap for existing
   * indexes or at creation time for new ones).
   */
  public void registerIndex(FederatedIndex index) {
    nextIndex[index.id] = new AtomicInteger(0)
  }

  /**
   * Return the index into the given FederatedIndex's list of sub-indexes that
   * the next document should be sent to.  This is the value from the nextIndex
   * map taken modulo the number of sub-indexes that the federated index
   * contains.
   */
  public int getNextIndex(FederatedIndex index) {
    if(!nextIndex.containsKey(index.id)) {
      throw new IllegalArgumentException("Federated index ${index.indexId} not registered")
    }
    return (int)(nextIndex[index.id].getAndIncrement() % index.indexes.size())
  }
  
  /**
   * Register existing indexes with this class at startup.
   */
  public void init() {
    FederatedIndex.list().each {
      findProxy(it)
      if(it.state == Index.READY) registerIndex(it)
    }
  }
  
  public QueryRunner getQueryRunner(FederatedIndex index, String query) 
      throws ParseException {
    QueryRunner[] subRunners = new QueryRunner[index.indexes.size()]
    try {
      index.indexes.eachWithIndex { Index subIndex, i ->
        subRunners[i] = subIndex.startQuery(query)
      }
      return new FederatedQueryRunner(subRunners)
    } catch(Throwable t) {
      log.error("Error creating query runner for sub-index: ${t.message}")
      for(QueryRunner subRunner in subRunners){ 
        try {
          subRunner?.close()
        } catch(Throwable t2) {
          // ignore
        }
      }
      // and re-throw
      throw t
    }
  }

  public QueryRunner getQueryRunner(FederatedIndex index, QueryNode query) {
    QueryRunner[] subRunners = new QueryRunner[index.indexes.size()]
    try {
      index.indexes.eachWithIndex { Index subIndex, i ->
        subRunners[i] = subIndex.startQuery(query)
      }
      return new FederatedQueryRunner(subRunners)
    } catch(Throwable t) {
      log.error("Error creating query runner for sub-index: ${t.message}")
      for(QueryRunner subRunner in subRunners){
        try {
          subRunner?.close()
        } catch(Throwable t2) {
          // ignore
        }
      }
      // and re-throw
      throw t
    }
  }
      
  public TermsResultSet postTermsQuery(FederatedIndex index, TermsQuery query) {
    if(query instanceof CompoundTermsQuery) {
      // if query is compound, split by sub-query, then combine results
      CompoundTermsQuery compQ = (CompoundTermsQuery)query
      // split by components
      TermsResultSet[] resSets = compQ.getSubQueries().collect { 
        TermsQuery subQ -> postTermsQuery(index, subQ)
      }
      // combine the results
      return compQ.combine(resSets)
    } else {
      // query is not compound: split by sub-indexes
      if(query instanceof DocumentsBasedTermsQuery) {
        // if query is documents based, split by sub-index, rewrite the docIDs
        // then OR the results
        DocumentsBasedTermsQuery docsQ = (DocumentsBasedTermsQuery)query
        // split by sub-index
        TermsResultSet[] resSets = docsQ.getDocumentIds().toList().groupBy { 
          long docId -> getSubIndex(index, docId)
        }.collect { subIndex, docIds ->
          DocumentsBasedTermsQuery copyQ = docsQ.clone()
          // rewrite the docIDs
          long[] newDocIds = docIds.collect{getDocIdInSubIndex(index, it)}
          copyQ.setDocumentIds(newDocIds)
          // post the modified query copy 
          return subIndex.postTermsQuery(copyQ)
        }
        // OR the results
        return TermsResultSet.groupByDescription(resSets)
      } else {
        // query is not compound, nor documents based: just pass it to the 
        //  sub-index and OR the results 
        return TermsResultSet.groupByDescription(
          index.indexes.collect{ 
            Index subIndex -> subIndex.postTermsQuery(query)
          } as TermsResultSet[]
        )
      }
    }   
  }    
      
  private void deleteOrUndelete(String method, FederatedIndex fedIndex, Collection<Long> documentIds) {
    def numIndexes = fedIndex.indexes.size()
    // map the supplied federated document IDs to the corresponding IDs in the
    // sub-indexes - first separate out the IDs that belong in each index
    Map subIndexIds = documentIds.groupBy { (int)(it % numIndexes) }
    // and intdiv them by the number of indexes to get the sub-index document ID
    subIndexIds.each { i, fedIds ->
      fedIndex.indexes[i]."${method}Documents"(fedIds.collect { it.intdiv(numIndexes) })
    }
  }

  public void deleteDocuments(FederatedIndex index, Collection<Long> documentIds) {
    deleteOrUndelete("delete", index, documentIds)
  }

  public void undeleteDocuments(FederatedIndex index, Collection<Long> documentIds) {
    deleteOrUndelete("undelete", index, documentIds)
  }
  
  public DocumentData getDocumentData(FederatedIndex fedIndex, long documentId) {
    return getSubIndex(fedIndex, documentId).getDocumentData(
      getDocIdInSubIndex(fedIndex, documentId))
  }
  
  public void renderDocument(FederatedIndex fedIndex, long documentId, 
      Appendable out) {
    getSubIndex(fedIndex, documentId).renderDocument(
      getDocIdInSubIndex(fedIndex, documentId), out)
  }
   
  /**
   * Given a federated index and document ID, finds the sub-index the document
   * belongs to.       
   * @param fedIndex the federated index to use 
   * @param documentId the document ID to look-up
   * @return the sub index of the federated index to which the provided
   * documentID belongs.
   */
  private Index getSubIndex(FederatedIndex fedIndex, long documentId) {
    return fedIndex.indexes[(int)(documentId % fedIndex.indexes.size())]
  }
  
  /**
   * Given a federated index and document ID, finds the document ID local to 
   * the sub-index the document belongs to.       
   * @param fedIndex the federated index to use 
   * @param documentId the document ID to look-up
   * @return the document ID correct inside the sub index to which the provided
   * documentId belongs.
   */
  private long getDocIdInSubIndex(FederatedIndex fedIndex, long documentId) {
    return documentId.intdiv(fedIndex.indexes.size())
  }
}

class FederatedIndexProxy implements Runnable{
  
  public FederatedIndexProxy(FederatedIndex index){
    this.id = index.id
    Thread t = new Thread(this)
    t.setDaemon(true)
    t.start()
    FederatedIndex.withTransaction{
      updateData(index)  
    }
  }
  
  /**
   * Updates the internal data for the federated index that we're managing, 
   * based on the data from  the children.
   */
  private void updateData (FederatedIndex index) {
    index.state = index.indexes.collect { it.state }.inject(null) { prev, cur ->
      if(prev == null) {
        // first step
        return cur
      }
      else if(prev == Index.FAILED) {
        // anything failed => everything failed
        return Index.FAILED
      }
      else if(prev == cur) {
        // all the same so far
        return cur
      }
      else if([prev,cur].containsAll([Index.READY, Index.CLOSING])) {
        // we know prev != cur, if one is closing and the other ready then
        // some of our children have finished closing and some haven't
        return Index.CLOSING
      }
      else {
        // states are inconsistent but may become clean shortly (e.g. delays
        // in obtaining the remote states)
        return Index.WORKING
      }
    }
  }
  
  /**
   * The hibernate ID of the index for which this proxy was created.
   */
  def id
  private static final Logger log = Logger.getLogger("grails.app.service.${FederatedIndexProxy.class.getName()}")
  private static final int DELAY = 10000
  boolean stop = false
    
  public void run(){
    Thread.sleep(DELAY)
    while(!stop) {
      //get the index object
      FederatedIndex.withTransaction{
        FederatedIndex index = FederatedIndex.get(id)
        updateData(index)
      }
      Thread.sleep(DELAY)
    }    
  }
  
  public void close() {
    stop = true
  }
}
