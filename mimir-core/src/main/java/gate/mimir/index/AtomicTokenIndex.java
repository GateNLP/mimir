/*
 *  AtomicTokenIndex.java
 *
 *  Copyright (c) 2007-2013, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 19 Dec 2013
 *
 *  $Id: AtomicTokenIndex.java 17371 2014-02-20 15:45:05Z valyt $
 */
package gate.mimir.index;

import gate.Annotation;
import gate.FeatureMap;
import gate.mimir.DocumentMetadataHelper;
import gate.mimir.IndexConfig.TokenIndexerConfig;
import gate.mimir.MimirIndex;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.dsi.lang.ObjectParser;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;

/**
 * An {@link AtomicIndex} implementation for indexing tokens.
 */
public class AtomicTokenIndex extends AtomicIndex {
  
  private final static Logger logger = Logger.getLogger(AtomicTokenIndex.class);
  
  /**
   * A constant (empty String array) used for filtering terms from indexing.
   * @see #calculateTermStringForAnnotation(Annotation, GATEDocument)
   * implementation.
   */
  private static final String[] DO_NOT_INDEX = new String[]{};
  
  
  protected final CharsetEncoder UTF8_CHARSET_ENCODER = Charset.forName("UTF-8").newEncoder();
  
  protected final CharsetDecoder UTF8_CHARSET_DECODER = Charset.forName("UTF-8").newDecoder();
  
  /**
   * Is this token index responsible for writing the zip collection?
   */
  protected boolean zipCollectionEnabled = false;
  
  /**
   * Stores the document tokens for writing to the zip collection;
   */
  protected List<String> documentTokens;
  
  /**
   * Stores the document non-tokens for writing to the zip collection;
   */
  protected List<String> documentNonTokens;
  
  /**
   * An array of helpers for creating document metadata. 
   */
  protected DocumentMetadataHelper[] docMetadataHelpers;
  
  /**
   * GATE document factory used by the zip builder, and also to
   * translate field indexes to field names.
   */
  protected GATEDocumentFactory factory;
  
  
  /**
   * The feature name corresponding to the field.
   */
  protected String featureName;
  

  
  /**
   * Creates a new atomic index for indexing tokens. 
   * @param parent the top level {@link MimirIndex} to which this new atomic 
   * index belongs.
   * @param name the name for the new atomic index. This will be used as the
   * name of the top level directory for this atomic index (which is a 
   * sub-directory of the parent) and as a base name for all the files of this 
   * atomic index.
   * @param hasDirectIndex should a direct index be created as well.
   * @param inputQueue the queue where documents are submitted for indexing;
   * @param outputQueue the queue where indexed documents are returned to;
   * @throws IndexException 
   * @throws IOException 
   */
  public AtomicTokenIndex(MimirIndex parent, String name,
      boolean hasDirectIndex, BlockingQueue<GATEDocument> inputQueue,
      BlockingQueue<GATEDocument> outputQueue, TokenIndexerConfig config,
      boolean zipCollection) throws IOException, IndexException {
    super(parent, name, hasDirectIndex, 
        config.getTermProcessor(), inputQueue, outputQueue);
    this.featureName = config.getFeatureName();
    this.zipCollectionEnabled = zipCollection;
    if(zipCollectionEnabled) {
      documentTokens = new LinkedList<String>();
      documentNonTokens = new LinkedList<String>();
      docMetadataHelpers = parent.getIndexConfig().getDocMetadataHelpers();
    }
    
    // save the term processor
    additionalProperties.setProperty(Index.PropertyKeys.TERMPROCESSOR, 
        ObjectParser.toSpec(termProcessor));
    
    try {
      UTF8_CHARSET_ENCODER.replaceWith("[?]".getBytes("UTF-8"));
      UTF8_CHARSET_ENCODER.onMalformedInput(CodingErrorAction.REPLACE);
      UTF8_CHARSET_ENCODER.onUnmappableCharacter(CodingErrorAction.REPLACE);
    } catch(UnsupportedEncodingException e) {
      // this should never happen
      throw new RuntimeException("UTF-8 not supported");
    }
    
    indexingThread = new Thread(this, "Mimir-" + name + " indexing thread");
    indexingThread.start();
  }

  
  /**
   * If zipping, inform the collection builder that a new document
   * is about to start.
   */
  protected void documentStarting(GATEDocument gateDocument) throws IndexException {
    if(zipCollectionEnabled) {
      // notify the metadata helpers
      if(docMetadataHelpers != null){
        for(DocumentMetadataHelper aHelper : docMetadataHelpers){
          aHelper.documentStart(gateDocument);
        }
      }
    }
    // set lastTokenIndex to -1 so we don't have to special-case the first
    // token in the document in calculateStartPosition
    tokenPosition = -1;
  }

