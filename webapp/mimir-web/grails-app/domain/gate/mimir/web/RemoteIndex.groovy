/*
 *  RemoteIndex.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 06 Jan 2010
 *  
 *  $Id$
 */
package gate.mimir.web;

import gate.mimir.index.DocumentData
import gate.mimir.search.QueryRunner
import gate.mimir.search.RemoteQueryRunner
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.parser.ParseException
import gate.mimir.search.terms.TermsQuery;
import gate.mimir.search.terms.TermsResultSet;
import gate.mimir.tool.WebUtils
import gate.mimir.util.WebUtilsManager

/**
 * A remote index, accessed via a web service, and published locally.
 */
class RemoteIndex extends Index {
  
  protected static final String ACTION_POST_TERMS_QUERY_BIN = "search/postTermsQueryBin";
  
  static constraints = {
    remoteUrl(blank:false, nullable:false)
    remoteUsername(blank:true, nullable:true)
    remotePassword(blank:true, nullable:true)
  }
  
  /**
   * The URL of the server hosting the remote index.
   */
  String remoteUrl
  
  
  /**
   * If the remote server uses authentication, the username to be used when 
   * connecting.
   */
  String remoteUsername
  
  /**
   * If the remote server uses authentication, the password to be used when 
   * connecting
   */
  String remotePassword
  
  // behaviour
  
  /**
   * Shared thread pool (autowired)
   */
  transient searchThreadPool
  
  /**
   * The remote index service (autowired) 
   */
  transient remoteIndexService;
  
  /**
   * The web utils manager, used to get the appropriate WebUtils for
   * remote calls.
   */
  transient webUtilsManager;

  static mapping = {
    autowire true
  }
  
  
  /**
   * Start running the given query.
   */
  QueryRunner startQuery(String query) throws ParseException {
    //post query to the actual index
    
    //create a local RemoteQueryRunner and store the service URL, index ID, 
    //and query ID in it.
    return new RemoteQueryRunner(remoteUrl, query, searchThreadPool, 
      webUtilsManager.currentWebUtils(this))
  }

  /**
   * Start running the given query.
   */
  QueryRunner startQuery(QueryNode query) {
    //create a local RemoteQueryRunner and store the service URL, index ID,
    //and query ID in it.
    return new RemoteQueryRunner(remoteUrl, query, searchThreadPool,
      webUtilsManager.currentWebUtils(this))
  }
  
  
  /* (non-Javadoc)
   * @see gate.mimir.web.Index#postTermsQuery(gate.mimir.search.terms.TermsQuery)
   */
  @Override
  public TermsResultSet postTermsQuery(TermsQuery query) {
    String urlStr = (remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/")) +
      ACTION_POST_TERMS_QUERY_BIN;
    
    return webUtilsManager.currentWebUtils(this).rpcCall(urlStr, query)
  }


  /**
   * Gets the {@link DocumentData} value for a given document ID.
   * @param documentID
   * @return
   */
  DocumentData getDocumentData(long documentID) {
    String urlStr = (remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/")) +
        "search/documentDataBin";
    return (DocumentData)webUtilsManager.currentWebUtils(this).getObject(
          urlStr,  "documentId", Long.toString(documentID));
  }
 
  /* (non-Javadoc)
   * @see gate.mimir.web.Index#renderDocument(int, java.lang.Appendable)
   */
  @Override
  public void renderDocument(long documentID, Appendable out) {
    String urlStr = (remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/")) + "search/renderDocument";
    webUtilsManager.currentWebUtils(this).getText(out, urlStr,
      "documentId", Long.toString(documentID));
  }

  /**
   * Obtains the annotations config from the remote controller.
   */
  String[][] annotationsConfig() {
    //call the appropriate method on the remote search controller
    String urlStr = (remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/")) + 
        "search/annotationsConfigBin";
    try{
      return webUtilsManager.currentWebUtils(this).getObject(urlStr)
    }catch(Exception e){
      return new String[0][0]
    }
  }
  
  String indexUrl() {
    StringBuilder responseString = new StringBuilder()
    String urlStr = (remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/")) + 
        "manage/indexUrl";
    try{
      webUtilsManager.currentWebUtils(this).getText(responseString, urlStr)
    }catch(IOException e){
      log.error("Problem communicating with the remote server!", e)
      RemoteIndex.withTransaction{
        //by convention, any communication error switches the index state
        state = Index.FAILED
      }
    }   
    return responseString.toString()
  }
  
  /**
   * Asks the remote index to start closing.
   */
  void close() {
    String urlStr = (remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/")) + 
    "manage/close";
    try{
      webUtilsManager.currentWebUtils(this).getVoid(urlStr)
    }catch(IOException e){
      log.error("Problem communicating with the remote server!", e)
      RemoteIndex.withTransaction{
        //by convention, any communication error switches the index state
        state = Index.FAILED
      }
    }   
  }
  
  /**
   * Asks the remote index to mark objects as deleted.
   */
  void deleteDocuments(Collection<Long> documentIds) {
    doDeleteOrUndelete("delete", documentIds)
  }

  /**
   * Asks the remote index to mark objects as not deleted.
   */
  void undeleteDocuments(Collection<Integer> documentIds) {
    doDeleteOrUndelete("undelete", documentIds)
  }

  private void doDeleteOrUndelete(String method, Collection<Long> documentIds) {
    String urlStr = (remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/")) +
        "manage/${method}DocumentsBin";
    try{
      webUtilsManager.currentWebUtils(this).postObject(urlStr, documentIds)
    }catch(IOException e){
      log.error("Problem communicating with the remote server!", e)
      RemoteIndex.withTransaction{
        //by convention, any communication error switches the index state
        state = Index.FAILED
      }
    }
  }

  /**
   * Ask the remote index to sync.
   */
  void sync() {
    String urlStr = (remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/")) + 
    "manage/sync";
    try{
      webUtilsManager.currentWebUtils(this).getVoid(urlStr)
    }catch(IOException e){
      log.error("Problem communicating with the remote server!", e)
      RemoteIndex.withTransaction{
        //by convention, any communication error switches the index state
        state = Index.FAILED
      }
    }   
  }

}
