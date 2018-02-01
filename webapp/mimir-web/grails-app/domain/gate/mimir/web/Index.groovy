/*
 *  Index.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id$
 */
package gate.mimir.web;

import gate.mimir.search.QueryRunner
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.parser.ParseException
import gate.mimir.search.terms.TermsQuery;
import gate.mimir.search.terms.TermsResultSet;
import gate.mimir.index.DocumentData

/**
 * Top level class representing a single index (local or remote) in mimir.
 */
class Index implements Serializable {
  /**
   * Name of the index, as distinct from the auto-assigned db identifier.
   */
  String name
  
  /**
   * Unique identifier for the index.
   */
  String indexId
  

  String state
  
  /**
   * The custom CSS injected in the results page produced by this index. This
   * allows the user customisation of how results are presented (in concert with
   * suitable HTML tags being stored at indexing time).
   */
  String css
  
  /**
   * Are document URIs in this index actually valid URLs that can be linked
   * to?  If true, an extra external link to the original document is shown
   * on the GUS result page.
   */
  boolean uriIsExternalLink = false
  
  static constraints = {
    name(unique:true)
    indexId(unique:true)
    state()
    uriIsExternalLink()
    css(nullable:true, maxSize:20480, blank: true)
  }

  // Abstract methods defining behaviour - these should be implemented by
  // subclasses

  /**
   * Return a URL to which a client should submit a document for indexing.
   * This should be a string suitable for passing to
   * HttpServletResponse.sendRedirect, so may be an absolute URL or a path
   * which will be resolved against the current web application.
   */
  String indexUrl() {
    throw new UnsupportedOperationException()
  }

  /**
   * Accept documents for indexing.  The given input stream should be assumed
   * to contain one or more documents in a suitable format (typically
   * Java-serialized GATE Documents).
   */
  void indexDocuments(InputStream stream) {
    throw new UnsupportedOperationException()
  }

  /**
   * Ask the index to shut down.  This method should begin the shutdown process
   * and then return promptly, it should not wait for the shutdown to finish.
   */
  void close() {
    throw new UnsupportedOperationException()
  }
  
  /**
   * Return the annotation configuration for this index, as used for
   * autocompletion in gus.
   */
  String[][] annotationsConfig() {
    throw new UnsupportedOperationException()
  }

  /**
   * Start running the given query.
   */
  QueryRunner startQuery(String query) throws ParseException {
    throw new UnsupportedOperationException() 
  }

  /**
   * Start running the given query.
   */
  QueryRunner startQuery(QueryNode query) {
    throw new UnsupportedOperationException()
  }
  
  TermsResultSet postTermsQuery(TermsQuery query) {
    throw new UnsupportedOperationException()
  }
  
  /**
   * Gets the {@link DocumentData} value for a given document ID.
   * @param documentID
   * @return
   */
  DocumentData getDocumentData(long documentID) {
    throw new UnsupportedOperationException()
  }
  
    /**
    * Renders the document specified by its ID to the provided writer.
    * @param documentID
    * @return
    */
   void renderDocument(long documentID, Appendable out) {
     throw new UnsupportedOperationException()
   }
  
  /**
   * Mark the given document IDs in this index as having been deleted,
   * preventing them from being subsequently returned by any queries.
   */
  void deleteDocuments(Collection<Long> documentIds) {
    throw new UnsupportedOperationException()
  }

  /**
   * Mark the given document IDs in this index as not having been deleted,
   * allowing them to bereturned by future queries.
   */
  void undeleteDocuments(Collection<Long> documentIds) {
    throw new UnsupportedOperationException()
  }

  /**
   * Request that any outstanding indexed documents be flushed to disk.
   */
  void sync() {
    throw new UnsupportedOperationException()
  }
  
  // Constants for the possible state values
  public static final String READY = "ready" // good for searching and indexing
  public static final String CLOSING = "closing"
  public static final String WORKING = "working"
  public static final String FAILED = "failed"
}
