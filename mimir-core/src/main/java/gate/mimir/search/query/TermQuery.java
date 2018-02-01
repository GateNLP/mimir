/*
 *  TermQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 03 Mar 2009
 *  
 *  $Id: TermQuery.java 20208 2017-04-19 08:35:28Z domrout $
 */

package gate.mimir.search.query;

import gate.mimir.IndexConfig;
import gate.mimir.index.AtomicIndex;
import gate.mimir.search.IndexReaderPool;
import gate.mimir.search.QueryEngine;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.lang.MutableString;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.index.IndexIterator;
import it.unimi.di.big.mg4j.index.IndexIterators;
import it.unimi.di.big.mg4j.index.IndexReader;
import it.unimi.di.big.mg4j.index.payload.Payload;
import it.unimi.di.big.mg4j.search.DocumentIterator;
import it.unimi.di.big.mg4j.search.IntervalIterator;
import it.unimi.di.big.mg4j.search.visitor.DocumentIteratorVisitor;

import java.io.IOException;

import static gate.mimir.search.QueryEngine.IndexType;

/**
 * A {@link QueryNode} for term queries. A term query consists of an index name 
 * and a query term. 
 */
public class TermQuery implements QueryNode {

  private static final long serialVersionUID = 7302348587893649887L;

  /**
   * The query term
   */
  private String term;
  
  /**
   * The term ID for this query. If not known, 
   * {@link DocumentIterator#END_OF_LIST} is used.
   */
  private long termId = DocumentIterator.END_OF_LIST;
  
  
  /**
   * The name of the index to search.
   */
  private String indexName;
  
  /**
   * The type of the index to be searched.
   */
  private IndexType indexType;
  
  /**
   * The length of the matches. Defaults to <code>1</code>.
   */
  private int length;
  
  
  /**
   * A {@link QueryExecutor} for {@link TermQuery} nodes.
   */
  public static class TermQueryExecutor extends AbstractQueryExecutor implements IndexIterator{

    
    /**
     * The {@link TermQuery} node being executed.
     */
    private TermQuery query;
    
    /**
     * A local reference to the {@link IndexReaderPool} from the 
     * {@link QueryEngine}.
     */
    private AtomicIndex atomicIndex;
    
    /**
     * The {@link IndexReader} from the {@link #atomicIndex}.
     */
    private IndexReader indexReader;
    
    /**
     * The index iterator used to obtain hits. 
     */
    private IndexIterator indexIterator;
    
