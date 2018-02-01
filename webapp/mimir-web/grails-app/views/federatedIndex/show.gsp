<%@ page import="gate.mimir.web.Index"%>
<%@ page import="gate.mimir.web.FederatedIndex"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<title>Show FederatedIndex</title>
</head>
<body>
<div class="nav">
  <span class="menuButton"> <g:link class="home"
        controller="mimirStaticPages" action="admin">Admin Home</g:link>
  </span>
</div>
<div class="body">
<h1>Show FederatedIndex</h1>
<g:if test="${flash.message}">
	<div class="message">
	${flash.message}
	</div>
</g:if>
<div class="dialog">
<table>
	<tbody>
		<tr class="prop">
			<td valign="top" class="name">Type:</td>
			<td valign="top" class="value">Federated Index</td>
		</tr>

		<tr class="prop">
			<td valign="top" class="name">Name:</td>

			<td valign="top" class="value">
			${fieldValue(bean:federatedIndexInstance,
								field:'name')}
			</td>

		</tr>

		<tr class="prop">
			<td valign="top" class="name">Index Id:</td>

			<td valign="top" class="value">
			${fieldValue(bean:federatedIndexInstance,
								field:'indexId')}
			</td>

		</tr>

		<tr class="prop">
			<td valign="top" class="name">Index URL:</td>

			<td valign="top" class="value"><mimir:createIndexUrl
				indexId="${federatedIndexInstance.indexId}" /></td>
		</tr>

		<tr class="prop">
			<td valign="top" class="name">State:</td>

			<td valign="top" class="value">
			${fieldValue(bean:federatedIndexInstance,
								field:'state')}
			</td>

		</tr>

		<tr class="prop">
			<td valign="top" class="name">Indexes:</td>

			<td valign="top" style="text-align: left;" class="value">
			<ul>
				<g:each var="i" in="${federatedIndexInstance.indexes}">
					<li><g:link controller="indexAdmin" action="admin"
						params="[indexId:i.indexId]">
						${i?.getName()}
					</g:link></li>
				</g:each>
			</ul>
			</td>

		</tr>

		<tr class="prop">
			<td valign="top" class="name"><label for="uriIsExternalLink">Document
			URIs are external links:</label></td>
			<td valign="top" class="value"><g:formatBoolean
				boolean="${federatedIndexInstance.uriIsExternalLink}" /></td>
		</tr>

		<!--
							<tr class="prop"> <td valign="top" class="name">Federated Index
							Service:</td> <td valign="top"
							class="value">${fieldValue(bean:federatedIndexInstance,
							field:'federatedIndexService')}</td> </tr>
						-->
	</tbody>
</table>
</div>
<div class="buttons"><g:form>
	<input type="hidden" name="id" value="${federatedIndexInstance?.id}" />
	<span class="button"> <g:actionSubmit class="edit" value="Edit"
		title="Click to modify this index." /> </span>

	<span class="button"> <g:actionSubmit class="delete"
		title="Click to delete this index."
		onclick="return confirm('Are you sure?');" value="Delete" /> </span>
</g:form></div>
</div>
</body>
</html>
