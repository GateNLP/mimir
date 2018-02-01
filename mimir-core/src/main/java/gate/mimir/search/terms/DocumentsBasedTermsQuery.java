/*
 *  DocumentsBasedTermsQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 28 Nov 2012
 *
 *  $Id: DocumentsBasedTermsQuery.java 16353 2012-11-28 21:21:33Z valyt $
 */
package gate.mimir.search.terms;

/**
 * Interface for {@link TermsQuery} types that use documents as part of the
 * query specification.
 */
public interface DocumentsBasedTermsQuery extends TermsQuery, Cloneable {
  
  /**
   * Gets the IDs of the documents that are part of this query specification.
   * @return
   */
  public long[] getDocumentIds();
  
  /**
   * Change the IDs of the documents used in this query specification. This 
   * method is used to translate between different ID schemas. For example, in 
   * the case of federated indexes, the document IDs for the same document are 
   * different at the federated index level and at the member sub-index. 
   * @param newDocIds
   */
  public void setDocumentIds(long... newDocIds);
  
}
