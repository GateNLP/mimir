/*
 *  RankingQueryRunnerImpl.java
 *
 *  Copyright (c) 1995-2010, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 16 Nov 2011
 *
 *  $Id: RankingQueryRunnerImpl.java 18271 2014-08-21 13:40:10Z ian_roberts $
 */
package gate.mimir.search;

import gate.mimir.index.IndexException;
import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.score.MimirScorer;
import it.unimi.dsi.fastutil.doubles.DoubleBigArrayBigList;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import it.unimi.dsi.fastutil.objects.ObjectBigList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;

import org.apache.log4j.Logger;

/**
 * A QueryRunner implementation that can perform ranking.
 * This query runner has two modes of functioning: ranking and non-ranking, 
 * depending on whether a {@link MimirScorer} is provided  during construction
 * or not.
 * All documents are referred to using their rank (i.e. position in the list of 
 * results). When working in non-ranking mode, ranking order is the same as 
 * document ID order.
 */
public class RankingQueryRunnerImpl implements QueryRunner {
  
  
  /**
   * Constant used as a flag to mark then of a list of tasks.
   */
  private static final Runnable NO_MORE_TASKS = new Runnable(){ 
    public void run() {}
  };
  
  /**
   * The background thread implementation: simply collects {@link Runnable}s 
   * from the {@link RankingQueryRunnerImpl#backgroundTasks} queue and runs them. 
   */
  protected class BackgroundRunner implements Runnable {
    @Override
    public void run() {
      try {
        while(!closed) {
          Runnable job = backgroundTasks.take();
          if(job == NO_MORE_TASKS) break;
          else  job.run();
        }
      } catch(InterruptedException e) {
        Thread.currentThread().interrupt();
        e.printStackTrace();
      }
    }
  }
   
  
  /**
   * Collects the document hits (i.e. {@link Binding}s) for the documents 
   * between the two provided ranks (indexes in the {@link #documentsOrder} 
   * list. If ranking is not being performed ( {@link #documentsOrder} is
   * <code>null</null>, then the indexes are used against the 
   * {@link #documentIds} list.
   * 
   * This is the only actor that writes to the {@link #documentHits} list.
   */  
  protected class HitsCollector implements Runnable {
    /**
     * The starting rank
     */
    long start;
    
    /**
     * The ending rank
     */
    long end;
    
    public HitsCollector(long rangeStart, long rangeEnd) {
      this.start = rangeStart;
      this.end = rangeEnd;
    }
    
