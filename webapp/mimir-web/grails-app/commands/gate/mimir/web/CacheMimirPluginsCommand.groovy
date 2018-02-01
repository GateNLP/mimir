package gate.mimir.web

import gate.Gate
import gate.creole.Plugin
import gate.util.maven.SimpleMavenCache
import org.eclipse.aether.artifact.DefaultArtifact

import grails.dev.commands.*

class CacheMimirPluginsCommand implements GrailsApplicationCommand {

    boolean handle() {
      def artifactsToCache = Gate.getCreoleRegister().getPlugins().findAll {
        it instanceof Plugin.Maven
      }.collect { p ->
        println "Found plugin ${p}"
        new DefaultArtifact(p.getGroup(), p.getArtifact(), "jar", p.getVersion())
      }

      if(artifactsToCache) {
        def cachePath = applicationContext.grailsApplication.config.gate.mimir.pluginCache.main
        def webapp = new File(new File(new File("src"), "main"), "webapp").toURI()
        // make sure the dir URL ends with a slash
        if(!webapp.toString().endsWith("/")) {
          webapp = URI.create(webapp.toString() + "/")
        }
        def cacheURI = webapp.resolve(cachePath)
        File cacheDir = new File(cacheURI)
        println "Cacheing plugins to ${cacheDir.absolutePath}"
        SimpleMavenCache cache = new SimpleMavenCache(cacheDir)
        artifactsToCache.each { cache.cacheArtifact(it) }
      } else {
        println "No plugins loaded, nothing to do"
      }

      return true
    }
}
