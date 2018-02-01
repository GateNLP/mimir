package gate.mimir.web

import gate.mimir.web.IndexTemplate
import gate.mimir.web.MimirConfiguration
import gate.Gate

import grails.core.GrailsApplication

class MimirWebBootStrap {

    def localIndexService
    def remoteIndexService
    def federatedIndexService

    GrailsApplication grailsApplication

    def init = { servletContext ->
      // make sure the deafult index template exists
      if(IndexTemplate.count() == 0) {
        log.info("No index templates found in database, adding default one")

        def defaultHelper = 'gate.mimir.db.DBSemanticAnnotationHelper'
        try {
          Class.forName(defaultHelper, true, Gate.classLoader)
        } catch(Exception e) {
          log.info("Couldn't load DB helper", e)
          // db not loaded, try ordi
          defaultHelper = 'gate.mimir.ordi.ORDISemanticAnnotationHelper'
          try {
            Class.forName(defaultHelper, true, Gate.classLoader)
          } catch(Exception e2) {
            defaultHelper = 'com.example.MySemanticAnnotationHelper'
          }
        }
        
        def defaultIndexConfig = """\
import gate.creole.ANNIEConstants
import gate.mimir.SemanticAnnotationHelper.Mode
import gate.mimir.index.OriginalMarkupMetadataHelper
import ${defaultHelper} as DefaultHelper

tokenASName = ""
tokenAnnotationType = ANNIEConstants.TOKEN_ANNOTATION_TYPE
tokenFeatures = {
  string()
  category()
  root()
}

semanticASName = ""
semanticAnnotations = {
  index {
    annotation helper:new DefaultHelper(annType:'Sentence')
  }
  index {
    annotation helper:new DefaultHelper(annType:'Person', nominalFeatures:["gender"])
    annotation helper:new DefaultHelper(annType:'Location', nominalFeatures:["locType"])
    annotation helper:new DefaultHelper(annType:'Organization', nominalFeatures:["orgType"])
    annotation helper:new DefaultHelper(annType:'Date', integerFeatures:["normalized"])
    annotation helper:new DefaultHelper(annType:'Document', integerFeatures:["date"], mode:Mode.DOCUMENT)
  }
}
documentRenderer = new OriginalMarkupMetadataHelper()
documentMetadataHelpers = [documentRenderer]

// miscellaneous options - these are the defaults
//timeBetweenBatches = 1.hour
//maximumBatches = 20
"""
        IndexTemplate.withTransaction {
          def defaultTemplate = new IndexTemplate(
              name:'default',
              comment:'The default index configuration',
              configuration:defaultIndexConfig)
    
          if(!defaultTemplate.save(flush:true)) {
            log.warn("Couldn't save default index template")
          }
        }
      }
      
      // make sure the singleton configuration domain object exists
      if(MimirConfiguration.count() == 0) {
        log.info('No configuration object, creating one now.')
        // if the data exists in config, copy it
        if(grailsApplication.config.gate.mimir.indexBaseDirectory) {
          MimirConfiguration.withTransaction() {
            MimirConfiguration conf = new MimirConfiguration(indexBaseDirectory:
              new File(grailsApplication.config.gate.mimir.indexBaseDirectory).absolutePath)
            conf.save(flush:true)
          }
        }
      } else if(MimirConfiguration.count() > 1) {
        // error!
        throw new IllegalStateException(
          'Multiple configuration versions found! Aborting.')
      }
      
      // initialise the index services, in the right order
      localIndexService.init()
      remoteIndexService.init()
      federatedIndexService.init()
    }
    def destroy = {
    }
}
