/*
 *  QueryEngine.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 04 Mar 2009
 *  
 *  $Id: QueryEngine.java 17261 2014-01-30 14:05:14Z valyt $
 */
package gate.mimir.search;

import gate.LanguageAnalyser;
import gate.mimir.DocumentMetadataHelper;
import gate.mimir.DocumentRenderer;
import gate.mimir.IndexConfig;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.mimir.MimirIndex;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.AtomicAnnotationIndex;
import gate.mimir.index.AtomicTokenIndex;
import gate.mimir.index.DocumentData;
import gate.mimir.index.IndexException;
import gate.mimir.search.query.AnnotationQuery;
import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.parser.ParseException;
import gate.mimir.search.query.parser.QueryParser;
import gate.mimir.search.score.MimirScorer;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents the entry point to the Mimir search API.
 */
public class QueryEngine {
  
  
  /**
   * Represents the type of index that should be searched. Mimir uses two types
   * of indexes: token indexes (which index the text input) and annotation
   * indexes (which index semantic annotations).
   */
  public static enum IndexType{
    /**
     * Value representing token indexes, used for the document text.
     */
    TOKENS,
    
    /**
     * Value representing annotation indexes, used for the document semantic
     * annotations.
     */
    ANNOTATIONS
  }
  
  /**
   * The maximum size of an index that can be loaded in memory (by default 64
   * MB).
   */
  public static final long MAX_IN_MEMORY_INDEX = 64 * 1024 * 1024;
  
  /**
   * The default value for the document block size.
   * @see #setDocumentBlockSize(int)
   */
  public static final int DEFAULT_DOCUMENT_BLOCK_SIZE = 1000;

  /**
   * The index being searched.
   */
  protected final MimirIndex index;

  /**
   * The index configuration this index was built from.
   */
  protected IndexConfig indexConfig;

  /**
   * Should sub-bindings be generated when searching?
   */
  protected boolean subBindingsEnabled;

  /**
   * A callable that produces new {@link MimirScorer} instances on request. 
   */
  protected Callable<MimirScorer> scorerSource;
  
  protected static final Logger logger = LoggerFactory.getLogger(QueryEngine.class);

  /**
   * The tokeniser (technically any GATE LA) used to split the text segments
   * found in queries into individual tokens. The same tokeniser used to create
   * the indexed documents should be used here. If this value is not set, then a
   * default ANNIE tokeniser will be used.
   */
  protected LanguageAnalyser queryTokeniser;

  /**
   * The executor used to run tasks for query execution. If the value is not
   * set, then new threads are created as needed.
   */
  protected Executor executor;

  /**
   * How many documents get ranked in one ranking stage.
   */
  private int documentBlockSize = DEFAULT_DOCUMENT_BLOCK_SIZE;
  
  /**
   * A list of currently active QueryRunners. This is used to close all active 
   * runners when the query engine itself is closed (thus releasing all open 
   * files).
   */
  private List<QueryRunner> activeQueryRunners;

  /**
   * Are sub-bindings used in this query engine. Sub-bindings are used to
   * associate sub-queries with segments of the returned hits. This can be
   * useful for showing high-level details about the returned hits. By default,
   * sub-bindings are not used.
   * 
   * @return the subBindingsEnabled
   */
  public boolean isSubBindingsEnabled() {
    return subBindingsEnabled;
  }

  /**
   * @param subBindingsEnabled
   *          the subBindingsEnabled to set
   */
  public void setSubBindingsEnabled(boolean subBindingsEnabled) {
    this.subBindingsEnabled = subBindingsEnabled;
  }

  /**
   * Gets the configuration parameter specifying the number of documents that 
   * get processed as a block. This is used to optimise the search 
   * process by limiting the number of results that get calculated by default.
   * @return
   */
  public int getDocumentBlockSize() {
    return documentBlockSize;
  }
  
