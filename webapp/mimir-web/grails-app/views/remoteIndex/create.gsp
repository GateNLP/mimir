<%@ page import="gate.mimir.web.*"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<title>Connect to Remote Index</title>
</head>
<body>
<div class="nav">
  <span class="menuButton"> <g:link class="home"
        controller="mimirStaticPages" action="admin">Admin Home</g:link>
  </span>
</div>
<div class="body">
<h1>Connect to Remote Index</h1>
<g:if test="${flash.message}">
	<div class="message">
	${flash.message}
	</div>
</g:if> <g:hasErrors bean="${remoteIndexInstance}">
	<div class="errors"><g:renderErrors bean="${remoteIndexInstance}"
		as="list" /></div>
</g:hasErrors> <g:form action="save" method="post">
	<div class="dialog">
	<table>
		<tbody>

			<g:set var="i" value="${0}" />
			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="name">Name:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:remoteIndexInstance,field:'name','errors')}">
				<input type="text" id="name" name="name"
					value="${fieldValue(bean:remoteIndexInstance,field:'name')}" /></td>
			</tr>

			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="remoteUrl">Remote
				URL:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:remoteIndexInstance,field:'remoteUrl','errors')}">
				<input type="text" id="remoteUrl" name="remoteUrl"
					value="${fieldValue(bean:remoteIndexInstance,field:'remoteUrl')}" />
				</td>
			</tr>
			<tr>
			  <td class="${(i % 2) ? 'even' : 'odd'}" colspan="2">This should be the "Index URL" from the target index's management page.</td>
			</tr>

			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="remoteUsername">Remote Username:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:remoteIndexInstance,field:'remoteUsername','errors')}">
				<input type="text" id="remoteUsername" name="remoteUsername"
					value="${fieldValue(bean:remoteIndexInstance,field:'remoteUsername')}" />
				</td>
			</tr>
			<tr>
			  <td class="${(i % 2) ? 'even' : 'odd'}" colspan="2">If the remote server requires authentication, enter 
			  above the username that should be used when connecting.</td>
			</tr>
			
			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="remotePassword">Remote Password:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:remoteIndexInstance,field:'remotePassword','errors')}">
				<input type="password" id="remotePassword" name="remotePassword"
					value="${fieldValue(bean:remoteIndexInstance,field:'remotePassword')}" />
				</td>
			</tr>
			<tr>
			  <td class="${(i % 2) ? 'even' : 'odd'}" colspan="2">If the remote server requires authentication, enter 
				above the password that should be used when connecting.</td>
			</tr>

      <tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
        <td valign="top" class="name"><label for="uriIsExternalLink">Document
      URIs are external links:</label></td>
        <td valign="top" class="value"><g:checkBox
          name="uriIsExternalLink"
          value="${remoteIndexInstance?.uriIsExternalLink}" /></td>
      </tr>

			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="indexId">Index ID (optional):</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:remoteIndexInstance,field:'indexId','errors')}">
				<input type="text" id="indexId" name="indexId"
					value="${fieldValue(bean:remoteIndexInstance,field:'indexId')}" /></td>
			</tr>

		</tbody>
	</table>
	</div>
	<div class="buttons"><span class="button"> <input
		class="save" type="submit" value="Create" /> </span></div>
</g:form></div>
</body>
</html>
