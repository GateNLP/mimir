/*
 *  DocumentMetadataHelper.java
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
 *  $Id: DocumentMetadataHelper.java 17261 2014-01-30 14:05:14Z valyt $
 */
package gate.mimir;

import gate.mimir.index.DocumentData;
import gate.mimir.index.GATEDocument;

/**
 * Interface for classes that implement a method of generating document 
 * metadata.
 */
public interface DocumentMetadataHelper {
  
  /**
   * Called when the indexing a new document begins.
   * @param document the document being indexed.
   */
  public void documentStart(GATEDocument document);
  
  /**
   * Called when the indexing of a document has completed. This method should
   * add metadata fields to the provided documentData object. 
   * @param document the document being indexed
   * @param documentData the documentData value that will be stored as part of
   * the index, and which holds the metadata fields.
   */
  public void documentEnd(GATEDocument document, DocumentData documentData);
}