  /**
   * Sets the configuration parameter specifying the number of documents that 
   * get processed in one go (e.g. the number of documents that get ranked when
   * enumerating results). This is used to optimise the search 
   * process by limiting the number of results that get calculated by default.
   * Defaults to {@link #DEFAULT_DOCUMENT_BLOCK_SIZE}.
   * @param documentBlockSize
   */
  public void setDocumentBlockSize(int documentBlockSize) {
    this.documentBlockSize = documentBlockSize;
  }

  /**
   * Gets the current source of scorers.
   * @see #setScorerSource(Callable)
   * @return
   */
  public Callable<MimirScorer> getScorerSource() {
    return scorerSource;
  }

  /**
   * Provides a {@link Callable} that the Query Engine can use for obtaining
   * new instances of {@link MimirScorer} to be used for ranking new queries.
   * @param scorerSource
   */
  public void setScorerSource(Callable<MimirScorer> scorerSource) {
    this.scorerSource = scorerSource;
  }

  /**
   * Gets the executor used by this query engine.
   * 
   * @return an executor that can be used for running tasks pertinent to this
   *         QueryEngine.
   */
  public Executor getExecutor() {
    return executor;
  }

  /**
   * Sets the {@link Executor} used for executing tasks required for running
   * queries. This allows the use of some type thread pooling, is needed. If
   * this value is not set, then new threads are created as required.
   * 
   * @param executor
   */
  public void setExecutor(Executor executor) {
    this.executor = executor;
  }

  /**
   * Sets the tokeniser (technically any GATE analyser) used to split the text
   * segments found in queries into individual tokens. The same tokeniser used
   * to create the indexed documents should be used here. If this value is not
   * set, then a default ANNIE tokeniser will be used.
   * 
   * @param queryTokeniser
   *          the new tokeniser to be used for parsing queries.
   */
  public void setQueryTokeniser(LanguageAnalyser queryTokeniser) {
    this.queryTokeniser = queryTokeniser;
  }

  /**
   * Finds the location for a given sub-index in the arrays returned by 
   * {@link #getIndexes()} and {@link #getDirectIndexes()}.
   * @param indexType the IndexType of the requested sub-index (tokens or 
   * annotations).
   * @param indexName the &quot;name&quot; of the requested sub-index (the 
   * indexed feature name for {@link IndexType#TOKENS} indexes, or the 
   * annotation type in the case of {@link IndexType#ANNOTATIONS} indexes). 
   * @return the position in the indexes array for the requested index, or -1 if
   * the requested index does not exist.
   */
  public int getSubIndexPosition(IndexType indexType, String indexName) {
    if(indexType == IndexType.TOKENS) {
      for(int i = 0; i < indexConfig.getTokenIndexers().length; i++) {
        if(indexConfig.getTokenIndexers()[i].getFeatureName().equals(indexName)) {
          return i; 
        }
      }
      return -1;
    } else if(indexType == IndexType.ANNOTATIONS) {
      for(int i = 0; i < indexConfig.getSemanticIndexers().length; i++) {
        for(String aType : 
            indexConfig.getSemanticIndexers()[i].getAnnotationTypes()) {
          if(aType.equals(indexName)) { 
            return indexConfig.getTokenIndexers().length + i; 
          }
        }
      }      
      return -1;
    } else {
      throw new IllegalArgumentException(
        "Don't understand sub-indexes of type " + indexType);
    }
  }

  /**
   * Returns the index that stores the data for a particular feature of token
   * annotations.
   * 
   * @param featureName
   * @return
   */
  public AtomicTokenIndex getTokenIndex(String featureName) {
    return index.getTokenIndex(featureName);
  }
  
  /**
   * Returns the index that stores the data for a particular semantic annotation
   * type.
   * 
   * @param annotationType
   * @return
   */
  public AtomicAnnotationIndex getAnnotationIndex(String annotationType) {
    return index.getAnnotationIndex(annotationType);
  }
  
