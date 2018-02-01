<%@ page import="gate.mimir.web.FederatedIndex"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<title>Create Federated Index</title>
</head>
<body>
<div class="nav">
  <span class="menuButton"> <g:link class="home"
        controller="mimirStaticPages" action="admin">Admin Home</g:link>
  </span>
</div>
<div class="body">
<h1>Create Federated Index</h1>
<g:if test="${flash.message}">
	<div class="message">
	${flash.message}
	</div>
</g:if> <g:hasErrors bean="${federatedIndexInstance}">
	<div class="errors"><g:renderErrors
		bean="${federatedIndexInstance}" as="list" /></div>
</g:hasErrors> <g:form action="save" method="post">
	<div class="dialog">
	<table>
		<tbody>

			<g:set var="i" value="${0}" />
			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="name">Name:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:federatedIndexInstance,field:'name','errors')}">
				<input type="text" id="name" name="name"
					value="${fieldValue(bean:federatedIndexInstance,field:'name')}" />
				</td>
			</tr>

			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="indexes">Indexes:</label>
				</td>
				<td valign="top"
					class="value ${hasErrors(bean:federatedIndexInstance,field:'indexes','errors')}">
				<g:select name="indexes" from="${gate.mimir.web.Index.list()}"
					optionKey="id" optionValue="name" size="5" multiple="yes"
					optionKey="id" value="${federatedIndexInstance?.indexes}" /></td>
			</tr>

      <tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
        <td valign="top" class="name"><label for="uriIsExternalLink">Document
      URIs are external links:</label></td>
        <td valign="top" class="value"><g:checkBox
          name="uriIsExternalLink"
          value="${federatedIndexInstance.uriIsExternalLink}" /></td>
      </tr>

			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="indexId">Index ID (optional):</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:federatedIndexInstance,field:'indexId','errors')}">
				<input type="text" id="indexId" name="indexId"
					value="${fieldValue(bean:federatedIndexInstance,field:'indexId')}" />
				</td>
			</tr>

		</tbody>
	</table>
	</div>
	<div class="buttons"><span class="button"><input
		class="save" type="submit" value="Create" /></span></div>
</g:form></div>
</body>
</html>
