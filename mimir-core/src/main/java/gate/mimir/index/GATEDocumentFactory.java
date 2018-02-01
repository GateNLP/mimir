/*
 *  GATEDocumentFactory.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 15 Apr 2009
 *
 *  $Id: GATEDocumentFactory.java 17261 2014-01-30 14:05:14Z valyt $
 */
package gate.mimir.index;

import gate.mimir.IndexConfig;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.di.big.mg4j.document.Document;
import it.unimi.di.big.mg4j.document.DocumentFactory;

import java.io.IOException;
import java.io.InputStream;


/**
 * An MG4J {@link DocumentFactory} for GATE documents, configured according 
 * to the current indexing requirements.
 */
public class GATEDocumentFactory implements DocumentFactory{

  /**
   * 
   */
  private static final long serialVersionUID = 6070650764387229146L;
  
  /**
   * The index configuration.
   */
  private IndexConfig indexConfig;
  
  public GATEDocumentFactory(IndexConfig indexConfig){
    this.indexConfig = indexConfig;
  }
  
  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.DocumentFactory#copy()
   */
  public DocumentFactory copy() {
    throw new UnsupportedOperationException(getClass().getName() + 
            " does not support copying!");
  }

  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.DocumentFactory#fieldIndex(java.lang.String)
   */
  public int fieldIndex(String fieldName) {
    for(int i = 0; i < indexConfig.getTokenIndexers().length; i++){
      if(indexConfig.getTokenIndexers()[i].getFeatureName().equals(fieldName)){
        return i;
      }
    }
    return -1;
  }

  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.DocumentFactory#fieldName(int)
   */
  public String fieldName(int field) {
    return indexConfig.getTokenIndexers()[field].getFeatureName();
  }

  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.DocumentFactory#fieldType(int)
   */
  public FieldType fieldType(int field) {
    // all GATE fields are TEXT
    return FieldType.TEXT;
  }

  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.DocumentFactory#getDocument(java.io.InputStream, it.unimi.dsi.fastutil.objects.Reference2ObjectMap)
   */
  public Document getDocument(InputStream rawContent,
          Reference2ObjectMap<Enum<?>, Object> metadata) throws IOException {
    //we do not support reading of documents from streams
    throw new UnsupportedOperationException(getClass().getName() + 
            " does not support reading from streams!");
  }

  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.DocumentFactory#numberOfFields()
   */
  public int numberOfFields() {
    return indexConfig.getTokenIndexers().length;
  }
}