  public SemanticAnnotationHelper getAnnotationHelper(String annotationType) {
    for(int i = 0; i < indexConfig.getSemanticIndexers().length; i++) {
      String[] annTypes = indexConfig.getSemanticIndexers()[i]
          .getAnnotationTypes(); 
      for(int j = 0; j < annTypes.length; j++) {
        if(annTypes[j].equals(annotationType)) {
          return indexConfig.getSemanticIndexers()[i].getHelpers()[j];
        }
      }
    }
    return null;
  }
  
  
  /**
   * Gets the index this query engine is searching.
   * @return
   */
  public MimirIndex getIndex() {
    return index;
  }

  /**
   * @return the index configuration for this index
   */
  public IndexConfig getIndexConfig() {
    return indexConfig;
  }

  
  
  /**
   * Constructs a new query engine for a {@link MimirIndex}.
   * @param index the index to be searched.
   */
  public QueryEngine(MimirIndex index) {
    this.index = index;
    this.indexConfig = index.getIndexConfig();
    activeQueryRunners = Collections.synchronizedList(
        new ArrayList<QueryRunner>());
    subBindingsEnabled = false;
  }

//  /**
//   * Constructs a new {@link QueryEngine} for a specified Mimir index. The mimir
//   * semantic repository will be initialized using the default location in the
//   * filesystem, provided by the IndexConfig
//   * 
//   * @param indexDir
//   *          the directory containing an index.
//   * @throws IndexException
//   *           if there are problems while opening the indexes.
//   */
//  public QueryEngine(File indexDir) throws gate.mimir.index.IndexException {
//    // read the index config
//    try {
//      indexConfig =
//        IndexConfig.readConfigFromFile(new File(indexDir,
//                Indexer.INDEX_CONFIG_FILENAME), indexDir);
//      initMG4J();
//      // initialise the semantic indexers
//      if(indexConfig.getSemanticIndexers() != null && 
//              indexConfig.getSemanticIndexers().length > 0) {
//        for(SemanticIndexerConfig sic : indexConfig.getSemanticIndexers()){
//          for(SemanticAnnotationHelper sah : sic.getHelpers()){
//            sah.init(this);
//            if(sah.getMode() == SemanticAnnotationHelper.Mode.DOCUMENT &&
//                documentSizes == null) {
//              // we need to load the document sizes from a token index
//              documentSizes = getIndexes()[0].getIndex().sizes;
//            }            
//          }
//        }
//      }
//      
//      
//      activeQueryRunners = Collections.synchronizedList(
//              new ArrayList<QueryRunner>());
//    } catch(FileNotFoundException e) {
//      throw new IndexException("File not found!", e);
//    } catch(IOException e) {
//      throw new IndexException("Input/output exception!", e);
//    }
//    subBindingsEnabled = false;
//
//  }

  /**
   * Get the {@link SemanticAnnotationHelper} corresponding to a query's
   * annotation type.
   * @throws IllegalArgumentException if the annotation helper for this
   *         type cannot be found.
   */
  public SemanticAnnotationHelper getAnnotationHelper(AnnotationQuery query) {
    for(SemanticIndexerConfig semConfig : indexConfig.getSemanticIndexers()){
      for(int i = 0; i < semConfig.getAnnotationTypes().length; i++){
        if(query.getAnnotationType().equals(
                semConfig.getAnnotationTypes()[i])){
          return semConfig.getHelpers()[i];
        }
      }
    }
    throw new IllegalArgumentException("Semantic annotation type \""
            + query.getAnnotationType() + "\" not known to this query engine.");
  }
  
  
  /**
   * Obtains a query executor for a given {@link QueryNode}.
   * 
   * @param query
   *          the query to be executed.
   * @return a {@link QueryExecutor} for the provided query, running over the
   *         indexes in this query engine.
   * @throws IOException
   *           if the index files cannot be accessed.
   */
  public QueryRunner getQueryRunner(QueryNode query) throws IOException {
    logger.info("Executing query: " + query.toString());
    QueryExecutor qExecutor = query.getQueryExecutor(this);
    QueryRunner qRunner;
    MimirScorer scorer = null;
    try {
      scorer = scorerSource == null ? null : scorerSource.call();
    } catch(Exception e) {
      logger.error("Could not obtain a scorer. Running query unranked.", e);
    }
    qRunner = new RankingQueryRunnerImpl(qExecutor, scorer);
    activeQueryRunners.add(qRunner);
    return qRunner;
  }
  
