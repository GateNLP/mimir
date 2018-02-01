/*
 *  IndexException.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Niraj Aswani, 19/March/07
 *
 *  $Id: IndexException.java 14541 2011-11-14 19:31:23Z ian_roberts $
 */
package gate.mimir.index;

/**
 * Exception that should be thrown should something unexpected happens during
 * creating/updating/deleting index.
 * 
 * @author niraj
 * 
 */
public class IndexException extends Exception {

  /**
   * serial version id
   */
  private static final long serialVersionUID = 3257288036893931833L;

  /** Consructor of the class. */
  public IndexException(String msg) {
    super(msg);
  }

  public IndexException(Throwable t) {
    super(t);
  }

  public IndexException(String message, Throwable t) {
    super(message, t);
  }
}
