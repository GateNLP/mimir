/*
 *  AtomicMentionsIndex.java
 *
 *  Copyright (c) 2007-2014, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 10 Jan 2014
 *
 *  $Id: AtomicAnnotationIndex.java 18573 2015-02-12 19:07:12Z ian_roberts $
 */
package gate.mimir.index;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.FeatureMap;
import gate.Node;
import gate.annotation.AnnotationImpl;
import gate.event.AnnotationListener;
import gate.mimir.IndexConfig;
import gate.mimir.MimirIndex;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.util.OffsetComparator;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.index.NullTermProcessor;
import it.unimi.dsi.lang.ObjectParser;

import org.apache.log4j.Logger;

/**
 *
 */
public class AtomicAnnotationIndex extends AtomicIndex {
  
  private final static Logger logger = Logger.getLogger(AtomicAnnotationIndex.class);
  
  /**
   * A simple object of type {@link Annotation} that can be used as a special marker.
   */
  private static class ConstAnnotation extends AnnotationImpl {
    private static final long serialVersionUID = 8224738902788616055L;
    public ConstAnnotation() {
      super(null, null, null, null, null);
    }
  }
  
  private static final Annotation DOCUMENT_VIRTUAL_ANN = new ConstAnnotation();
  
  /**
   * The {@link IndexConfig} used by the {@link MimirIndex} that contains this
   * mentions index.
   */
  protected IndexConfig indexConfig;
  
  protected SemanticIndexerConfig semIdxConfid;
  /**
   * Helpers for each semantic annotation type.
   */
  protected Map<String, SemanticAnnotationHelper> annotationHelpers;
  
  protected List<SemanticAnnotationHelper> documentHelpers;
  
  /**
   * An {@link OffsetComparator} used to sort the annotations by offset before 
   * indexing.
   */
  protected OffsetComparator offsetComparator;
  
  /**
   * Creates a new atomic index for indexing annotations.
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
  public AtomicAnnotationIndex(MimirIndex parent, String name, 
      boolean hasDirectIndex,
      BlockingQueue<GATEDocument> inputQueue,
      BlockingQueue<GATEDocument> outputQueue,
      SemanticIndexerConfig siConfig) throws IOException, IndexException {
    super(parent, name, hasDirectIndex, 
        NullTermProcessor.getInstance(), inputQueue, outputQueue);
    this.semIdxConfid = siConfig;
    indexConfig = parent.getIndexConfig();
    //get the helpers
    annotationHelpers = new HashMap<String, SemanticAnnotationHelper>(
              siConfig.getAnnotationTypes().length);
    documentHelpers = new LinkedList<SemanticAnnotationHelper>();
    for(int i = 0; i <  siConfig.getAnnotationTypes().length; i++){
      SemanticAnnotationHelper theHelper = siConfig.getHelpers()[i];
      theHelper.init(this);
      if(theHelper.getMode() == SemanticAnnotationHelper.Mode.DOCUMENT) {
        documentHelpers.add(theHelper);
      } else {
        annotationHelpers.put(siConfig.getAnnotationTypes()[i], theHelper);  
      }
    }
    offsetComparator = new OffsetComparator();
    // start the indexing thread
    indexingThread = new Thread(this, "Mimir-" + name + " indexing thread");
    indexingThread.start();
  }

  
  
  @Override
  protected void documentStarting(GATEDocument gateDocument)
      throws IndexException {
    for(SemanticAnnotationHelper aHelper : annotationHelpers.values()){
      aHelper.documentStart(gateDocument.getDocument());
    }
    for(SemanticAnnotationHelper aHelper : documentHelpers){
      aHelper.documentStart(gateDocument.getDocument());
    }
  }

  @Override
  protected void documentEnding(GATEDocument gateDocument)
      throws IndexException {
    for(SemanticAnnotationHelper aHelper : annotationHelpers.values()){
      aHelper.documentEnd();
    }
    // index the document mode annotations
    if(!documentHelpers.isEmpty()) {
      processAnnotation(DOCUMENT_VIRTUAL_ANN, gateDocument);
    }
    for(SemanticAnnotationHelper aHelper : documentHelpers){     
      aHelper.documentEnd();
    }
  }



  /* (non-Javadoc)
   * @see gate.mimir.index.AtomicIndex#getAnnotsToProcess(gate.mimir.index.mg4j.GATEDocument)
   */
  @Override
  protected Annotation[] getAnnotsToProcess(GATEDocument gateDocument)
      throws IndexException {
    Document document = gateDocument.getDocument();
    Annotation[] semanticAnnots;
    AnnotationSet semAnnSet = 
      (indexConfig.getSemanticAnnotationSetName() == null ||
      indexConfig.getSemanticAnnotationSetName().length() == 0) ?
      document.getAnnotations() :
      document.getAnnotations(indexConfig.getSemanticAnnotationSetName());
    if(semAnnSet.size() > 0){
      AnnotationSet semAnns = null;
      synchronized(semAnnSet) {
        semAnns = semAnnSet.get(annotationHelpers.keySet());
      }
      semanticAnnots = semAnns.toArray(new Annotation[semAnns.size()]);
      Arrays.sort(semanticAnnots, offsetComparator);
    } else {
      semanticAnnots  = new Annotation[0];
    }
    return semanticAnnots;
  }