  /**
   * Notifies the QueryEngine that the given QueryRunner has been closed. 
   * @param qRunner
   */
  public void releaseQueryRunner(QueryRunner qRunner) {
    activeQueryRunners.remove(qRunner);
  }

  /**
   * Obtains a query executor for a given query, expressed as a String.
   * 
   * @param query
   *          the query to be executed.
   * @return a {@link QueryExecutor} for the provided query, running over the
   *         indexes in this query engine.
   * @throws IOException
   *           if the index files cannot be accessed.
   * @throws ParseException
   *           if the string provided for the query cannot be parsed.
   */
  public QueryRunner getQueryRunner(String query) throws IOException,
  ParseException {
    logger.info("Executing query: " + query.toString());
    QueryNode qNode =
      (queryTokeniser == null) ? QueryParser.parse(query) : QueryParser
              .parse(query, queryTokeniser);
      return getQueryRunner(qNode);
  }

  /**
   * Obtains the document text for a given search hit.
   * 
   * @param hit
   *          the search hit for which the text is sought.
   * @param leftContext
   *          the number of tokens to the left of the hit to be included in the
   *          result.
   * @param rightContext
   *          the number of tokens to the right of the hit to be included in the
   *          result.
   * @return an array of arrays of {@link String}s, representing the tokens and
   *         spaces at the location of the search hit. The first element of the
   *         array is an array of tokens, the second element contains the
   *         spaces.The first element of each array corresponds to the first
   *         token of the left context.
   * @throws IOException
   */
  public String[][] getHitText(Binding hit, int leftContext, int rightContext)
  throws IndexException {
    return getText(hit.getDocumentId(), hit.getTermPosition() - leftContext,
            leftContext + hit.getLength() + rightContext);
  }

  /**
   * Gets the text covered by a given binding.
   * 
   * @param hit
   *          the binding.
   * @return an array of two string arrays, the first representing the tokens
   *         covered by the binding and the second the spaces after each token.
   * @throws IOException
   */
  public String[][] getHitText(Binding hit) throws IndexException {
    return getText(hit.getDocumentId(), hit.getTermPosition(), hit.getLength());
  }

  /**
   * Get the text to the left of the given binding.
   * 
   * @param hit
   *          the binding.
   * @param numTokens
   *          the maximum number of tokens of context to return. The actual
   *          number of tokens returned may be smaller than this if the hit
   *          starts within <code>numTokens</code> tokens of the start of the
   *          document.
   * @return an array of two string arrays, the first representing the tokens
   *         before the binding and the second the spaces after each token.
   * @throws IOException
   */
  public String[][] getLeftContext(Binding hit, int numTokens)
  throws IndexException {
    int startOffset = hit.getTermPosition() - numTokens;
    // if numTokens is greater than the start offset of the hit
    // then we need to return all the document text up to the
    // token before the hit position (possibly no tokens...)
    if(startOffset < 0) {
      numTokens += startOffset; // startOffset is negative, so this will
      // subtract from numTokens
      startOffset = 0;
    }
    return getText(hit.getDocumentId(), startOffset, numTokens);
  }

  /**
   * Get the text to the right of the given binding.
   * 
   * @param hit
   *          the binding.
   * @param numTokens
   *          the maximum number of tokens of context to return. The actual
   *          number of tokens returned may be smaller than this if the hit ends
   *          within <code>numTokens</code> tokens of the end of the document.
   * @return an array of two string arrays, the first representing the tokens
   *         after the binding and the second the spaces after each token.
   * @throws IOException
   */
  public String[][] getRightContext(Binding hit, int numTokens)
  throws IndexException {
    DocumentData docData;
    try {
      docData = index.getDocumentData(hit.getDocumentId());
    } catch(IOException e) {
      throw new IndexException(e);
    }
    int startOffset = hit.getTermPosition() + hit.getLength();
    if(startOffset >= docData.getTokens().length) {
      // hit is at the end of the document
      return new String[][]{new String[0], new String[0]};
    }
    if(startOffset + numTokens > docData.getTokens().length) {
      // fewer than numTokens tokens of right context available, adjust
      numTokens = docData.getTokens().length - startOffset;
    }
    return getText(hit.getDocumentId(), startOffset, numTokens);
  }

