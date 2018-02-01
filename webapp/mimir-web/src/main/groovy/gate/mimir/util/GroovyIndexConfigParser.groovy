/*
 *  GroovyIndexConfigParser.groovy
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
package gate.mimir.util

import gate.Gate;
import gate.mimir.DocumentMetadataHelper
import gate.mimir.IndexConfig
import gate.mimir.IndexConfig.SemanticIndexerConfig
import gate.mimir.IndexConfig.TokenIndexerConfig

import groovy.time.TimeCategory

/**
 * Helper class for parsing Groovy index configuration scripts into IndexConfig
 * objects.
 */
public class GroovyIndexConfigParser {
  public static IndexConfig createIndexConfig(String groovyScript, File indexDir) {
    // evaluate the configuration script
    def scriptBinding = new Binding([:])
    def shell = new GroovyShell(Gate.classLoader,
        scriptBinding)
    def tokenFeaturesHandler = new TokenFeaturesHandler()
    def semanticAnnotationsHandler = new SemanticAnnotationsHandler()
    Script script = shell.parse(groovyScript)

    // make the "annotation" method available anywhere in the script
    // using the metaclass mechanism - this makes it possible to declare
    // a method in the script that calls annotation(helper:...) and
    // then call that method from inside an index {} block, but it will
    // still throw an exception if there is not an index {} closure
    // somewhere in the current call stack. 
    GroovySystem.metaClassRegistry.removeMetaClass(script.getClass())
    def mc = script.getClass().metaClass
    mc.annotation = semanticAnnotationsHandler.&annotation
    script.metaClass = mc
    
    use(TimeCategory, script.&run)

    // process the tokenFeatures section
    def tokenFeaturesClosure = scriptBinding.tokenFeatures
    tokenFeaturesClosure.delegate = tokenFeaturesHandler
    tokenFeaturesClosure.call()

    // process the semanticAnnotations section
    def semanticAnnotationsClosure = scriptBinding.semanticAnnotations
    semanticAnnotationsClosure.delegate = semanticAnnotationsHandler
    semanticAnnotationsClosure.call()

    // build the index config
    def indexConfig = new IndexConfig(
        indexDir,
        scriptBinding.tokenASName,
        scriptBinding.tokenAnnotationType,
        scriptBinding.semanticASName,
        tokenFeaturesHandler.indexerConfigs as TokenIndexerConfig[],
        semanticAnnotationsHandler.indexerConfigs as SemanticIndexerConfig[],
        scriptBinding.documentMetadataHelpers as DocumentMetadataHelper[],
        scriptBinding.documentRenderer)

    if(scriptBinding.hasVariable('timeBetweenBatches')) {
      // if timeBetweenBatches is a Duration like 5.minutes then convert
      // it back to milliseconds, otherwise assume it is just a number of
      // milliseconds in the first place
      if(scriptBinding.timeBetweenBatches.respondsTo("toMilliseconds")) {
        indexConfig.timeBetweenBatches = (int)scriptBinding.timeBetweenBatches.toMilliseconds()
      } else {
        indexConfig.timeBetweenBatches = scriptBinding.timeBetweenBatches as int
      }
    }

    if(scriptBinding.hasVariable('maximumBatches')) {
      indexConfig.maximumBatches = scriptBinding.maximumBatches as int
    }

    semanticAnnotationsHandler.clear()
    tokenFeaturesHandler.clear()
    // clean up the metaclass to prevent memory leaks
    script.metaClass = null
    GroovySystem.metaClassRegistry.removeMetaClass(script.getClass())

    return indexConfig
  }

}
