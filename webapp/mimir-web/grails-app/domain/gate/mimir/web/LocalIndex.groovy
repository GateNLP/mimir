/*
 *  LocalIndex.groovy
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


import java.io.Writer;

import gate.mimir.MimirIndex;
import gate.mimir.index.DocumentData
import gate.mimir.search.QueryRunner
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.parser.ParseException
import gate.mimir.search.terms.TermsQuery;
import gate.mimir.search.terms.TermsResultSet;
import gate.Document
import gate.Gate
import gate.creole.ResourceData
import gate.creole.ResourceInstantiationException

/**
 * Index stored locally on disk.
 */
class LocalIndex extends Index implements Serializable {
  /**
   * Path to the directory storing the index.
   */
  String indexDirectory


  /**
   * The scorer to be used during searching.
   */
  String scorer
  
  /**
   * Should sub-bindings be included when the search engine returns hits.
   * Defaults to null (equivalent to false in Groovy-truth), to supports 
   * backwards compatibility with old DBs.
   */
  Boolean subBindingsEnabled = null
  
  static constraints = {
    indexDirectory (nullable:false, blank:false)
    scorer (nullable:true, blank:true)
    subBindingsEnabled(nullable:true) 
  }
  
  // behaviour

  static mapping = {
    autowire true
  }

  /**
   * Grails service that actually interacts with a local index.
   */
  transient localIndexService

  transient mimirIndexService

  String indexUrl() {
    return mimirIndexService.createIndexUrl([indexId:indexId]) + 
        '/manage/addDocuments'
  }

  void indexDocuments(InputStream stream) {
    MimirIndex mIndex = localIndexService.getIndex(this)
    if(!mIndex) {
      throw new IllegalStateException("Cannot open index for index ${this}")
    }

    new ObjectInputStream(stream).withStream { objectStream ->
      objectStream.eachObject { Document doc ->
        ResourceData rd = Gate.creoleRegister[doc.getClass().name]
        if(rd) {
          rd.addInstantiation(doc)
          mIndex.indexDocument(doc)
        }
        else {
          throw new ResourceInstantiationException(
              "Could not find resource data for class ${doc.getClass().name}")
        }
      }
    }
  }

  void close() {
    localIndexService.close(this)
  }
  
  String[][] annotationsConfig() {
    return localIndexService.annotationsConfig(this)
  }

  QueryRunner startQuery(String queryString) throws ParseException {
    return localIndexService.getQueryRunner(this, queryString)
  }
  
  QueryRunner startQuery(QueryNode query) {
    return localIndexService.getIndex(this).getQueryEngine().getQueryRunner(query)
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.web.Index#postTermsQuery(gate.mimir.search.terms.TermsQuery)
   */
  @Override
  public TermsResultSet postTermsQuery(TermsQuery query) {
    return query.execute(localIndexService.getIndex(this).getQueryEngine())
  }

  /**
  * Gets the {@link DocumentData} value for a given document ID.
  * @param documentID
  * @return
  */
  DocumentData getDocumentData(long documentID) {
    return localIndexService.getDocumentData(this, documentID)
  }

  /* (non-Javadoc)
   * @see gate.mimir.web.Index#renderDocument(int, java.io.Writer)
   */
  @Override
  public void renderDocument(long documentID, Appendable out) {
    localIndexService.renderDocument(this, documentID, out)
  }

  void deleteDocuments(Collection<Long> documentIds) {
    localIndexService.deleteDocuments(this, documentIds)
  }

  void undeleteDocuments(Collection<Long> documentIds) {
    localIndexService.undeleteDocuments(this, documentIds)
  }

  void sync() {
    localIndexService.getIndex(this).requestSyncToDisk()
  }
}
