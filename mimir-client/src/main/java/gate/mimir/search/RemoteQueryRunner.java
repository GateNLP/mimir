/*
 *  RemoteQueryRunner.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 08 Dec 2011
 *  
 *  $Id: RemoteQueryRunner.java 17423 2014-02-26 10:36:54Z valyt $
 */
package gate.mimir.search;

import gate.mimir.index.DocumentData;
import gate.mimir.index.IndexException;
import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryNode;
import gate.mimir.tool.WebUtils;
import it.unimi.dsi.fastutil.doubles.DoubleBigArrayBigList;
import it.unimi.dsi.fastutil.doubles.DoubleBigList;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.fastutil.longs.LongBigList;

import java.io.IOException;
import java.io.Serializable;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;

import org.apache.log4j.Logger;

/**
 * A {@link QueryRunner} implementation that proxies a QueryRunner running on
 * a remote Mímir server.  
 */
public class RemoteQueryRunner implements QueryRunner {
  
  protected static final String SERVICE_SEARCH = "search";
  
  protected static final String ACTION_POST_QUERY_BIN = "postQueryBin";
  
  protected static final String ACTION_CURRENT_DOC_COUNT_BIN = "documentsCurrentCountBin";
  
  protected static final String ACTION_DOC_COUNT_BIN = "documentsCountBin";
  
  protected static final String ACTION_DOC_IDS_BIN = "documentIdsBin";
  
  protected static final String ACTION_DOC_SCORES_BIN = "documentsScoresBin";
  
  protected static final String ACTION_DOC_HITS_BIN = "documentHitsBin";
  
  protected static final String ACTION_DOC_DATA_BIN = "documentDataBin";
  
  protected static final String ACTION_RENDER_DOCUMENT = "renderDocument";
  
  protected static final String ACTION_CLOSE = "close";
  
  /**
   * The maximum number of documents to be stored in the local document cache.
   */
  protected static final int DOCUMENT_CACHE_SIZE = 1000;
  
  /**
   * Action run in a background thread, used to update the document data 
   * (document ID, document score) from the remote endpoint.
   * This runs once,  started during the creation of the query runner.
   */
  protected class DocumentDataUpdater implements Runnable {
    @Override
    public void run() {
      int failuresAllowed = 10;
      // wait for the first pass to complete
      while(documentsCount < 0) {
        if(closed) return;
        // update document counts
        try {
          long newDocumentsCount = webUtils.getLong(
              getActionBaseUrl(ACTION_DOC_COUNT_BIN), "queryId", 
              URLEncoder.encode(queryId, "UTF-8"));
          if(newDocumentsCount < 0) {
            // still not finished-> update current count
            currentDocumentsCount = webUtils.getLong(
              getActionBaseUrl(ACTION_CURRENT_DOC_COUNT_BIN), "queryId", 
              URLEncoder.encode(queryId, "UTF-8"));
            // ... and wait a while before asking again
            Thread.sleep(500);
          } else {
            // remote side has finished enumerating all documents
            // download the first block of IDs and scores
            downloadDocIdScores(0);
            // ...and we're done!
            documentsCount = newDocumentsCount;
          }
        } catch(InterruptedException e) {
          Thread.currentThread().interrupt();
          logger.warn("Interrupted while waiting", e);
        } catch (Exception e) {
          if(failuresAllowed > 0) {
            failuresAllowed --;
            logger.error("Exception while obtaining remote document data (will retry)", e);
            try {
              Thread.sleep(100);
            } catch(InterruptedException e1) {
              Thread.currentThread().interrupt();
            }
          } else {
            logger.error("Exception while obtaining remote document data.", e);
            exceptionInBackgroundThread = e;
            return;
          }          
        }
      }
    }
  }
  
  /**
   * The size of the document block (the number of documents for which the IDs
   * are downloaded in one operation.
   */
  private int docBlockSize = 1000;
  
  /**
   * A cache of MG4J {@link Document}s used for returning the hit text.
   */
  private Long2ObjectLinkedOpenHashMap<DocumentData> documentCache;
  
