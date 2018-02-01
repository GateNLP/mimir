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
package gate.mimir.cloud

import gate.mimir.security.Requestmap;
import gate.mimir.security.User;

class MimirStaticPagesController extends gate.mimir.web.MimirStaticPagesController {

  def springSecurityService
  
  def passwords() {
    return [adminUser: User.findByUsername('admin'), 
        managerUser: User.findByUsername('manager'),
        userUser: User.findByUsername('user')] 
  }
  
  def savePasswords() {
    User adminUser = User.findByUsername('admin')
    if(!adminUser) {
      log.error 'Security misconfiguration: cannot locate administrative user!'
      throw new IllegalStateException(
        'Security misconfiguration: cannot locate administrative user!')
    }
    User managerUser = User.findByUsername('manager')
    if(!managerUser) {
      log.error 'Security misconfiguration: cannot locate manager user!'
      throw new IllegalStateException(
        'Security misconfiguration: cannot locate manager user!')
    }
    User userUser = User.findByUsername('user')
    if(!userUser) {
      log.error 'Security misconfiguration: cannot locate non-administrative user!'
      throw new IllegalStateException(
        'Security misconfiguration: cannot locate non-administrative user!')
    }
    
    if(!adminUser?.password?.equals('not set')) {
      //check old pass
      if(springSecurityService.encodePassword(params.oldAdminPass) != 
          adminUser.password) {
        flash.message = "Wrong current password. Try again!"
        forward(controller:'mimirStaticPages', action:'passwords', params:params)
        return
      }
    }
    
    StringBuilder messages = new StringBuilder()
    if(params.newAdminPass) {
      if(params.newAdminPass != params.newAdminPassRep) {
        flash.message = "Passwords for <strong>admin</strong> don't match!"
        forward(controller:'mimirStaticPages', action:'passwords', params:params)
        return
      } else {
        String newPass = springSecurityService.encodePassword(params.newAdminPass)
        if(adminUser.password != newPass){
          adminUser.password = newPass  
          adminUser.save(flush:true, failOnError:true)
          messages << "Pasword for <strong>admin</strong> account changed.<br />"
        } else {
          messages << "Pasword for <strong>admin</strong> account <strong>not</strong> changed (old value provided).<br />"
        }
      }
    } else {
      if(adminUser.password != "not set"){
        messages << "Pasword for <strong>admin</strong> account <strong>not</strong> changed.<br />"
      } else {
        // no old admin pass and no new one supplied
        flash.message = "You must supply a password for the <strong>admin</strong> account!"
        forward(controller:'mimirStaticPages', action:'passwords', params:params)
        return
      }
    }
    
    if(params.newManagerPass) {
      if(params.newManagerPass == params.newManagerPassRep) {
        String newPass = springSecurityService.encodePassword(params.newManagerPass)
        if(managerUser.password != newPass) {
          managerUser.password = newPass
          managerUser.save(flush:true, failOnError:true)
          messages << "Pasword for <strong>manager</strong> account changed.<br />"
        } else {
          messages << "Pasword for <strong>manager</strong> account <strong>not</strong> changed (old value provided).<br />"
        }
        // make sure the manager user gets enabled
        setSecurityConstraint('/*/manage/**',  'ROLE_MANAGER, IS_AUTHENTICATED_FULLY')
        messages << "The <strong>manager</strong> account is enabled.<br />"
      } else {
        flash.message = "Passwords for <strong>manager</strong> account don't match!"
        forward(controller:'mimirStaticPages', action:'passwords', params:params)
        return
      }
    } else {
      // manager account has empty password: no restrictions on manage actions
      setSecurityConstraint('/*/manage/**',  'IS_AUTHENTICATED_ANONYMOUSLY')
      messages << "No pasword for <strong>manager</strong> account: " + 
          "<strong>open</strong> acess to all management actions!<br />"
    }
    
    
    if(params.newUserPass) {
      if(params.newUserPass == params.newUserPassRep) {
        String newPass = springSecurityService.encodePassword(params.newUserPass)
        if(userUser.password != newPass) {
          userUser.password = newPass
          userUser.save(flush:true, failOnError:true)
          messages << "Pasword for <strong>user</strong> account changed.<br />"
        } else {
          messages << "Pasword for <strong>user</strong> account <strong>not</strong> changed (old value provided).<br />"
        }
        // make sure the user user gets enabled
        setSecurityConstraint('/*/search/**',  'ROLE_USER, IS_AUTHENTICATED_FULLY')
        messages << "The <strong>user</strong> account is enabled.<br />"
      } else {
        flash.message = "Passwords for <strong>user</strong> account don't match!"
        forward(controller:'mimirStaticPages', action:'passwords', params:params)
        return
      }
    } else {
      // user account has empty password: no restrictions on search actions
      setSecurityConstraint('/*/search/**',  'IS_AUTHENTICATED_ANONYMOUSLY')
      messages << "No pasword for <strong>user</strong> account: " +
          "<strong>open</strong> acess to all search actions!<br />"
    }
    flash.message = messages.toString()
    redirect(uri:'/')
  }
  

  /**
   * Sets the Requestmap configAttribute (list of security tokens) for a given 
   * URL, replacing any previous one.
   * @param url
   * @param configAttribute
   * @return
   */
  private setSecurityConstraint(String url, String configAttribute) {
    List<Requestmap> rules = Requestmap.findAllByUrl(url)
    if(rules) {
      if(rules.size() != 1) {
        log.error("Security misconfiguration: found ${rules.size()} rules for ${url} URL!")
        throw new IllegalStateException ("Security misconfiguration: found " +
          "multiple (${rules.size()}) rules for a single URL!")
      }
      Requestmap rule = rules[0]
      rule.configAttribute = configAttribute
      rule.save(flush:true, failOnError:true)
    } else {
      //no pre-existing rules
      new Requestmap(url:url, configAttribute:configAttribute).save(flush:true, 
        failOnError:true)  
    }
    // update the S2 cache
    springSecurityService.clearCachedRequestmaps()
  }
}
