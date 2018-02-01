/*
 *  IndexArchive.groovy
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
package gate.mimir.cloud

import gate.mimir.util.IndexArchiveState;

/**
 * Domain class holding the data required for downloading a local index.
 *
 */
class IndexArchive {

  /**
   * The index this domain object refers to
   */
  static belongsTo = [theIndex : gate.mimir.web.LocalIndex]
  
  String localDownloadDir
  
  IndexArchiveState state
  
  /**
   * The reason for the current state (used to store any error messages that may 
   * useful in debugging a failed state)
   */
  String cause
  
  /**
   * A 0 - 100 value indicating the progress of the current operation
   */
  int progress
  
  static constraints = {
    theIndex (nullable:false)
    localDownloadDir(nullable:true, blank:true)
    state(nullable:false)
    cause(nullable:true, blank:true)
  }
}
