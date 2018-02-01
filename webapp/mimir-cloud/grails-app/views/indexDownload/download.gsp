<%@page import="gate.mimir.util.IndexArchiveState"%>
<html>
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
		<meta name="layout" content="mimir" />
		<link rel="stylesheet" href="${resource(dir:'css',file:'progressbar.css', plugin:'mimir-web')}" />
		<title>Downloading M&iacute;mir index &quot;${indexArchive?.theIndex?.name}&quot;</title>

	<g:if test="${indexArchive?.state == IndexArchiveState.PENDING}">
	  <meta http-equiv="refresh" content="10">
	</g:if>

	</head>
	<body>
		<div class="nav">
			<span class="menuButton">
				<g:link class="home" controller="mimirStaticPages" action="index">Home</g:link>
			</span>
		</div>
		<div class="body">
			<g:if test="${flash.message}">
				<div class="message">${flash.message}</div>
			</g:if>
			<h1>Downloading M&iacute;mir index &quot;${indexArchive?.theIndex?.name}&quot;</h1>
			
			<div class="dialog">

			<g:if test="${indexArchive?.state == IndexArchiveState.AVAILABLE}">
				<g:if test="${files}">
				<p>Your index archive comprises the following files. Please download 
				them all to the same directory then follow the instructions included 
				in the README.txt file:</p>
				  <table>
				  <g:each in="${files}" var="aFile">
				    <tr>
				    <td><g:link action="getFile" id="${indexArchive?.id}" 
				    		params="[fileName:aFile.name]">${aFile.name}</g:link></td>
				    <td>(${aFile.size})</td>
				    </tr>		
				  </g:each>
				  </table>
				  <p>Alternatively, if you prefer to use a download manager, you can 
				  get a list of URLs from <g:link action="urlList" 
				  id="${indexArchive?.theIndex?.id}">this link</g:link>. Note that these
				  URLs are only available to the admin user, so you will need to 
				  configure your download manager to supply the correct credentials.</p>
				</g:if>
			</g:if>
			<g:elseif test="${indexArchive?.state == IndexArchiveState.PENDING}">
				<p>The index is currently being archived for download. Please wait.</p>
				<mimir:progressbar value="${(double)indexArchive?.progress / 100}"/>
				<g:javascript> </g:javascript>
			</g:elseif>
			<g:elseif test="${indexArchive?.state == IndexArchiveState.FAILED}">
				<p>The archiving of the index for download has failed.</p>
				<g:if test="${indexArchive?.cause}"><p>The reason given was:
				&quot;${indexArchive?.cause}&quot;.</p></g:if>
				<p>Please try again later or contact your administrator.</p>
			</g:elseif>
			</div>
		</div>
	</body>
</html>
