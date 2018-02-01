/*
 *  SearchController.groovy
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


import java.io.OutputStreamWriter;

import gate.mimir.web.Index;
import gate.mimir.web.SearchService;
import groovy.xml.StreamingMarkupBuilder;
import gate.mimir.index.DocumentData;
import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.parser.ParseException;
import gate.mimir.search.terms.TermsQuery;
import gate.mimir.search.terms.TermsResultSet;

import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryRunner;


/**
 * A controller for searching mimir indexes known to this instance.
 * It supports HTML search, XML-over-HTTP, and AJAX-backing RPC actions.
 */
class SearchController {
  
  /**
   * Constant for Error state
   */
  public static final String ERROR = "ERROR"

  /**
   * Constant for Success state
   */
  public static final String SUCCESS = "SUCCESS"
   
  /**
   * XML namespace for mimir request and response messages.
   */
  public static final String MIMIR_NAMESPACE = 'http://gate.ac.uk/ns/mimir'
  
  /**
   * Reference to the search service, autowired.
   */
  def searchService

  def gwtService
  
  /**
  * By default just run the help action.
  */
  static defaultAction = "info"

 
// ==================== HTML-GWT Interface ===================================
  
  /**
   * Action that supports the GWT-based web UI
   */
  def index() {
    // GWT takes care of the rest
    return [index:request.theIndex]
  }
  
  /**
  * Render the content of the given document.  Most of the magic happens in
  * the documentContent tag of the GusTagLib.
  */
 def document() {
   if(params.queryId && params.documentRank) {
     QueryRunner runner = searchService.getQueryRunner(params.queryId)
     if(runner){
       Index index = Index.findByIndexId(params.indexId)
       return [
           index:index,
           documentRank: params.documentRank,
           queryId:params.queryId,
           documentTitle:runner.getDocumentTitle(params.documentRank as int),
           baseHref:index?.isUriIsExternalLink() ? runner.getDocumentURI(params.documentRank as int) : null
           ]
     } else {
       //query has expired
       return [ queryId:params.queryId ]
     }
   } else if(params.indexId && params.documentId) {
     Index index = Index.findByIndexId(params.indexId)
     return [
       index:index,
       documentId: params.documentId,
       documentTitle:"",
       baseHref: null]
   }
 }
  
  /**
  * Action that forwards to the real GWT RPC handler
  */
 def gwtRpc() {
   gwtService.handleRpc(request, response)
   return null
 }
  
  
  
// ==================== XML-over-HTTP Interface ===============================
  
  def info() {
    return [indexInstance:request.theIndex]
  }

  /**
   * Default action: prints a short message explaining how to use the 
   * controller.
   * Parameters: none.
   */
  def help() {
    return [index:request.theIndex] 
  }
  
  def postQuery() {
    def p = params["request"] ?: params
    //get the query string
    String queryString = p["queryString"]
    try{
      String runnerId = searchService.postQuery(request.theIndex, queryString)
      render(buildMessage(SUCCESS, null){
        queryId(runnerId)
      }, contentType:"text/xml")
    } catch(ParseException pe) {
      log.error("Syntax error in query: ${pe.message}")
      render(buildMessage(ERROR, pe.message, null), contentType:"text/xml")
    } catch(Exception e){
      log.error("Exception posting query", e)
      render(buildMessage(ERROR, e.message, null), contentType:"text/xml")
    }
  }
  
  /**
   * Gets the number of result documents.
   * @return <code>-1</code> if the search has not yet completed, the total 
   * number of result document otherwise. 
   */
  def documentsCount() {
    doDocumentsCount { runner -> runner.getDocumentsCount() }
  }

  /**
   * Synchronous version of documentsCount that waits until the count is known
   * before returning.  This is only truly synchronous on a local index, since
   * remote and federated indexes use a polling cycle internally.
   */
  def documentsCountSync() {
    doDocumentsCount { runner -> runner.getDocumentsCountSync() }
  }

  /**
   * Gets the number of result documents found so far. After the search 
   * completes, the result returned by this call is identical to that of 
   * {@link #documentsCount}.
   * @return the number of result documents known so far.   */
  def documentsCurrentCount() {
    doDocumentsCount { runner -> runner.getDocumentsCurrentCount() }
  }