  /**
   * The WebUtils instance we use to communicate with the remote
   * index.
   */
  private WebUtils webUtils;
  
  /**
   * The URL to the server hosting the remote index we're searching
   */
  private String remoteUrl;

  /**
   * The query ID for the actual query runner, local to the remote index.
   */
  protected String queryId;

  /**
   * The total number of result documents (or -1 if not yet known).
   */
  private volatile long documentsCount;
  
  /**
   * The current number of documents. After all documents have been retrieved, 
   * this value is identical to {@link #documentsCount}. 
   */
  private volatile long currentDocumentsCount;
  
  /**
   * The task that's working on collecting all the document IDs. When this 
   * activity has finished, the precise documents count is known.
   */
  private volatile FutureTask<Object> docDataUpdaterFuture;
  
  private volatile boolean closed;
  
  /**
   * Shared Logger
   */
  private static Logger logger = Logger.getLogger(RemoteQueryRunner.class);
  
  
  /**
   * If the background thread encounters an exception, it
   * will save it here. As the background thread cannot report it itself, it is
   * the job of any of the interactive methods to report it.
   */
  private Exception exceptionInBackgroundThread;

  /**
   * The document IDs in ranking order. If ranking is not preformed, then the
   * document IDs are in the order they are returned by the index. 
   */
  protected LongBigList documentIds;  
  
  /**
   * The document scores. This list is aligned to {@link #documentIds}.   
   */
  protected DoubleBigList documentScores;
  
  
  /**
   * Creates a new remote query runner instance which executes a search on a 
   * Mímir server and makes the results available locally.
   * 
   * @param remoteUrl the index URL for the index being searched. This can be 
   * obtained from the admin interface of the remote Mímir server.
   * 
   * @param queryString the Mímir query to be executed, represented as a string.
   * 
   * @param threadSource a source of threads  (such as a thread pool) used for
   * background processes. If <code>null</code> is given then new threads are
   * started as required.
   * 
   * @param webUtils an instance of {@link WebUtils}. If the remote server
   * requires authentication, the correct user name and password should be set
   * on the WebUtils instance before being used for the creation of remote query
   * runners. WebUtils instances can be reused for multiple query runners.
   * 
   * @throws IOException
   */
  public RemoteQueryRunner(String remoteUrl, String queryString, 
      Executor threadSource,  WebUtils webUtils) throws IOException {
    this.remoteUrl = remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/");
    this.webUtils = webUtils;
    this.closed = false;
    // submit the remote query
    try {
      init((String) webUtils.getObject(
              getActionBaseUrl(ACTION_POST_QUERY_BIN), 
              "queryString", 
              URLEncoder.encode(queryString, "UTF-8")), threadSource);
    } catch(ClassNotFoundException e) {
      //we were expecting a String but got some object of unknown class
      throw (IOException) new IOException(
          "Was expecting a String query ID value, but got " +
          "an unknown object type!").initCause(e);
    }
  }

  /**
   * Creates a new remote query runner instance which executes a search on a 
   * Mímir server and makes the results available locally.
   * 
   * @param remoteUrl the index URL for the index being searched. This can be 
   * obtained from the admin interface of the remote Mímir server.
   *  
   * @param query the query to be executed. This constructor variant takes a
   * {@link QueryNode}  value; for queries expressed as strings, use the other
   * constructor.
   * 
   * @param threadSource a source of threads  (such as a thread pool) used for
   * background processes. If <code>null</code> is given then new threads are
   * started as required.
   * 
   * @param webUtils an instance of {@link WebUtils}. If the remote server
   * requires authentication, the correct user name and password should be set
   * on the WebUtils instance before being used for the creation of remote query
   * runners. WebUtils instances can be reused for multiple query runners.      
   * 
   * @throws IOException
   */
  public RemoteQueryRunner(String remoteUrl, QueryNode query, 
      Executor threadSource,  WebUtils webUtils) throws IOException {
    this.remoteUrl = remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/");
    this.webUtils = webUtils;
    this.closed = false;
    // submit the remote query
    try {
      init((String) webUtils.rpcCall(
              getActionBaseUrl(ACTION_POST_QUERY_BIN),
              query), threadSource);
    } catch(ClassNotFoundException e) {
      //we were expecting a String but got some object of unknown class
      throw (IOException) new IOException(
          "Was expecting a String query ID value, but got " +
          "an unknown object type!").initCause(e);
    }
  }
  
