/*
 *  AtomicIndex.java
 *
 *  Copyright (c) 2007-2013, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 1 Nov 2013
 *
 *  $Id: AtomicIndex.java 20154 2017-02-20 19:19:38Z ian_roberts $
 */
package gate.mimir.index;

import gate.Annotation;
import gate.mimir.MimirIndex;
import gate.mimir.search.IndexReaderPool.IndexDictionary;
import gate.util.GateRuntimeException;
import it.unimi.di.big.mg4j.index.BitStreamIndex;
import it.unimi.di.big.mg4j.index.CompressionFlags;
import it.unimi.di.big.mg4j.index.CompressionFlags.Coding;
import it.unimi.di.big.mg4j.index.CompressionFlags.Component;
import it.unimi.di.big.mg4j.index.DiskBasedIndex;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.index.Index.UriKeys;
import it.unimi.di.big.mg4j.index.IndexIterator;
import it.unimi.di.big.mg4j.index.IndexReader;
import it.unimi.di.big.mg4j.index.IndexWriter;
import it.unimi.di.big.mg4j.index.NullTermProcessor;
import it.unimi.di.big.mg4j.index.QuasiSuccinctIndex;
import it.unimi.di.big.mg4j.index.QuasiSuccinctIndexWriter;
import it.unimi.di.big.mg4j.index.SkipBitStreamIndexWriter;
import it.unimi.di.big.mg4j.index.TermProcessor;
import it.unimi.di.big.mg4j.index.cluster.ContiguousDocumentalStrategy;
import it.unimi.di.big.mg4j.index.cluster.ContiguousLexicalStrategy;
import it.unimi.di.big.mg4j.index.cluster.DocumentalCluster;
import it.unimi.di.big.mg4j.index.cluster.DocumentalConcatenatedCluster;
import it.unimi.di.big.mg4j.index.cluster.LexicalCluster;
import it.unimi.di.big.mg4j.io.IOFactory;
import it.unimi.di.big.mg4j.tool.Combine;
import it.unimi.di.big.mg4j.tool.Combine.IndexType;
import it.unimi.di.big.mg4j.tool.Concatenate;
import it.unimi.di.big.mg4j.tool.Scan;
import it.unimi.dsi.big.io.FileLinesCollection;
import it.unimi.dsi.big.util.ShiftAddXorSignedStringMap;
import it.unimi.dsi.big.util.StringMap;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.Arrays;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.Swapper;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntBigList;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.fastutil.objects.Object2LongAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import it.unimi.dsi.fastutil.objects.ObjectBigList;
import it.unimi.dsi.io.OutputBitStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.lang.ObjectParser;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.mph.LcpMonotoneMinimalPerfectHashFunction;
import it.unimi.dsi.util.BloomFilter;
import it.unimi.dsi.util.Properties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;

import com.google.common.io.PatternFilenameFilter;

/**
 * <p>
 * An inverted index associating terms with documents. Terms can be either token
 * feature values, or annotations. Optionally, a direct index may also be 
 * present.
 * </p>
 * <p>
 * An atomic index manages a head index (the principal data) and a set of tail 
 * indexes (batches containing updates). Additionally, the data representing 
 * all the new documents that have been queued for indexing since the last tail
 * was written are stored in RAM.
 * </p>
 * <p>
 * When direct indexing is enabled, the term IDs in the direct index are 
 * different from the term IDs in the inverted index. In the inverted index 
 * the term IDs are their position in the lexicographically sorted list of all
 * terms. In the directed index, the term IDs are their position in the list
 * sorted by the time they were first seen during indexing.
 * </p>
 * <p>
 * The head and tail batches can be combined into a new head by a 
 * <em>compact</em> operation.
 */
public abstract class AtomicIndex implements Runnable {
  
  /**
   * A callable that does nothing. This is used to produce instances of 
   * {@link CustomFuture} which are only used to wait for the completion of an 
   * operation that returns nothing.
   */
  private static final Callable<Void> noOpVoid = new Callable<Void>() {
    @Override
    public Void call() throws Exception {
      return null;
    }
  };
  
  /**
   * An in-RAM representation of a postings list
   */
  protected static class PostingsList {
    
    /**
     * The first document pointer added to this postings list.
     */
    private long firstDocumentPointer = -1;
    
    /**
     * The last seen document pointer.
     */
    private long lastDocumentPointer = -1;
    
    /**
     * The list of document pointer differentials (differences from the previous
     * document pointer. The first document pointer value is stored in
     * {@link #firstDocumentPointer}), and we store a <tt>0</tt> in the first 
     * position of this list. The actual value of a document pointer for a given
     * position is:
     * <dl>
     *   <dt>pos 0</dt>
     *   <dd>{@link #firstDocumentPointer}</dd>
     *   <dt>pos i</dt>
     *   <dd>pointer at position (i-1) + documentPointersDifferential[i]</dd>
     * </dl>
     */
    private IntList documentPointersDifferential;
    

    /**
     * The count (number of terms) for each document. This list is aligned with 
     * {@link #documentPointersDifferential}.
     */
    private IntList counts;
    
    /**
     * The list of positions in this postings list. For each document at 
     * position <tt>i</i>, there will be counts[i] positions stored in this 
     * list. This value is <code>non-null</code> only if positions are stored,
     * which is configured through a construction-time parameter.
     */
    private IntArrayList positions;
    
    /**
     * The last seen position in the current document.
     */
    private int lastPosition = -1;
    
    /**
     * The number of positions in the current document
     */
    private int count = 0; 
    
    /**
     * The maximum term count of all the stored documents
     */
    private int maxCount = 0;
    
    /**
     * The number of document pointers contained
     */
    private long frequency = 0;
    
    /**
     * The total number of term occurrences in all stored documents.
     */
    private long occurrences = 0;
    
    /**
     * The sum of the maximum positions for each document.
     */
    private long sumMaxPos = 0;
    
    public PostingsList(boolean storePositions) {
      firstDocumentPointer = -1;
      documentPointersDifferential = new IntArrayList();
      counts = new IntArrayList();
      if(storePositions) {
        positions = new IntArrayList();
      }
    }

    /**
     * Start storing the data for a new document
     * @param pointer
     */
    public void newDocumentPointer(long pointer) {
      // is this really a new document?
      if(pointer != lastDocumentPointer) {
        if(firstDocumentPointer < 0) firstDocumentPointer = pointer;
        if(lastDocumentPointer == -1) {
          // this is the first document
          documentPointersDifferential.add(0);  
        } else {
          // close previous document
          flush();
          // add the new document
          documentPointersDifferential.add((int)(pointer - lastDocumentPointer));
        }
        lastDocumentPointer = pointer;
        // reset the lastPosition when moving to a new document
        lastPosition = -1;
        
        frequency++;
      }
    }

    public void addPosition(int pos) {
      // ignore if the position hasn't changed: we don't store two identical 
      // records
      if(pos != lastPosition) {
        positions.add(pos);
        count++;
        //and update lastPosition
        lastPosition = pos;        
      }
    }
    
    /**
     * When storing positions, the count is automatically calculated. When not 
     * storing positions, it needs to be explicitly set by calling this method.
     * @param count
     */
    public void setCount(int count) {
      this.count = count;
    }
    
    /**
     * Checks whether the given position is valid (i.e. greater than the last 
     * seen position. If the position is invalid, this means that a call to
     * {@link #addPosition(int)} with the same value would actually be a 
     * no-operation.  
     * @param pos
     * @return
     */
    public boolean checkPosition(int pos){
      return pos > lastPosition;
    }
    
    /**
     * Notifies this postings list that it has received all the data
     */
    public void flush() {
      if(count > 0) {
        // we have some new positions for the last document: they were already
        // added to positions, but we now need to store their count
        counts.add(count);
        if(count > maxCount) maxCount = count;
        sumMaxPos += lastPosition;
        occurrences += count;
      }
      count = 0;
    }
    
    /**
     * Empties all the data from this postings list making it ready to be reused.
     */
    public void clear() {
      documentPointersDifferential.clear();
      count = 0;
      counts.clear();
      maxCount = 0;
      occurrences = 0;
      if(positions != null){
        positions.clear();
        lastPosition = -1;
        sumMaxPos = 0;
      }
      firstDocumentPointer = -1;
      lastDocumentPointer = -1;
      frequency = 0;
    }
    
