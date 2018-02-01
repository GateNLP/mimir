
<%@ page import="gate.mimir.web.*"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<title>Import Local Index</title>
</head>
<body>
<div class="nav">
  <span class="menuButton"> <g:link class="home"
        controller="mimirStaticPages" action="admin">Admin Home</g:link>
  </span>
</div>
<div class="body">
<h1>Import Local Index</h1>
<g:if test="${flash.message}">
	<div class="message">
	${flash.message}
	</div>
</g:if> <g:hasErrors bean="${localIndexInstance}">
	<div class="errors"><g:renderErrors bean="${localIndexInstance}"
		as="list" /></div>
</g:hasErrors> <g:form action="doImport" method="post">
	<div class="dialog">
	<table>
		<tbody>

			<g:set var="i" value="${0}" />
			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="name">Name:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:localIndexInstance,field:'name','errors')}">
				<input type="text" id="name" name="name"
					value="${fieldValue(bean:localIndexInstance,field:'name')}" /></td>
			</tr>

			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="indexId">Index ID (optional):</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:localIndexInstance,field:'indexId','errors')}">
				<input type="text" id="indexId" name="indexId"
					value="${fieldValue(bean:localIndexInstance,field:'indexId')}" /></td>
			</tr>

			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="indexDirectory">Index
				directory:</label></td>
				<td valign="top" class="value"><input type="text"
					id="indexDirectory" name="indexDirectory"
					value="${fieldValue(bean:localIndexInstance,field:'indexDirectory')}" />
				</td>
			</tr>

			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="uriIsExternalLink">Document
      URIs are external links:</label></td>
				<td valign="top" class="value"><g:checkBox
					name="uriIsExternalLink"
					value="${localIndexInstance.uriIsExternalLink}" /></td>
			</tr>

		</tbody>
	</table>
	</div>
	<div class="buttons"><span class="button"><input
		class="save" type="submit" value="Import" /></span></div>
</g:form></div>
</body>
</html>
