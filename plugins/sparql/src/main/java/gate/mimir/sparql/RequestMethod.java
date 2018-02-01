/*
 * RequestMethod.java
 * 
 * Copyright (c) 2007-2015, The University of Sheffield.
 * 
 * This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html),
 * and is free software, licenced under the GNU Lesser General Public License,
 * Version 3, June 2007 (also included with this distribution as file
 * LICENCE-LGPL3.html).
 * 
 * Ian Roberts, 5 May 2015
 * 
 * $Id: RequestMethod.java 18666 2015-05-05 15:28:35Z ian_roberts $
 */
package gate.mimir.sparql;

public enum RequestMethod {
  /**
   * Send the query as a URL parameter ?query=...
   */
  GET,

  /**
   * Send the query as an application/x-www-form-urlencoded POST request
   * query=....
   */
  POST_ENCODED,

  /**
   * Send the query as a plain (not URL encoded) POST request with
   * Content-Type: application/sparql-query
   */
  POST_PLAIN;
}