    /**
     * Writes the data contained in this postings list to an index writer
     * @param indexWriter
     * @throws IOException 
     */
    public void write(IndexWriter indexWriter) throws IOException {
      flush();
      if(indexWriter instanceof QuasiSuccinctIndexWriter) {
        ((QuasiSuccinctIndexWriter)indexWriter).newInvertedList(
            frequency,
            occurrences, 
            positions!= null ? sumMaxPos : 0);
      } else {
        indexWriter.newInvertedList();
      }
      
      indexWriter.writeFrequency(frequency);
      long currDocumentPointer = firstDocumentPointer;
      int positionsStart = 0;
      for(int docId = 0; docId < documentPointersDifferential.size(); docId++) {
        currDocumentPointer += documentPointersDifferential.get(docId);
        int currCount = counts.get(docId);
        OutputBitStream obs = indexWriter.newDocumentRecord();
        indexWriter.writeDocumentPointer(obs, currDocumentPointer);
        indexWriter.writePositionCount(obs, currCount);
        if(positions != null){
          indexWriter.writeDocumentPositions(obs, positions.elements(),
              positionsStart, currCount, -1);
          positionsStart += currCount;       
        }
      }
    }

    @Override
    public String toString() {
      StringBuilder str = new StringBuilder();
      long docPointer = firstDocumentPointer;
      int positionsPointer = 0;
      boolean firstDoc = true;
      for(int i = 0; i < documentPointersDifferential.size(); i++) {
        docPointer += documentPointersDifferential.get(i);
        int count = counts.getInt(i);
        if(firstDoc) {
          firstDoc = false;
        } else {
          str.append("; ");
        }
        str.append(docPointer).append("(");
        boolean firstPos = true;
        for(int j = positionsPointer; j < positionsPointer + count; j++) {
          if(firstPos) {
            firstPos = false;
          } else {
            str.append(", ");
          }
          str.append(positions.getInt(j));
        }
        str.append(") ");
      }
      
      return str.toString();
    }
    
    
  }
  
  /**
   * Class representing an MG4J index batch, such as the head or any of the 
   * tails.
   */
  protected static class MG4JIndex {
    protected File indexDir;
    protected Index invertedIndex;
    protected Index directIndex;
    protected BloomFilter<Void> invertedTermFilter;
    protected BloomFilter<Void> directTermFilter;
    protected String indexName;
    
    public MG4JIndex(
        File indexDir,
        String indexName,
        Index invertedIndex,  
        BloomFilter<Void> invertedTermFilter,
        Index directIndex,
        BloomFilter<Void> directTermFilter) {
      
      this.indexDir = indexDir;
      this.indexName = indexName;
      this.invertedIndex = invertedIndex;
      this.invertedTermFilter = invertedTermFilter;
      
      this.directIndex = directIndex;
      this.directTermFilter = directTermFilter;
    }
  }
  
  /**
   * Given a terms file (text file with one term per line) this method generates
   * the corresponding termmap file (binary representation of a StringMap).
   * Optionally, a {@link BloomFilter} can also be generated, if the suitable
   * target file is provided.
   * 
   * @param termsFile the input file
   * @param termmapFile the output termmap file, or <code>null</code> if a 
   * termmap is not required.
   * @param bloomFilterFile the file to be used for writing the 
   * {@link BloomFilter} for the index, or <code>null</code> if a Bloom filter
   * is not required.
   * @throws IOException
   */
  public static void generateTermMap(File termsFile, File termmapFile,
      File bloomFilterFile) throws IOException {
    FileLinesCollection fileLinesCollection =
        new FileLinesCollection(termsFile.getAbsolutePath(), "UTF-8");
    if(termmapFile != null) {
      StringMap<CharSequence> terms =
          new ShiftAddXorSignedStringMap(
              fileLinesCollection.iterator(),
              new LcpMonotoneMinimalPerfectHashFunction.Builder<CharSequence>()
                .keys(fileLinesCollection)
                .transform(TransformationStrategies.prefixFreeUtf16())
              .build());
              //new LcpMonotoneMinimalPerfectHashFunction<CharSequence>(
              //    fileLinesCollection, TransformationStrategies.prefixFreeUtf16()));      
      BinIO.storeObject(terms, termmapFile);      
    }

    if(bloomFilterFile != null) {
      BloomFilter<Void> bloomFilter = BloomFilter.create(fileLinesCollection.size64());
      for(MutableString term : fileLinesCollection) {
        bloomFilter.add(term);
      }
      BinIO.storeObject(bloomFilter, bloomFilterFile);
    }
  }  

  /**
   * Creates a documental cluster from a list of {@link MG4JIndex} values.
   * 
   * @param batches the indexes to be combined into a cluster 
   * @param termProcessor the term processor to be used (can be null)
   * @return a documental cluster view of the list of indexes provided.
   */
  protected final static Index openInvertedIndexCluster(
      List<MG4JIndex> batches, TermProcessor termProcessor){
    
    if(batches == null || batches.size() == 0) return null;
    if(batches.size() == 1) return batches.get(0).invertedIndex;
    
    // prepare the documental cluster
    Index[] indexes = new Index[batches.size()];
    // cut points between the batches - there are numBatches+1 cutpoints,
    // cutPoints[0] is always zero, and cutPoints[i] is the sum of the
    // sizes of batches 0 to i-1 inclusive
    long[] cutPoints = new long[indexes.length + 1];
    cutPoints[0] = 0;
    int numberOfTerms = -1;
    int numberOfDocuments = -1;
    long numberOfPostings = -1;
    long numberOfOccurences =-1;
    int maxCount =-1;
    int indexIdx = 0;
    IntBigList sizes = new IntBigArrayBigList();
    @SuppressWarnings("unchecked")
    BloomFilter<Void> bloomFilters[] = new BloomFilter[indexes.length];
    
    for(MG4JIndex aSubIndex : batches) {
      indexes[indexIdx] = aSubIndex.invertedIndex;
      cutPoints[indexIdx + 1] = cutPoints[indexIdx] + 
          aSubIndex.invertedIndex.numberOfDocuments;
      numberOfTerms += aSubIndex.invertedIndex.numberOfTerms;
      numberOfDocuments += aSubIndex.invertedIndex.numberOfDocuments;
      numberOfPostings += aSubIndex.invertedIndex.numberOfPostings;
      numberOfOccurences += aSubIndex.invertedIndex.numberOfOccurrences;
      if(maxCount < aSubIndex.invertedIndex.maxCount){
        maxCount = aSubIndex.invertedIndex.maxCount;
      }
      bloomFilters[indexIdx] = aSubIndex.invertedTermFilter;
      sizes.addAll(aSubIndex.invertedIndex.sizes);
      indexIdx++;
    }
    
    return new DocumentalConcatenatedCluster(indexes,
          new ContiguousDocumentalStrategy(cutPoints),
          false, // flat = all component indexes have the same term list
          bloomFilters, // Bloom Filters
          numberOfDocuments == -1 ? -1 : numberOfDocuments + 1, 
          numberOfTerms == -1 ? -1 : numberOfTerms + 1, 
          numberOfPostings == -1 ? -1 : numberOfPostings + 1, 
          numberOfOccurences == -1 ? -1 : numberOfOccurences + 1, 
          maxCount, 
          null, // payload
          true, // hasCounts 
          true, // hasPositions, 
          termProcessor, 
          null, // field 
          sizes, // sizes
          null // properties
          );
  }  
  