  /* (non-Javadoc)
   * @see gate.mimir.index.AtomicIndex#calculateStartPositionForAnnotation(gate.Annotation, gate.mimir.index.mg4j.GATEDocument)
   */
  @Override
  protected void calculateStartPositionForAnnotation(Annotation ann,
      GATEDocument gateDocument) throws IndexException {
    if(ann == DOCUMENT_VIRTUAL_ANN) {
      // we're supposed index the document metadata
      tokenPosition = 0;
    } else {
      //calculate the term position for the current semantic annotation
      while(tokenPosition <  gateDocument.getTokenAnnots().length &&
            gateDocument.getTokenAnnots()[tokenPosition].
              getEndNode().getOffset().longValue() <= 
              ann.getStartNode().getOffset().longValue()){
        tokenPosition++;
      }
      //check if lastTokenposition is valid
      if(tokenPosition >= gateDocument.getTokenAnnots().length){
        //malfunction
        logger.error(
                "Semantic annotation [Type:" + ann.getType() +
                ", start: " + ann.getStartNode().getOffset().toString() +
                ", end: " + ann.getEndNode().getOffset().toString() +
                "] outside of the tokens area in document" +
                " URI: " + gateDocument.uri() +
                " Title: " + gateDocument.title());
      }      
    }
  }

  /* (non-Javadoc)
   * @see gate.mimir.index.AtomicIndex#calculateTermStringForAnnotation(gate.Annotation, gate.mimir.index.mg4j.GATEDocument)
   */
  @Override
  protected String[] calculateTermStringForAnnotation(Annotation ann,
      GATEDocument gateDocument) throws IndexException {
    if(ann == DOCUMENT_VIRTUAL_ANN) {
      // obtain the URIs to be indexed for the *document* metadata
      List<String> terms = new LinkedList<String>();
      for(SemanticAnnotationHelper aHelper : documentHelpers) {
        String[] someTerms = aHelper.getMentionUris(null, Mention.NO_LENGTH, this);
        if(someTerms != null) {
          for(String aTerm : someTerms) {
            terms.add(aTerm);
          }
        }
      }
      return terms.toArray(new String[terms.size()]);
    } else {
      //calculate the annotation length (as number of terms)
      SemanticAnnotationHelper helper = annotationHelpers.get(ann.getType());
      int length = 1;
      while(tokenPosition + length <  gateDocument.getTokenAnnots().length &&
              gateDocument.getTokenAnnots()[tokenPosition + length].
                getStartNode().getOffset().longValue() < 
                ann.getEndNode().getOffset().longValue()){
          length++;
        }
      //get the annotation URI
      return helper.getMentionUris(ann, length, this);
    }
  }

  @Override
  protected void flush() throws IOException {
    for(SemanticAnnotationHelper sah : annotationHelpers.values()) {
      sah.close(this);
    }
  }
  
  
}
