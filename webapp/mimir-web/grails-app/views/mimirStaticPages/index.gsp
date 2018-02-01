<%@ page import="gate.mimir.web.Index" %>
<%@ page import="gate.mimir.web.LocalIndex" %>
<%@ page import="gate.mimir.web.RemoteIndex" %>
<%@ page import="gate.mimir.web.FederatedIndex" %>

<g:set var="localIdxCnt" value="${LocalIndex.count()}" />
<g:set var="remoteIdxCnt" value="${RemoteIndex.count()}" />
<g:set var="fedIdxCnt" value="${FederatedIndex.count()}" />

<html>
  <head>
    <title>
      Welcome to Mimir (<mimir:createRootUrl />)
    </title>
    <meta name="layout" content="mimir" />
    <mimir:load />
  </head>
  <body>
    <div class="body">
    <h1>Welcome to M&iacute;mir (<mimir:createRootUrl />)</h1>
    
    <g:if test="${flash.message}">
      <div class="message">${flash.message}</div>
    </g:if>
		<p>    
    You can administer your M&iacute;mir instance on <g:link action="admin">this page</g:link>.
    </p>
    </div>    
  </body>
</html>
