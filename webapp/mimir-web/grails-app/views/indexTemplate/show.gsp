
<%@ page import="gate.mimir.web.IndexTemplate" %>
<html>
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
		<meta name="layout" content="mimir" />
		<title>Show IndexTemplate</title>
	</head>
	<body>
		<div class="nav">
			<span class="menuButton">
				<g:link class="home" controller="mimirStaticPages" action="index">Home</g:link>
			</span>
			<span class="menuButton">
				<g:link class="list" action="list">IndexTemplate List</g:link>
			</span>
			<span class="menuButton">
				<g:link class="create" action="create">New IndexTemplate</g:link>
			</span>
		</div>
		<div class="body">
			<h1>Show IndexTemplate</h1>
			<g:if test="${flash.message}">
				<div class="message">${flash.message}</div>
			</g:if>
			<div class="dialog">
				<table>
					<tbody>
						<tr class="prop">
							<td valign="top" class="name">Name:</td>
							<td valign="top" class="value">${fieldValue(bean:indexTemplateInstance,
								field:'name')}</td>
						</tr>
						<tr class="prop">
							<td valign="top" class="name">Comment:</td>
							<td valign="top" class="value">${fieldValue(bean:indexTemplateInstance,
								field:'comment')}</td>
						</tr>
						<tr class="prop">
							<td valign="top" class="name">Configuration:</td>
							<td valign="top" class="value">
							<hr/>
							<pre>${fieldValue(bean:indexTemplateInstance, field:'configuration')}</pre>
							<hr/>
							</td>

						</tr>

					</tbody>
				</table>
			</div>
			<div class="buttons">
				<g:form>
					<input type="hidden" name="id" value="${indexTemplateInstance?.id}" />
					<span class="button">
						<g:actionSubmit class="edit" value="Edit" />
					</span>
					<span class="button">
						<g:actionSubmit class="delete"
							onclick="return confirm('Are you sure?');" value="Delete" />
					</span>
				</g:form>
			</div>
		</div>
	</body>
</html>
