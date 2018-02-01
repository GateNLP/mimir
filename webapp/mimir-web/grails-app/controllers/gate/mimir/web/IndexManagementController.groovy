/*
 *  IndexManagementController.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Ian Roberts, 28 Jan 2010
 *  
 *  $Id$
 */
package gate.mimir.web

import gate.mimir.web.Index;

import javax.servlet.http.HttpServletResponse

class IndexManagementController {
  
  static defaultAction = 'index'
  
  def index() {
    redirect(controller: "search", params:[indexId:params.indexId])
  }
  
  /**
   * Requests a URL to which documents should be posted.  Clients must treat
   * the URL returned from this method as transient and should re-query each
   * time they want to submit a document for indexing.  This is because a
   * federated index may return a different URL each time.
   */
  def indexUrl() {
    def theIndex = Index.findByIndexId(params.indexId)
    if(theIndex) {
      render(text:theIndex.indexUrl(), contentType:"text/plain",
        encoding:"UTF-8")
    }
  }

  def close() {
    def theIndex = Index.findByIndexId(params.indexId)
    if(theIndex) {
      theIndex.close()
      render("OK")
    }
  }

  def sync() {
    def theIndex = Index.findByIndexId(params.indexId)
    if(theIndex) {
      theIndex.sync()
      render("OK")
    }
  }

  /**
   * Takes a binary serialization of one or more GATE documents on the input
   * stream, deserializes it and passes it to the indexer.
   */
  def addDocuments() {
    def theIndex = Index.findByIndexId(params.indexId)
    if(theIndex) {
      request.inputStream.withStream { stream ->
        theIndex.indexDocuments(stream)
      }
      render("OK")
    }
  }
  
  def stateBin() {
    def indexInstance = Index.findByIndexId(params.indexId)
    if(indexInstance){
      render(text:indexInstance.state, contentType:'text/plain', encoding:'UTF-8')
    }  else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
      "Index ID ${params.indexId} not known!")
    }
  }
  

  /**
   * Common implementation for delete and undelete actions
   * (they are identical apart from the method to call on the
   * underlying Index domain object).
   */
  private handleDeleteOrUndeleteBin = { String method, dummy ->
    def indexInstance = Index.findByIndexId(params.indexId)
    if(indexInstance){
      try {
        def documentIds = null
        request.inputStream.withObjectInputStream { ois ->
          documentIds = ois.readObject()
        }
        if(documentIds instanceof Collection) {
          indexInstance."${method}Documents"(documentIds)
          render(text:"OK", contentType:'text/plain', encoding:'UTF-8')
        }
      } catch(Exception e) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error while marking documents as ${method}d: \"" + e.getMessage() + "\"!")
      }
    } else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Index ID ${params.indexId} not known!")
    }
  }

  def deleteDocumentsBin = handleDeleteOrUndeleteBin.curry("delete")

  def undeleteDocumentsBin = handleDeleteOrUndeleteBin.curry("undelete")

}
