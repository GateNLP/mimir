package gate.mimir.cloud

import gate.mimir.security.Requestmap;
import gate.mimir.security.Role;
import gate.mimir.security.User;
import gate.mimir.security.UserRole;

import grails.core.GrailsApplication

class BootStrap {

  GrailsApplication grailsApplication

  def springSecurityService
  
  def init = { servletContext ->
    // security set-up
    Role adminRole = Role.findByAuthority('ROLE_ADMIN') ?:
        new Role(authority: 'ROLE_ADMIN').save(failOnError: true)
    User adminUser = User.findByUsername('admin') ?:
        new User(username:'admin', password:'not set', enabled:true,
        accountExpired:false, accountLocked:false,
        passwordExpired:false).save(failOnError:true, flush:true)
    if(adminUser.password == 'not set') {
      // admin user has no password set
      String defaultPass = grailsApplication.config.gate.mimir.defaultAdminPassword
      if(defaultPass != 'not set') {
        // a default admin password was provided via external config
        adminUser.password = springSecurityService.encodePassword(defaultPass)
        adminUser.save(flush:true, failOnError:true)
      }
    }
    UserRole uRole = UserRole.findByUserAndRole(adminUser, adminRole) ?:
        UserRole.create(adminUser, adminRole)
        
    Role managerRole = Role.findByAuthority('ROLE_MANAGER') ?:
        new Role(authority: 'ROLE_MANAGER').save(failOnError: true)
    User managerUser = User.findByUsername('manager') ?:
        new User(username:'manager', password:'not set', enabled:true,
        accountExpired:false, accountLocked:false,
        passwordExpired:false).save(failOnError:true, flush:true)
    uRole = UserRole.findByUserAndRole(managerUser, managerRole) ?:
        UserRole.create(managerUser, managerRole)

    Role userRole = Role.findByAuthority('ROLE_USER') ?:
        new Role(authority: 'ROLE_USER').save(failOnError: true)
    User userUser = User.findByUsername('user') ?:
        new User(username:'user', password:"not set", enabled:true,
        accountExpired:false, accountLocked:false,
        passwordExpired:false).save(failOnError:true, flush:true)
    uRole = UserRole.findByUserAndRole(userUser, userRole ) ?:
        UserRole.create(userUser, userRole, true)

    // default security rules (all URLs must be lowercased!)
    [
          '/admin/passwords':  ['IS_AUTHENTICATED_ANONYMOUSLY'],
          '/admin/savepasswords': ['IS_AUTHENTICATED_ANONYMOUSLY'],
          '/admin/**':    ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'],
          '/*/manage/**':   ['IS_AUTHENTICATED_ANONYMOUSLY'],
          '/*/search/**':   ['IS_AUTHENTICATED_ANONYMOUSLY'],
          '/':  ['IS_AUTHENTICATED_ANONYMOUSLY'],
          '/assets/**':  ['IS_AUTHENTICATED_ANONYMOUSLY'],
          '/login/**':    ['IS_AUTHENTICATED_ANONYMOUSLY'],
          '/logout/**':   ['IS_AUTHENTICATED_ANONYMOUSLY'],
          '/j_spring_security_check':   ['IS_AUTHENTICATED_ANONYMOUSLY'],
          '/_gcn_active.html':   ['IS_AUTHENTICATED_ANONYMOUSLY']
        ].each{String key, List<String> value ->
          Requestmap reqMap = Requestmap.findByUrl(key)
          if(!reqMap) {
            boolean firstAttr = true
            String configAttr = value.sum {
              if(firstAttr) {
                firstAttr = false;
                return it
              } else {
                return ", " + it
              }
            }
            new Requestmap(url:key, configAttribute: configAttr).save(flush:true, failOnError:true)
          }
        }
        // flush the cache
        springSecurityService.clearCachedRequestmaps()
    }
    def destroy = {
    }
}
