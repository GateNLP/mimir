/*
 *  IndexTemplate.groovy
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
package gate.mimir.web;

/**
 * A template for an index configuration, used to create new local indexes.
 */
class IndexTemplate {
  /**
   * Name of this template.
   */
  String name
  
  /**
   * Longer description.
   */
  String comment
  
  /**
   * Groovy fragment that defines the index configuration.
   */
  String configuration
  
  static constraints = {
    name(nullable:false, blank:false)
    comment(nullable:true, blank:true)
    configuration(nullable:false, maxSize:102400)
  }

  /**
   * Use the name of this template as its string representation.
   */
  public String toString() {
    return name
  }
}