  protected void init(String queryId, Executor threadSource) {
    this.queryId = queryId;
    // init the caches
    this.documentIds = new LongBigArrayBigList();
    this.documentScores = new DoubleBigArrayBigList();
    this.documentCache = new Long2ObjectLinkedOpenHashMap<DocumentData>();
    
    // start the background action
    documentsCount = -1;
    currentDocumentsCount = 0;
    docDataUpdaterFuture = new FutureTask<Object>( new DocumentDataUpdater(), null); 
    if(threadSource != null) {
      threadSource.execute(docDataUpdaterFuture);
    } else {
      new Thread(docDataUpdaterFuture, 
        DocumentDataUpdater.class.getCanonicalName()).start();
    }    
  }

  protected String getActionBaseUrl(String action) throws IOException{
    //this method is always called from interactive methods, that are capable of
    //reporting errors to the user. So we use this place to check if the 
    //background thread had any problems, and report them if so.
    if(exceptionInBackgroundThread != null){
      Exception e = exceptionInBackgroundThread;
      exceptionInBackgroundThread = null;
      throw (IOException)new IOException(
          "Problem communicating with the remote index", e);
    }
    //an example URL looks like this:
    //http://localhost:8080/mimir/bf25398ff0874224/search/documentsCountBin?queryId=c4da799e-9ca2-46ae-8ded-30bdc37ad607
    StringBuilder str = new StringBuilder(remoteUrl);
    str.append(SERVICE_SEARCH);
    str.append('/');
    str.append(action);
    return str.toString();
  }