  /**
   * Common implementation for all the getDocuments*Count methods.
   */
  private doDocumentsCount(callable) {
    def p = params["request"] ?: params
    //a closure representing the return message
    def message;
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      try{
        //we have all required parameters
        long docCount = callable.call(runner)
        message = buildMessage(SUCCESS, null){
          value(docCount)
        }
      }catch(Exception e){
        message = buildMessage(ERROR,
                "Error while obtaining the documents count: \"" +
                e.getMessage() + "\"!", null)
      }
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    //return the results
    render(contentType:"text/xml", builder: new StreamingMarkupBuilder(),
        message)
  }

  
  /**
   * Gets the ID of a result document.
   * @param rank the index of the desired document in the list of documents. 
   * This should be a value between 0 and {@link #documentsCount} -1.
   *  
   * If the requested document position has not yet been ranked (i.e. we know 
   * there is a document at that position, but we don't yet know which one) then 
   * the necessary ranking is performed before this method returns. 
   *
   * @return an int value, representing the ID of the requested document.   */
  def documentId() {
    def p = params["request"] ?: params
    def message
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the other params
      String rankStr = p["rank"]
      if(rankStr){
        try {
          long rank = Long.parseLong(rankStr)
          long docId = runner.getDocumentID(rank)
          message = buildMessage(SUCCESS, null){
            value(docId)
          }
        } catch(NumberFormatException e) {
          message = buildMessage(ERROR, 
            "Non-integer value provided for parameter rank", null);
        }
      }else{
        message = buildMessage(ERROR, "No value provided for parameter rank", 
            null)
      }
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    render(contentType:"text/xml", message)
  }
  
  /**
   * Get the score for a given result document. The value for the score depends 
   * on the scorer used by the {@link QueryEngine} (see 
   * {@link QueryEngine#setScorerSource(java.util.concurrent.Callable)}). 
   * @param rank the index of the desired document in the list of documents. 
   * This should be a value between 0 and {@link #documentsCount} -1.   
   */
  def documentScore() {
    def p = params["request"] ?: params
    def message
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the other params
      String rankStr = p["rank"]
      if(rankStr){
        try {
          long rank = Long.parseLong(rankStr)
          double score = runner.getDocumentScore(rank)
          message = buildMessage(SUCCESS, null){
            value(score)
          }
        } catch(NumberFormatException e) {
          message = buildMessage(ERROR,
            "Non-integer value provided for parameter rank", null);
        }
      } else {
        message = buildMessage(ERROR,  "No value provided for parameter rank", 
          null)
      }
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    render(contentType:"text/xml", message)
  }
  
  /**
   * Retrieves the hits within a given result document.
   * @param rank the index of the desired document in the list of documents.
   * This should be a value between 0 and {@link #documentsCount} -1.
   * 
   * This method call waits until the requested data is available before 
   * returning (document hits are being collected by a background thread).   
   */
  def documentHits() {
    def p = params["request"] ?: params
    //a closure representing the return message
    def message;
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the other params
      String rankStr = p["rank"]
      if(rankStr){
        try {
          long rank = Long.parseLong(rankStr)
          //we have all required parameters
          List<Binding> hits = runner.getDocumentHits(rank)
          message = buildMessage(SUCCESS, null){
            if(hits) {
              delegate.hits {
                for(Binding hit : hits){
                  delegate.hit (documentId: hit.getDocumentId(),
                      termPosition: hit.getTermPosition(),
                      length: hit.getLength())
                }
              }
            }
          }
        } catch(NumberFormatException e) {
          message = buildMessage(ERROR,
            "Non-integer value provided for parameter rank", null);
        }
      } else {
        message = buildMessage(ERROR,  "No value provided for parameter rank",
          null)
      }
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    //return the results
    render(contentType:"text/xml",
        builder: new StreamingMarkupBuilder(),
        message)
  }
  
  
  /**
   * Gets a segment of the document text for a given document.
   * @param rank the rank of the requested document. This should be a value 
   * between 0 and {@link #getDocumentsCount()} -1.
   * @param termPosition the first term requested.
   * @param length the number of terms requested.
   * @return two parallel String arrays, one containing term text, the other 
   * containing the spaces in between. The first term is results[0][0], the 
   * space following it is results[1][0], etc.
   *
   * The effect of the default values for termPosition and length is that if
   * both are omitted the text for the whole of the given document is returned.
   */
  def documentText() {
    def p = params["request"] ?: params
    def paramName = null;
    def message
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the other params
      String rankStr = p["rank"]
      String positionStr = p["termPosition"] ?: "0"
      String lengthStr = p["length"] ?: "-1"
      if(rankStr){
        try {
          paramName = "rank"
          long rank = Long.parseLong(rankStr)
          paramName = "termPosition"
          int position = Integer.parseInt(positionStr)
          paramName = "length"
          int length = Integer.parseInt(lengthStr)
          message = buildMessage(SUCCESS, null){
            String[][] docText = runner.getDocumentText(rank, position, length)
            for(int i = 0; i < docText[0].length; i++){
              String token = docText[0][i]
              String space = docText[1][i]
              delegate.text(position: position+i, token)
              if(space) delegate.space(space)
            }
          }
        } catch(NumberFormatException e) {
          message = buildMessage(ERROR, 
              "Non-integer value provided for parameter ${paramName}", null);
        }
      }else{
        message = buildMessage(ERROR, 
            "No value provided for parameter rank", null)
      }
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    render(contentType:"text/xml", message)
  }
  
