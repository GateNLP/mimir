/**
 *  DocumentData.java
 * 
 *  Copyright (c) 1995-2010, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 05 Dec 2011 
 */
package gate.mimir.web.client;

import java.util.List;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Data about a result document
 */
public class DocumentData implements IsSerializable {
  
  protected int documentRank;
  
  protected String documentTitle;
  
  protected String documentUri;
  
  protected List<String[]> snippets;

  /**
   * @return the documentRank
   */
  public int getDocumentRank() {
    return documentRank;
  }

  /**
   * @return the documentTitle
   */
  public String getDocumentTitle() {
    return documentTitle;
  }

  /**
   * @return the documentUri
   */
  public String getDocumentUri() {
    return documentUri;
  }

  /**
   * @return the snippets
   */
  public List<String[]> getSnippets() {
    return snippets;
  }
  
  
}
