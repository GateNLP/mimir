description "Initialises your application.groovy config with settings for Mímir", "grails mimir-config"

println "Adding default Mímir configuration to grails-app/conf/application.groovy"

file('grails-app/conf/application.groovy').withWriterAppend { BufferedWriter writer ->
  writer.newLine()
  writer.newLine()
  writer.writeLine("// Added by mimir-web plugin")
  writer.newLine()
  writer.writeLine("gate {")
  writer.writeLine("  mimir {")
  writer.writeLine("    // uncomment this to use a different tokeniser for your queries from")
  writer.writeLine("    // the default ANNIE English one - the tokenisation of your queries")
  writer.writeLine("    // must match that of the documents that were indexed in order for")
  writer.writeLine("    // free-text queries to return sensible results.")
  writer.writeLine("    //queryTokeniserGapp = \"file:/path/to/tokeniser.xgapp\"")
  writer.newLine()
  writer.writeLine("    // Plugins that should be loaded to provide the semantic annotation")
  writer.writeLine("    // helpers used by your indexes.  You will almost always want the")
  writer.writeLine("    // default \"h2\" plugin, the others are optional")
  writer.writeLine("    plugins {")
  writer.writeLine("      h2 {")
  writer.writeLine("        group = \"uk.ac.gate\"")
  writer.writeLine("        artifact = \"mimir-plugin-dbh2\"")
  writer.writeLine("        //version = \"...\" // if version omitted, we assume the same as mimir-web")
  writer.writeLine("      }")
  writer.writeLine("      //measurements {")
  writer.writeLine("      //  group = \"uk.ac.gate\"")
  writer.writeLine("      //  artifact = \"mimir-plugin-measurements\"")
  writer.writeLine("      //}")
  writer.writeLine("      //sparql {")
  writer.writeLine("      //  group = \"uk.ac.gate\"")
  writer.writeLine("      //  artifact = \"mimir-plugin-sparql\"")
  writer.writeLine("      //}")
  writer.writeLine("    }")
  writer.writeLine("  }")
  writer.writeLine("}")
}

def pc = file('src/main/webapp/WEB-INF/gate/plugin-cache')
pc.mkdirs()
new File(pc, 'README').withWriter { BufferedWriter writer ->
  writer << """\
This is a cache directory that will hold a copy of the Maven-style plugins used
by your Mímir app, along with their dependencies, so the app does not have to
download them from the internet the first time it starts up.  To populate the
cache, once you have configured your chosen plugins in
grails-app/conf/application.groovy, run

  ./grailsw run-command cache-mimir-plugins

in the top level of your Grails application.
"""
}
