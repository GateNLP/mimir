<html>
    <head>
        <title><g:message code="gus.document.title" args="${[documentTitle]}" /></title>
        <meta name="layout" content="mimir" />
  <%-- Add any custom CSS from the current index --%>
  <g:if test="${index?.css}">
  <content tag="customCss">${index?.css}</content>
  </g:if>        
    </head>
    <body>
      <g:if test="${documentTitle}">
       <h1><g:message code="gus.document.heading" args="${[documentTitle]}" /></h1>
      </g:if>
			<g:if test="${queryId}">
				<mimir:documentContent queryId="${queryId}" documentRank="${documentRank}"/></g:if>
			<g:elseif test="${documentId}">
				<mimir:documentContent indexId="${index?.indexId}" documentId="${documentId}" />
		  </g:elseif>         
      <g:else>
        <p>Cannot find query with given ID; perhaps your session expired. Please try your search again!</p>
      </g:else>
    </body>
</html>