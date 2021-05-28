/*
 *  DocumentFeaturesMetadataHelper.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 6 Oct 2009
 *
 *  $Id: DocumentFeaturesMetadataHelper.java 17261 2014-01-30 14:05:14Z valyt $
 */
package gate.mimir.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gate.mimir.DocumentMetadataHelper;
import gate.mimir.index.DocumentData;
import gate.mimir.index.GATEDocument;
import gate.mimir.search.QueryEngine;


/**
 * A simple {@link DocumentMetadataHelper} that copies the values of some GATE 
 * document features as metadata fields in the index. Note that the values of 
 * the specified features must be {@link Serializable}; values that are not will
 * not be saved in the index.
 * 
 * The values thus saved can be retrieved at search time by calling 
 * {@link QueryEngine#getDocumentMetadataField(int, String)}.
 */
public class DocumentFeaturesMetadataHelper implements DocumentMetadataHelper {
  
  /**
   * A map storing the correspondence between the  GATE document feature name 
   * and the metadata field name in the Mimir index. 
   */
  protected Map<String, String> featureNameToFieldName;
  
  private static Logger logger = LoggerFactory.getLogger(
          DocumentFeaturesMetadataHelper.class);
  
  /**
   * Creates a new DocumentFeaturesMetadataHelper.
   * @param featureNameToFieldName a map storing the correspondence between the 
   * GATE document feature name and the metadata field name; keys are names of
   * document features; values are names of metadata fields. 
   */
  public DocumentFeaturesMetadataHelper(
          Map<String, String> featureNameToFieldName) {
    this.featureNameToFieldName = featureNameToFieldName;
  }
  
  /**
   * Creates a new DocumentFeaturesMetadataHelper.
   * @param featureNames an array of feature names. For each indexed document,
   * the values for the features specified here are obtained and stored in the
   *  index, as document metadata fields with the same names as the GATE 
   *  document features. If you need the names of the Mimir document metadata
   *  fields to be different from the GATE document features, then you should 
   *  use the {@link #DocumentFeaturesMetadataHelper(Map)} variant. 
   */  
  public DocumentFeaturesMetadataHelper(String... featureNames) {
    this.featureNameToFieldName = new HashMap<String, String>();
    for(String f : featureNames) {
      featureNameToFieldName.put(f, f);
    }
  }

  @Override
  public void documentStart(GATEDocument document) {
    // do nothing
  }

  @Override
  public void documentEnd(GATEDocument document, DocumentData documentData) {
    for(Map.Entry<String, String> mapping : featureNameToFieldName.entrySet()) {
      Object value = document.getDocument().getFeatures().get(mapping.getKey());
      if(value instanceof Serializable) {
        documentData.putMetadataField(mapping.getValue(), (Serializable)value);
      } else if(value != null) { // null is not an instanceof anything
        logger.warn("Value for document feature \"" + mapping.getKey() + 
                "\" on document with title \"" + 
                (document.title() == null ? "<null>" : document.title()) +
                "\", and URI: \"" +
                (document.uri() == null ? "<null>" : document.uri()) +
                "\" is not serializable. Document metadata field NOT saved.");
      }
    }
  }
}
