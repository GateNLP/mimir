/*
 *  MimirStaticPagesInterceptor.groovy
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

import gate.mimir.security.User;

class MimirStaticPagesInterceptor {

  MimirStaticPagesInterceptor() {
    match(controller:'mimirStaticPages')
      .except(action:~/^(?:passwords|savePasswords)/)
  }

  boolean before() {
    User adminUser = User.findByUsername('admin')
    if(adminUser) {
      if(!adminUser?.password?.equals('not set')) {
        // admin password is set, use default behaviour
        //log.debug 'Password is set for administrative user, OK!'
        return true
      } else {
        log.debug 'Password not set for administrative user, redirecting!'
        redirect(action:'passwords')
        return false
      }
    } else {
      flash.message = 'Security misconfiguration: cannot locate administrative user!'
      log.error 'Security misconfiguration: cannot locate administrative user!'
      return false
    }
  }

  boolean after() { true }

  void afterView() {}
}
