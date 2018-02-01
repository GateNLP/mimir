<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
    <head>
        <title><g:layoutTitle default="Mimir" /></title>
        <asset:javascript src="application.js"/>
        <asset:stylesheet src="mimir.css"/>
        <link rel="shortcut icon" href="${assetPath(src: 'mimir-favicon.ico')}" type="image/x-icon">
        <g:layoutHead />
        <%-- Add any custom CSS content provided by the page. --%>
        <style type="text/css">
        <g:pageProperty name="page.customCss" default="" />
        </style>
    </head>
<body>
	<div id="spinner" class="spinner" style="display: none;">
		<img src="${resource(dir:'images',file:'spinner.gif')}" alt="Spinner" />
	</div>
	<div id="paneHeader" class="paneHeader">
		<table>
			<tbody>
				<tr>
					<td valign="top" colSpan="3">
						<div align="left">
							<mimir:logo />
						</div></td>
					<td valign="top" width="20%">
						<div align="right">
							<mimir:powered/>
							<sec:ifLoggedIn>
								<br />You are logged in as <strong><sec:username /></strong>. (<g:link
									controller="logout">Log out</g:link>)</sec:ifLoggedIn>
						</div></td>
				</tr>
			</tbody>
		</table>
	</div>
	<div id="content">
		<g:layoutBody />
		<div align="center">
			<p>M&iacute;mir <mimir:version />, &copy; <a href="http://gate.ac.uk">GATE</a> ${Calendar.getInstance().get(Calendar.YEAR)}.
			</p>
		</div>
	</div>
</body>
</html>