  /**
   * Opens the direct index files from all the batches and combines them into
   * a {@link LexicalCluster}.
   * @param batches the batches to be opened.
   * @return
   */
  protected final static Index openDirectIndexCluster(List<MG4JIndex> batches){
    
    if(batches == null || batches.size() == 0) return null;
    if(batches.size() == 1) return batches.get(0).directIndex;
    
    // prepare the lexical cluster
    Index[] indexes = new Index[batches.size()];
    int[] cutPoints = new int[indexes.length];
    cutPoints[0] = 0;
    String[] cutPointTerms = new String[indexes.length];
    cutPointTerms[0] = longToTerm(0);
    int numberOfTerms = -1;
    int numberOfDocuments = -1;
    long numberOfPostings = -1;
    long numberOfOccurences =-1;
    int maxCount =-1;
    int indexIdx = 0;
    @SuppressWarnings("unchecked")
    BloomFilter<Void> bloomFilters[] = new BloomFilter[indexes.length];
    
    for(MG4JIndex aSubIndex : batches) {
      indexes[indexIdx] = aSubIndex.directIndex;
      // we build this based on the inverted index, as the cut-points for the
      // lexical partitioning are based on document IDs
      if(indexIdx < cutPoints.length - 1) {
        cutPoints[indexIdx + 1] = cutPoints[indexIdx] + 
            (int)aSubIndex.invertedIndex.numberOfDocuments;
        cutPointTerms[indexIdx + 1] = longToTerm(cutPoints[indexIdx + 1]);
      }
      numberOfTerms += aSubIndex.directIndex.numberOfTerms;
      numberOfDocuments += aSubIndex.directIndex.numberOfDocuments;
      numberOfPostings += aSubIndex.directIndex.numberOfPostings;
      numberOfOccurences += aSubIndex.directIndex.numberOfOccurrences;
      if(maxCount < aSubIndex.directIndex.maxCount){
        maxCount = aSubIndex.directIndex.maxCount;
      }
      bloomFilters[indexIdx] = aSubIndex.directTermFilter;
      indexIdx++;
    }
    cutPointTerms[cutPointTerms.length - 1] = null;
    
    return new LexicalCluster(indexes,
          new ContiguousLexicalStrategy(cutPoints, cutPointTerms),
          bloomFilters, // Bloom Filters
          numberOfDocuments == -1 ? -1 : numberOfDocuments + 1, 
          numberOfTerms == -1 ? -1 : numberOfTerms + 1, 
          numberOfPostings == -1 ? -1 : numberOfPostings + 1, 
          numberOfOccurences == -1 ? -1 : numberOfOccurences + 1, 
          maxCount, 
          null, // payload
          true, // hasCounts 
          false, // hasPositions, 
          NullTermProcessor.getInstance(), 
          null, // field 
          null, // sizes
          null // properties
          );
  }  
  
  /**
   * Converts a long value into a String containing a zero-padded Hex 
   * representation of the input value. The lexicographic ordering of the 
   * generated strings is the same as the natural order of the corresponding
   * long values.
   *  
   * @param value the value to convert.
   * @return the string representation.
   */
  public static final String longToTerm(long value) {
    String valueStr = Long.toHexString(value);
    return "0000000000000000".substring(valueStr.length()) + valueStr;
  }  
  
  /**
   * The file name (under the current directory for this atomic index) which 
   * stores the principal index. 
   */
  public static final String HEAD_FILE_NAME = "head";
  
  /**
   * The file extension used for the temporary directory where the updated head
   * is being built.
   */
  public static final String HEAD_NEW_EXT = ".new";
  
  /**
   * The file extension used for the temporary directory where the old head 
   * index is being stored while the newly updated one is being installed.
   */
  public static final String HEAD_OLD_EXT = ".old";
  
  /**
   * The prefix used for file names (under the current directory for this 
   * atomic index) for updates to the head index.
   */
  public static final String TAIL_FILE_NAME_PREFIX = "tail-";
  
  
  public static final String DIRECT_TERMS_FILENAME = "direct.terms";
  
  /**
   * FIles belonging to teh direct index get this suffix added to their 
   * basename.
   */
  public static final String DIRECT_INDEX_NAME_SUFFIX = "-dir";
  
  /**
   * The file name (under the current directory for this atomic index) for the
   * directory containing the documents that have been queued for indexing, but 
   * not yet indexed. 
   */
  public static final String DOCUMENTS_QUEUE_FILE_NAME = "queued-documents";
  
  /** The initial size of the term map. */
  private static final int INITIAL_TERM_MAP_SIZE = 1024;
  
  /**
   * A marker value that gets queued to indicate a request to
   * write the in-RAM data to a new index batch.
   */
  private static final GATEDocument DUMP_BATCH = new GATEDocument(){};

  /**
   * A marker value that gets queued to indicate a request to combine all the
   * on-disk batches into a new head.
   */
  private static final GATEDocument COMPACT_INDEX = new GATEDocument(){};
  
  private static Logger logger = Logger.getLogger(AtomicIndex.class);
  
  protected static final PatternFilenameFilter TAILS_FILENAME_FILTER = 
      new PatternFilenameFilter("\\Q" + TAIL_FILE_NAME_PREFIX + "\\E\\d+");
  
  /**
   * The name of this atomic index.
   */
  protected String name;
  
  /**
   * The directory where this atomic index stores its files.
   */
  protected File indexDirectory;
  
  /**
   * The term processor used to process the feature values being indexed.
   */
  protected TermProcessor termProcessor = null;
  
  /**
   * The size (number of terms) for the longest document indexed but not yet 
   * saved. 
   */
  protected int maxDocSizeInRAM = -1;
  
  /**
   * The number of occurrences represented in RAM and not yet written to disk.  
   */
  protected long occurrencesInRAM = 0;
  

  
  /**
   * The {@link MimirIndex} that this atomic index is a member of.
   */
  protected MimirIndex parent;
  
  /**
   * A list containing the head and tails of this index.
   */
  protected List<MG4JIndex> batches;
  
  /**
   * The cluster-view of all the MG4J indexes that are part of this index (i.e.
   * the head and all the tails). 
   */
  protected Index invertedIndex;
  
  /**
   * The direct index for this atomic index. If 
   * <code>{@link #hasDirectIndex()}</code> is false, then this index will be 
   * <code>null</code>.
   */
  protected Index directIndex;
  
  /**
   * A set of properties added to the ones obtained from the index writer when
   * writing out batches.
   */
  protected Properties additionalProperties;
  
  /**
   * A set of properties added to the ones obtained from the direct index writer
   * when writing out batches.
   */
  protected Properties additionalDirectProperties;
  
  /**
   * Is the direct indexing enabled? Direct indexes are used to find terms 
   * occurring in given documents. This is the reverse operation to the typical
   * search, which finds documents containing a given a set of terms.
   */
  protected boolean hasDirectIndex;
  
  /**
   * This map associates direct index terms with their IDs. See the note at the
   * top-level javadocs for this class for a discussion on direct and inverted 
   * term IDs. 
   */
  protected Object2LongMap<String> directTermIds;
  
  /**
   * The terms in the direct index, in the order they were first seen during 
   * indexing.
   */
  protected ObjectBigList<String> directTerms;
  
  /**
   * The single thread used to index documents. All writes to the index files
   * are done from this thread.
   */
  protected Thread indexingThread;
  
  /**
   * Documents to be indexed are queued in this queue.
   */
  protected BlockingQueue<GATEDocument> inputQueue;
  
  /**
   * Documents that have been indexed are passed on to this queue.
   */
  protected BlockingQueue<GATEDocument> outputQueue;

    
  /**
   * The position of the current (or most-recently used) token in the current
   * document.
   */
  protected int tokenPosition;
  
  /**
   * A mutable string used to create instances of MutableString on the cheap.
   */
  protected MutableString currentTerm;
  
  /**
   * The number of documents currently stored in RAM.
   */
  protected int documentsInRAM;
  
  /**
   * An in-memory inverted index that gets dumped to files for each batch. 
   */
  protected Object2ReferenceOpenHashMap<MutableString, PostingsList> termMap;
  
  /**
   * The sizes (numbers of terms) for all the documents indexed in RAM.
   */
  protected IntArrayList documentSizesInRAM;
  
  /**
   * If a request was made to compress the index (combine all sub-indexes 
   * into a new head) this value will be non-null. The operation will be 
   * performed on the indexing thread at the first opportunity. At that point 
   * this future will complete, and the value will be set back to null.
   */
  protected RunnableFuture<Void> compactIndexTask;
  
  /**
   * If a request was made to write the in-RAM index data to disk this value 
   * will be not null. The operation will be performed on the indexing
   * thread at the first opportunity.  At that point the Future will complete, 
   * and the value will be set back to null.
   */
  protected RunnableFuture<Long> batchWriteTask;
  