    @Override
    public void run() {
      long[] documentIndexes = null;
      if(ranking) {
        // we're ranking -> first calculate the range of documents in ID order
        documentIndexes = new long[(int)(end - start)];
        for(long i = start; i < end; i++) {
          documentIndexes[(int)(i - start)] = documentsOrder.getLong(i);
        }
        Arrays.sort(documentIndexes);
      }
      
      try {
        // see if we can get at the first document
        long docIndex = (documentIndexes != null ? documentIndexes[0] : start);
        long docId = documentIds.getLong(docIndex);
        if(queryExecutor.getLatestDocument() < 0 ||
           queryExecutor.getLatestDocument() >= docId) {
          // we need to 'scroll back' the executor: get a new executor
          QueryExecutor oldExecutor = queryExecutor;
          queryExecutor = queryExecutor.getQueryNode().getQueryExecutor(
                  queryEngine);
          oldExecutor.close();
        }
        for(long i = start; i < end; i++) {
          docIndex = (documentIndexes != null ? 
              documentIndexes[(int)(i - start)] : i);
          docId = documentIds.getLong(docIndex);
          // don't need to check for deletion here as we know for sure that this
          // doc ID is ok.  The only exception would be if it was deleted since
          // this query was originally issued, but I think we can live with that
          long newDoc = queryExecutor.nextDocument(docId - 1);
          // sanity check
          if(newDoc == docId) {
            List<Binding> hits = new ObjectArrayList<Binding>();
            Binding aHit = queryExecutor.nextHit();
            while(aHit != null) {
              hits.add(aHit);
              aHit = queryExecutor.nextHit();
            }
            documentHits.set(docIndex, hits);
          } else {
            // this could happen if we've been closed in the mean time
            if(closed) return;
            // we got the wrong document ID
            logger.error("Unexpected document ID returned by executor " +
            		"(got " + newDoc + " while expecting " + docId + "!");
          }
        }
      } catch(IOException e) {
        // this could happen if we've been closed in the mean time
        if(closed) return;
        // otherwise, it's an error
        logger.error("Exception while restarting the query executor.", e);
        try {
          close();
        } catch(IOException e1) {
          logger.error("Exception while closing the query runner.", e1);
        }
      }
    }
  }
  
  
  /**
   * The first action started when a new {@link RankingQueryRunnerImpl} is 
   * created. It performs the following actions:
   * <ul>
   *   <li>collects all document IDs in 
   *   {@link RankingQueryRunnerImpl#documentIds}</li>
   *   <li>if ranking enabled
   *     <ul>
   *     <li>it collects all document scores
   *     </ul>
   *   </li>  
   *   <li>if ranking not enabled
   *     <ul>
   *       <li>it collects the document hits for the first 
   *       block of documents</li>
   *     </ul>
   *   </li>
   *   <li>If ranking enabled, after all document IDs are obtained, it starts 
   *   the work for ranking the first block of documents (which, upon 
   *   completion, will also start a background job to collect all the hits for 
   *   that block).</li>  
   * </ul>
   */
  protected class DocIdsCollector implements Runnable {
    @Override
    public void run() {
      try{
        // collect all documents and their scores
        if(ranking) scorer.wrap(queryExecutor);
        long docId = nextNotDeleted();
        while(docId >= 0) {
          // enlarge the hits list
          if(ranking){
            documentScores.add(scorer.score());
            documentHits.add(null);
          } else {
            // not scoring: also collect the hits for the first block of documents
            if(docId < docBlockSize) {
              ObjectList<Binding> hits = new ObjectArrayList<Binding>();
              Binding hit = queryExecutor.nextHit();
              while(hit != null) {
                hits.add(hit);
                hit = queryExecutor.nextHit();
              }
              documentHits.add(hits);
            } else {
              documentHits.add(null);
            }
          }
          // and store the new doc ID
          documentIds.add(docId);
          docId = nextNotDeleted();
        }
        allDocIdsCollected = true;
        if(ranking) {
          // now rank the first batch of documents
          // this will also start a second background job to collect the hits
          rankDocuments(docBlockSize -1);
        }
      } catch (Exception e) {
        // this could happen if we've been closed in the mean time
        if(closed) return;
        // otherwise, it's an error
        logger.error("Exception while collecting document IDs", e);
        try {
          close();
        } catch(IOException e1) {
          logger.error("Exception while closing, after exception.", e1);
        }
      }
    }
  }
  
  /**
   * Shared logger instance.
   */
  protected static Logger logger =  Logger.getLogger(RankingQueryRunnerImpl.class);
  
  /**
   * The {@link QueryExecutor} for the query being run.
   */
  protected QueryExecutor queryExecutor;
  
  /**
   * The QueryEngine we run inside.
   */
  protected QueryEngine queryEngine;
  
  /**
   * The {@link MimirScorer} to be used for ranking documents.
   */
  protected MimirScorer scorer;
  
  /**
   * Flag set to <code>true</code> when ranking is being performed, or 
   * <code>false</code> otherwise.
   */
  final boolean ranking;

  /**
   * The number of documents to be ranked (of have their hits collected) as a 
   * block.
   */
  protected int docBlockSize;
  
  /**
   * The document IDs for the documents found to contain hits. This list is
   * sorted in ascending documentID order.
   */
  protected LongBigList documentIds;
  
  /**
   * If scoring is enabled ({@link #scorer} is not <code>null</code>), this list
   * contains the scores for the documents found to contain hits. This list is 
   * aligned to {@link #documentIds}.   
   */
  protected DoubleBigArrayBigList documentScores;
  
  /**
   * The sets of hits for each returned document. This data structure is lazily 
   * built, so some elements may be null. This list is aligned to 
   * {@link #documentIds}.   
   */
  protected ObjectBigList<List<Binding>> documentHits;

  /**
   * The order the documents should be returned in (elements in this list are 
   * indexes in {@link #documentIds}).
   */
  protected LongBigList documentsOrder;
  
  /**
   * Data structure holding references to {@link Future}s that are currently 
   * working (or have worked) on collecting hits for a range of document 
   * indexes.
   */
  protected SortedMap<long[], Future<?>> hitCollectors;
  
  /**
   * The background thread used for collecting hits.
   */
  protected Thread runningThread;
  
  /**
   * A queue with tasks to be executed by the background thread. 
   */
  protected BlockingQueue<Runnable> backgroundTasks;
  
  /**
   * Flag used to mark that all results documents have been counted.
   */
  protected volatile boolean allDocIdsCollected = false;
  
  /**
   * The task that's working on collecting all the document IDs. When this 
   * activity has finished, the precise documents count is known.
   */
  protected volatile FutureTask<Object> docIdCollectorFuture;
  
