package mimir.web

import grails.plugins.*
import gate.mimir.util.WebUtilsManager
import gate.mimir.web.IndexTemplate
import gate.mimir.web.MimirConfiguration
import gate.util.spring.ExtraGatePlugin
import gate.Gate

import java.util.concurrent.Executors

class MimirWebGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.3.2 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def title = "Mimir Web" // Headline display name of the plugin
    def author = "GATE Team"
    def authorEmail = "gate-users@lists.sourceforge.net"
    def description = '''\
This plugin is the main bulk of the Mímir web application.
'''
    def profiles = ['web']

    // URL to the plugin's documentation
    def documentation = "https://gate.ac.uk/mimir"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
//    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
//    def organization = [ name: "My Company", url: "http://www.my-company.com/" ]

    // Any additional developers beyond the author specified above.
//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    // Location of the plugin's issue tracker.
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    // Online location of the plugin's browseable source code.
//    def scm = [ url: "http://svn.codehaus.org/grails-plugins/" ]

    Closure doWithSpring() {
      {->
        // web utils manager
        webUtilsManager(WebUtilsManager)
        
        // thread pool for the search service
        searchThreadPool(Executors) { bean ->
          bean.factoryMethod = 'newCachedThreadPool'
          bean.destroyMethod = 'shutdown'
        }
        
        xmlns gate:'http://gate.ac.uk/ns/spring'
        // take <gate:init> attributes from configuration
        def pluginCache = config.gate.mimir.pluginCache
        gate.init(config.gate.mimir.gateInit) {
          if(pluginCache) {
            gate.'maven-caches' {
              if(pluginCache instanceof Map) {
                for(p in pluginCache.values()) {
                  value(p)
                }
              } else {
                value(pluginCache)
              }
            }
          }
        }

        // Mimir plugins
        if(config.gate.mimir.plugins) {
          URI baseURI = new File(System.getProperty('user.dir')).toURI()
          for(loc in config.gate.mimir.plugins.entrySet()) {
            def plg = loc.value
            "mimirPlugin-${loc.key}"(ExtraGatePlugin) {
              if(plg instanceof Map) {
                // Maven-style
                groupId = plg.group
                artifactId = plg.artifact
                version = plg.version ?: plugin.version // default to matching the mimir-web version number
              } else {
                location = baseURI.resolve(loc.value).toString()
              }
            }
          }
        }
        
        // the default query tokeniser (can be overridden in the
        // host app's resources.xml/groovy
        gate.'saved-application'(id:"queryTokeniser",
          location:application.config.gate.mimir.queryTokeniserGapp.toString())
      }
    }

    void doWithDynamicMethods() {
        // TODO Implement registering dynamic methods to classes (optional)
    }

    void doWithApplicationContext() {
    }

    void onChange(Map<String, Object> event) {
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    void onConfigChange(Map<String, Object> event) {
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    void onShutdown(Map<String, Object> event) {
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}
