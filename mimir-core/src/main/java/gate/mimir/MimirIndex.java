/*
 *  Index.java
 *
 *  Copyright (c) 2007-2013, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 30 Oct 2013
 *
 *  $Id: MimirIndex.java 17795 2014-04-10 12:00:46Z ian_roberts $
 */
package gate.mimir;

import gate.Document;
import gate.Gate;
import gate.creole.AnalyserRunningStrategy;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.mimir.IndexConfig.TokenIndexerConfig;
import gate.mimir.index.AtomicAnnotationIndex;
import gate.mimir.index.AtomicIndex;
import gate.mimir.index.AtomicTokenIndex;
import gate.mimir.index.DocumentCollection;
import gate.mimir.index.DocumentData;
import gate.mimir.index.GATEDocument;
import gate.mimir.index.IndexException;
import gate.mimir.search.QueryEngine;
import gate.util.GateRuntimeException;
import it.unimi.di.big.mg4j.index.cluster.IndexCluster;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

import org.apache.log4j.Logger;

/**
 * <p>
 * A Mímir index which can index documents and answer queries. This class is the
 * main entry point to the Mímir API.
 * </p>
 * A Mímir index is a compound index comprising the following data elements:
 * <ul>
 * <li>one or more sub-indexes (implemented by classes that extend
 * {@link AtomicIndex}.</li>
 * <li>a document collection containing the document textual content and
 * metadata</li>
 * </ul>
 * <p>
 * Each sub-index indexes either a certain feature of token annotations 
 * ({@link AtomicTokenIndex}) or one or more annotation types 
 * ({@link AtomicAnnotationIndex}).
 * </p>
 * <p>
 * A Mímir index is continually accepting documents to be indexed (through calls
 * to {@link #indexDocument(Document)}) and can answer queries though the
 * {@link QueryEngine} instance returned by {@link #getQueryEngine()}.
 * </p>
 * <p>
 * Documents submitted for indexing are initially accumulated in RAM, during
 * which time they are not available for being searched. After documents in RAM
 * are written to disk (a <em>sync-to-disk</em> operation), they become
 * searchable. In-RAM documents are synced to disk after a certain amount of
 * data has been accumulated (see {@link #setOccurrencesPerBatch(long)}) and
 * also at regular time intervals (see {@link #setTimeBetweenBatches(int)}).
 * </p>
 * <p>
 * Client code can request a <em>sync to disk</em> operation by calling
 * {@link #requestSyncToDisk()}.
 * </p>
 * <p>
 * Every sync-to-disk operation causes a new index <em>batch</em> to be created.
 * All the batches are merged into a {@link IndexCluster} which is then used to
 * serve queries. If the number of clusters gets too large, it can harm
 * efficiency or the system can run into problems due to too large a number of
 * files being open. To avoid this, the index batches can be <em>compacted</em>
 * into a single batch. The index will automatically do that once the number of
 * batches exceeds {@link IndexConfig#setMaximumBatches(int)}.
 * </p>
 * <p>
 * Client code can request a compact operation by calling
 * {@link #requestCompactIndex()}.
 * </p>
 * <p>
 * In order to keep its consistency, a Mímir index <strong>must</strong> be
 * closed orderly by calling {@link #close()} before the JVM is shut down.
 * </p>
 */
public class MimirIndex {
  

  /**
   * The name of the file in the index directory where the index config is
   * saved.
   */
  public static final String INDEX_CONFIG_FILENAME = "config.xml";
  
  /**
   * The name for the file (stored in the root index directory) containing 
   * the serialised version of the {@link #deletedDocumentIds}. 
   */
  public static final String DELETED_DOCUMENT_IDS_FILE_NAME = "deleted.ser";
  
  /**
   * How many occurrences to index in each batch. This metric is more reliable, 
   * than document counts, as it does not depend on average document size. 
   */
  public static final int DEFAULT_OCCURRENCES_PER_BATCH = 100 * 1000 * 1000;
  
  /**
   * The default length for the buffer input / output queues for sub-indexers.
   */
  public static final int DEFAULT_INDEXING_QUEUE_SIZE = 30;
  
  /**
   * Special value used to indicate that the index is closing and there will be 
   * no more sync tasks to process (an END_OF_QUEUE value for 
   * {@link #syncRequests}). 
   */
  protected final static Future<Long> NO_MORE_TASKS = new FutureTask<Long>(
      new Callable<Long>() {
        @Override
        public Long call() throws Exception {
          return 0l;
        }
  });
  