  /**
   * Internal flag used to mark when this query runner has been closed.
   */
  protected volatile boolean closed;
  
  /**
   * Creates a query runner in ranking mode.
   * @param qNode the {@link QueryNode} for the query being executed.
   * @param scorer the {@link MimirScorer} to use for ranking.
   * @param qEngine the {@link QueryEngine} used for executing the queries.
   * @throws IOException
   */
  public RankingQueryRunnerImpl(QueryExecutor executor, MimirScorer scorer) throws IOException {
    this.queryExecutor = executor;
    this.scorer = scorer;
    this.closed = false;
    ranking = scorer != null;
    queryEngine = queryExecutor.getQueryEngine();
    docBlockSize = queryEngine.getDocumentBlockSize();
    documentIds = new LongBigArrayBigList();
    documentHits = new ObjectBigArrayBigList<List<Binding>>();
    if(scorer != null) {
      documentScores = new DoubleBigArrayBigList();
      documentsOrder = new LongBigArrayBigList(docBlockSize);
    }
    hitCollectors = new Object2ObjectAVLTreeMap<long[], Future<?>>(
        new Comparator<long[]>(){
          @Override
          public int compare(long[] o1, long[] o2) {
            long res = o1[0] - o2[0]; 
            return res > 0 ? 1 : (res == 0 ? 0 : -1); 
          }
        });
    // start the background thread
    backgroundTasks = new LinkedBlockingQueue<Runnable>();
    Runnable backgroundRunner = new BackgroundRunner();
    //get a thread from the executor, if one exists
    if(queryEngine.getExecutor() != null){
      try {
        queryEngine.getExecutor().execute(backgroundRunner);
      } catch(RejectedExecutionException e) {
        logger.warn("Could not allocate a new background thread", e);
        throw new RejectedExecutionException(
          "System overloaded, please try again later."); 
      }
    }else{
      Thread theThread = new Thread(backgroundRunner, getClass().getName());
      theThread.setDaemon(true);
      theThread.start();
    }

    // queue a job for collecting all document ids
    try {
      docIdCollectorFuture = new FutureTask<Object>(new DocIdsCollector(), null);
      backgroundTasks.put(docIdCollectorFuture);
      if(!ranking) {
        // if not ranking, the doc IDs collector will all collect the
        // hits for the first docBlockSize number of documents
        synchronized(hitCollectors) {
          hitCollectors.put(new long[]{0, docBlockSize}, docIdCollectorFuture);
        }
      }
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Could not queue a background task.", e);
    }
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentsCount()
   */
  @Override
  public long getDocumentsCount() {
    if(allDocIdsCollected) return documentIds.size64();
    else return -1;
  }
  
  /**
   * Synchronous version of {@link #getDocumentsCount()} that waits if necessary
   * before returning the correct result (instead of returning  <code>-1</code>
   * of the value is not yet known).
   * @return the total number of documents found to match the query.
   */
  @Override
  public long getDocumentsCountSync() {
    try {
      docIdCollectorFuture.get();
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
    return documentIds.size64();
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentID(int)
   */
  @Override
  public long getDocumentID(long rank) throws IndexOutOfBoundsException, IOException {
    return documentIds.getLong(getDocumentIndex(rank));
  }
  
  @Override
  public double getDocumentScore(long rank) throws IndexOutOfBoundsException, IOException {
    return (documentScores != null) ? 
        documentScores.getDouble(getDocumentIndex(rank)) : 
        DEFAULT_SCORE;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentHits(int)
   */
  @Override
  public List<Binding> getDocumentHits(long rank) throws IndexOutOfBoundsException, IOException {
    long documentIndex = getDocumentIndex(rank);
    List<Binding> hits = documentHits.get(documentIndex);
    if(hits == null) {
      // hits not collected yet
      try {
        // find the Future working on it, or start a new one, 
        // then wait for it to complete
        collectHits(new long[]{rank, rank + 1}).get();
        hits = documentHits.get(documentIndex);
      } catch(Exception e) {
        logger.error("Exception while waiting for hits collection", e);
        throw new RuntimeException(
          "Exception while waiting for hits collection", e); 
      }
    }
    return hits;
  }
  
  /**
   * Given a document rank, return its index in the {@link #documentIds} list.
   * If ranking is not being performed, then the rank is interpreted as an index 
   * against the {@link #documentIds} list and is simply returned. 
   * @param rank
   * @return
   * @throws IOException, IndexOutOfBoundsException 
   */
  protected long getDocumentIndex(long rank) throws IOException, 
      IndexOutOfBoundsException {
    long maxRank = documentIds.size64();
    if(rank >= maxRank) throw new IndexOutOfBoundsException(
      "Document rank too large (" + rank + " > " + maxRank + ".");
    if(documentsOrder != null) {
      // we're in ranking mode
      if(rank >= documentsOrder.size64()) {
        // document exists, but has not been ranked yet
        rankDocuments(rank);
      }
      return documentsOrder.getLong(rank);
    } else {
      return rank;
    }
  }
  
  /**
   * Ranks some more documents (i.e. adds more entries to the 
   * {@link #documentsOrder} list, making sure that the document at provided 
   * rank is included (if such a document exists). If the provided rank is 
   * larger than the number of result documents, then all documents will be
   * ranked before this method returns. 
   * This is the only method that writes to the {@link #documentsOrder} list.
   * This method is executed synchronously in the client thread.
   *  
   * @param rank
   * @throws IOException 
   */
  protected void rankDocuments(long rank) throws IOException {
    if(rank < documentsOrder.size64()) return;
    synchronized(documentsOrder) {
      // rank some documents
      long rankRangeStart = documentsOrder.size64();
      // right boundary is exclusive
      long rankRangeEnd = rank + 1;
      if((rankRangeEnd - rankRangeStart) < (docBlockSize)) {
        // extend the size of the chunk of documents to be ranked
        rankRangeEnd = rankRangeStart + docBlockSize; 
      }
      // the document with the minimum score already ranked.
      long smallestOldScoreDocId = rankRangeStart > 0 ? 
        documentIds.getLong(documentsOrder.getLong(rankRangeStart -1))
        : -1;
      // the score for the document above, which is a the upper limit for new scores
      double smallestOldScore = rankRangeStart > 0 ? 
          documentScores.getDouble(documentsOrder.getLong(rankRangeStart -1))
          : Double.POSITIVE_INFINITY;
      // now collect some more documents
      for(long i = 0; i < documentIds.size64(); i++) {
        long documentId = documentIds.getLong(i);
        double documentScore = documentScores.getDouble(i);
        // the index for the document with the smallest score, 
        // from the new ones being ranked 
        long smallestDocIndex = rankRangeStart < documentsOrder.size64() ?
            documentsOrder.getLong(rankRangeStart) : -1;
        // the smallest score that's been seen in this new round 
        double smallestNewScore = smallestDocIndex == -1 ? Double.NEGATIVE_INFINITY : 
            documentScores.getDouble(smallestDocIndex);
        // we care about this new document if:
        // - we haven't collected enough documents yet, or
        // - it has a better score than the smallest score so far, but a 
        // smaller score than the maximum permitted score (i.e. it has not 
        // already been ranked)., or
        // - it's a new document (i.e. with an ID strictly larger) with the same 
        // score as the largest permitted score
        if(documentsOrder.size64() < rankRangeEnd
           || 
           (documentScore > smallestNewScore && documentScore < smallestOldScore) 
           ||
           (documentScore == smallestOldScore && documentId > smallestOldScoreDocId)
           ) {
          // find the rank for the new doc in the documentsOrder list, and insert
          documentsOrder.add(findRank(documentScore, rankRangeStart, 
              documentsOrder.size64()), i);
          // if we have too many documents, drop the lowest scoring one
          if(documentsOrder.size64() > rankRangeEnd) {
            documentsOrder.removeLong(documentsOrder.size64() - 1);
          }          
        }
      }
      // start collecting the hits for the newly ranked documents (in a new thread)
      if(documentsOrder.size64() > rankRangeStart){
        collectHits(new long[] {rankRangeStart, documentsOrder.size64()});
      }
    }
  }
  
  /**
   * Given a document score, finds the correct insertion point into the 
   * {@link #documentsOrder} list, within a given range of ranks.
   * This method performs binary search followed by a linear scan so that the 
   * returned insertion point is the largest correct one (i.e. later documents 
   * with the same score get sorted after earlier ones, thus keeping the sorting
   * stable).
   *      
   * @param documentScore the score for the new document.
   * @param start the start of the search range within {@link #documentsOrder} 
   * @param end the end of the search range within {@link #documentsOrder} 
   * @return the largest correct insertion point
   */
  protected long findRank(double documentScore, long start, long end) {
    // standard binary search
    double midVal;
    end--;
    while (start <= end) {
     long mid = (start + end) >>> 1;
     midVal = documentScores.getDouble(documentsOrder.getLong(mid));
     // note that the documentOrder list is in decreasing score order!
     if (midVal > documentScore) start = mid + 1;
     else if (midVal < documentScore) end = mid - 1;
     else {
       // we found a doc with exactly the same score: scan to the right
       while(documentsOrder.size64() < mid && 
             documentScores.getDouble(documentsOrder.getLong(mid)) == 
           documentScore){
         mid++;
       }
       return mid;
     }
    }
    return start;
  }
  
  /**
   * Makes sure all the documents in the specified range are queued for hit 
   * collection. 
   * @param interval the interval specified by 2 document ranks. The interval is
   * defined as the elements in {@link #documentsOrder} between ranks 
   * interval[0] and (interval[1]-1) inclusive. 
   * @return the future that has been queued for collecting the hits.
   */
  protected Future<?> collectHits(long[] interval) {
    // expand the interval to block size (or size of documentsOrder)
    if(interval[1] - interval[0] < docBlockSize) {
      final long expansion = docBlockSize - (interval[1] - interval[0]);
      // expand up to (expansion / 2) to the left
      interval[0] = Math.max(0, interval[0] - (expansion / 2));
      // expand to the right
      long upperBound = documentsOrder != null ? 
          documentsOrder.size64() : documentIds.size64();
      interval[1] = Math.min(upperBound, interval[0] + docBlockSize);
    }
    HitsCollector hitsCollector = null;
    synchronized(hitCollectors) {
      SortedMap<long[], Future<?>> headMap = hitCollectors.headMap(interval); 
      long[] previousInterval = headMap.isEmpty() ? new long[]{0, 0} : 
          headMap.lastKey();
      if(previousInterval[1] >= interval[1]) {
        // we're part of previous interval
        return hitCollectors.get(previousInterval);
      } else {
        // calculate an appropriate interval to collect hits for
        SortedMap<long[], Future<?>> tailMap = hitCollectors.tailMap(
          new long[]{interval[1], interval[1]});
        long[] followingInterval = tailMap.isEmpty() ? 
            new long[]{interval[1], interval[1]} : tailMap.firstKey();
        long start = Math.max(previousInterval[1] - 1, interval[0]);
        long end = Math.min(followingInterval[0], interval[1]);
        hitsCollector = new HitsCollector(start, end);
        FutureTask<?> future = new FutureTask<Object>(hitsCollector, null);
        hitCollectors.put(new long[]{start, end}, future);
        try {
          backgroundTasks.put(future);
        } catch(InterruptedException e) {
          logger.error("Error while queuing background work", e);
          throw new RuntimeException("Error while queuing background work", e);
        }
        return future;
      }
    }
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentText(int, int, int)
   */
  @Override
  public String[][] getDocumentText(long rank, int termPosition, int length) 
          throws IndexException, IndexOutOfBoundsException, IOException {
    return queryEngine.getText(getDocumentID(rank), termPosition, length);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentURI(int)
   */
  @Override
  public String getDocumentURI(long rank) throws IndexException, 
      IndexOutOfBoundsException, IOException {
    return queryEngine.getDocumentURI(getDocumentID(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentTitle(int)
   */
  @Override
  public String getDocumentTitle(long rank) throws IndexException, 
      IndexOutOfBoundsException, IOException {
    return queryEngine.getDocumentTitle(getDocumentID(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataField(int, java.lang.String)
   */
  @Override
  public Serializable getDocumentMetadataField(long rank, String fieldName)
      throws IndexException, IndexOutOfBoundsException, IOException {
    return queryEngine.getDocumentMetadataField(getDocumentID(rank), fieldName);
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
        queryEngine.renderDocument(getDocumentID(rank), 
                getDocumentHits(rank), out);
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#close()
   */
  @Override
  public void close() throws IOException {
    this.closed = true;
    try{
      if(queryEngine != null) queryEngine.releaseQueryRunner(this);
      if(queryExecutor != null) queryExecutor.close();
      scorer = null;      
    } finally {
      try {
        // stop the background tasks runnable, 
        // which will return the thread to the pool
        backgroundTasks.put(NO_MORE_TASKS);
      } catch(InterruptedException e) {
        // ignore
      }      
    }
  }

  /**
   * Find the next document ID for the current query executor which is not
   * marked as deleted in the index.
   */
  protected long nextNotDeleted() throws IOException {
    long docId = ranking ? scorer.nextDocument(-1)
                         : queryExecutor.nextDocument(-1);
    while(docId >= 0 && queryEngine.getIndex().isDeleted(docId)) {
      docId = ranking ? scorer.nextDocument(-1)
                      : queryExecutor.nextDocument(-1);
    }
    
    return docId;
  }
}