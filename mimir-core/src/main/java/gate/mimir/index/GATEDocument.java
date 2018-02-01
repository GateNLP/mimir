/*
 *  GATEDocument.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 24 Feb 2009
 *
 *  $Id: GATEDocument.java 17307 2014-02-14 11:47:27Z valyt $
 */
package gate.mimir.index;

import gate.Annotation;
import gate.AnnotationSet;
import gate.mimir.IndexConfig;
import gate.util.OffsetComparator;
import it.unimi.dsi.io.WordReader;
import it.unimi.dsi.lang.MutableString;
import it.unimi.di.big.mg4j.document.Document;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;


/**
 * An implementation of MG4J Document interface for representing GATE documents
 * during the indexing process.
 */
public class GATEDocument implements Document {
  /**
   * The URI prefix used for generating document URIs, when no explicit URI is
   * provided as a document feature.
   * The actual URIs will comprise this value with a number appended, generated
   * by {@link #documentID}++. 
   */
  private static final String DOCUMENT_URI_PREFIX = "urn:mimir:document:";

  /**
   * A reader used to satisfy the MG4J interfaces, but that provides no actual
   * data.
   */
  private static final Reader emptyReader = new StringReader("");

  
  private static Logger logger = Logger.getLogger(GATEDocument.class);
  
  /**
   * Used to generate unique document URIs, if no URIs are provided as document 
   * features.
   */
  private static long documentID = 0;
  
  /**
   * The number of occurrences (in all sub-indexes) generated as a result of 
   * indexing this document.
   */
  private long occurrences = 0;
  
  /**
   * An MG4J word reader for this document.
   */
  private class GATEDocumentWordReader implements WordReader{
    /**
     * the index of the next token
     */
    private int index = 0;
    
    /**
     * The token feature from which the data is read. 
     */
    private String tokenFeature;
    
    /**
     * Constructs a GATE Document reader.
     * @param tokens an array of token annotations, sorted by offset.  
     * @param nonTokens an array of string, representing the non-tokens (the 
     * document content between tokens). 
     * @param tokenFeature the name of the feature to be read from the token 
     * annotations.
     */
    public GATEDocumentWordReader(String tokenFeature){
      this.tokenFeature = tokenFeature;
    }
    
    /* (non-Javadoc)
     * @see it.unimi.dsi.io.WordReader#copy()
     */
    public WordReader copy() {
      return this;
    }

    /* (non-Javadoc)
     * @see it.unimi.dsi.io.WordReader#next(it.unimi.dsi.lang.MutableString, it.unimi.dsi.lang.MutableString)
     */
    public boolean next(MutableString word, MutableString nonWord)
            throws IOException {
      if(index < tokenAnnots.length){
        word.replace((String)tokenAnnots[index].getFeatures().get(tokenFeature));
        nonWord.replace(nonTokens[index]);
        index++;
        return true;
      }else{
        return false;  
      }
    }

    /* (non-Javadoc)
     * @see it.unimi.dsi.io.WordReader#setReader(java.io.Reader)
     */
    public WordReader setReader(Reader reader) {
      if(reader != emptyReader) 
        throw new UnsupportedOperationException(getClass().getName() + 
              " does not support resetting!");
      return this;
    }
    
  }
  
  /**
   * The index config for this document
   */
  private IndexConfig indexConfig;
  
  /**
   * The queue where this document should add itself upon closing.
   */
  private BlockingQueue<GATEDocument> outputQueue;
  
  /**
   * The GATE Document wrapped by this object.
   */
  private gate.Document gateDocument;
  
  /**
   * A list of all the token annotations, sorted by offset. 
   */
  private Annotation[] tokenAnnots;
  
  /**
   * A list containing all the strings between tokens.
   */
  private String[] nonTokens;
  
  /**
   * A special instance of GATEDocument used to mark the end of a queue.
   */
  public static final GATEDocument END_OF_QUEUE = new GATEDocument();
  
  /**
   * Private constructor used to create the {@link #END_OF_QUEUE} instance.
   */
  protected GATEDocument(){
  }
  
