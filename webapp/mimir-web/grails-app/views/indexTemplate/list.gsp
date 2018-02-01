
<%@ page import="gate.mimir.web.IndexTemplate" %>
<html>
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
		<meta name="layout" content="mimir" />
		<title>IndexTemplate List</title>
	</head>
	<body>
		<div class="nav">
			<span class="menuButton">
				<g:link class="home" controller="mimirStaticPages" action="index">Home</g:link>
			</span>
			<span class="menuButton">
				<g:link class="create" action="create">New IndexTemplate</g:link>
			</span>
		</div>
		<div class="body">
			<h1>IndexTemplate List</h1>
			<g:if test="${flash.message}">
				<div class="message">${flash.message}</div>
			</g:if>
			<div class="list">
				<table>
					<thead>
						<tr>
							<g:sortableColumn property="name" title="Name" />
							<g:sortableColumn property="comment" title="Comment" />
						</tr>
					</thead>
					<tbody>
						<g:each in="${indexTemplateInstanceList}" status="i"
							var="indexTemplateInstance">
							<tr class="${(i % 2) == 0 ? 'odd' : 'even'}">
								<td>
									<g:link action="show" id="${indexTemplateInstance.id}">${fieldValue(bean:indexTemplateInstance,
										field:'name')}</g:link>
								</td>
								<td>${fieldValue(bean:indexTemplateInstance, field:'comment')}
								</td>
							</tr>
						</g:each>
					</tbody>
				</table>
			</div>
			<div class="paginateButtons">
				<g:paginate total="${indexTemplateInstanceTotal}" />
			</div>
		</div>
	</body>
</html>