  /**
   * Gets (from the cache, or from the remote endpoint) the {@link DocumentData}
   * for the document at the specified rank.
   * @param rank
   * @return
   * @throws IndexException
   * @throws IndexOutOfBoundsException
   * @throws IOException
   */
  protected DocumentData getDocumentData(long rank) throws IndexException, 
      IndexOutOfBoundsException, IOException {
    DocumentData docData = documentCache.getAndMoveToFirst(rank);
    if(docData == null) {
      // cache miss -> remote retrieve
      try {
        docData = (DocumentData)webUtils.getObject(
            getActionBaseUrl(ACTION_DOC_DATA_BIN),
            "queryId",  URLEncoder.encode(queryId, "UTF-8"),
            "documentRank", Long.toString(rank));
        documentCache.putAndMoveToFirst(rank, docData);
        if(documentCache.size() > DOCUMENT_CACHE_SIZE) {
          // reduce size
          documentCache.removeLast();
        }
      } catch(IOException e) {
        throw new IndexException(e);
      } catch(ClassNotFoundException e) {
        throw new IndexException("Was expecting a DocumentData value, " +
        		"but got an unknown object type!", e);
      }
    }
    return docData;
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentsCount()
   */
  @Override
  public long getDocumentsCount() {
    return documentsCount;
  }

  
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentsCountSync()
   */
  @Override
  public long getDocumentsCountSync() {
    try{
      docDataUpdaterFuture.get();
    } catch(Exception e) {
      logger.error("Exception while getting all document IDs", e);
      throw new IllegalStateException(
        "Exception while getting all document IDs", e);
    }    
    return getDocumentsCount();
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getCurrentDocumentsCount()
   */
  @Override
  public long getDocumentsCurrentCount() {
    return (documentsCount < 0) ? currentDocumentsCount : documentsCount;
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentID(int)
   */
  @Override
  public long getDocumentID(long rank) throws IndexOutOfBoundsException,
          IOException {
    if(rank >= documentIds.size()) {
      // we need to get more document IDs&scores
      downloadDocIdScores(rank);
    }
    return documentIds.getLong(rank);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentScore(int)
   */
  @Override
  public double getDocumentScore(long rank) throws IndexOutOfBoundsException,
          IOException {
    if(rank >= documentScores.size64()) {
      // we need to get more document IDs&scores
      downloadDocIdScores(rank);
    }    
    return documentScores.get(rank);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentHits(int)
   */
  @SuppressWarnings("unchecked")
  @Override
  public List<Binding> getDocumentHits(long rank)
          throws IndexOutOfBoundsException, IOException {
    try {
      return (List<Binding>)webUtils.getObject(
        getActionBaseUrl(ACTION_DOC_HITS_BIN),
        "queryId", queryId,
        "documentRank", Long.toString(rank));
    } catch(ClassNotFoundException e) {
      throw new RuntimeException("Got wrong value type from remote endpoint", 
        e);
    }
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentText(int, int, int)
   */
  @Override
  public String[][] getDocumentText(long rank, int termPosition, int length)
          throws IndexException, IndexOutOfBoundsException, IOException {
    return getDocumentData(rank).getText(termPosition, length);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentURI(int)
   */
  @Override
  public String getDocumentURI(long rank) throws IndexException,
          IndexOutOfBoundsException, IOException {
    return getDocumentData(rank).getDocumentURI();
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentTitle(int)
   */
  @Override
  public String getDocumentTitle(long rank) throws IndexException,
          IndexOutOfBoundsException, IOException {
    return getDocumentData(rank).getDocumentTitle();
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataField(int, java.lang.String)
   */
  @Override
  public Serializable getDocumentMetadataField(long rank, String fieldName)
          throws IndexException, IndexOutOfBoundsException, IOException {
    return getDocumentData(rank).getMetadataField(fieldName);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataFields(int, java.util.Set)
   */
  @Override
  public Map<String, Serializable> getDocumentMetadataFields(long rank,
      Set<String> fieldNames) throws IndexException, IndexOutOfBoundsException, 
      IOException {
    Map<String, Serializable> res = new HashMap<String, Serializable>();
    for(String fieldName : fieldNames) {
      Serializable value = getDocumentMetadataField(rank, fieldName);
      if(value != null) res.put(fieldName, value);
    }
    return res;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#renderDocument(int, java.lang.Appendable)
   */
  @Override
  public void renderDocument(long rank, Appendable out) throws IOException,
          IndexException {
    webUtils.getText(out, getActionBaseUrl(ACTION_RENDER_DOCUMENT),
            "queryId", queryId,
            "rank", Long.toString(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#close()
   */
  @Override
  public void close() throws IOException {
    webUtils.getVoid(getActionBaseUrl(ACTION_CLOSE),
            "queryId", queryId);
    closed = true;
    documentCache.clear();
  }
  
  /**
   * Gets from the remote end point a range of document IDs and document scores,
   * which is guaranteed to include the document at the given rank.
   * @param rank
   * @throws IOException 
   */
  protected void downloadDocIdScores(long rank) throws IOException {
    long firstRank = documentIds.size64();
    if(firstRank != documentScores.size64()) {
      throw new IllegalStateException("Document IDs and scores out of sync.");
    }
    long size = rank - firstRank + 1;
    if(size < docBlockSize) size = docBlockSize;
    
    long[] newDocIds;
    double[] newDocScores;
    try {
      newDocIds = (long[]) webUtils.getObject(
        getActionBaseUrl(ACTION_DOC_IDS_BIN), 
        "queryId", URLEncoder.encode(queryId, "UTF-8"),
        "firstRank", Long.toString(firstRank),
        "size", Long.toString(size));
      documentIds.addElements(firstRank, new long[][]{newDocIds});
      newDocScores = (double[]) webUtils.getObject(
        getActionBaseUrl(ACTION_DOC_SCORES_BIN), 
        "queryId", URLEncoder.encode(queryId, "UTF-8"),
        "firstRank", Long.toString(firstRank),
        "size", Long.toString(size));
      documentScores.addElements(firstRank, new double[][]{newDocScores});
    } catch(ClassNotFoundException e) {
      // this should really not happen (the 'class' is double)
      throw new RuntimeException("Error communicating to remote endpoint", e);
    }
  }

  /**
   * Returns the query ID that this instance is working over.
   */
  public String getQueryId() {
    return queryId;
  } 
}
