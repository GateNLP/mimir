grails{
  /**
   * Grails Security config
   */
  plugin{
    springsecurity {
      userLookup.userDomainClassName = 'gate.mimir.security.User'
      userLookup.authorityJoinClassName = 'gate.mimir.security.UserRole'
      authority.className = 'gate.mimir.security.Role'
      // backwards compatibility with existing databases
      password.algorithm='SHA-256'
      password.hash.iterations = 1
      requestMap.className = 'gate.mimir.security.Requestmap'
      //requestMap.urlField = 'url'
      //requestMap.configAttributeField = 'configAttribute'
      securityConfigType = 'Requestmap'
          
      // allow access to unspecified resources
      rejectIfNoRule = true
      
      /** Role hierarchy */
      roleHierarchy =
'''\
ROLE_ADMIN > ROLE_MANAGER
ROLE_ADMIN > ROLE_USER
ROLE_MANAGER > ROLE_USER
'''
      // allow Basic HTTP auth
      useBasicAuth = true
      basic.realmName = "GATECloud.net Mímir Server"
    
    //...but only for the search, manage, and getFile actions
      filterChain.chainMap = [
        [pattern: '/assets/**',      filters: 'none'],
        [pattern:'/*/search/**', filters:'JOINED_FILTERS,-exceptionTranslationFilter'],
        [pattern:'/*/manage/**', filters:'JOINED_FILTERS,-exceptionTranslationFilter'],
        [pattern:'/admin/indexDownload/getFile/**', filters:'JOINED_FILTERS,-exceptionTranslationFilter'],
        [pattern:'/**', filters:'JOINED_FILTERS,-basicAuthenticationFilter,-basicExceptionTranslationFilter']
      ]

      controllerAnnotations.staticRules = [
	[pattern: '/',               access: ['permitAll']],
	[pattern: '/error',          access: ['permitAll']],
	[pattern: '/index',          access: ['permitAll']],
	[pattern: '/index.gsp',      access: ['permitAll']],
	[pattern: '/shutdown',       access: ['permitAll']],
	[pattern: '/assets/**',      access: ['permitAll']],
      ]
    }
  }
}

gate.mimir.runningOnCloud = false

// If supplied, this value is used to override the scheme, server name, and port
// parts of the index URL. This can be used to make cloud-based installations 
// show their private URl here instead of the one obtained from the current 
// request 
// gate.mimir.indexUrlBase = 'http://aws-mimir-server.change-me.example.com:8080/'

// The temporary directory gets set to one of the ephemeral partitions by the
// cloud-init scripts. This is a default value used during development
gate.mimir.tempDir = '/data-local/mimir/temp'

// The initial value for the admin password. This would normally get overridden
// by the external config file. 
gate.mimir.defaultAdminPassword = 'not set'


// Added by mimir-web plugin

gate {
  mimir {
    // uncomment this to use a different tokeniser for your queries from
    // the default ANNIE English one - the tokenisation of your queries
    // must match that of the documents that were indexed in order for
    // free-text queries to return sensible results.
    //queryTokeniserGapp = "file:/path/to/tokeniser.xgapp"

    // Plugins that should be loaded to provide the semantic annotation
    // helpers used by your indexes.  You will almost always want the
    // default "h2" plugin, the others are optional
    plugins {
      h2 {
        group = "uk.ac.gate.mimir"
        artifact = "mimir-plugin-dbh2"
      }
      measurements {
        group = "uk.ac.gate.mimir"
        artifact = "mimir-plugin-measurements"
      }
      sparql {
        group = "uk.ac.gate.mimir"
        artifact = "mimir-plugin-sparql"
      }
    }
  }
}
