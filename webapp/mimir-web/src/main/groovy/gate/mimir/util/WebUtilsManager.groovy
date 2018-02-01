/*
 *  WebUtilsManager.groovy
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

import org.springframework.web.context.request.RequestContextHolder as RCH

import gate.mimir.tool.WebUtils
import gate.mimir.web.RemoteIndex;

class WebUtilsManager {
  
  static WebUtils staticWebUtils = new WebUtils();
  
  public WebUtils currentWebUtils(RemoteIndex remoteIndex) {
    if(RCH.requestAttributes) {
      WebUtils utils = RCH.requestAttributes.session.webUtilsInstance
      if(!utils) {
        utils = new WebUtils(remoteIndex.remoteUsername, 
          remoteIndex.remotePassword)
        RCH.requestAttributes.session.webUtilsInstance = utils
      }
      return utils
    } else {
      // no thread-bound request, use the static instance (if no authentication
      // required), or a fresh one every time
      if(remoteIndex.remoteUsername) {
        return new WebUtils(remoteIndex.remoteUsername, 
            remoteIndex.remotePassword)   
      } else {
        return staticWebUtils
      }
    }
  }
}