  public GATEDocument(gate.Document gateDocument,
          IndexConfig indexConfig){
    this.gateDocument = gateDocument;
    this.indexConfig = indexConfig;
    
    //build the list of tokens
    AnnotationSet tokenSet = indexConfig.getTokenAnnotationSetName() == null?
            gateDocument.getAnnotations() :
            gateDocument.getAnnotations(indexConfig.getTokenAnnotationSetName());  
    AnnotationSet allTokens = null;
    if(tokenSet != null) {
      synchronized(tokenSet) {
        allTokens = tokenSet.get(indexConfig
                        .getTokenAnnotationType());
      }
    }
    if(allTokens != null && allTokens.size() > 0){
      //we have some tokens
      tokenAnnots = allTokens.toArray(new Annotation[allTokens.size()]);
      Arrays.sort(tokenAnnots, new OffsetComparator());
    }else{
      //no tokens
      tokenAnnots = new Annotation[0];
    }
    //build the list of non-tokens
    nonTokens = new String[tokenAnnots.length];
    String docContent = gateDocument.getContent().toString();
    //for each token, add the doc content after it (and before the next token)
    //to the nonTokens array. 
    for(int i = 0; i < tokenAnnots.length - 1; i++){
      int nonTokenStart = tokenAnnots[i].getEndNode().getOffset().intValue();
      int nonTokenEnd = tokenAnnots[i+1].getStartNode().getOffset().intValue();
      nonTokens[i] = (nonTokenStart < nonTokenEnd) ?
              docContent.substring(nonTokenStart, nonTokenEnd) : "";
    }
    //set the last value to all remaining document content, if we have any tokens
    if(tokenAnnots.length > 0){
      int nonTokenStart = tokenAnnots[tokenAnnots.length - 1].getEndNode().
          getOffset().intValue();
      nonTokens[nonTokens.length -1] = (nonTokenStart < docContent.length()) ?
              docContent.substring(nonTokenStart) : "";
    }
  }
  
  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.Document#close()
   */
  public void close() throws IOException {
    // put the finished document in the output queue 
    try {
      outputQueue.put(this);
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  
  /**
   * Sets the output queue for this document. When the {@link #close()} method
   * is called, this document will add itself to the output queue.  
   * @param outputQueue the outputQueue to set
   */
  public void setOutputQueue(BlockingQueue<GATEDocument> outputQueue) {
    this.outputQueue = outputQueue;
  }

  
  /**
   * Obtains the GATE document wrapped by this object.
   * @return the gateDocument
   */
  public gate.Document getDocument() {
    return gateDocument;
  }

  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.Document#content(int)
   */
  public Object content(int field) throws IOException {
    return emptyReader;
  }

  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.Document#title()
   */
  public CharSequence title() {
    return gateDocument.getName();
  }

  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.Document#uri()
   */
  public synchronized CharSequence uri() {
    String uri = (String)gateDocument.getFeatures().get(
            indexConfig.getDocumentUriFeatureName());
    if(uri == null){
      uri = DOCUMENT_URI_PREFIX + documentID;
      logger.warn(
        "No document URI provided, generating a default one: " + documentID);
      documentID++;
      gateDocument.getFeatures().put(
              indexConfig.getDocumentUriFeatureName(), uri);
    }
    return uri;
  }

  /**
   * Notifies this GATEDocument that some more index occurrences were produced
   * in the process of indexing it.
   * 
   * This method is synchronized because the same GATEDocument instance is being
   * indexed in parallel by multiple sub-indexers.
   *  
   * @param newOccurrences the number of new occurrences generated
   */
  public synchronized void addOccurrences(long newOccurrences) {
    occurrences += newOccurrences;
  }
  
  /**
   * Returns the number of index occurrences that the indexing of this 
   * GATEDocument has generated. This value is only correct after the document
   * has been indexed by all sub-indexers.
   * 
   * @return the number of occurrences.
   */
  public long getOccurrences() {
    return occurrences;
  }

  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.Document#wordReader(int)
   */
  public WordReader wordReader(int field) {
    return new GATEDocumentWordReader(
            indexConfig.getTokenIndexers()[field].getFeatureName());
  }

  /**
   * Gets the array of offset-sorted token annotations for this document.
   * The value returned is the actual internally used array, so modifications 
   * can lead to undefined behaviour! 
   * @return the tokenAnnots
   */
  public Annotation[] getTokenAnnots() {
    return tokenAnnots;
  }

  /**
   * Gets the array of string representing the document content segments between
   * the token annotations.
   * The value returned is the actual internally used array, so modifications 
   * can lead to undefined behaviour!
   * @return the nonTokens
   */
  public String[] getNonTokens() {
    return nonTokens;
  }
  
  
}
