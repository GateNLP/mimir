
<%@ page import="gate.mimir.web.RemoteIndex" %>
<%@ page
import="gate.mimir.web.Index" %>
<html>
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
		<meta name="layout" content="mimir" />
		<title>Remote Index List</title>
	</head>
	<body>
		<div class="nav">
			<span class="menuButton">
				<g:link class="home" controller="mimirStaticPages" action="admin">Admin Home</g:link>
			</span>
			<span class="menuButton">
				<g:link class="create" action="create">Connect to New Remote Index</g:link>
			</span>
		</div>
		<div class="body">
			<h1>Remote Index List</h1>
			<g:if test="${flash.message}">
				<div class="message">${flash.message}</div>
			</g:if>
			<div class="list">
				<table>
					<thead>
						<tr>
							<g:sortableColumn property="name" title="Name" />

							<g:sortableColumn property="state" title="State" />
							<th>&nbsp;
							</th>
						</tr>
					</thead>
					<tbody>
						<g:each in="${remoteIndexInstanceList}" status="i"
							var="remoteIndexInstance">
							<tr class="${(i % 2) == 0 ? 'odd' : 'even'}">

								<td>
									<g:link action="show" id="${remoteIndexInstance.id}">${fieldValue(bean:remoteIndexInstance,
										field:'name')}</g:link>
								</td>

								<td>${fieldValue(bean:remoteIndexInstance, field:'state')}</td>
								<td>
									<g:if test='${remoteIndexInstance.state == Index.READY}'>
										<g:link controller="gus" action="search"
											params="[indexId:remoteIndexInstance.indexId]">Search this index.</g:link>

									</g:if>
									<g:else>
                    &nbsp;
									</g:else>
								</td>

							</tr>
						</g:each>
					</tbody>
				</table>
			</div>
			<div class="paginateButtons">
				<g:paginate total="${remoteIndexInstanceTotal}" />
			</div>
		</div>
	</body>
</html>
