
<%@ page import="gate.mimir.web.Index"%>
<%@ page import="gate.mimir.web.RemoteIndex"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<title>Show Remote Index</title>
</head>
<body>
<div class="nav">
  <span class="menuButton"> <g:link class="home"
        controller="mimirStaticPages" action="admin">Admin Home</g:link>
  </span>
</div>
<div class="body">
<h1>Show Remote Index</h1>
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
			<td valign="top" class="value">Remote Index</td>
		</tr>
		<tr class="prop">
			<td valign="top" class="name">Name:</td>
			<td valign="top" class="value">
			${fieldValue(bean:remoteIndexInstance,
								field:'name')}
			</td>
		</tr>
		<tr class="prop">
			<td valign="top" class="name">Index UUID:</td>
			<td valign="top" class="value">
			${fieldValue(bean:remoteIndexInstance,
								field:'indexId')}
			</td>
		</tr>

		<tr class="prop">
			<td valign="top" class="name">Index URL:</td>

			<td valign="top" class="value"><mimir:createIndexUrl
				indexId="${remoteIndexInstance.indexId}" /></td>
		</tr>

		<tr class="prop">
			<td valign="top" class="name">State:</td>

			<td valign="top" class="value">
			${fieldValue(bean:remoteIndexInstance,
								field:'state')}
			</td>

		</tr>

		<tr class="prop">
			<th valign="top" class="name" colspan="2">Remote Data:
			</td>
		</tr>
		<tr class="prop">
			<td valign="top" class="name"><label style="margin-left: 20px">Remote
			URL:</label></td>
			<td valign="top" class="value">
			${fieldValue(bean:remoteIndexInstance,
								field:'remoteUrl')}
			</td>
		</tr>

		<tr class="prop">
			<td valign="top" class="name"><label style="margin-left: 20px">Remote
			Username:</label></td>
			<td valign="top" class="value">
			${fieldValue(bean:remoteIndexInstance,
								field:'remoteUsername')}
			</td>
		</tr>
		
    <tr class="prop">
      <td valign="top" class="name"><label for="uriIsExternalLink">Document
      URIs are external links:</label></td>
      <td valign="top" class="value"><g:formatBoolean
        boolean="${remoteIndexInstance.uriIsExternalLink}" /></td>
    </tr>

	</tbody>
</table>
</div>
<div class="buttons"><g:form>
	<input type="hidden" name="id" value="${remoteIndexInstance?.id}" />
	<span class="button"> <g:actionSubmit class="edit" value="Edit"
		title="Click to modify this index." /> </span>
	<span class="button"> <g:actionSubmit class="delete"
		title="Click to delete this index."
		onclick="return confirm('Are you sure?');" value="Delete" /> </span>
</g:form></div>
</div>
</body>
</html>
