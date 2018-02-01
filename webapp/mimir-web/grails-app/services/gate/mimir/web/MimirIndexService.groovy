/*
 *  MimirIndexService.groovy
 *
 *  Copyright (c) 2007-2012, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Ian Roberts, 18 Dec 2012
 *  
 *  $Id$
 */
package gate.mimir.web;

import org.grails.web.servlet.mvc.GrailsWebRequest

class MimirIndexService {
  static transactional = false

  def grailsLinkGenerator

  /**
   * Creates an absolute URL pointing to the home page of the web app.
   */
  String createRootUrl() {
    StringBuilder out = new StringBuilder()
    def request = GrailsWebRequest.lookup()?.currentRequest
    out << request.scheme
    out << "://"
    out << request.serverName
    if((request.scheme == "https" && request.serverPort != 443) ||
    (request.scheme == "http" && request.severPort != 80)) {
      out << ":${request.serverPort}"
    }
    out << request.contextPath

    return out.toString()
  }

  String createIndexUrl(attrs) {
    StringBuilder out = new StringBuilder()
    if(attrs.urlBase) {
      String urlBase = attrs.urlBase.toString()
      out << (urlBase.endsWith('/') ? urlBase.substring(0, urlBase.length() -1) : urlBase)
    } else {
      def request = GrailsWebRequest.lookup()?.currentRequest
      out << request.getScheme()
      out << "://"
      out << (attrs?.serverName?: request.getServerName())
      if((request.getScheme() == "https" && request.getServerPort() != 443) ||
         (request.getScheme() == "http" && request.getServerPort() != 80)) {
        out << ":${request.getServerPort()}"
      }
    }
    out << grailsLinkGenerator.link(controller:"indexManagement", action:"index",
                        params:[indexId:attrs.indexId])

    return out.toString()
  }
}