  /**
   * How many occurrences to be accumulated in RAM before a new tail batch is
   * written to disk.
   */
  protected long occurrencesPerBatch = DEFAULT_OCCURRENCES_PER_BATCH;
  
  private static final Logger logger = Logger.getLogger(MimirIndex.class);
  
  /**
   * A {@link Runnable} used in a background thread to perform various index 
   * maintenance tasks:
   * <ul>
   *   <li>check that the documents are being returned from the sub-indexers
   *   in the same order as they were submitted for indexing;</li>
   *   <li>update the {@link MimirIndex#occurrencesInRam} value by adding the 
   *   occurrences produced by indexing new documents.</li>
   *   <li>delete indexed documents from GATE</li>
   * </ul>
   */
  protected class IndexMaintenanceRunner implements Runnable {
    public void run(){
      boolean finished = false;
      while(!finished){
        GATEDocument currentDocument = null;
        try {
          //get one document from each of the sub-indexers
          //check identity and add to output queue.
          for(AtomicIndex aSubIndexer : subIndexes){
            GATEDocument aDoc = aSubIndexer.getOutputQueue().take();
            if(currentDocument == null){
              currentDocument = aDoc;
            }else if(aDoc != currentDocument){
              //malfunction!
              throw new RuntimeException(
                      "Out of order document received from sub-indexer!");
            }
          }
          //we obtained the same document from all the sub-indexers
          if(currentDocument != GATEDocument.END_OF_QUEUE) {
            occurrencesInRam += currentDocument.getOccurrences();
            // let's delete it
            logger.debug("Deleting document "
                + currentDocument.getDocument().getName());
            gate.Factory.deleteResource(currentDocument.getDocument());
            logger.debug("Document deleted.  "
                    + Gate.getCreoleRegister().getLrInstances(
                        currentDocument.getDocument().getClass().getName())
                            .size() + " documents still live.");            
          } else {
            // we're done
            finished = true;
          }
        } catch(InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }  
  
  /**
   * A {@link Runnable} used in a background thread to perform various index 
   * maintenance tasks:
   * <ul>
   *   <li>update the {@link MimirIndex#occurrencesInRam} value by subtracting 
   *   the occurrence counts for all the batches that have recently been written
   *   to disk. It finds these by consuming the {@link Future}s in 
   *   {@link MimirIndex#syncRequests}.</li>
   *   <li>Compact the index when too many on-disk batches have been created.</li>
   *   <li>compact the document collection when too many archive files have 
   *   been created.</li>
   * </ul>
   * 
   * We use a different background thread (instead of adding more work to 
   * {@link IndexMaintenanceRunner}) because each of the threads gets its tasks
   * from a different blocking queue, which allows us to sleep the background
   * threads while they're not needed.
   */
  protected class IndexMaintenanceRunner2 implements Runnable {

    @Override
    public void run() {
      try {
        Future<Long> aTask = syncRequests.take();
        while(aTask != NO_MORE_TASKS) {
          try {
            occurrencesInRam -= aTask.get();
            if(syncRequests.isEmpty()) {
              // latest dump finished: compact index if needed;
              boolean compactNeeded = false;
              for(AtomicIndex aSubIndex : subIndexes) {
                if(aSubIndex.getBatchCount() > indexConfig.getMaximumBatches()) {
                  compactNeeded = true;
                  break;
                }
              }
              if(compactNeeded && !closed){
                logger.debug("Compacting sub-indexes");
                compactIndexSync();
              }
              if(documentCollection.getArchiveCount() >  indexConfig.getMaximumBatches()
                 && !closed) {
                try {
                  logger.debug("Compacting document collection");
                  compactDocumentCollection();
                } catch(Exception e) {
                  logger.error("Error while compacting document collection. "
                      + "Index is now invalid. Closing index to avoid further damage.",
                      e);
                  try {
                    close();
                  } catch(InterruptedException e1) {
                    logger.error("Received interrupt request while closing "
                        + "operation in progress", e);
                    Thread.currentThread().interrupt();
                  } catch(IOException e1) {
                    logger.error("Further IO exception while closing index.", e1);
                  }
                }
              }
              
            }
          } catch(ExecutionException e) {
            // a sync request has failed. The index may be damaged, so we will
            // close it to avoid further damage.
            logger.error("A sync-to-disk request has failed. Closing index "
                + "to avoid further damage.", e);
            try {
              close();
            } catch(IOException e1) {
              logger.error("A further error was generated while attmepting "
                  + "to close index.", e1);
            }
          }
          aTask = syncRequests.take();
        }
      } catch(InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    
    /**
     * Request the index compaction, and waits for all the operations to 
     * complete.
     * @throws InterruptedException
     */
    protected void compactIndexSync() throws InterruptedException {
      List<Future<Void>> futures = requestCompactIndex();

      for(Future<Void> f : futures){
        try {
          f.get();
        } catch(InterruptedException e) {
          // we were interrupted while waiting for a compacting operation
          logger.error("Received interrupt request while compacting "
              + "operation in progress", e);
          Thread.currentThread().interrupt();
        } catch(ExecutionException e) {
          logger.error("Execution exception while compacting the index. Index "
              + "may now be corrupted, closing it to avoid further damage", e);
          try {
            close();
          } catch(InterruptedException e1) {
            logger.error("Received interrupt request while closing "
                + "operation in progress", e);
          } catch(IOException e1) {
            logger.error("Further IO exception while closing index.", e1);
          }
        }
      }      
    }
  }  
  
  
  private class WriteDeletedDocsTask extends TimerTask {
    public void run() {
      synchronized(maintenanceTimer) {
        File delFile = new File(indexDirectory, DELETED_DOCUMENT_IDS_FILE_NAME);
        if(delFile.exists()) {
          delFile.delete();
        }
        try{
          logger.debug("Writing deleted documents set");
          ObjectOutputStream oos = new ObjectOutputStream(
                  new GZIPOutputStream(
                  new BufferedOutputStream(
                  new FileOutputStream(delFile))));
          oos.writeObject(deletedDocumentIds);
          oos.flush();
          oos.close();
          logger.debug("Writing deleted documents set completed.");
        }catch (IOException e) {
          logger.error("Exception while writing deleted documents set", e);
        }        
      }
    }
  }
  
  /**
   * {@link TimerTask} used to regularly dump the latest document to an on-disk
   * batch, allowing them to become searchable.
   */
  protected class SyncToDiskTask extends TimerTask {
    @Override
    public void run() {
      if(occurrencesInRam > 0) {
        try {
          requestSyncToDisk();
        } catch(InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }
  
  /**
   * The {@link IndexConfig} used for this index.
   */
  protected IndexConfig indexConfig;

  /**
   * The top level directory containing this index.
   */
  protected File indexDirectory;
  
 
  /**
   * The zipped document collection from MG4J (built during the indexing of the
   * first token feature). This can be used to obtain the document text and to
   * display the content of the hits.
   */
  protected DocumentCollection documentCollection;
  

  
  /**
   * The thread used to clean-up GATE documents after they have been indexed.
   */
  protected Thread maintenanceThread;
  
  /**
   * Background thread used to subtract occurrence counts for batches that have
   * recently been dumped to disk.
   */
  protected Thread maintenanceThread2;
  
  protected volatile boolean closed = false;
  
  /**
   * A list of futures representing sync-to-disk operations currently 
   * in-progress in all of the sub-indexes.
   */
  protected BlockingQueue<Future<Long>> syncRequests;
  
  /**
   * The set of IDs for the documents marked as deleted. 
   */
  private transient SortedSet<Long> deletedDocumentIds;
  
  /**
   * A timer used to execute various regular index maintenance tasks, such as 
   * the writing of deleted documents data to disk, and making sure regular 
   * dumps to disk are performed.
   */
  private transient Timer maintenanceTimer;
  
  /**
   * The timer task used to top write to disk the deleted documents data.
   * This value is non-null only when there is a pending write. 
   */
  private volatile transient WriteDeletedDocsTask writeDeletedDocsTask;
  
  /**
   * Timer task used to schedule regular dumps to disk making sure recent 
   * documents become searcheable after at most {@link #timeBetweenBatches} #
   * milliseconds.
   */
  private volatile transient SyncToDiskTask syncToDiskTask;
  
  /**
   * The token indexes, in the order they are listed in the {@link #indexConfig}.
   */
  protected AtomicTokenIndex[] tokenIndexes;
  
  /**
   * The annotation indexes, in the order they are listed in the 
   * {@link #indexConfig}.
   */
  protected AtomicAnnotationIndex[] mentionIndexes;

  /**
   * The {@link #tokenIndexes} and {@link #mentionIndexes} in one single array.
   */
  protected AtomicIndex[] subIndexes;
  
  protected int indexingQueueSize = DEFAULT_INDEXING_QUEUE_SIZE;
  
  /**
   * The total number of occurrences in all sub-indexes that have not yet been
   * written to disk.
   */
  protected volatile long occurrencesInRam;
  
  /**
   * The {@link QueryEngine} used to run searches on this index.
   */
  protected QueryEngine queryEngine;
  
  /**
   * Creates a new Mímir index.
   * 
   * @param indexConfig the configuration for the index.
   * @throws IOException 
   * @throws IndexException 
   */
  public MimirIndex(IndexConfig indexConfig) throws IOException, IndexException {
    this.indexConfig = indexConfig;
    this.indexDirectory = this.indexConfig.getIndexDirectory();
    
    openIndex();

    // save the config for the new index
    IndexConfig.writeConfigToFile(indexConfig, new File(indexDirectory,
            INDEX_CONFIG_FILENAME));
  }
  
  /**
   * Open and existing Mímir index.
   * @param indexDirectory the on-disk directory containing the index to be 
   * opened.
   * @throws IndexException if the index cannot be opened
   * @throws IllegalArgumentException if an index cannot be found at the 
   * specified location.
   * @throws IOException if the index cannot be opened. 
   */
  public MimirIndex(File indexDirectory ) throws IOException, IndexException {
    if(!indexDirectory.isDirectory()) throw new IllegalArgumentException(
        "No index found at " + indexDirectory);
    File indexConfigFile = new File(indexDirectory, INDEX_CONFIG_FILENAME);
    if(!indexConfigFile.canRead()) throw new IllegalArgumentException(
        "Cannot read index config from " + indexConfigFile); 
    
    this.indexConfig = IndexConfig.readConfigFromFile(indexConfigFile, 
        indexDirectory);
    this.indexDirectory = this.indexConfig.getIndexDirectory();
    if(indexConfig.getFormatVersion() < 7){
      throw new IndexException("The index at " + indexDirectory + 
          " uses too old a format and cannot be opened.");
    }
    openIndex();
  }
  
  /**
   * Opens the index files, if any, prepares all the sub-indexers specified in 
   * the index config, and gets this index ready to start indexing documents and
   * answer queries. 
   * @throws IOException 
   * @throws IndexException 
   */
  protected void openIndex() throws IOException, IndexException {
    // ####################
    // Prepare for indexing
    // ####################
    // read the index config and create the sub-indexers
    TokenIndexerConfig tokConfs[] = indexConfig.getTokenIndexers();
    tokenIndexes = new AtomicTokenIndex[tokConfs.length];
    for(int i = 0; i < tokConfs.length; i++) {
      String subIndexname = "token-" + i;
      tokenIndexes[i] = new AtomicTokenIndex(
          this, 
          subIndexname, 
          tokConfs[i].isDirectIndexEnabled(),
          new LinkedBlockingQueue<GATEDocument>(indexingQueueSize),
          new LinkedBlockingQueue<GATEDocument>(indexingQueueSize),
          tokConfs[i],
          i == 0);
    }
    
    SemanticIndexerConfig sics[] = indexConfig.getSemanticIndexers();
    mentionIndexes = new AtomicAnnotationIndex[sics.length];
    for(int  i = 0; i < sics.length; i++) {
      String subIndexname = "mention-" + i;
      mentionIndexes[i] = new AtomicAnnotationIndex(
          this, 
          subIndexname, 
          sics[i].isDirectIndexEnabled(),
          new LinkedBlockingQueue<GATEDocument>(),
          new LinkedBlockingQueue<GATEDocument>(),
          sics[i]);
    }
    
    // construct the joint array of sub-indexes
    subIndexes = new AtomicIndex[tokenIndexes.length + mentionIndexes.length];
    System.arraycopy(tokenIndexes, 0, subIndexes, 0, tokenIndexes.length);
    System.arraycopy(mentionIndexes, 0, subIndexes, tokenIndexes.length, 
        mentionIndexes.length);
    
    occurrencesInRam = 0;
    syncRequests = new LinkedBlockingQueue<Future<Long>>();
    
    // #####################
    // Prepare for searching
    // #####################
    readDeletedDocs();
    
    // #####################
    // Index maintenance 
    // #####################
    // start the documents collector thread
    maintenanceThread = new Thread(new IndexMaintenanceRunner(),
        indexDirectory.getAbsolutePath() + " index maintenance");
    maintenanceThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        logger.error("Uncaught exception in background tread", e);
      }
    });
    maintenanceThread.start();
    
    // start the occurrences subtractor thread
    maintenanceThread2 = new Thread(
        new IndexMaintenanceRunner2(),
        indexDirectory.getAbsolutePath() + " index maintenance 2");
    maintenanceThread2.setPriority(Thread.MIN_PRIORITY);
    maintenanceThread2.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        logger.error("Uncaught exception in background tread", e);
      }
    });
    maintenanceThread2.start();
    
    // start the timer for regular sync-ing, and maintenance of the deleted docs
    maintenanceTimer = new Timer("Mímir index maintenance timer");
    synchronized(maintenanceTimer) {
      syncToDiskTask = new SyncToDiskTask();
      if(indexConfig.getTimeBetweenBatches() <= 0) {
        indexConfig.setTimeBetweenBatches(IndexConfig.DEFAULT_TIME_BETWEEN_BATCHES);
      }
      maintenanceTimer.schedule(syncToDiskTask, 
          indexConfig.getTimeBetweenBatches(), 
          indexConfig.getTimeBetweenBatches());
    }
    // open the zipped document collection
    documentCollection = new DocumentCollection(indexDirectory);
  }
  
  /**
   * Queues a new document for indexing. The document will first go into the
   * indexing queue, from where the various sub-indexes take their input. Once
   * processed, the document data is stored in RAM until a sync-to-disk 
   * operation occurs. Only after that does the document become searchable. 
   * 
   * @param document the document to be indexed.
   * @throws InterruptedException if the process of posting the new document
   * to all the input queues is interrupted.
   * @throws IllegalStateException if the index has already been closed.
   */
  public void indexDocument(Document document) throws InterruptedException {
    if(closed) throw new IllegalStateException("This index has been closed, "
        + "no further documents can be indexed.");
    
    // check if we need to write a new batch:
    // we have too many occurrences and 
    // there are no outstanding batch writing operations
    if( occurrencesInRam > occurrencesPerBatch && syncRequests.isEmpty()) {
      requestSyncToDisk();
    }

    GATEDocument gDocument = new GATEDocument(document, indexConfig);
    synchronized(subIndexes) {
      for(AtomicIndex aSubIndex: subIndexes){
        aSubIndex.getInputQueue().put(gDocument);
      }      
    }
  }

  /**
   * Asks this index to write to disk all the index data currently stored in 
   * RAM so that it can become searchable. The work happens in several 
   * background threads (one for each sub-index) at the earliest opportunity.
   * @return a list of futures that can be used to find out when the operation 
   * has completed. 
   * @throws InterruptedException if the current thread has been interrupted
   * while trying to queue the sync request.
   */
  public List<Future<Long>> requestSyncToDisk() throws InterruptedException {
    List<Future<Long>> futures = new ArrayList<Future<Long>>();
    if(syncRequests.isEmpty()) {
      synchronized(subIndexes) {
        for(AtomicIndex aSubIndex : subIndexes) {
          Future<Long> task = aSubIndex.requestSyncToDisk(); 
          futures.add(task);
          syncRequests.put(task);
        }
      }      
    } else {
      // sync already in progress: instead of causing a new sync, notify caller
      // when current operation completes
      futures.addAll(syncRequests);
    }
    return futures;
  }
  
  /**
   * Asks each of the sub-indexes in this index to compact all their batches
   * into a single index. This reduces the number of open file handles required.
   * The work happens in several background threads (one for each sub-index) at
   * the earliest opportunity.
   * 
   * @return a list of futures (one for each sub-index) that can be used to find
   *         out when the operation has completed.
   * @throws InterruptedException
   *           if the current thread has been interrupted while trying to queue
   *           the compaction request.
   */
  public List<Future<Void>> requestCompactIndex() throws InterruptedException {
    List<Future<Void>> futures = new ArrayList<Future<Void>>();
    synchronized(subIndexes) {
      for(AtomicIndex aSubIndex : subIndexes) {
        futures.add(aSubIndex.requestCompactIndex());
      }      
    }
    return futures;
  }
  
  /**
   * Requests that the {@link DocumentCollection} contained by this index is 
   * compacted. This method blocks until the compaction has completed.
   * 
   * In normal operation, the index maintains the collection, which includes 
   * regular compactions, so there should be no reason to call this method.
   * 
   * @throws ZipException
   * @throws IOException
   * @throws IndexException
   */
  public void compactDocumentCollection() throws ZipException, IOException, IndexException {
    documentCollection.compact();
  }
  
  /**
   * Called by the first token indexer when a new document has been indexed
   * to ask the main index to save the necessary zip collection data
   * @param gDocument
   * @throws IndexException 
   */
  public void writeZipDocumentData(DocumentData docData) throws IndexException {
    documentCollection.writeDocument(docData);
  }
  
  /**
   * Stops this index from accepting any further document for indexing, stops
   * this index from accepting any more queries, finishes indexing all the 
   * currently queued documents, writes all the files to disk, after which it
   * returns control to the calling thread.
   * This may be a lengthy operation, depending on the amount of data that still
   * needs to be written to disk.
   * 
   * @throws InterruptedException 
   * @throws IOException 
   */
  public void close() throws InterruptedException, IOException {
    if(closed) return;
    closed = true;

    // close the query engine
    if(queryEngine != null) queryEngine.close();
    // stop the indexing
    synchronized(subIndexes) {
      for(AtomicIndex aSubIndex : subIndexes) {
        aSubIndex.getInputQueue().put(GATEDocument.END_OF_QUEUE);
      }      
    }
    
    synchronized(maintenanceTimer) {
      // write the deleted documents set
      if(writeDeletedDocsTask != null) {
        writeDeletedDocsTask.cancel();
      }
      // explicitly call it one last time
      new WriteDeletedDocsTask().run();
      maintenanceTimer.cancel();
    }

    // wait for indexing to end
    maintenanceThread.join();
    
    syncRequests.put(NO_MORE_TASKS);
    maintenanceThread2.join();
    
    // close the document collection
    documentCollection.close();
    // write the config file
    try {
      IndexConfig.writeConfigToFile(indexConfig, new File(indexDirectory,
              INDEX_CONFIG_FILENAME));
    } catch(IOException e) {
      throw new GateRuntimeException("Could not save the index configuration!",
              e);
    }
    logger.info("Index shutdown complete");
  }

  
  /**
   * Gets the {@link IndexConfig} value for this index.
   * @return
   */
  public IndexConfig getIndexConfig() {
    return indexConfig;
  }
  
  /**
   * Returns the {@link QueryEngine} instance that can be used to post queries
   * to this index. Each index holds one single query engine, so the same value
   * will always be returned by repeated calls.
   * @return
   */
  public QueryEngine getQueryEngine() {
    if(queryEngine == null) {
      queryEngine = new QueryEngine(this);
    }
    return queryEngine;
  }

  
  /**
   * Gets the top level directory for this index.
   * @return
   */
  public File getIndexDirectory() {
    return indexDirectory;
  }

  /**
   * Gets the current estimated number of occurrences in RAM. An occurrence
   * represents one term (either a token or an annotation) occurring in an
   * indexed document. This value can be used as a good measurement of the total
   * amount of data that is currently being stored in RAM and waiting to be
   * synced to disk.
   * @return
   */
  public long getOccurrencesInRam() {
    return occurrencesInRam;
  }

  /**
   * Returns the size of the indexing queue. See 
   * {@link #setIndexingQueueSize(int)} for more comments.
   * @return
   */
  public int getIndexingQueueSize() {
    return indexingQueueSize;
  }

  /**
   * Sets the size of the indexing queue(s) used by this index.
   * Documents submitted for indexing are held in a queue until the indexers 
   * become ready to process them. One queue is used for each of the 
   * sub-indexes. A larger queue size can smooth out bursts of activity, but 
   * requires more memory (as a larger number of documents may need to be stored
   * at the same time). A smaller value is more economical, but it can leads to 
   * slow-downs when certain documents take too long to index, and can clog up
   * the queue. Defaults to {@value #DEFAULT_INDEXING_QUEUE_SIZE}.
   * @param indexingQueueSize
   */
  public void setIndexingQueueSize(int indexingQueueSize) {
    this.indexingQueueSize = indexingQueueSize;
  }

  /**
   * Gets the number of occurrences that should be used as a trigger for a sync
   * to disk operation, leading to the creation of a new index batch.
   * @return
   */
  public long getOccurrencesPerBatch() {
    return occurrencesPerBatch;
  }
  
  /**
   * Sets the number of occurrences that should trigger a sync-to-disk operation
   * leading to a new batch being created from the data previously stored in
   * RAM.
   * 
   * An occurrence represents one term (either a token or an annotation)
   * occurring in an indexed document. This value can be used as a good
   * measurement of the total amount of data that is currently being stored in
   * RAM and waiting to be synced to disk.
   * 
   * @param occurrencesPerBatch
   */
  public void setOccurrencesPerBatch(long occurrencesPerBatch) {
    this.occurrencesPerBatch = occurrencesPerBatch;
  }
  
  
  /**
   * Gets the time interval (in milliseconds) between sync-to-disk operations.
   * This is approximately the maximum amount of time that a document can spend
   * being stored in RAM (and thus not searchable) after having been submitted
   * for indexing. The measurement is not precise because of the time spent by
   * the document in the indexing queue (after being received but before being
   * processed) and the time take to write a new index batch to disk.
   * 
   * @return
   */
  public int getTimeBetweenBatches() {
    return getIndexConfig().getTimeBetweenBatches();
  }

  /**
   * Sets the time interval (in milliseconds) between sync-to-disk operations.
   * This is approximately the maximum amount of time that a document can spend
   * being stored in RAM (and thus not searchable) after having been submitted
   * for indexing. The measurement is not precise because of the time spent by
   * the document in the indexing queue (after being received but before being
   * processed) and the time take to write a new index batch to disk.
   * 
   * @return
   */  
  public void setTimeBetweenBatches(int timeBetweenBatches) {
    if(indexConfig.getTimeBetweenBatches() != timeBetweenBatches) {
      indexConfig.setTimeBetweenBatches(timeBetweenBatches);
      synchronized(maintenanceTimer) {
        if(syncToDiskTask != null) {
          syncToDiskTask.cancel();
        }
        syncToDiskTask = new SyncToDiskTask();
        maintenanceTimer.schedule(syncToDiskTask, timeBetweenBatches, 
            timeBetweenBatches);
      }
    }
  }

  /**
   * Gets the {@link DocumentCollection} instance used by this index. The 
   * document collection is normally fully managed by the index, so there should
   * be no need to access it directly through this method.
   * 
   * @return
   */
  public DocumentCollection getDocumentCollection() {
    return documentCollection;
  }
  
  /**
   * Gets the total number of documents currently searcheable 
   * @return
   */
  public long getIndexedDocumentsCount() {
    if(subIndexes != null && subIndexes.length > 0 && 
        subIndexes[0].getIndex() != null){
      return subIndexes[0].getIndex().numberOfDocuments;
    } else {
      return 0;
    }
  }
  
  /**
   * Gets the {@link DocumentData} for a given document ID, from the on disk 
   * document collection. In memory caching is performed to reduce the cost of 
   * this call. 
   * @param documentID
   *          the ID of the document to be obtained.
   * @return the {@link DocumentData} associated with the given document ID.
   * @throws IOException 
   */
  public synchronized DocumentData getDocumentData(long documentID)
  throws IndexException, IOException {
    if(isDeleted(documentID)) {
      throw new IndexException("Invalid document ID " + documentID);
    }
    return  documentCollection.getDocumentData(documentID);
  }
  
  /**
   * Gets the size (number of tokens) for a document.
   * @param documentId the document being requested.
   * 
   * @return
   */
  public int getDocumentSize(long documentId) {
    return tokenIndexes[0].getIndex().sizes.get(documentId);
  }
  
  /**
   * Marks a given document (identified by its ID) as deleted. Deleted documents
   * are never returned as search results.
   * @param documentId
   */
  public void deleteDocument(long documentId) {
    if(deletedDocumentIds.add(documentId)) {
      writeDeletedDocsLater();
    }
  }

  /**
   * Marks the given batch of documents (identified by ID) as deleted. Deleted
   * documents are never returned as search results.
   * @param documentIds
   */
  public void deleteDocuments(Collection<? extends Number> documentIds) {
    List<Long> idsToDelete = new ArrayList<Long>(documentIds.size());
    for(Number n : documentIds) {
      idsToDelete.add(Long.valueOf(n.longValue()));
    }
    if(deletedDocumentIds.addAll(idsToDelete)) {
      writeDeletedDocsLater();
    }
  }
  
  /**
   * Checks whether a given document (specified by its ID) is marked as deleted. 
   * @param documentId
   * @return
   */
  public boolean isDeleted(long documentId) {
    return deletedDocumentIds.contains(documentId);
  }
  
  /**
   * Mark the given document (identified by ID) as <i>not</i> deleted.  Calling
   * this method for a document ID that is not currently marked as deleted has
   * no effect.
   */
  public void undeleteDocument(long documentId) {
    if(deletedDocumentIds.remove(documentId)) {
      writeDeletedDocsLater();
    }
  }
  
  /**
   * Mark the given documents (identified by ID) as <i>not</i> deleted.  Calling
   * this method for a document ID that is not currently marked as deleted has
   * no effect.
   */
  public void undeleteDocuments(Collection<? extends Number> documentIds) {
    List<Long> idsToUndelete = new ArrayList<Long>(documentIds.size());
    for(Number n : documentIds) {
      idsToUndelete.add(Long.valueOf(n.longValue()));
    }
    if(deletedDocumentIds.removeAll(idsToUndelete)) {
      writeDeletedDocsLater();
    }
  }
  
  /**
   * Writes the set of deleted document to disk in a background thread, after a
   * short delay. If a previous request has not started yet, this new request 
   * will replace it. 
   */
  protected void writeDeletedDocsLater() {
    synchronized(maintenanceTimer) {
      if(writeDeletedDocsTask != null) {
        writeDeletedDocsTask.cancel();
      }
      writeDeletedDocsTask = new WriteDeletedDocsTask();
      maintenanceTimer.schedule(writeDeletedDocsTask, 1000);
    }
  }
  
  /**
   * Reads the list of deleted documents from disk. 
   */
  @SuppressWarnings("unchecked")
  protected synchronized void readDeletedDocs() throws IOException{
    deletedDocumentIds = Collections.synchronizedSortedSet(
            new TreeSet<Long>());
    File delFile = new File(indexDirectory, DELETED_DOCUMENT_IDS_FILE_NAME);
    if(delFile.exists()) {
      try {
        ObjectInputStream ois = new ObjectInputStream(
                new GZIPInputStream(
                new BufferedInputStream(
                new FileInputStream(delFile))));
        // an old index will have saved a Set<Integer>, a new one will be
        // Set<Long>
        Set<? extends Number> savedSet = (Set<? extends Number>)ois.readObject();
        for(Number n : savedSet) {
          deletedDocumentIds.add(Long.valueOf(n.longValue()));
        }
      } catch(ClassNotFoundException e) {
        // this should never happen
        throw new RuntimeException(e);
      }
    }
  }
  
  
  /**
   * Returns the {@link AtomicTokenIndex} responsible for indexing a particular
   * feature on token annotations.
   * 
   * @param featureName
   * @return
   */
  public AtomicTokenIndex getTokenIndex(String featureName) {
    if(featureName == null) {
      // return the default token index
      return tokenIndexes[0];
    } else {
      for(int i = 0; i < indexConfig.getTokenIndexers().length; i++) {
        if(indexConfig.getTokenIndexers()[i].getFeatureName().equals(featureName)) {
          return tokenIndexes[i]; 
        }
      }      
    }
    return null;
  }
  
  /**
   * Returns the {@link AtomicAnnotationIndex} instance responsible for indexing
   * annotations of the type specified.
   * 
   * @param annotationType
   * @return
   */
  public AtomicAnnotationIndex getAnnotationIndex(String annotationType) {
    for(int i = 0; i < indexConfig.getSemanticIndexers().length; i++) {
      for(String aType : 
          indexConfig.getSemanticIndexers()[i].getAnnotationTypes()) {
        if(aType.equals(annotationType)) {
          return mentionIndexes[i];
        }
      }
    }
    return null;
  }
  
}
