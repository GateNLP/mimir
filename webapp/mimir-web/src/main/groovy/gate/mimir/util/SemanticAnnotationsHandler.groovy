/*
 *  SemanticAnnotationsHandler.groovy
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
import gate.mimir.SemanticAnnotationHelper
import gate.mimir.IndexConfig.SemanticIndexerConfig

import org.apache.log4j.Logger

/**
 * Class used as closure delegate to handle the
 * mimir.rpcIndexer.semanticAnnotations closure in mimir configuration.  Method
 * calls are interpreted as the names of semantic annotation types to index.
 * Each method call should have either a single parameter, being the
 * SemanticAnnotationHelper that should be used to handle that annotation type,
 * or a map of zero or more named parameters nominalFeatures, numericFeatures,
 * textFeatures and uriFeatures, which will be used to create a default helper.
 */
class SemanticAnnotationsHandler {

  private static final Logger log = Logger.getLogger(SemanticAnnotationsHandler)

  List indexerConfigs = []

  Map currentIndex = [:]

  public void clear() {
    indexerConfigs?.clear()
    currentIndex?.clear()
  }

  def index(Closure callable) {
    index([:], callable)
  }
  
  def index(Map params, Closure callable) {
    Map savedCurrentIndex = currentIndex
    currentIndex = [annotationTypes:[], helpers:[]]
    callable.delegate = this
    callable.call()

    indexerConfigs << new SemanticIndexerConfig(
        currentIndex.annotationTypes as String[],
        currentIndex.helpers as SemanticAnnotationHelper[],
        params?.directIndex ? true : false)
    
    currentIndex = savedCurrentIndex
  }

  /**
   * Main DSL method.  Expects a named parameter "helper" specifying
   * the SemanticAnnotationHelper to register.  If the helper does
   * not provide a getAnnotationType method (i.e. if it is not a
   * subclass of AbstractSemanticAnnotationHelper) then an additional
   * argument named "type" is also required to specify the annotation
   * type against which the helper should be registered.
   * <pre>
   * annotation helper:new DefaultHelper(annType:'Person', nominalFeatures:['gender'])
   * </pre>
   */
  void annotation(Map args) {
    if(!currentIndex) {
      throw new IllegalStateException("annotation method called outside any \"index\" closure")
    }
    def helper = args.helper
    if(!helper) {
      throw new IllegalArgumentException("annotation method requires a \"helper\" parameter")
    }
    if(!(helper instanceof SemanticAnnotationHelper)) {
      throw new IllegalArgumentException("annotation method \"helper\" parameter must be a " +
        "SemanticAnnotationHelper, but ${helper.getClass().getName()} is not.")
    }
    def type = args.type
    if(!type) {
      if(helper.hasProperty("annotationType")) {
        type = helper.annotationType
      }
    }
    if(!type) {
      throw new IllegalArgumentException("annotation method could not determine annotation " +
        "type - type could not be determined from the helper, and no explicit \"type\" parameter " +
        "was provided.")
    }

    currentIndex.annotationTypes << type
    currentIndex.helpers << helper
  }

  /**
   * Old-style DSL method to create and register a semantic annotation helper by calling a method named for the annotation type.  This pattern is now deprecated.
   * Expects the following
   * map entries:
   * <dl>
   *   <dt>type</dt><dd>The Class object representing the type
   *     of the helper.  This class must implement
   *     {@link SemanticAnnotationHelper} and must provide a
   *     no-argument constructor and JavaBean property setters for
   *     at least the property "annType".</dd>
   * </dl>
   * This method will call the Groovy-style Map constructor, passing a map
   * containing the key "annType" (mapped to the name of the "missing" method)
   * along with any other keys in the map that was passed to this method except
   * the "type" key.  The resulting helper is then passed to the normal
   * <code>annotation</code> method in the usual way.  For example, a call of
   * <pre>
   * Person(type:DefaultHelper, nominalFeatures:["gender"])
   * </pre>
   * is converted to a call
   * <pre>
   * annotation type:"Person", helper:new DefaultHelper(annType:"Person", nominalFeatures:["gender"])
   * </pre>
   */
  void methodMissing(String annotationType, args) {
    if(args.size() != 1 || !(args[0] instanceof Map)) {
      throw new MissingMethodException(annotationType, this.getClass(), args)
    }
    Map defParams = args[0]
    Class theClass = null
    if(defParams?.type) {
      // a Class object
      theClass = defParams.type
    } else {
      throw new IllegalArgumentException("Index template does not include the " +
      "type for the semantic annotation helper for annotation type ${annotationType}.")
    }

    def helperParams = [:]
    helperParams.putAll(defParams)
    helperParams.remove('type')
    helperParams.annType = annotationType
    annotation(type:annotationType, helper:theClass.newInstance(helperParams))
  }
}
