/*
 *  SearchService.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id$
 */
package gate.mimir.web

import gate.mimir.web.Index;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import grails.web.mapping.LinkGenerator;

import com.google.common.cache.CacheBuilder
import com.google.common.cache.Cache
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import gate.mimir.search.QueryRunner
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.parser.ParseException;

import grails.gorm.transactions.Transactional

@Transactional
class SearchService {
  
  
  LinkGenerator grailsLinkGenerator
  
  /**
   * A map holding the currently active query runners. 
   */
  //Map<String, QueryRunner> queryRunners = [:].asSynchronized()
  Cache<String, QueryRunnerHolder> queryRunners
  
  CacheCleaner cacheCleaner
          
  public QueryRunner getQueryRunner(String queryId){
    return queryId ? queryRunners.getIfPresent(queryId)?.queryRunner : null
  }

  public Index getQueryRunnerIndex(String queryId){
    return queryId ? queryRunners.getIfPresent(queryId)?.index : null
  }
  
  public boolean closeQueryRunner(String id){
    QueryRunner runner = getQueryRunner(id)
    if(runner){
      log.debug("Releasing query ID ${id}")
      // the cache listener will close the runner
      queryRunners.invalidate(id)
      return true
    }
    return false
  }
  
  /**
   * Posts a query to a specified index. Creates a query runner for it, stores
   * the query runner in the internal runners map, starts the query executions
   *  and returns the ID for the query runner.
   */
  public String postQuery(Index theIndex, String queryString) 
      throws IOException, ParseException {
    QueryRunner aRunner = theIndex.startQuery(queryString)
    if(aRunner){
      String runnerId = UUID.randomUUID()
      queryRunners.put(runnerId, 
        new QueryRunnerHolder(queryRunner:aRunner, index:theIndex))
      return runnerId
    } else {
      throw new RuntimeException("Could not start query")
    } 
  }
  
  /**
   * Posts a query to a specified index. Creates a query runner for it, stores
   * the query runner in the internal runners map, starts the query executions
   *  and returns the ID for the query runner.
   */
  public String postQuery(Index theIndex, QueryNode queryNode)
      throws IOException, ParseException {
    QueryRunner aRunner = theIndex.startQuery(queryNode)
    if(aRunner){
      String runnerId = UUID.randomUUID()
      queryRunners.put(runnerId,
        new QueryRunnerHolder(queryRunner:aRunner, index:theIndex))
      return runnerId
    } else {
      throw new RuntimeException("Could not start query")
    }
  }
      
  /**
   * Posts a query to a specified index. Creates a query runner for it, stores
   * the query runner in the internal runners map, starts the query executions
   *  and returns the ID for the query runner.
   */
  public String postQuery(String indexId, String queryString) 
      throws IOException, ParseException {
    Index theIndex = Index.findByIndexId(indexId)
    if(theIndex){
     return postQuery (theIndex, queryString) 
    }
    throw new IllegalArgumentException("Index with specified ID not found!")
  }
  
      
  /**
   * This method produces CSV text representing the documents found by a given 
   * query. For each document a link will be generated that will display the
   * document contents at a later time. If the index also stores original URLs,
   * then a second column is used to include the external document links. If 
   * any metadata fields are requested, each such field will be included into an
   * additional column.
   *     
   * @param queryId the query for which the results are requested
   * @param out an Appendable to which the resulting CSV data is streamed.
   * @param metadataFields which document metadata fields should be included in
   * the produced output. By default this is empty, so no additional columns are
   * generated.
   */
  public void downloadQueryResults(String queryId, Appendable out, 
      List<String> metadataFields = []) {
    QueryRunnerHolder qrh = queryRunners.getIfPresent(queryId)
    if(!qrh) throw new IllegalArgumentException("No query found with ID $queryId")
    QueryRunner qRunner = qrh.queryRunner
    boolean externalLinks = qrh.index.uriIsExternalLink
    boolean fields = metadataFields
    Set<String> fieldNames = metadataFields as Set<String>
    
    //wait for the query runner to complete
    while(qRunner.getDocumentsCount() < 0) {
      Thread.sleep(20)
    }
    // write the header row
    out.append('"Document URL"')
    if(externalLinks) out.append(',"Original document URL"')
    metadataFields.each{ out.append(",\"${it}\"") }
    out.append("\n")
    // for each document, write the row
    for(long rank = 0; rank < qRunner.getDocumentsCount(); rank++) {
      long docId = qRunner.getDocumentID(rank)
      out.append('"' + 
          grailsLinkGenerator.link(controller:"search", 
              action:"document", params:[documentId:docId, 
                indexId:qrh.index.indexId], 
          absolute:true) + 
          '"')
      if(externalLinks) {
        out.append(",\"${qRunner.getDocumentURI(rank)}\"")
      }
      if(fields) {
        Map<String, Serializable> fieldValues = 
            qRunner.getDocumentMetadataFields(rank, fieldNames)
        metadataFields.each{
          out.append(",\"${fieldValues[it]}\"")
        }
      }
      out.append("\n")
    }
  }
      
  @PostConstruct
  public void setUp() {
    // construct the runners cache
    queryRunners = CacheBuilder.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .removalListener(new CacheRemovalListener(log:log))
        .build()
    // background thread used to clean up the cache
    cacheCleaner = new CacheCleaner(theCache:queryRunners, log:log)
    new Thread(cacheCleaner).start()
  }
  
  @PreDestroy
  public void destroy() {
    // close all remaining query runners
    queryRunners.invalidateAll()
    cacheCleaner.interrupt()
  }
}

class QueryRunnerHolder {
  QueryRunner queryRunner
  Index index
}

/**
 * Implementation of a cache removal listener, so that we can close the query
 * runners as they get evicted.
 */
class CacheRemovalListener implements RemovalListener<String, QueryRunnerHolder> {

  def log
  
  /* (non-Javadoc)
   * @see com.google.common.cache.RemovalListener#onRemoval(com.google.common.cache.RemovalNotification)
   */
  @Override
  public void onRemoval(RemovalNotification<String, QueryRunnerHolder> notification) {
    log.debug("Evicting query ${notification.key}.")
    notification.value.queryRunner.close()
  }
}

/**
 * Action to regularly clean up the cache, running from a background thread.
 */
class CacheCleaner implements Runnable {

  volatile Cache theCache
  
  def log
  
  Thread myThread
  
  public void interrupt() {
    theCache = null
    myThread?.interrupt()
  }
  
  public void run() {
    myThread = Thread.currentThread()
    while(theCache != null) {
      theCache.cleanUp();
      log.debug("Removed stale queries; count after clean-up: ${theCache.size()}")
      try {
        // every 30 seconds, clear out the old runners
        Thread.sleep(30 * 1000);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt()
      }
    }
  }
}