  /**
   * Action for obtaining the document metadata.
   * Parameters:
   * - queryId: the for the query to be used for getting the document. 
   * - rank: the rank of the desired document
   * - documentId, as an alternative to the (queryId, rank) pair: the ID for the
   *   requested document.
   * - fieldNames (optional): a comma-separated list of other field names to 
   *   be returned. 
   * Returns:
   *   - the document URI
   *   - the document title
   *   - the values for the other field names, if requested and present.
   */
  def documentMetadata() {
    def p = params["request"] ?: params
    // get the document ID
    long documentId
    String queryId = p["queryId"]
    if(queryId) {
      QueryRunner runner = searchService.getQueryRunner(queryId);
      if(runner) {
        def documentRankParam = p["rank"]
        if(documentRankParam) {
          //we have all the required parameters
          long documentRank
          try{
            documentRank = documentRankParam as long
            documentId = runner.getDocumentID(documentRank)
println("Doc ID $documentId")            
          } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
              "Invalid value provided for parameter rank (not an integer)!")
            return;
          }
        } else {
          response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "No value provided for the rank parameter.")
          return;
        }
      } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "Could not find query with ID $queryId!")
      }
    } else {
      //no queryId value supplied: use documentId
      String documentIdStr = p["documentId"]
      if(documentIdStr) {
        try{
          documentId = documentIdStr as long
        } catch (Exception e) {
          log.error("Error in render", e)
          response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "Invalid value provided for parameter documentId (not an integer)!")
          return;
        }
      } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "You must supply either a documentId or the queryId and rank!")
      }
    }
    // at this point we have the documentId
    Index theIndex = (Index)request.theIndex
println("Doc ID2 $documentId")
    DocumentData docData = theIndex.getDocumentData(documentId)
    //a closure representing the return message
    def message;
    
    Map<String, Serializable> metadata = null;
    def fieldNamesStr = p["fieldNames"]
    if(fieldNamesStr) {
      // split on each comma (not preceded by a backslash)
      metadata = fieldNamesStr.split(/\s*(?<!\\),\s*/).collect{
        // un-escape commas
        it.replace('\\,', ',')
      }.collectEntries { String fieldName ->
         [(fieldName):docData.getMetadataField(fieldName)]
      }
    }
    //we have all required values
    String documentURI = docData.getDocumentURI()
    String documentTitle = docData.getDocumentTitle()
    message = buildMessage(SUCCESS, null){
      delegate.documentTitle(documentTitle)
      delegate.documentURI(documentURI)
      metadata?.each{String key, Serializable value ->
        delegate.metadataField(name:key, value:value.toString())
      }
    }
    //return the results
    render(contentType:"text/xml", builder: new StreamingMarkupBuilder(),
        message)
  }

    
  def close() {
    def p = params["request"] ?: params
    
    //get the query ID
    String queryId = p["queryId"]
    if(searchService.closeQueryRunner(queryId)){
      render(buildMessage(SUCCESS, null, null), contentType:"text/xml")
    }else{
      render(buildMessage(ERROR, "Query ID ${queryId} not known!", null), 
        contentType:"text/xml")
    }
  }
  
  
  /**
   * A method to build a closure representing a Mimir message.
   * @param theState the state value (either {@link #SUCCESS} or {@link #ERROR})
   * @param theError the text describing the error (if any)
   * @param dataClosure a closure representing the contents for the data
   * element.
   */
  private buildMessage(String theState, String theError, dataClosure) {
    return  {
      mkp.xmlDeclaration()
      mkp.declareNamespace('':MIMIR_NAMESPACE)
      
      delegate.message {
        delegate.state(theState)
        if(theError) {
          delegate.error(theError)
        }
        if(dataClosure){
          delegate.data {
            dataClosure.delegate = delegate
            //            dataClosure.resolveStrategy = Closure.DELEGATE_FIRST
            dataClosure.call()
          }
        }
      }
    }
  }
  