  /**
   * Creates a new AtomicIndex
   * 
   * @param parent the {@link MimirIndex} containing this atomic index.
   * @param name the name of the sub-index, e.g. <em>token-i</em> or 
   *  <em>mentions-j</em>
   * @param indexDirectory the directory where this index should store all its 
   *  files.
   * @param hasDirectIndex should a direct index be used?
   * @param inputQueue the input queue for documents to be indexed.
   * @param outputQueue the output queue for documents that have been indexed.
   * @throws IndexException 
   * @throws IOException 
   */
	protected AtomicIndex(MimirIndex parent, String name,
      boolean hasDirectIndex, TermProcessor termProcessor,
      BlockingQueue<GATEDocument> inputQueue,
      BlockingQueue<GATEDocument> outputQueue) throws IOException, IndexException {
    this.parent = parent;
    this.name = name;
    this.indexDirectory = new File(parent.getIndexDirectory(), name);
    this.hasDirectIndex = hasDirectIndex;
    this.termProcessor = termProcessor;
    this.inputQueue = inputQueue;
    this.outputQueue = outputQueue;
    
    this.currentTerm = new MutableString();
    
    this.additionalProperties = new Properties();
    // save the term processor
    additionalProperties.setProperty(Index.PropertyKeys.TERMPROCESSOR, 
        ObjectParser.toSpec(termProcessor));
    if(hasDirectIndex) {
      additionalDirectProperties = new Properties();
      additionalDirectProperties.setProperty(Index.PropertyKeys.TERMPROCESSOR, 
          ObjectParser.toSpec(NullTermProcessor.getInstance()));
    }
    initIndex();
  }

	/**
	 * Opens the index and prepares it for indexing and searching. 
	 * @throws IndexException 
	 * @throws IOException 
	 */
	protected void initIndex() throws IOException, IndexException {
    // open the index
	  batches = new ArrayList<AtomicIndex.MG4JIndex>();
    if(indexDirectory.exists()) {
      // opening an existing index
      List<String> batchNames = new ArrayList<String>();
      
      File headDir = new File(indexDirectory, HEAD_FILE_NAME);
      if(headDir.exists()) {
        batchNames.add(HEAD_FILE_NAME);
      }
      Map<Integer, String> tails = new TreeMap<Integer, String>();
      for(String aTail : indexDirectory.list(TAILS_FILENAME_FILTER)) {
        tails.put(
            Integer.parseInt(aTail.substring(TAIL_FILE_NAME_PREFIX.length())), 
            aTail);
      }
      // add the tails in order
      batchNames.addAll(tails.values());
      // modify internal state
      synchronized(this) {
        // load all batches, in order
        for(String batchName : batchNames) {
          batches.add(openSubIndex(batchName));
        }
      }      
    } else {
      // new index creation
      indexDirectory.mkdirs();
    }
    synchronized(this) {
      invertedIndex = openInvertedIndexCluster(batches, termProcessor);
    }
    // open direct index
    if(hasDirectIndex) {
      directTerms = new ObjectBigArrayBigList<String>();
      directTermIds = new Object2LongAVLTreeMap<String>();
      directTermIds.defaultReturnValue(-1);
      File directTermsFile = new File(indexDirectory, DIRECT_TERMS_FILENAME);
      if(directTermsFile.exists()) {
        FileLinesCollection fileLines = new FileLinesCollection(
            directTermsFile.getAbsolutePath(), "UTF-8");
        Iterator<MutableString> termsIter = fileLines.iterator();
        long termID = 0;
        while(termsIter.hasNext()) {
          String term = termsIter.next().toString();
          directTerms.add(term);
          directTermIds.put(term, termID++);
        }
      }
      synchronized(this) {
        directIndex = openDirectIndexCluster(batches);
      }
    }
	}
		
  /**
	 * Gets the name of this atomic index. This is used as the file name for the 
	 * directory storing the index files.
	 * @return
	 */
	public String getName() {
	  return name;
	}
	
	/**
	 * Is a direct index configured for this atomic index. 
	 * @return
	 */
	public boolean hasDirectIndex(){
	  return hasDirectIndex;
	}
		
	/**
	 * Starts a new MG4J batch. First time around this will be the head, 
	 * subsequent calls will start a new tail.
	 */
	protected void newBatch() {
	  occurrencesInRAM = 0;
    maxDocSizeInRAM = -1;
    documentsInRAM = 0;
    if(termMap == null) {
      termMap = new Object2ReferenceOpenHashMap<MutableString, 
          PostingsList>(INITIAL_TERM_MAP_SIZE, Hash.FAST_LOAD_FACTOR );      
    } else {
      termMap.clear();
      termMap.trim( INITIAL_TERM_MAP_SIZE );
    } 
    if(documentSizesInRAM  == null) {
      documentSizesInRAM = new IntArrayList();
    } else {
      documentSizesInRAM.clear();
    }
	}
	
	/**
	 * Writes all the data currently stored in RAM to a new index batch. The first
	 * batch is the head index, all other batches are tail indexes.
	 * @throws IOException 
	 * @throws IndexException
	 * @return the number of occurrences written to disk 
	 */
	protected long writeCurrentBatch() throws IOException, IndexException {
	  if(documentsInRAM == 0) return 0;
	  
	  // find the name for the new tail
	  int tailNo = -1;
	  File headDir = new File(indexDirectory, HEAD_FILE_NAME);
	  if(headDir.exists()) {
	    // we have a head, calculate the tail number for this new tail
	    String[] existingTails = indexDirectory.list(TAILS_FILENAME_FILTER);
	    for(String aTail : existingTails) {
	      int aTailNo = Integer.parseInt(aTail.substring(TAIL_FILE_NAME_PREFIX.length()));
	      if(aTailNo > tailNo) tailNo = aTailNo;
	    }
	    tailNo++;	    
	  }
	  
	  // Open an index writer for the new tail
	  String newTailName = tailNo == -1 ? HEAD_FILE_NAME : 
	      (TAIL_FILE_NAME_PREFIX + Integer.toString(tailNo));
	  File newTailDir = new File(indexDirectory, newTailName);
	  newTailDir.mkdir();
	  String mg4jBasename = new File(newTailDir, name).getAbsolutePath();
	  QuasiSuccinctIndexWriter indexWriter = new QuasiSuccinctIndexWriter(
	      IOFactory.FILESYSTEM_FACTORY,
	      mg4jBasename,
	      documentsInRAM,
	      Fast.mostSignificantBit(QuasiSuccinctIndex.DEFAULT_QUANTUM),
	      QuasiSuccinctIndexWriter.DEFAULT_CACHE_SIZE,
	      CompressionFlags.DEFAULT_QUASI_SUCCINCT_INDEX,
	      ByteOrder.nativeOrder());
	  // write the data from RAM
    int numTermsInRAM = termMap.size();
    logger.info( "Generating index for batch " + newTailName + 
            "; documents: " + documentsInRAM + "; terms:" + numTermsInRAM + 
            "; occurrences: " + occurrencesInRAM +
            " / " + parent.getOccurrencesInRam());
    
    // We write down all term in appearance order in termArray.
    final MutableString[] termArray = termMap.keySet().toArray(new MutableString[ numTermsInRAM ]);
    // We sort the terms appearing in the batch and write them on disk.
    Arrays.quickSort(0, termArray.length, 
            new IntComparator() {
              @Override
              public int compare(Integer one, Integer other) {
                return compare(one.intValue(), other.intValue());
              }
              
              @Override
              public int compare(int one, int other) {
                return termArray[one].compareTo(termArray[other]);
              }
            },
            new Swapper() {
              @Override
              public void swap(int one, int other) {
                MutableString temp = termArray[one];
                termArray[one] = termArray[other];
                termArray[other] = temp;
              }
            });
	  // write the terms, termmap, and bloom filter files
    
    // make sure we can't create a Bloom filter of expected size 0
    BloomFilter<Void> termFilter = BloomFilter.create(Math.max(numTermsInRAM, 1));
    PrintWriter pw = new PrintWriter( 
        new OutputStreamWriter(new FastBufferedOutputStream(
            new FileOutputStream(mg4jBasename + DiskBasedIndex.TERMS_EXTENSION), 
            64 * 1024), 
        "UTF-8" ));
    for (MutableString t : termArray ) {
      t.println( pw );
      termFilter.add(t);
    }
    pw.close();
    generateTermMap(new File(mg4jBasename + DiskBasedIndex.TERMS_EXTENSION),
        new File(mg4jBasename + DiskBasedIndex.TERMMAP_EXTENSION), null);
    // write the bloom filter
    BinIO.storeObject(termFilter, 
        new File(mg4jBasename + DocumentalCluster.BLOOM_EXTENSION)); 
    // write the sizes file
    File sizesFile = new File(mg4jBasename + DiskBasedIndex.SIZES_EXTENSION);
    OutputBitStream sizesStream = new OutputBitStream(sizesFile);   
    for(int docSize : documentSizesInRAM.elements()) {
      sizesStream.writeGamma(docSize);
    }
    sizesStream.close();
    // write the actual index
    int maxCount = 0;
    for ( int i = 0; i < numTermsInRAM; i++ ) {
      PostingsList postingsList = termMap.get( termArray[ i ] );
      if ( maxCount < postingsList.maxCount ) maxCount = postingsList.maxCount;
      postingsList.write(indexWriter);
    }
    indexWriter.close();
    // write the index properties
    try {
      Properties properties = indexWriter.properties();
      additionalProperties.setProperty( Index.PropertyKeys.SIZE, 
          indexWriter.writtenBits());
      // -1 means unknown
      additionalProperties.setProperty( Index.PropertyKeys.MAXDOCSIZE, 
          maxDocSizeInRAM);
      additionalProperties.setProperty( Index.PropertyKeys.MAXCOUNT, maxCount );
      additionalProperties.setProperty( Index.PropertyKeys.OCCURRENCES, 
          occurrencesInRAM );
      properties.addAll(additionalProperties);
      Scan.saveProperties( IOFactory.FILESYSTEM_FACTORY, properties, 
          mg4jBasename + DiskBasedIndex.PROPERTIES_EXTENSION );
      
      // write stats
      PrintStream statsPs = new PrintStream(new File(mg4jBasename + 
          DiskBasedIndex.STATS_EXTENSION));
      indexWriter.printStats(statsPs);
      statsPs.close();
    } catch(ConfigurationException e) {
      // this should never happen
      throw new IndexException("Error while saving tail properties", e);
    }
	  
    if(hasDirectIndex) {
      writeDirectIndex(newTailDir);
    }
    // update parent
    long res = occurrencesInRAM;
    
    // clear out internal state, in preparation for the next tail  
    newBatch();
    
    // merge new tail into index cluster
    try {
      // modify internal state
      synchronized(this) {
        batches.add(openSubIndex(newTailName));
        invertedIndex = openInvertedIndexCluster(batches, termProcessor);
        if(hasDirectIndex) {
          directIndex = openDirectIndexCluster(batches);
        }
      }
    } catch(Exception e) {
      throw new IndexException("Could not open the index just written to " +
         mg4jBasename , e);
    }
    return res;
	}
	
