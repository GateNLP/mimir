<%@ page import="gate.mimir.web.FederatedIndex" %>
<%@ page import="gate.mimir.web.Index" %>
<html>
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
		<meta name="layout" content="mimir" />
		<title>FederatedIndex List</title>
	</head>
	<body>
		<div class="nav">
			<span class="menuButton">
				<g:link class="home" controller="mimirStaticPages" action="admin">Admin Home</g:link>
			</span>
			<span class="menuButton">
				<g:link class="create" action="create">New FederatedIndex</g:link>
			</span>
		</div>
		<div class="body">
			<h1>FederatedIndex List</h1>
			<g:if test="${flash.message}">
				<div class="message">${flash.message}</div>
			</g:if>
			<div class="list">
				<table>
					<thead>
						<tr>
							<g:sortableColumn property="name" title="Name" />
							<g:sortableColumn property="state" title="State" />
              <th>&nbsp;</th>
						</tr>
					</thead>
					<tbody>
						<g:each in="${federatedIndexInstanceList}" status="i"
							var="federatedIndexInstance">
							<tr class="${(i % 2) == 0 ? 'odd' : 'even'}">
								<td>
									<g:link action="show" id="${federatedIndexInstance.id}">${fieldValue(bean:federatedIndexInstance, field:'name')}</g:link>
								</td>

								<td>${fieldValue(bean:federatedIndexInstance, field:'state')}
								</td>
								<td>
									<g:if test='${federatedIndexInstance.state == Index.READY}'>
									  <g:link controller="gus" action="search"
	                    params="[indexId:federatedIndexInstance.indexId]">Search this index.</g:link>
									
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
				<g:paginate total="${federatedIndexInstanceTotal}" />
			</div>
		</div>
	</body>
</html>
