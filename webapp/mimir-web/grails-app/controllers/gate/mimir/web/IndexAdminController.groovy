/*
 *  IndexAdminController.groovy
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

import gate.mimir.web.Index;

import grails.converters.JSON

import java.text.NumberFormat;
import javax.servlet.http.HttpServletResponse;

/**
 * Controller for operations common to all types of index.
 */
class IndexAdminController {
  static defaultAction = "admin"

  static NumberFormat percentNumberInstance = NumberFormat.getPercentInstance(Locale.US)

  static{
    percentNumberInstance.setMaximumFractionDigits(2)
    percentNumberInstance.setMinimumFractionDigits(2)
  }

  def admin() {
    def indexInstance = Index.findByIndexId(params.indexId)
    if(!indexInstance) {
      flash.message = "Index not found with index id ${params.indexId}"
      redirect(uri:'/')
      return
    }

    [indexInstance:indexInstance]
  }
  
  def close() {
    def indexInstance = Index.findByIndexId(params.indexId)
    
    if(!indexInstance) {
      flash.message = "Index not found with index id ${params.indexId}"
    }
    else {
      if(indexInstance.state == Index.CLOSING) {
        flash.message = "Index  \"${indexInstance.name}\" is already in the process of closing."
      }
      else if(indexInstance.state == Index.READY) {
        indexInstance.close()
        flash.message = "Index  \"${indexInstance.name}\" closing.  This may take a long time."
      }
    }
    redirect(action:admin, params:params)    
  }

  /**
   * Ask the index to sync all documents to disk
   */
  def sync() {
    def indexInstance = Index.findByIndexId(params.indexId)
    
    if(!indexInstance) {
      flash.message = "Index not found with index id ${params.indexId}"
      redirect(action:admin, params:params)
    }
    else {
      indexInstance.sync()
      flash.message = "Sync to disk was requested."
      redirect(action:admin, params:params)
    }
  }
  
  def deletedDocuments() {
    def indexInstance = Index.findByIndexId(params.indexId)

    if(!indexInstance) {
      flash.message = "Index not found with index id ${params.indexId}"
      redirect(action:admin, params:params)
    }
    else if(indexInstance.state != Index.READY) {
      flash.message = "Index \"${indexInstance.name}\" is not open"
      redirect(action:admin, params:params)
    }
    else {
      return [indexInstance:indexInstance, documents:new DeletedDocumentsCommand(documentIds:"")]
    }
  }

  /**
   * deleteDocuments and undeleteDocuments are almost identical, so we
   * use a common implementation.
   */
  def doDeleteOrUndelete(DeletedDocumentsCommand cmd) {
    def indexInstance = Index.findByIndexId(params.indexId)

    if(!indexInstance) {
      flash.message = "Index not found with index id ${params.indexId}"
      redirect(action:admin, params:params)
    }
    else if(indexInstance.state != Index.READY) {
      flash.message = "Index \"${indexInstance.name}\" is not open"
      redirect(action:admin, params:params)
    }
    else {
      if(cmd.hasErrors()) {
        // invalid operation
        render(view:'deletedDocuments', model:[indexInstance:indexInstance, documents:cmd])
      } else {
        def lastTriedNumber = null
        try {
          def idsToDelete = cmd.documentIds.split(/\p{javaWhitespace}+/).collect {
            lastTriedNumber = it
            return it.toLong()
          }

          // if we get to here we have a list of Integers
          indexInstance."${cmd.operation}Documents"(idsToDelete)
          flash.message = "${idsToDelete.size()} document(s) marked as ${cmd.operation}d"
          redirect(action:admin, params:[indexId:params.indexId])
        }
        catch(NumberFormatException e) {
          cmd.errors.rejectValue("documentIds",
            "gate.mimir.web.DeletedDocumentsCommand.documentIds.notnumber",
            [lastTriedNumber] as String[],
            "\"${lastTriedNumber}\" is not a valid document ID")
          render(view:'deletedDocuments', model:[indexInstance:indexInstance, documents:cmd])
        }
      }
    }
  }
}


/**
 * Simple command object used by the deleteDocuments and undeleteDocuments
 * actions so we can attach errors to the field.
 */
class DeletedDocumentsCommand {
  String documentIds
  String operation

  static constraints = {
    operation(inList:['delete', 'undelete'])
  }
}
