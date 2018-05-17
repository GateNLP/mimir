/*
 *  LocalIndexController.groovy
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
package gate.mimir.web

import gate.mimir.web.Index;
import gate.mimir.web.IndexTemplate;
import gate.mimir.web.LocalIndex;

import org.springframework.beans.factory.annotation.Autowired

import java.util.UUID;
import java.util.concurrent.Callable


class LocalIndexController {
  
  @Autowired
  ScorerSource scorerSource

  /**
   * Service for interacting with local indexes.
   */
  def localIndexService
  
  def index() { redirect(uri:"/") }
  
  // the delete, save and update actions only accept POST requests
  static allowedMethods = [delete:'POST', save:'POST', update:'POST', deleteBin:'POST']
  
  def list() {
    params.max = Math.min( params.max ? params.max.toInteger() : 10,  100)
    [ localIndexInstanceList: LocalIndex.list( params ), localIndexInstanceTotal: LocalIndex.count() ]
  }
  
  def show() {
    def localIndexInstance = LocalIndex.get( params.id )
    
    if(!localIndexInstance) {
      flash.message = "LocalIndex not found with id ${params.id}"
      redirect(controller:'indexManagement', action:'home')
    }
    else { 
      
      return [ 
          localIndexInstance : localIndexInstance,
          indexedDocs: localIndexService.getIndex(localIndexInstance)?.getIndexedDocumentsCount(),
          oldVersion: localIndexService.isOldVersion(localIndexInstance)]
    }
  }
  
  
  def upgradeFormat() {
    def localIndexInstance = LocalIndex.get( params.id )
    if(!localIndexInstance) {
      flash.message = "LocalIndex not found with id ${params.id}"
      redirect(controller:'indexManagement', action:'home')
    } else {
      try{
        localIndexService.upgradeIndex(localIndexInstance)
        flash.message = "Index converted"
      } catch (Exception e) {
        log.error('Error upgrading index', e)
        flash.message = "There was an error while trying to convert the index."
      }
      redirect(action:'show', params:[id:localIndexInstance.id])
    }
  }
  
  
  def deleteBin() {
    def indexInstance = Index.findByIndexId(params.indexId)
    localIndexService.deleteIndex(indexInstance,params.deleteFiles)
    render("OK")
  }
  
  def delete() {
    def token = UUID.randomUUID().toString()
    session.indexDeleteToken = token
    [localIndexInstance:LocalIndex.get(params.id), token:token]
  }

  def doDelete() {
    def sessionToken = session.indexDeleteToken
    session.removeAttribute('indexDeleteToken')
    if(params.token == sessionToken) {
      LocalIndex localIndexInstance = LocalIndex.get(params.id)
      localIndexService.deleteIndex(localIndexInstance, params.deleteFiles)
      flash.message = "Local index ${localIndexInstance.id} deleted"
      redirect(controller:'mimirStaticPages', action:'admin')
    } else {
      flash.message = "Invalid token"
      redirect(action:'show', params:[id:params.id])
    }
  }
  
  //    def delete = {
  //        def localIndexInstance = LocalIndex.get( params.id )
  //        if(localIndexInstance) {
  //            try {
  //                localIndexInstance.delete(flush:true)
  //                flash.message = "LocalIndex ${params.id} deleted"
  //                redirect(action:list)
  //            }
  //            catch(org.springframework.dao.DataIntegrityViolationException e) {
  //                flash.message = "LocalIndex ${params.id} could not be deleted"
  //                redirect(action:show,id:params.id)
  //            }
  //        }
  //        else {
  //            flash.message = "LocalIndex not found with id ${params.id}"
  //            redirect(action:list)
  //        }
  //    }
  
  def edit() {
    def localIndexInstance = LocalIndex.get( params.id )
    
    if(!localIndexInstance) {
      flash.message = "LocalIndex not found with id ${params.id}"
      redirect(uri:"/")
    }
    else {
      return [ localIndexInstance : localIndexInstance,
        timeBetweenBatches : localIndexService.getIndex(localIndexInstance)?.timeBetweenBatches,
        availableScorers : scorerSource.scorerNames()
         ]
    }
  }
  
  
  def getState() {
    def localIndexInstance = LocalIndex.get( params.id )
    render(localIndexInstance.state, contentType:"text/plain")
  }
  
  def update() {
    def localIndexInstance = LocalIndex.get( params.id )
    if(localIndexInstance) {
      if(params.version) {
        def version = params.version.toLong()
        if(localIndexInstance.version > version) {
          
          localIndexInstance.errors.rejectValue("version", "localIndex.optimistic.locking.failure", "Another user has updated this LocalIndex while you were editing.")
          render(view:'edit',model:[localIndexInstance:localIndexInstance,
              timeBetweenBatches : localIndexService.getIndex(localIndexInstance)?.timeBetweenBatches,
              availableScorers:scorerSource.scorerNames()])
          return
        }
      }
      localIndexInstance.properties = params
      if(localIndexInstance.scorer == 'null') localIndexInstance.scorer = null
      if(!localIndexInstance.hasErrors() && localIndexInstance.save()) {
        localIndexService.getIndex(localIndexInstance).setTimeBetweenBatches(
          Integer.parseInt(params.timeBetweenBatches))
        flash.message = "LocalIndex ${localIndexInstance.name} updated"
        redirect(controller:"indexAdmin", action:"admin", 
          params:[indexId:localIndexInstance.indexId])
      }
      else {
        render(view:'edit',model:[localIndexInstance:localIndexInstance,
            timeBetweenBatches : localIndexService.getIndex(localIndexInstance)?.timeBetweenBatches,
            availableScorers:scorerSource.scorerNames()])
      }
    }
    else {
      flash.message = "LocalIndex not found with id ${params.id}"
      redirect(controller:'mimirStaticPages', action: 'admin')
    }
  }
  
  /**
   * Create a new index, open for indexing.
   */
  def create() {
    def localIndexInstance = new LocalIndex()
    localIndexInstance.properties = params
    if(localIndexInstance.scorer == 'null') localIndexInstance.scorer = null
    return ['localIndexInstance':localIndexInstance]
  }
  
  /**
   * Action to create a new index for indexing.
   */
  def save() {
    def indexTemplateInstance = IndexTemplate.get(params.indexTemplateId)
    if(!indexTemplateInstance) {
      flash.message = "Index template not found with ID ${params.indexTemplateId}"
      redirect(controller:'mimirStaticPages', action:'admin')
      return
    }
    
    def indexId = params.indexId ?: UUID.randomUUID().toString()
    def localIndexInstance = new LocalIndex(indexId:indexId)
    localIndexInstance.name = params.name
    localIndexInstance.uriIsExternalLink = params.uriIsExternalLink ? true : false
    localIndexInstance.state = Index.READY
    try {
      def mimirConfigurationInstance = MimirConfiguration.findByIndexBaseDirectoryIsNotNull()
      if(!mimirConfigurationInstance) {
        flash.message = "This instance is not fully configured " +
            "(could not find the configuration data). Please fix this on the " +
            "admin page and try again."
        log.error("No instance of ${MimirConfiguration.class.name} could be found!")    
        redirect(controller:'mimirStaticPages', action: "admin")
        return
      }
      
      def tempFile = File.createTempFile('index-', '.mimir',
            new File(mimirConfigurationInstance.indexBaseDirectory))
      tempFile.delete()
      localIndexInstance.indexDirectory = tempFile.absolutePath
    } catch(IOException e) {
      flash.message = "Couldn't create directory for new index: ${e}"
      log.info("Couldn't create directory for new index", e)
      redirect(controller:'mimirStaticPages', action: "admin")
      return
    }
    
    if(!localIndexInstance.hasErrors() && localIndexInstance.save()) {
      try{
        localIndexService.createIndex(localIndexInstance,
            indexTemplateInstance)
        flash.message = "LocalIndex \"${localIndexInstance.name}\" created"
        redirect(controller:'mimirStaticPages', action: "admin")
        return
      }catch (Exception e) {
        flash.message = "Could not create local index. Problem was: \"${e.message}\"."
        log.debug("Error creating local index", e)
        localIndexInstance.delete()
        redirect(controller:'mimirStaticPages', action: "admin")
        return
      }
    }
    else {
      render(view:'create',model:[localIndexInstance:localIndexInstance])
    }
  }
  
  /**
   * Register an existing index directory to be opened for searching.
   */
  def importIndex() {
    def localIndexInstance = new LocalIndex()
    localIndexInstance.properties = params
    return ['localIndexInstance':localIndexInstance]
  }
  
  def doImport() {
    def localIndexInstance = new LocalIndex()
    localIndexInstance.name = params.name
    localIndexInstance.uriIsExternalLink = params.uriIsExternalLink ? true : false
    localIndexInstance.indexDirectory = params.indexDirectory
    localIndexInstance.state = Index.READY
    // sanity check that the specified directory exists and has the right
    // stuff in it
    def indexDir = new File(params.indexDirectory)
    if(!indexDir.isDirectory()) {
      localIndexInstance.errors.rejectValue('indexDirectory', 'gate.mimir.web.LocalIndex.indexDirectory.notexist')
      render(view:'importIndex', model:[localIndexInstance:localIndexInstance])
    }
    else if(!new File(indexDir, 'config.xml').isFile()) {
      localIndexInstance.errors.rejectValue('indexDirectory', 'gate.mimir.web.LocalIndex.indexDirectory.notindex')
      render(view:'importIndex', model:[localIndexInstance:localIndexInstance])
    }
    else {
      localIndexInstance.indexId = params.indexId ?: UUID.randomUUID().toString()
      if(localIndexInstance.save()) {
        flash.message = "Local Index \"${localIndexInstance.name}\" imported"
        redirect(controller:'mimirStaticPages', action: "admin")
      }
      else {
        render(view:'importIndex', model:[localIndexInstance:localIndexInstance])
      }
    }
  }
}