	/**
	 * Writes the in-RAM data to a new direct index batch.
	 * @param batchDir
	 */
  protected void writeDirectIndex(File batchDir) 
      throws IOException, IndexException {
    // The index we are writing is a direct index, so we give it new terms
    // which are actually document IDs, and they have posting lists containing
    // document IDs, which are actually termIDs.

    // The document pointers in RAM are zero-based, so we need to add all the 
    // documents on disk to this.
    long docsOnDisk = 0;
    for(MG4JIndex index : batches) {
      docsOnDisk += index.invertedIndex.numberOfDocuments;
    }
    
    //1. invert index data in RAM
    Object2ReferenceOpenHashMap<MutableString, PostingsList> docMap = 
          new Object2ReferenceOpenHashMap<MutableString, 
            PostingsList>(INITIAL_TERM_MAP_SIZE, Hash.FAST_LOAD_FACTOR );
    MutableString docIdStr = new MutableString();
    // make sure all the terms about to be indexed have direct ID
    for(MutableString termMS : termMap.keySet()) {
      String termString = termMS.toString();
      long directTermId = directTermIds.getLong(termString);
      if(directTermId == directTermIds.defaultReturnValue()) {
        // term not seen before
        directTerms.add(termString);
        directTermId = directTerms.size64() -1;
        directTermIds.put(termString, directTermId);
      }
    }
    // we now read the posting lists for all the terms, in ascending term order    
    MutableString termMS = new MutableString();
    for(long directTermId = 0; directTermId < directTerms.size64(); directTermId++){
      String termString = directTerms.get(directTermId);
      termMS.replace(termString);
      PostingsList termPostings = termMap.get(termMS);
      if(termPostings != null) {
        long docPointer = docsOnDisk + termPostings.firstDocumentPointer;
        for(int i = 0; i < termPostings.documentPointersDifferential.size(); i++) {
          docPointer += termPostings.documentPointersDifferential.get(i);       
          int count = termPostings.counts.getInt(i);
          // convert data to the correct type
          docIdStr.replace(longToTerm(docPointer));
          // at this point we have term, document, counts so we can write the data
          // to the in-RAM direct index
          PostingsList docPostings = docMap.get(docIdStr);
          if(docPostings == null) {
            docPostings = new PostingsList(false);
            docMap.put(docIdStr.copy(), docPostings);
          }
          docPostings.newDocumentPointer(directTermId);
          docPostings.setCount(count); 
          docPostings.flush();
        } 
      }
    }
    
    // 2. write the data from RAM
    String mg4jBasename = new File(batchDir, name + 
        DIRECT_INDEX_NAME_SUFFIX).getAbsolutePath();
    // copy the default compression flags, and remove positions
    Map<Component, Coding> flags = new HashMap<Component, Coding>(
        CompressionFlags.DEFAULT_QUASI_SUCCINCT_INDEX);
    flags.remove(Component.POSITIONS);
    QuasiSuccinctIndexWriter directIndexWriter =
        new QuasiSuccinctIndexWriter(
            IOFactory.FILESYSTEM_FACTORY,
            mg4jBasename, 
            directTerms.size64(),
            Fast.mostSignificantBit(QuasiSuccinctIndex.DEFAULT_QUANTUM),
            QuasiSuccinctIndexWriter.DEFAULT_CACHE_SIZE,
            flags,
            ByteOrder.nativeOrder());
    
    // sort all the docIds
    final MutableString[] docArray = docMap.keySet().toArray(new MutableString[ docMap.size() ]);
    // We sort the terms appearing in the batch and write them on disk.
    Arrays.quickSort(0, docArray.length, 
            new IntComparator() {
              @Override
              public int compare(Integer one, Integer other) {
                return compare(one.intValue(), other.intValue());
              }
              
              @Override
              public int compare(int one, int other) {
                return docArray[one].compareTo(docArray[other]);
              }
            },
            new Swapper() {
              @Override
              public void swap(int one, int other) {
                MutableString temp = docArray[one];
                docArray[one] = docArray[other];
                docArray[other] = temp;
              }
            });
    
    BloomFilter<Void> docBloomFilter = BloomFilter.create(docArray.length);
    PrintWriter pw = new PrintWriter( 
        new OutputStreamWriter(new FastBufferedOutputStream(
            new FileOutputStream(mg4jBasename + DiskBasedIndex.TERMS_EXTENSION), 
            64 * 1024), 
        "UTF-8" ));
    for (MutableString t : docArray ) {
      t.println( pw );
      docBloomFilter.add(t);
    }
    pw.close();
    generateTermMap(new File(mg4jBasename + DiskBasedIndex.TERMS_EXTENSION),
        new File(mg4jBasename + DiskBasedIndex.TERMMAP_EXTENSION), null);
    // write the bloom filter
    BinIO.storeObject(docBloomFilter, 
        new File(mg4jBasename + DocumentalCluster.BLOOM_EXTENSION)); 
    // write the sizes file
    // this is a list of document sizes (directTerms in our case)    
    File sizesFile = new File(mg4jBasename + DiskBasedIndex.SIZES_EXTENSION);
    OutputBitStream sizesStream = new OutputBitStream(sizesFile);
    int maxTermSize = -1; // -1 means unknown
    //for(MutableString term : termArray) {
    for(long directTermId = 0; directTermId < directTerms.size64(); directTermId++){
      String termString = directTerms.get(directTermId);
      termMS.replace(termString);
      PostingsList termPostings = termMap.get(termMS);
      int termSize = termPostings != null ?
          (int)termPostings.frequency : 0;
      sizesStream.writeGamma(termSize);
      if(termSize > maxTermSize) maxTermSize = termSize;
    }
    sizesStream.close();
    
    // write the actual index
    int maxCount = 0;
    long occurrences = 0;
    for ( int i = 0; i < docArray.length; i++ ) {
      PostingsList postingsList = docMap.get( docArray[ i ] );
      if ( maxCount < postingsList.maxCount ) maxCount = postingsList.maxCount;
      postingsList.write(directIndexWriter);
      occurrences += postingsList.occurrences;
    }
    directIndexWriter.close();
    // write the index properties
    try {
      Properties properties = directIndexWriter.properties();
      additionalDirectProperties.setProperty( Index.PropertyKeys.SIZE, 
          directIndexWriter.writtenBits());
      // -1 means unknown
      additionalDirectProperties.setProperty( Index.PropertyKeys.MAXDOCSIZE, 
          maxTermSize);
      additionalDirectProperties.setProperty( Index.PropertyKeys.MAXCOUNT, maxCount );
      additionalDirectProperties.setProperty( Index.PropertyKeys.OCCURRENCES, 
          occurrences);
      properties.addAll(additionalDirectProperties);
      Scan.saveProperties( IOFactory.FILESYSTEM_FACTORY, properties, 
          mg4jBasename + DiskBasedIndex.PROPERTIES_EXTENSION );
      
      // write stats
      PrintStream statsPs = new PrintStream(new File(mg4jBasename + 
          DiskBasedIndex.STATS_EXTENSION));
      directIndexWriter.printStats(statsPs);
      statsPs.close();
    } catch(ConfigurationException e) {
      // this should never happen
      throw new IndexException("Error while saving tail properties", e);
    }
    //update the index-wide direct terms file
    File newDirectTermsFile = new File(indexDirectory, DIRECT_TERMS_FILENAME + HEAD_NEW_EXT);
    pw = new PrintWriter(new OutputStreamWriter(new FastBufferedOutputStream(
        new FileOutputStream(newDirectTermsFile), 64 * 1024), "UTF-8" ));
    for (String t : directTerms ) {
      pw.println(t);
    }
    pw.close();

    File directTermsFile = new File(indexDirectory, DIRECT_TERMS_FILENAME);
    File oldDirectTermsFile = new File(indexDirectory, DIRECT_TERMS_FILENAME + HEAD_OLD_EXT);
    if(!directTermsFile.exists() || directTermsFile.renameTo(oldDirectTermsFile)) {
      if(newDirectTermsFile.renameTo(directTermsFile)) {
        oldDirectTermsFile.delete();
      } else {
        throw new IndexException("Unable to save direct terms file");
      }
    }
  }
	
	
	/**
	 * Combines all the currently existing batches, generating a new head index.
	 * @throws IndexException 
	 * @throws IOException 
	 * @throws ConfigurationException 
	 */
	protected void compactIndex() throws IndexException, IOException, ConfigurationException {
	  File headDirNew = new File(indexDirectory, HEAD_FILE_NAME + HEAD_NEW_EXT);
	  // make a local copy of the sub-indexes
	  List<MG4JIndex> indexesToMerge = 
	      new ArrayList<AtomicIndex.MG4JIndex>(batches);
	  if(!headDirNew.mkdir()) {
	    throw new IndexException("Could not create new head directory at " + 
	        headDirNew.getAbsolutePath() +  "!"); 
	  }
	  
	  Map<Component,Coding> codingFlags = 
	      CompressionFlags.DEFAULT_QUASI_SUCCINCT_INDEX;
	  String outputBaseName = new File(headDirNew, name).getAbsolutePath();
	  
	  String[] inputBaseNames = new String[indexesToMerge.size()];
	  for(int i = 0; i < inputBaseNames.length; i++) {
	    inputBaseNames[i] = new File(indexesToMerge.get(i).indexDir, name)
	      .getAbsolutePath(); 
	  }
	  
	  try {
      new Concatenate(
          IOFactory.FILESYSTEM_FACTORY,
          outputBaseName,
          inputBaseNames,
          false, // metadataOnly 
          Combine.DEFAULT_BUFFER_SIZE, 
          codingFlags,
          IndexType.QUASI_SUCCINCT,
          true, // skips
          // BitStreamIndex.DEFAULT_QUANTUM,
          // replaced with optimised automatic calculation
          -5, 
          BitStreamIndex.DEFAULT_HEIGHT, 
          SkipBitStreamIndexWriter.DEFAULT_TEMP_BUFFER_SIZE, 
          ProgressLogger.DEFAULT_LOG_INTERVAL).run();
      // generate term map
      generateTermMap(new File(outputBaseName + DiskBasedIndex.TERMS_EXTENSION), 
          new File(outputBaseName +  DiskBasedIndex.TERMMAP_EXTENSION),
          new File(outputBaseName +  DocumentalCluster.BLOOM_EXTENSION));
    } catch(Exception e) {
      throw new IndexException("Exception while combining sub-indexes", e);
    }

    if(hasDirectIndex()) {
      combineDirectIndexes(indexesToMerge, new File(headDirNew, name + 
          DIRECT_INDEX_NAME_SUFFIX).getAbsolutePath());
    }	  
	  
	  // update the internal state
    synchronized(this) {
      // remove the indexes that were merged
      batches.removeAll(indexesToMerge);
      // insert the new head at the front of the list
      File headDir = new File(indexDirectory, HEAD_FILE_NAME);
      File headDirOld = new File(indexDirectory, HEAD_FILE_NAME + HEAD_OLD_EXT);
      if(headDir.exists() && headDir.renameTo(headDirOld)){
        if(headDirNew.renameTo(headDir)) {
          batches.add(0, openSubIndex(HEAD_FILE_NAME));
          invertedIndex = openInvertedIndexCluster(batches, termProcessor);
          if(hasDirectIndex) {
            directIndex =openDirectIndexCluster(batches);
          }
          // clean-up: delete old head, used-up tails
          if(!gate.util.Files.rmdir(headDirOld)) {
            throw new IndexException(
                "Could not fully delete old sub-index at: " + headDirOld);
          }
          for(MG4JIndex aSubIndex : indexesToMerge) {
            if(!aSubIndex.indexDir.equals(headDir)) {
              if(!gate.util.Files.rmdir(aSubIndex.indexDir)){
                throw new IndexException(
                    "Could not fully delete old sub-index at: " + 
                    aSubIndex.indexDir);
              }              
            }
          }
        } else {
          throw new IndexException("Cold not rename new head at " + 
              headDirNew.getAbsolutePath() + " to " + headDir);
        }
      } else {
        throw new IndexException("Cold not rename head at " + 
            headDir.getAbsolutePath() + " to " + headDirOld);
      }
    }
	}
	
