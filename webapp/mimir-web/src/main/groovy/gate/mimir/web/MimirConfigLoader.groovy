/*
 *  MimirConfigLoader.groovy
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

import grails.util.Environment
import groovy.util.ConfigSlurper
import groovy.util.ConfigObject

class MimirConfigLoader {
  static boolean mimirConfigLoaded = false
  
  public static synchronized void loadMimirConfig(config) {
    if(mimirConfigLoaded) return
    ConfigObject fullMimirConfig = new ConfigObject()
    ConfigSlurper slurper = new ConfigSlurper(Environment.current.name)
    try {
      // parse the app-provided MimirConfig (if it exists) and merge this into
      // the default config (app-provided settings win over defaults)
      GroovyClassLoader classLoader = new GroovyClassLoader(MimirConfigLoader.class.getClassLoader())
      ConfigObject mimirConf = slurper.parse(classLoader.loadClass("MimirConfig"))
      fullMimirConfig = fullMimirConfig.merge(mimirConf)
    } catch(Exception e) {
      // ignore, MimirConfig may legitimately be missing.
    }

    // finally merge in any settings coming from the existing app config
    fullMimirConfig = fullMimirConfig.merge(config.gate.mimir)

    // and store the result back in the main config
    config.gate.mimir = fullMimirConfig

    mimirConfigLoaded = true
  }
}
