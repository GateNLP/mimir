
<%@ page import="gate.mimir.web.*"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<title>Create Local Index</title>
</head>
<body>
<div class="nav">
  <span class="menuButton"> <g:link class="home"
        controller="mimirStaticPages" action="admin">Admin Home</g:link>
  </span>
</div>
<div class="body">
<h1>Create Local Index</h1>
<g:if test="${flash.message}">
	<div class="message">
	${flash.message}
	</div>
</g:if> <g:hasErrors bean="${localIndexInstance}">
	<div class="errors"><g:renderErrors bean="${localIndexInstance}"
		as="list" /></div>
</g:hasErrors> <g:form action="save" method="post">
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
				<td valign="top" class="name"><label for="indexTemplateId">Index
				template:</label></td>
				<td valign="top" class="value"><g:select id="indexTemplateId"
					name="indexTemplateId" from="${IndexTemplate.list()}"
					optionKey="id" value="${indexTemplateId}" /></td>
			</tr>

			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="uriIsExternalLink">Document
      URIs are external links:</label></td>
				<td valign="top" class="value"><g:checkBox
					name="uriIsExternalLink"
					value="${localIndexInstance.uriIsExternalLink}" /></td>
			</tr>

      <tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
        <td valign="top" class="name"><label for="subBindingsEnabled">Sub-bindings enabled:</label></td>
        <td valign="top" class="value"><g:checkBox
          name="subBindingsEnabled"
          value="${localIndexInstance.subBindingsEnabled?:false}" /></td>
      </tr>
      
			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="indexId">Index ID (optional):</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:localIndexInstance,field:'indexId','errors')}">
				<input type="text" id="indexId" name="indexId"
					value="${fieldValue(bean:localIndexInstance,field:'indexId')}" /></td>
			</tr>

		</tbody>
	</table>
	</div>
	<div class="buttons"><span class="button"><input
		class="save" type="submit" value="Create" /></span></div>
</g:form></div>
</body>
</html>
