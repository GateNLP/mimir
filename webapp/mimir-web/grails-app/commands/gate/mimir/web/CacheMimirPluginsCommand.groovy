package gate.mimir.web

import gate.Gate
import gate.creole.Plugin
import gate.util.maven.SimpleMavenCache
import grails.dev.commands.GrailsApplicationCommand
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.util.artifact.SubArtifact

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
        artifactsToCache.each { Artifact a ->
          cache.cacheArtifact(a)
          // also cache the -creole.jar
          try {
            cache.cacheArtifact(new SubArtifact(a, "creole", "jar"))
          } catch(Exception e) {
            println "Failed to cache \"-creole.jar\" for plugin ${a.groupId}:${a.artifactId}:${a.version} - this is " +
                    "not an error, but you should expect to see warning messages in your logs when Mimir starts up"
          }
        }
        cache.compact()
      } else {
        println "No plugins loaded, nothing to do"
      }

      return true
    }
}