	/**
	 * Given a set of direct indexes (MG4J indexes, with counts, but no positions,
	 * that form a lexical cluster) this method produces one single output index
	 * containing the data from all the input indexes.
	 * @param inputIndexes
	 * @param outputBasename
	 * @throws IOException 
	 * @throws ConfigurationException 
	 */
	protected static void combineDirectIndexes (List<MG4JIndex> inputIndexes, 
	    String outputBasename) throws IOException, ConfigurationException {
	  
	  long noOfDocuments = 0;
	  long noOfTerms = 0;
	  for(MG4JIndex index : inputIndexes) {
	    noOfDocuments += index.directIndex.numberOfDocuments;
	    noOfTerms += index.directIndex.numberOfTerms;
	  }
	  
	  // open the output writer
    // copy the default compression flags, and remove positions
    Map<Component, Coding> flags = new HashMap<Component, Coding>(
        CompressionFlags.DEFAULT_QUASI_SUCCINCT_INDEX);
    flags.remove(Component.POSITIONS);
    QuasiSuccinctIndexWriter outputIndexWriter =
        new QuasiSuccinctIndexWriter(
            IOFactory.FILESYSTEM_FACTORY,
            outputBasename, 
            noOfDocuments,
            Fast.mostSignificantBit(QuasiSuccinctIndex.DEFAULT_QUANTUM),
            QuasiSuccinctIndexWriter.DEFAULT_CACHE_SIZE,
            flags,
            ByteOrder.nativeOrder());
    
    BloomFilter<Void> bloomFilter = BloomFilter.create(noOfTerms);
    PrintWriter termsPw = new PrintWriter( 
        new OutputStreamWriter(new FastBufferedOutputStream(
            new FileOutputStream(outputBasename + DiskBasedIndex.TERMS_EXTENSION), 
            64 * 1024), 
        "UTF-8" ));
    
    // write the index
    long occurrences = 0;
    int maxCount = 0;
    PostingsList postingsList = new PostingsList(false);
    for(MG4JIndex inputIndex : inputIndexes) {
      IndexReader inputReader = inputIndex.directIndex.getReader();
      File directTermsFile = new File(inputIndex.indexDir, 
          inputIndex.indexName + DIRECT_INDEX_NAME_SUFFIX + 
          DiskBasedIndex.TERMS_EXTENSION);
      FileLinesCollection.FileLinesIterator termsIter =
          new FileLinesCollection(directTermsFile.getAbsolutePath(), 
          "UTF-8").iterator();
      MutableString termMS = null;
      IndexIterator inputIterator = inputReader.nextIterator();
      while(inputIterator != null && termsIter.hasNext()) {
        termMS = termsIter.next();
        bloomFilter.add(termMS);
        termMS.println(termsPw);
        long docPointer = inputIterator.nextDocument();
        while(docPointer !=  IndexIterator.END_OF_LIST) {
          postingsList.newDocumentPointer(docPointer);
          postingsList.setCount(inputIterator.count());
          docPointer = inputIterator.nextDocument();
        }
        postingsList.flush();
        occurrences += postingsList.occurrences;
        if ( maxCount < postingsList.maxCount ) maxCount = postingsList.maxCount;
        postingsList.write(outputIndexWriter);
        postingsList.clear();
        inputIterator = inputReader.nextIterator();
      }
      inputReader.close();
    }
    outputIndexWriter.close();
    termsPw.close();
    generateTermMap(new File(outputBasename + DiskBasedIndex.TERMS_EXTENSION),
        new File(outputBasename + DiskBasedIndex.TERMMAP_EXTENSION), null);
    // write the bloom filter
    BinIO.storeObject(bloomFilter, 
        new File(outputBasename + DocumentalCluster.BLOOM_EXTENSION));
    // direct indexes don't store positions, so sizes are not needed

    // write the index properties
    Properties properties = outputIndexWriter.properties();
    properties.setProperty(Index.PropertyKeys.TERMPROCESSOR, 
        ObjectParser.toSpec(NullTermProcessor.getInstance()));
    properties.setProperty( Index.PropertyKeys.SIZE,  
        outputIndexWriter.writtenBits());
    // -1 means unknown
    properties.setProperty( Index.PropertyKeys.MAXDOCSIZE, -1);
    properties.setProperty( Index.PropertyKeys.MAXCOUNT, maxCount );
    properties.setProperty( Index.PropertyKeys.OCCURRENCES, occurrences);
    Scan.saveProperties( IOFactory.FILESYSTEM_FACTORY, properties, 
        outputBasename + DiskBasedIndex.PROPERTIES_EXTENSION );
    
    // write stats
    PrintStream statsPs = new PrintStream(new File(outputBasename + 
        DiskBasedIndex.STATS_EXTENSION));
    outputIndexWriter.printStats(statsPs);
    statsPs.close();
	}
	
