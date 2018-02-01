# mimir
Multi-paradigm Information Management Index and Repository

This is the top level directory for the GATE Mímir source tree, containing the 
following modules which are built by the top-level POM:

- `mimir-core`: the core Mímir Java library.
- `mimir-connector`: Java library for sending documents to a remote Mímir server.
- `mimir-client`: Java library for querying a remote Mímir index.
- `mimir-indexing-plugin`: GATE plugin providing a PR that sends documents to
  Mímir for indexing.
- `plugins`: Mímir plugins providing various Semantic Annotation Helper (SAH) 
  implementations.
  - `db-h2`: generic SAH based on the H2 relational database
    (http://www.h2database.com/) 
  - `measurements`: specialised SAH providing advanced support for Measurement
    annotations
  - `sparql`: SAH implementation that uses semantic queries against a SPARQL
    end-point to filter the results of standard Mímir queries. 

The components of the Mímir web application are in the `webapp` directory,
and are built using Gradle and Grails 3.3:

- `mimir-web-ui`: the GWT search user interface used in `mimir-web`
- `mimir-web`: a Grails (http://grails.org) plugin providing Mímir
  functionality for Grails-base web applications.
- `mimir-cloud`: the Grails application used for the Mímir installs on 
  http://GATECloud.net. This is a fully-fledged application, which extends the
  `mimir-web` plugin with support for security. In most cases, if you need a simple way
  of deploying Mímir, you should be able to use this application as is. If you 
  need to integrate with an existing infrastructure (e.g. some already-existing
  single-sign-on solution), then you may find it easier to create your own app
  that depends on the `mimir-web` plugin.

Other components:

- doc: the Mímir user guide (LaTeX source and built PDF)

## How to build

Building Mímir is a two step process, you will require an installation of
Maven but not Grails/Gradle as the web components come with their own wrapper:

 1. run `mvn install` in this directory to build all the Java components
 2. run `./grailsw run-app` in `webapp/mimir-cloud` to build and start up
    the sample web application.

If you want to build a WAR for deployment to a Tomcat or similar web server
then change to the `webapp/mimir-cloud` directory and run:

 1. `./grailsw run-command cache-mimir-plugins`
 2. `./grailsw prod war`

The resulting WAR file will be created in `build/libs`
