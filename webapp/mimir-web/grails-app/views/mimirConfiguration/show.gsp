
<%@ page import="gate.mimir.web.MimirConfiguration"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<g:set var="entityName"
	value="${message(code: 'mimirConfiguration.label', default: 'MimirConfiguration')}" />
<title><g:message code="default.show.label" args="[entityName]" /></title>
</head>
<body>
	<div class="nav">
		<span class="menuButton">
		  <a class="home" href="${createLink(controller:'mimirStaticPages', action:'admin')}">
		  Back to Admin Page</a>
		</span>
		<span class="menuButton">
		  <g:link class="create" action="edit" id="1">
		  <g:message code="default.edit.label" args="[entityName]" />
			</g:link>
	  </span>
	</div>
	<div class="body">
		<h1>
			<g:message code="default.show.label" args="[entityName]" />
		</h1>
		<g:if test="${flash.message}">
			<div class="message">
				${flash.message}
			</div>
		</g:if>
		<div class="dialog">
		  <div class="list">
			<table>
			<thead>
			 <tr><th>Parameter</th><th>Value</th></tr>
			</thead>
				<tbody>
					<tr class="prop">
						<td valign="top" class="name"><g:message
								code="mimirConfiguration.indexBaseDirectory.label"
								default="Index Base Directory" /></td>

						<td valign="top" class="value">
							${fieldValue(bean: mimirConfigurationInstance, field: "indexBaseDirectory")}
						</td>

					</tr>

				</tbody>
			</table>
			</div>
		</div>
		<div class="buttons">
			<g:form>
				<g:hiddenField name="id" value="${mimirConfigurationInstance?.id}" />
				<span class="button"><g:actionSubmit class="edit"
						action="edit"
						value="${message(code: 'default.button.edit.label', default: 'Edit')}" /></span>
			</g:form>
		</div>
	</div>
</body>
</html>
