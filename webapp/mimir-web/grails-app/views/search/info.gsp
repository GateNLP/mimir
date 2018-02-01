<%@ page import="gate.mimir.web.Index" %>
<html>
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="layout" content="mimir" />
    <link rel="stylesheet" href="${resource(dir:'css',file:'progressbar.css')}" />
    <title>Mimir index ${indexInstance.indexId}</title>
  </head>
  <body>
    <div class="body">
      <g:if test="${flash.message}">
        <div class="message">${flash.message}</div>
      </g:if>
      <h1>Mimir index &quot;${indexInstance.name}&quot;</h1>
      <g:if test="${indexInstance.state == Index.READY}">
        <p><g:link action="index" params="[indexId:indexInstance.indexId]"
              title="Search this index">Search this index using the web UI.</g:link> </br>
						<g:link controller="search" action="help"
              params="[indexId:indexInstance.indexId]"
              title="Search this index">Search this index using the XML web-service.</g:link>              
        </p>
      </g:if>
    </div>
  </body>
</html>
