/*
 *  SearchInterceptor.groovy
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

import gate.mimir.web.Index
import javax.servlet.http.HttpServletResponse;

class SearchInterceptor {

  boolean before() {
    def theIndex = Index.findByIndexId(params.indexId)
    if(theIndex) {
      if(theIndex.state == Index.READY) {
        request.theIndex = theIndex
        return true
      }
      else if(theIndex.state == Index.CLOSING) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
            "Index with ID ${params.indexId} is in the process of closing")
      }
      else {
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
          "Index with ID ${params.indexId} is not ready")
      }
    }
    else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, "No index with ID ${params.indexId}")
    }
    return false
  }

  boolean after() { true }

  void afterView() {}
}
