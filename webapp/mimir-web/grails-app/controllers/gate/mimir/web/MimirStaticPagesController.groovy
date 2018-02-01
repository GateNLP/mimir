/*
 *  MimirStaticPagesController.groovy
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

/**
 * No-op controller used for static views in mimir to make URL mapping
 * more straightforward.
 */
class MimirStaticPagesController {
  def index() {}
  
  def admin() {}
  def error() {}
}