    /**
     * The positions iterator for the latest document.
     */
    private IntIterator positionsIterator;
    
    
    /**
     * @param node
     * @param invertedIndex
     * @throws IOException if the index files cannot be accessed.
     */
    public TermQueryExecutor(TermQuery node, QueryEngine engine) throws IOException {
      super(engine, node);
      this.query = node;
      atomicIndex = query.getIndex(engine);

      if(atomicIndex == null) throw new IllegalArgumentException(
              "No index provided for field " + node.getIndexName() + "!");
      Index mg4jIndex = atomicIndex.getIndex();
      if(mg4jIndex != null) {
        indexReader = mg4jIndex.getReader();      
        // if we have the term ID, use that
        if(query.termId != DocumentIterator.END_OF_LIST) {
          this.indexIterator = indexReader.documents(query.termId);
          // set the term (used by rankers)
          MutableString mutableString = new MutableString(query.getTerm());
          atomicIndex.getIndex().termProcessor.processTerm(mutableString);
          this.indexIterator.term(mutableString);
        } else {
          //use the term processor for the query term
          MutableString mutableString = new MutableString(query.getTerm());
          atomicIndex.getIndex().termProcessor.processTerm(mutableString);
          this.indexIterator = indexReader.documents(mutableString.toString());        
        }        
      } else {
        // the atomic index is empty: we have exhausted the search already
        latestDocument = -1;
      }

      positionsIterator = null;
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextDocument()
     */
    public long nextDocument(long from) throws IOException {
      if(closed) return latestDocument = -1;
      if(latestDocument == -1){
        //we have exhausted the search already
        return latestDocument;
      }
      
      if (from >= latestDocument){
        //we do need to skip
        latestDocument = indexIterator.skipTo(from + 1);
      }else{
        //from is lower than latest document, 
        //so we just return the next document
        latestDocument = indexIterator.nextDocument();
      }
      if(latestDocument == DocumentIterator.END_OF_LIST){
        //no more documents available
        latestDocument = -1;
      } else {
        positionsIterator = IndexIterators.positionIterator(indexIterator);
      }
      return latestDocument;
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextHit(java.util.Map)
     */
    public Binding nextHit() throws IOException{
      if(closed) return null;
      if(positionsIterator == null) positionsIterator = 
          IndexIterators.positionIterator(indexIterator);
      if(latestDocument >= 0 && positionsIterator.hasNext()){
        int position = positionsIterator.nextInt();
        return new Binding(query, latestDocument, position, query.length, null);
      }else{
        //no more positions, or no more documents
        return null;
      }
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#close()
     */
    public void close() throws IOException {
      if(closed) return;
      super.close();
      indexIterator = null;
      if(indexReader != null) indexReader.close();
//      atomicIndex.returnReader(indexReader);
    }

    
    /* (non-Javadoc)
     * @see it.unimi.di.big.mg4j.index.IndexIterator#nextPosition()
     */
    @Override
    public int nextPosition() throws IOException {
      // TODO Auto-generated method stub
      throw new UnsupportedOperationException("Method not implemented!");
    }

    public boolean hasNext() {
      throw new UnsupportedOperationException("Method not implemented!");
    }

    public Integer next() {
      throw new UnsupportedOperationException("Method not implemented!");
    }

    public void remove() {
      throw new UnsupportedOperationException("Method not implemented!");
    }

    public Index index() {
      return indexIterator.index();
    }

    public IntervalIterator intervalIterator() throws IOException {
      return indexIterator.intervalIterator();
    }

    public long frequency() throws IOException {
      return indexIterator.frequency();
    }

    public IntervalIterator intervalIterator(Index index) throws IOException {
      return indexIterator.intervalIterator(index);
    }

    public Payload payload() throws IOException {
      return indexIterator.payload();
    }

    public int count() throws IOException {
      return indexIterator.count();
    }

    public Reference2ReferenceMap<Index, IntervalIterator> intervalIterators()
      throws IOException {
      return indexIterator.intervalIterators();
    }

    public ReferenceSet<Index> indices() {
      return indexIterator.indices();
    }

    public IndexIterator id(int id) {
      return indexIterator.id(id);
    }

    public long nextDocument() throws IOException {
      return indexIterator.nextDocument();
    }

    public int id() {
      return indexIterator.id();
    }

    public long document() {
      return indexIterator.document();
    }

    public <T> T accept(DocumentIteratorVisitor<T> visitor) throws IOException {
      return indexIterator.accept(visitor);
    }

    public <T> T acceptOnTruePaths(DocumentIteratorVisitor<T> visitor)
      throws IOException {
      return indexIterator.acceptOnTruePaths(visitor);
    }

    public void dispose() throws IOException {
      indexIterator.dispose();
    }

    public long termNumber() {
      return indexIterator.termNumber();
    }

    public String term() {
      return indexIterator.term();
    }

    public IndexIterator term(CharSequence term) {
      return indexIterator.term(term);
    }

    public IndexIterator weight(double weight) {
      return indexIterator.weight(weight);
    }

    public long skipTo(long n) throws IOException {
      return indexIterator.skipTo(n);
    }

    public double weight() {
      return indexIterator.weight();
    }
    
  }
  
  /**
   * @return the term
   */
  public CharSequence getTerm() {
    return term;
  }
  
  /**
   * @return the termId
   */
  public long getTermId() {
    return termId;
  }

  /**
   * @return the indexName
   */
  public String getIndexName() {
    return indexName;
  }
  
  /**
   * Gets the index for this query in a given {@link QueryEngine}.
   * @param engine
   * @return
   */
  public AtomicIndex getIndex(QueryEngine engine) {
    switch(this.indexType){
      case TOKENS:
        return engine.getTokenIndex(indexName);
      case ANNOTATIONS:
        return engine.getAnnotationIndex(indexName);
      default:
        throw new IllegalArgumentException("Indexes of type " + 
                indexType + " are not supported!"); 
    }
  }
  
  
  /**
   * Creates a new term query, for searching over the document text. 
   * 
   * @param indexName the name of the index to be searched. This should be one
   * of the annotation feature names used for indexing tokens (see 
   * {@link IndexConfig.TokenIndexerConfig}).
   * 
   * @param term the term to be searched for.
   * 
   * @see IndexConfig.TokenIndexerConfig
   */
  public TermQuery(String indexName, String term) {
    this(IndexType.TOKENS, indexName, term, 1);
  }
  
  /**
   * Creates a new term query, for searching over the document text. 
   * 
   * @param indexName the name of the index to be searched. This should be one
   * of the annotation feature names used for indexing tokens (see 
   * {@link IndexConfig.TokenIndexerConfig}).
   * 
   * @param termId the term ID for the term to be searched for.
   * 
   * @see IndexConfig.TokenIndexerConfig
   */
  public TermQuery(String indexName, String term, long termId) {
    this(IndexType.TOKENS, indexName, term, termId, 1);
  }
  
  /**
   * Creates a new term query, for searching over semantic annotations.
   *   
   * @param annotationType the type of annotation sought. This should one of the 
   * annotation types used when indexing semantic annotations (see 
   * {@link IndexConfig.SemanticIndexerConfig}).
   * 
   * @param mentionURI the URI of the mention sought.
   * 
   * @param length the length of the mention sought.
   */
  public TermQuery(String annotationType, String mentionURI, int length) {
    this(IndexType.ANNOTATIONS, annotationType, mentionURI, length);
  }
  
  /**
   * Creates a new term query, for searching over semantic annotations.
   *   
   * @param annotationType the type of annotation sought. This should one of the 
   * annotation types used when indexing semantic annotations (see 
   * {@link IndexConfig.SemanticIndexerConfig}).
   * 
   * @param mentionTermid the term ID for the mentionURI sought.
   * 
   * @param length the length of the mention sought.
   */
  public TermQuery(String annotationType, String term, long mentionTermid, int length) {
    this(IndexType.ANNOTATIONS, annotationType, term, mentionTermid, length);
  }  
  
  /**
   * Creates a new term query. This constructor is part of a low-level API. see 
   * the other constructors of this class, which may be more suitable!
   *   
   * @param indexType The type of index to be searched.
   * 
   * @param indexName the name of the index to be searched. If the indexType is
   * {@link IndexType#TOKENS}, then the name is interpreted as the feature name 
   * for the document tokens, if the indexType is {@link IndexType#ANNOTATIONS}, 
   * then the name is interpreted as annotation type.
   * 
   * @param term the term to be searched for.
   * 
   * @param length the length of the hits (useful in the case of annotation 
   * indexes, where the length of each mention is stored external to the actual 
   * index).
   */
  public TermQuery(IndexType indexType, String indexName, String term, int length) {
    this.indexType = indexType;
    this.indexName = indexName;
    this.term = term;
    this.length = length;
  }
  
  /**
   * Creates a new term query. This constructor is part of a low-level API. see 
   * the other constructors of this class, which may be more suitable!
   *   
   * @param indexType The type of index to be searched.
   * 
   * @param indexName the name of the index to be searched. If the indexType is
   * {@link IndexType#TOKENS}, then the name is interpreted as the feature name 
   * for the document tokens, if the indexType is {@link IndexType#ANNOTATIONS}, 
   * then the name is interpreted as annotation type.
   * 
   * @param length the length of the hits (useful in the case of annotation 
   * indexes, where the length of each mention is stored external to the actual 
   * index).
   * 
   * @param termId the term ID for sought term.
   */
  public TermQuery(IndexType indexType, String indexName, String term, long termId, int length) {
    this.indexType = indexType;
    this.indexName = indexName;
    this.termId = termId;
    this.term = term;
    this.length = length;
  }
  
  
  
  /**
   * Gets a new query executor for this {@link TermQuery}.
   * @param indexes the set of indexes running on.
   * @return an appropriate {@link QueryExecutor} (in this case, an instance of
   * {@link TermQueryExecutor}).
   * @throws IOException if the index files cannot be accessed.
   * @throws IllegalArgumentException if the provided set of indexes does not
   * include an index for this query's {@link #indexName}.
   * @see gate.mimir.search.query.QueryNode#getQueryExecutor(java.util.Map)
   */
  public QueryExecutor getQueryExecutor(QueryEngine engine) throws IOException {
    return new TermQueryExecutor(this, engine);
  }
  
  public String toString() {
    return "TERM(" + 
        (indexName == null ? "" : indexName) + 
        ":" + term + ")";
  }

  public IndexType getIndexType() {
    return indexType;
  }

  public int getLength() {
    return length;
  }
}
