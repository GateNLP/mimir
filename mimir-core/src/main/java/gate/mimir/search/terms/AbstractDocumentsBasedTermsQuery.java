/*
 *  AbstractDocumentsBasedTermsQuery.java
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
 *  $Id: AbstractDocumentsBasedTermsQuery.java 16583 2013-03-12 13:07:53Z valyt $
 */
package gate.mimir.search.terms;


/**
 * Abstract base class for term queries that use document IDs to specify the
 * search. 
 */
public abstract class AbstractDocumentsBasedTermsQuery 
    implements DocumentsBasedTermsQuery {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -8782894544193518215L;
  
  /**
   * The IDs for the documents used by this query.
   */
  protected long[] documentIds;
  
  public AbstractDocumentsBasedTermsQuery(long[] documentIds) {
    this.documentIds = documentIds;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.terms.DocumentsBasedTermsQuery#getDocumentIds()
   */
  @Override
  public long[] getDocumentIds() {
    return documentIds;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.terms.DocumentsBasedTermsQuery#setDocumentIds(long[])
   */
  @Override
  public void setDocumentIds(long... newDocIds) {
    this.documentIds = newDocIds;
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