  /**
   * If zipping, inform the collection builder that we finished
   * the current document.
   */
  protected void documentEnding(GATEDocument gateDocument) throws IndexException {
    if(zipCollectionEnabled) {
      DocumentData docData = new DocumentData(
          gateDocument.uri().toString(), 
          gateDocument.title().toString(),
          documentTokens.toArray(new String[documentTokens.size()]),
          documentNonTokens.toArray(new String[documentNonTokens.size()])); 
      if(docMetadataHelpers != null){
        for(DocumentMetadataHelper aHelper : docMetadataHelpers){
          aHelper.documentEnd(gateDocument, docData);
        }
      }
      parent.writeZipDocumentData(docData);
      documentTokens.clear();
      documentNonTokens.clear();
    }
  }

  /**
   * Get the token annotations from this document, in increasing
   * order of offset.
   */
  protected Annotation[] getAnnotsToProcess(GATEDocument gateDocument) {
    Annotation[] tokens = gateDocument.getTokenAnnots(); 
    return tokens;
  }

  /**
   * This indexer always adds one posting per token, so the start
   * position for the next annotation is always one more than the
   * previous one.
   * 
   * @param ann
   * @param gateDocument
   */
  protected void calculateStartPositionForAnnotation(Annotation ann,
          GATEDocument gateDocument) {
    tokenPosition++;
  }

  /**
   * For a token annotation, the "string" we index is the feature value
   * corresponding to the name of the field to index.  As well as
   * calculating the string, this method writes an entry to the zip
   * collection builder if it exists.
   * 
   * @param ann
   * @param gateDocument
   */
  protected String[] calculateTermStringForAnnotation(Annotation ann,
          GATEDocument gateDocument) throws IndexException {
    FeatureMap tokenFeatures = ann.getFeatures();
    String value = (String)tokenFeatures.get(featureName);
    // make sure we get valid UTF-8 content
   // illegal strings will simply be rendered as "[UNMAPPED]"
    if(value != null) {
      try {
        CharBuffer cb = CharBuffer.wrap(value);
        ByteBuffer bb = UTF8_CHARSET_ENCODER.encode(cb);
        cb = UTF8_CHARSET_DECODER.decode(bb);
        value  = cb.toString();
      } catch(CharacterCodingException e) {
        // this should not happen
        value = null;
        logger.error("Error while normalizing input", e);
      }      
    }

    
    currentTerm.replace(value == null ? "" : value);
    //save the *unprocessed* term to the collection, if required.
    if(zipCollectionEnabled) {
      documentTokens.add(currentTerm.toString());
      documentNonTokens.add(gateDocument.getNonTokens()[tokenPosition]);
    }
    if(termProcessor.processTerm(currentTerm)){
      //the processor has changed the term, and allowed us to index it
      return null;  
    }else{
      //the processor has filtered the term -> don't index it.
      return DO_NOT_INDEX;
    }
  }

  /**
   * Overridden to close the zip collection builder.
   */
  @Override
  protected void flush() throws IOException {
  }
}