	/**
	 * Instructs this index to dump to disk all the in-RAM index data at the fist 
	 * opportunity.
	 * @return a {@link Future} value that, upon completion, will return the 
	 * number of occurrences written to disk.
	 * @throws InterruptedException if this thread is interrupted while trying to
	 * queue the dump request.
	 */
	public Future<Long> requestSyncToDisk() throws InterruptedException {
	  if(batchWriteTask == null) {
	    batchWriteTask = new FutureTask<Long>(new Callable<Long>() {
        @Override
        public Long call() throws Exception {
          return writeCurrentBatch();
        }
      });
	    inputQueue.put(DUMP_BATCH);  
	  }
	  return batchWriteTask;
	}
	
	/**
	 * Requests this atomic index to compact its on-disk batches into a single
	 * batch.
	 * 
	 * @return a {@link Future} which can be used to find out when the compaction
	 * operation has completed.
	 * @throws InterruptedException if this thread is interrupted while trying to
   * queue the compaction request.
	 */
  public Future<Void> requestCompactIndex() throws InterruptedException {
    if(compactIndexTask == null) {
      compactIndexTask = new FutureTask<Void>(new Callable<Void>(){
        @Override
        public Void call() throws Exception {
          compactIndex();
          return null;
        }
      });
      inputQueue.put(COMPACT_INDEX);
    }
    return compactIndexTask;
  }
	
	/**
	 * Opens one sub-index, specified as a directory inside this Atomic Index's
	 * index directory.
	 * @param subIndexDirname
	 * @return
	 * @throws IOException 
	 * @throws IndexException 
	 */
	protected MG4JIndex openSubIndex(String subIndexDirname) throws IOException, IndexException {
    Index invertedIndex = null;
    File subIndexDir = new File(indexDirectory, subIndexDirname);
    String mg4jBasename = new File(subIndexDir, name).getAbsolutePath(); 
    try {
      try{
        invertedIndex = Index.getInstance(
            mg4jBasename + "?" + UriKeys.MAPPED.name().toLowerCase() + "=1;", 
            true, true);
      } catch(IOException e) {
        // memory mapping failed
        logger.info("Memory mapping failed for index " + mg4jBasename
                + ". Loading as file index instead");
        // now try to open it as a plain an on-disk index
        invertedIndex = Index.getInstance(mg4jBasename, true, true);
      }
    } catch(Exception e) {
      throw new IndexException("Could not open the sub-index at" + mg4jBasename , e);
    }
    //read the Bloom filter 
    File bloomFile = new File(mg4jBasename + DocumentalCluster.BLOOM_EXTENSION);
    BloomFilter<Void> invertedTermFilter = null;
    try {
      if(bloomFile.exists()) {
        invertedTermFilter = (BloomFilter<Void>) BinIO.loadObject(bloomFile);
      }
    } catch(ClassNotFoundException e) {
      // this should never happen. If it does, it's not fatal
      logger.warn("Exception wile loading stre Bloom Filter", e);
    }
    
    Index directIndex = null;
    BloomFilter<Void> directTermFilter = null;
    if(hasDirectIndex) {
      // open direct index
      mg4jBasename = new File(subIndexDir, name + 
          DIRECT_INDEX_NAME_SUFFIX).getAbsolutePath();
      try {
        try{
          directIndex = Index.getInstance(
              mg4jBasename + "?" + UriKeys.MAPPED.name().toLowerCase() + "=1;", 
              true, false);
        } catch(IOException e) {
          // memory mapping failed
          logger.info("Memory mapping failed for index " + mg4jBasename
                  + ". Loading as file index instead");
          // now try to open it as a plain an on-disk index
          directIndex = Index.getInstance(mg4jBasename, true, false);
        }
      } catch(Exception e) {
        throw new IndexException("Could not open the sub-index at" + mg4jBasename , e);
      }
      //read the Bloom filter 
      bloomFile = new File(mg4jBasename + DocumentalCluster.BLOOM_EXTENSION);
      
      try {
        if(bloomFile.exists()) {
          directTermFilter = (BloomFilter<Void>) BinIO.loadObject(bloomFile);
        }
      } catch(ClassNotFoundException e) {
        // this should never happen. If it does, it's not fatal
        logger.warn("Exception wile loading stre Bloom Filter", e);
      }
    }
    
    MG4JIndex newIndexData = new MG4JIndex(subIndexDir, name,
        invertedIndex, invertedTermFilter, 
        directIndex, directTermFilter);
	  return newIndexData;
	}
	