  /**
   * Obtains the text for a specified region of a document. The return value is
   * a pair of parallel arrays, one of tokens and the other of the spaces
   * between them. If <code>length >= 0</code>, the two parallel arrays will
   * always be exactly <code>length</code> items long, but any token positions
   * that do not exist in the document (i.e. before the start or beyond the end
   * of the text) will be <code>null</code>. If <code>length &lt; 0</code> the
   * arrays will be of sufficient length to hold all the tokens from
   * <code>termPosition</code> to the end of the document, with no trailing
   * <code>null</code>s (there may be leading <code>null</code>s if
   * <code>termPosition &lt; 0</code>).
   * 
   * @param documentID
   *          the document ID
   * @param termPosition
   *          the position of the first term required
   * @param length
   *          the number of terms to return. May be negativem, in which case all
   *          terms from termPosition to the end of the document will be
   *          returned.
   * @return an array of two string arrays. The first represents the tokens and
   *         the second represents the spaces between them
   * @throws IndexException
   */
  public String[][] getText(long documentID, int termPosition, int length)
  throws IndexException {
    try {
      return index.getDocumentData(documentID).getText(termPosition, length);
    } catch(IOException e) {
      throw new IndexException(e); 
    }
  }

  /**
   * Renders a document and a list of hits.
   * 
   * @param docID
   *          the document to be rendered.
   * @param hits
   *          the list of hits to be rendered.
   * @param output
   *          the {@link Appendable} used to write the output.
   * @throws IOException
   *           if the output cannot be written to.
   * @throws IndexException
   *           if no document renderer is available.
   */
  public void renderDocument(long docID, List<Binding> hits, Appendable output)
  throws IOException, IndexException {
    DocumentRenderer docRenderer = indexConfig.getDocumentRenderer();
    if(docRenderer == null) { throw new IndexException(
    "No document renderer is configured for this index!"); }
    docRenderer.render(index.getDocumentData(docID), hits, output);
  }

  public String getDocumentTitle(long docID) throws IndexException {
    try {
      return index.getDocumentData(docID).getDocumentTitle();
    } catch(IOException e) {
      throw new IndexException(e);
    }
  }

  public String getDocumentURI(long docID) throws IndexException {
    try {
      return index.getDocumentData(docID).getDocumentURI();
    } catch(IOException e) {
      throw new IndexException(e);
    }
  }

  /**
   * Obtains an arbitrary document metadata field from the stored document data.
   * {@link DocumentMetadataHelper}s used at indexing time can add arbitrary 
   * {@link Serializable} values as metadata fields for the documents being
   * indexed. This method is used at search time to retrieve those values. 
   *  
   * @param docID the ID of document for which the metadata is sought.
   * @param fieldName the name of the metadata filed to be obtained
   * @return the de-serialised value stored at indexing time for the given 
   * field name and document.
   * @throws IndexException
   */
  public Serializable getDocumentMetadataField(long docID, String fieldName) 
      throws IndexException {
    try {
      return index.getDocumentData(docID).getMetadataField(fieldName);
    } catch(IOException e) {
      throw new IndexException(e);
    }
  }
  


  /**
   * Closes this {@link QueryEngine} and releases all resources.
   */
  public void close() {
    // close all active query runners
    List<QueryRunner> runnersCopy = new ArrayList<QueryRunner>(activeQueryRunners);
    for(QueryRunner aRunner : runnersCopy) {
      try {
        logger.debug("Closing query runner: " + aRunner.toString());
        aRunner.close();
      } catch(IOException e) {
        // log and ignore
        logger.error("Exception while closing query runner.", e);
      }
    }
  }

}