// ============== Binary protocol (used by remote clients) ===================
  
  /**
   * Binary version of post query 
   */
  def postQueryBin() {
    def p = params["request"] ?: params
    //get the query string or binary representation
    String queryString = p["queryString"]
    QueryNode queryNode = null;
    if(!queryString) {
      // no query string was given: maybe we got a serialized QueryNode instead
      request.inputStream.withStream { stream ->
        ObjectInputStream ois = new ObjectInputStream(stream)
        queryNode = ois.readObject()
        // drain input stream
        byte[] buf = new byte[1024];
        while(ois.read(buf) >= 0) {
          // do nothing
        }
        ois.close()
      }
    }
    
    if(queryString) {
      try {
        String runnerId = searchService.postQuery(request.theIndex, queryString)
        new ObjectOutputStream (response.outputStream).withStream {stream ->
          stream.writeObject(runnerId)
        }
      } catch(Exception e) {
        log.error("Exception posting query", e)
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
        "Problem posting query: \"" + e.getMessage() + "\"")
      }
    } else if(queryNode) {
      try {
        String runnerId = searchService.postQuery(request.theIndex, queryNode)
        new ObjectOutputStream (response.outputStream).withStream {stream ->
          stream.writeObject(runnerId)
        }
      } catch(Exception e) {
        log.error("Exception posting query", e)
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
        "Problem posting query: \"" + e.getMessage() + "\"")
      }
    } else {
      // no query provided at all
      log.error("No query given")
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
        "Did not receive any query to post")
    }
  }

  /**
   * Binary version of post query
   */
  def postTermsQueryBin() {
    def p = params["request"] ?: params
    // read the serialized TermsQuery from the remote caller
    TermsQuery theQuery = null
    request.inputStream.withStream { stream ->
      ObjectInputStream ois = new ObjectInputStream(stream) 
      theQuery = ois.readObject()
      // drain input stream
      byte[] buf = new byte[1024];
      while(ois.read(buf) >= 0) {
        // do nothing
      }
      ois.close()
    }
    
    try {
      Index theIndex = request.theIndex
      TermsResultSet trs = theIndex.postTermsQuery(theQuery)
  
      new ObjectOutputStream (response.outputStream).withStream {stream ->
        stream.writeObject(trs)
      }
    } catch(Exception e) {
      log.error("Exception posting query", e)
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
      "Problem posting query: \"" + e.getMessage() + "\"")
    }
  }
  
  
  /**
   * Gets the number of result documents found so far. After the search 
   * completes, the result returned by this call is identical to that of 
   * {@link #documentsCountBin}. The result is returned as a binary 
   * representation of an int value.
   */
  def documentsCurrentCountBin() {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner) {
      try {
        // we have all required parameters
        long docCount = runner.getDocumentsCurrentCount()
        new ObjectOutputStream (response.outputStream).withStream {stream ->
          stream.writeLong(docCount)
        }
      } catch(Exception e) {
        log.warn("Error while sending document current count", e)
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            "Error while obtaining the documents count: \"" + e.getMessage() + "\"!")
      }
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Query ID ${queryId} not known!")
      }
  }
 
  /**
   * Gets the number of result documents found.  Returns <code>-1</code> if the
   * search has not yet completed, the total number of result document 
   * otherwise. The result is returned as a binary representation of an int 
   * value.
   */
  def documentsCountBin() {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner) {
      try {
        // we have all required parameters
        long docCount = runner.getDocumentsCount()
        new ObjectOutputStream (response.outputStream).withStream {stream ->
          stream.writeLong(docCount)
        }
      } catch(Exception e) {
        log.warn("Error while sending document count", e)
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            "Error while obtaining the documents count: \"" + e.getMessage() + "\"!")
      }
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Query ID ${queryId} not known!")
    }
  }
  
  // protected static final String ACTION_DOC_IDS_BIN = "documentIdsBin";
  /**
   * Gets the IDs of a range of documents, in ranking order.
   */
  def documentIdsBin() {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      if(runner.getDocumentsCount() < 0) {
        // premature call
        response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Query ID ${queryId} has not completed collecting hits; please try later")
      }
      //get the parameters: int documentRank
      def firstRankParam = p["firstRank"]
      if (firstRankParam) {
        def sizeParam = p["size"]
        if(sizeParam) {
          //we have all required parameters
          try {
            long from = firstRankParam as long
            int resultSize = sizeParam as int
            long to = from + resultSize
            if(to > runner.getDocumentsCount()) {
              to = runner.getDocumentsCount()
            }
            long[] docIds = new long[(int)(to - from)]
            for(long rank = from; rank < to; rank++) {
              docIds[(int)(rank - from)] = runner.getDocumentID(rank)
            }
            new ObjectOutputStream (response.outputStream).withStream {stream ->
              stream.writeObject(docIds)
            }
          } catch(Exception e){
            log.warn("Error while sending document ID", e)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Error while obtaining the document ID: \"" +
                e.getMessage() + "\"!")
          }
        } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "No value provided for parameter size!")
        }
      } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "No value provided for parameter firstRank!")
      }
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Query ID ${queryId} not known!")
    }
  }

  // protected static final String ACTION_DOC_SCORES_BIN = "documentsScoresBin";  
  /**
   * Retrieves the scores for a range of documents
   */
  def documentsScoresBin() {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the parameters: int documentRank
      def firstRankParam = p["firstRank"]
      if (firstRankParam) {
        def sizeParam = p["size"]
        if(sizeParam) {
          //we have all required parameters
          try {
            long from = firstRankParam as long
            int resultSize = sizeParam as int
            long to = from + resultSize
            if(to > runner.getDocumentsCount()) {
              to = runner.getDocumentsCount()
            }
            double[] docScores = new double[(int)(to - from)]
            for(int rank = from; rank < to; rank++) {
              docScores[(int)(rank - from)] = runner.getDocumentScore(rank)
            }
            new ObjectOutputStream (response.outputStream).withStream {stream ->
              stream.writeObject(docScores)
            }
          } catch(Exception e){
            log.warn("Error while sending document ID", e)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Error while obtaining the document ID: \"" +
                e.getMessage() + "\"!")
          }
        } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "No value provided for parameter size!")
        }
      } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "No value provided for parameter firstRank!")
      }
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Query ID ${queryId} not known!")
    }
  }
  
  /**
   * Retrieves the hits within a given result document.
   */
  def documentHitsBin() {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the parameters
      def documentRankParam = p["documentRank"]
      if(documentRankParam){
        try{
          long documentRank = documentRankParam as long
          //we have all required parameters
          List<Binding> hits = runner.getDocumentHits(documentRank)
          new ObjectOutputStream (response.outputStream).withStream {stream ->
            stream.writeObject(hits)
          }
        }catch(Exception e){
          log.warn("Error while sending document hits", e)
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error while obtaining the hits: \"" + e.getMessage() + "\"!")
        }
        
      }else{
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "No value provided for parameter documentRank!")
      }
    } else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Query ID ${queryId} not known!")
    }
  }
  
  //  protected static final String ACTION_DOC_DATA_BIN = "documentDataBin";
  /**
   * Gets the document data (title, URI, text) for a given document. The 
   * requested document should be specified by providing paramter values for
   * either documentId or both queryId and documentRank. The result is a 
   * serialised {@link DocumentData} value.
   */
  def documentDataBin() {
    def p = params["request"] ?: params
    Index index = request.theIndex
    // get the document ID
    long documentId = -1;
    String documentIdParam = p["documentId"]
    if(documentIdParam) {
      documentId = documentIdParam as long
    } else {
      // we didn't get the explicit ID; try queryId and rank instead
      String queryId = p["queryId"]
      if(queryId) {
        QueryRunner runner = searchService.getQueryRunner(queryId);
        if(runner){
          String documentRankParam = p["documentRank"]
          if (documentRankParam) {
            long documentRank = documentRankParam as long
            documentId = runner.getDocumentID(documentRank)
          } else {
            log.warn("Error while sending document data: " +
              "Neither documentId nor documentRank parameters were provided!")
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
              "Neither documentId nor documentRank parameters were provided!")
          }
        } else {
          log.warn("Error while sending document data: " +
              "Query ID ${queryId} not known!")
          response.sendError(HttpServletResponse.SC_NOT_FOUND,
            "Query ID ${queryId} not known!")
        }
      } else {
        log.warn("Error while sending document data: " +
            "Neither documentId nor queryId parameters were provided!")
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "Neither documentId nor queryId parameters were provided!")
      }
    }
    // by this point we need to have the documentID
    if(documentId >= 0) {
      try {
        new ObjectOutputStream (response.outputStream).withStream {stream ->
          stream.writeObject(index.getDocumentData(documentId))
        }
      } catch(Exception e) {
        log.warn("Error while sending document data", e)
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            "Error while obtaining the document ID: \"" +
            e.getMessage() + "\"!")
      }
    } else {
      log.warn("Error while sending document data: " +
          "Could not find a valid documentId with the provided parameters!")
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "Could not find a valid documentId with the provided parameters!")
    }
  }
  
  /**
   * Calls the render document method on the corresponding query runner, piping
   * the output directly to the response stream.
   *  
   */
  def renderDocument() {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    if(queryId) {
      QueryRunner runner = searchService.getQueryRunner(queryId);
      if(runner){
        //get the parameters
        def documentRankParam = p["rank"]
        if(documentRankParam){
          try{
            //we have all the required parameters
            long documentRank = documentRankParam as long
            response.characterEncoding = "UTF-8"
            response.contentType = "text/plain"
            response.writer.withWriter{ writer ->
              runner.renderDocument(documentRank, writer)
            }
          }catch(Exception e){
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Error while rendering document: \"" +
                e.getMessage() + "\"!")
          }
        }else{
          response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "No value provided for parameter rank (required when using a queryId)!")
        }
      }else{
        response.sendError(HttpServletResponse.SC_NOT_FOUND,
            "Query ID ${queryId} not known!")
      }
    } else {
      //no queryId value supplied: use documentId
      String documentIdStr = p["documentId"]
      if(documentIdStr) {
        long docId
        try{
          docId = documentIdStr as long
        } catch (Exception e) {
          log.error("Error in render", e)
          response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "Invalid value provided for parameter documentId (not an integer)!")
          return;
        }
        try{
          response.characterEncoding = "UTF-8"
          response.contentType = "text/plain"
          response.writer.withWriter{ writer ->
            ((Index)request.theIndex).renderDocument(docId, writer)
          }
          return
        } catch (Exception e) {
          log.error("Error while rendering document", e)
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            "Error while rendering document: \"" +
            e.getMessage() + "\"!")
        }
      } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "You must supply either a documentId or the queryId and rank!")
      }
    }
  }
  
  
  def downloadDocumentList() {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    
    if(searchService.getQueryRunner(queryId)) {
      response.characterEncoding = "UTF-8"
      response.contentType = "text/csv"
      response.setHeader("Content-Disposition", "attachment; filename=document-list.csv")
      try {
        response.writer.withWriter{ writer ->
          searchService.downloadQueryResults(queryId, writer, params.list('fields'))
        }
      } catch (Exception e) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error while preparing document list: \"" + e.getMessage() + "\"!")
      }
    } else {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
        "The provided query ID $queryId was invalid.")
    }
  }
  
  /**
   * Gets the annotations config for a given index, as a serialised String[][] 
   * value.
   */
  def annotationsConfigBin() {
    def p = params["request"] ?: params
    //get the query ID
    String indexId = p["indexId"]
    if(indexId){
      Index theIndex = Index.findByIndexId(params.indexId)
      if(theIndex){
        try{
          String [][] indexConfig = theIndex.annotationsConfig()
          new ObjectOutputStream (response.outputStream).withStream {stream -> 
            stream.writeObject(indexConfig)
          }
        }catch(Exception e){
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
              "Error while obtaining the annotations configuration for " +
              "index \"" + indexId +  "\": \"" + e.getMessage() + "\"!")
        }          
      }else{
        //could not find the index
        response.sendError(HttpServletResponse.SC_NOT_FOUND, 
        "Index ID ${indexId} not known!")
      }
    }else{
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
      "No value provided for parameter indexId!")
    }
  }
}