	/**
	 * Runnable implementation: the logic of this run method is simply indexing
	 * documents queued to the input queue. To stop it, send a 
	 * {@link GATEDocument#END_OF_QUEUE} value to the input queue.
	 */
	public void run() {
	  indexingThread = Thread.currentThread();
	  GATEDocument aDocument;
	  try{
	    // start in-RAM indexing
	    newBatch();
  	  if(inputQueue != null) {
        do{
          aDocument = inputQueue.take();
          if(aDocument != GATEDocument.END_OF_QUEUE) {
            if(aDocument == DUMP_BATCH) {
              //dump batch was requested
              if(batchWriteTask != null){
                batchWriteTask.run();
              }
              batchWriteTask = null;
            } else if(aDocument == COMPACT_INDEX) {
              // compress index was requested
              if(compactIndexTask != null) {
                compactIndexTask.run();
              }
              compactIndexTask = null;
            } else {
              try {
                long occurencesBefore = occurrencesInRAM;
                processDocument(aDocument);
                aDocument.addOccurrences(occurrencesInRAM - occurencesBefore);
              } catch(Throwable e) {
                logger.error("Problem while indexing document!", e);
              }          
            }
          } else {
            // close down
            writeCurrentBatch();
            flush();
          }
          if(aDocument != DUMP_BATCH && aDocument != COMPACT_INDEX) {
            outputQueue.put(aDocument);  
          }
        } while(aDocument != GATEDocument.END_OF_QUEUE);
  	  }
	  }catch(InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch(Exception e) {
      logger.error("Exception during indexing!", e);
      throw new GateRuntimeException("Exception during indexing!", e);
    } finally {
      indexingThread = null;
    }
	}
	
	/**
	 * Closes all file-based resources.
	 * @throws IOException
	 */
	abstract protected void flush() throws IOException;
	
	/**
	 * Notifies this index to stop its indexing operations, and waits for all data
	 * to be written. 
	 * @throws InterruptedException is the waiting thread is interrupted before 
	 * the indexing thread has finished writing all the data.
	 */
	public void close() throws InterruptedException {
    inputQueue.put(GATEDocument.END_OF_QUEUE);
    if(indexingThread != null) {
      indexingThread.join();
    }
	}

  /**
   * Hook for subclasses, called before processing the annotations
   * for this document.  The default implementation is a no-op.
   */
  protected void documentStarting(GATEDocument gateDocument) throws IndexException {
  }

  /**
   * Hook for subclasses, called after annotations for this document
   * have been processed.  The default implementation is a no-op.
   */
  protected void documentEnding(GATEDocument gateDocument) throws IndexException {
  }
	
  /**
   * Get the annotations that are to be processed for a document,
   * in increasing order of offset.
   */
  protected abstract Annotation[] getAnnotsToProcess(
          GATEDocument gateDocument) throws IndexException;
  
  
  /**
   * Calculate the starting position for the given annotation, storing
   * it in {@link #tokenPosition}.  The starting position is the
   * index of the token within the document where the annotation starts,
   * and <em>must</em> be &gt;= the previous value of tokenPosition.
   * @param ann
   * @param gateDocument
   */
  protected abstract void calculateStartPositionForAnnotation(Annotation ann,
          GATEDocument gateDocument) throws IndexException;
  
  /**
   * Determine the string (or strings, if there are alternatives) that should 
   * be stored in the index for the given annotation.
   * 
   * If a single string value should be returned, it is more efficient to store
   * the value in {@link #currentTerm}, in which case <code>null</code> should 
   * be returned instead.
   * 
   * If the current term should not be indexed (e.g. it's a stop word), then 
   * the implementation should return an empty String array.
   * 
   * @param ann
   * @param gateDocument
   */
  protected abstract String[] calculateTermStringForAnnotation(Annotation ann,
          GATEDocument gateDocument) throws IndexException;
  
  /**
   * Adds the supplied document to the in-RAM index.
   * @param gateDocument the document to index
   * @throws IndexException
   */
  protected void processDocument(GATEDocument gateDocument) throws IndexException{
    //zero document related counters
    tokenPosition = 0;
    
    documentStarting(gateDocument);
    //get the annotations to be processed
    Annotation[] annotsToProcess = getAnnotsToProcess(gateDocument);
    logger.debug("Starting document "
        + gateDocument.getDocument().getName() + ". "
        + annotsToProcess.length + " annotations to process");    
    try {
      //process the annotations one by one.
      for(Annotation ann : annotsToProcess){
        processAnnotation(ann, gateDocument);
      }
      // the current document is finished
      int docLength = tokenPosition + 1;
      if(docLength > maxDocSizeInRAM) maxDocSizeInRAM = docLength;
      documentSizesInRAM.add(docLength);
    } finally {
      documentEnding(gateDocument);
      documentsInRAM++;
    }
  }
  
  /**
   * Indexes one annotation (either a Token or a semantic annotation).
   * @param ann the annotation to be indexed
   * @param gateDocument the GATEDocument containing the annotation
   * @throws IndexException
   * @throws IOException
   */
  protected void processAnnotation(Annotation ann,
      GATEDocument gateDocument) throws IndexException {
    // calculate the position and string for this annotation
    calculateStartPositionForAnnotation(ann, gateDocument);
    String[] terms = calculateTermStringForAnnotation(ann, gateDocument);
    if(terms == null){
      //the value was already stored in #currentTerm by the implementation.
      indexCurrentTerm();
    }else if(terms.length == 0){
      //we received an empty array -> we should NOT index the current term
    }else{
      //we have received multiple values from the implementation
      for(String aTerm : terms){
        currentTerm.replace(aTerm == null ? "" : aTerm);
        indexCurrentTerm();
      }
    }
  }
  
  /**
   * Adds the value in {@link #currentTerm} to the index.
   * @throws IOException 
   */
  protected void indexCurrentTerm() {
    //check if we have seen this mention before
    PostingsList termPostings = termMap.get(currentTerm);
    if(termPostings == null){
      //new term -> create a new postings list.
      termMap.put( currentTerm.copy(), termPostings = new PostingsList(true));
    }
    //add the current posting to the current postings list
    // In a documental cluster, each sub-index is zero-based. This is why we use
    // the local document pointer here.
    termPostings.newDocumentPointer(documentsInRAM);
    //this is needed so that we don't increment the number of occurrences
    //for duplicate values.
    if(termPostings.checkPosition(tokenPosition)){
      termPostings.addPosition(tokenPosition);
      occurrencesInRAM++;
    } else {
      logger.debug("Duplicate position");
    }
  }

  /**
   * Gets the top level directory for this atomic index. This will be a 
   * directory contained in the top level directory of the {@link MimirIndex}
   * which includes this atomic index.
   * @return
   */
  public File getIndexDirectory() {
    return indexDirectory;
  }

  /**
   * Gets the top level {@link MimirIndex} to which this atomic index belongs.
   * @return
   */
  public MimirIndex getParent() {
    return parent;
  }

  /**
   * Gets the input queue used by this atomic index. This queue is used to 
   * submit documents for indexing.
   * @return
   */
  public BlockingQueue<GATEDocument> getInputQueue() {
    return inputQueue;
  }

  /**
   * Gets the output queue used by this atomic index. This is used to 
   * &quot;return&quot; documents that have finished indexing. Notably, values 
   * in this queue will have their occurrences value (see
   * {@link GATEDocument#getOccurrences()}) increased by the number of 
   * occurrences generated by indexing the document in this atomic index.
   * 
   * @return
   */
  public BlockingQueue<GATEDocument> getOutputQueue() {
    return outputQueue;
  }

  /**
   * Gets the inverted index (an {@link Index} value) that can be used to 
   * search this atomic index. This will normally be a 
   * {@link DocumentalCluster} view over all the batches contained. 
   * @return
   */
  public Index getIndex() {
    return invertedIndex;
  }
  
  
  /**
   * Gets the direct index for this atomic index. The returned value is 
   * <code>non-null</code> only if the atomic index was configured to have a 
   * direct index upon its construction (see 
   * {@link #AtomicIndex(MimirIndex, String, File, boolean, TermProcessor, BlockingQueue, BlockingQueue)}.).
   * You can check if a direct index has been configured by calling 
   * {@link #hasDirectIndex()}.
   * @return an Index in which terms and documents are reversed. When querying 
   * the returned index, the &quot;terms&quot; provided should be String 
   * representations of document IDs (as produced by {@link #longToTerm(long)}).
   * The search results is a set of &quot;document IDs&quot;, which are actually
   * term IDs. The actual term string corresponding to the returned term IDs can
   * be obtained by calling {@link #getDirectTerm(long)}.   
   */
  public Index getDirectIndex() {
    return directIndex;
  }
 
  /**
   * Gets the term string for a given direct term ID. The term ID must have been 
   * obtained from the direct index of this index.
   * @param termId the ID for the term being sought.
   * @return the string for the given term.
   */
  public CharSequence getDirectTerm(long termId) {
    return directTerms.get(termId);
  }
  
  /**
   * Gets the list of direct terms for this index. The terms are sorted by the 
   * first they were seen, and <strong>not</strong> lexicographically.
   * @return
   */
  public ObjectBigList<? extends CharSequence> getDirectTerms() {
    return directTerms;
  }
  
  /**
   * Gets the occurrence count in the whole index for a given direct term,
   * specified by a direct term ID (which must have been obtained from the 
   * direct index of this index).
   * 
   * @param directTermId
   * @return
   * @throws IOException
   */
  public long getDirectTermOccurenceCount(long directTermId) throws IOException {
    String termStr = directTerms.get(directTermId);
    // we need to sum up all the counts for this term in the inverted index
    long count = 0;
    IndexIterator idxItr = invertedIndex.documents(termStr);
    long docId = idxItr.nextDocument();
    while(docId != IndexIterator.END_OF_LIST) {
      count += idxItr.count();
      docId = idxItr.nextDocument();
    }
    return count;
  }
  
  /**
   * Returns the number of batches in this atomic index.
   * @return
   */
  public int getBatchCount() {
    return batches.size();
  }
}
