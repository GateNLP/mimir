/*
 *  DocumentRenderer.java
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
 *  $Id: DocumentRenderer.java 17261 2014-01-30 14:05:14Z valyt $
 */
package gate.mimir;

import gate.mimir.index.DocumentData;
import gate.mimir.search.query.Binding;

import java.io.IOException;
import java.util.List;



/**
 * A document renderer is used to display a document and, optionally, a set of
 * query hits. 
 */
public interface DocumentRenderer {
  
  /**
   * Generates the output format (e.g. HTML) for a given document and a set of 
   * hits.
   * @param documentData the document to be rendered.
   * @param hits the list of hits to be highlighted.
   * @param ouput an {@link Appendable} to which the output should be written.  
   */
  public void render(DocumentData documentData, List<Binding> hits, 
          Appendable ouput) throws IOException;
}
