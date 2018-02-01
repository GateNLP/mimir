<html>
<head>
<title>M&iacute;mir (<mimir:createRootUrl />) Security
	Administration Page</title>
<meta name="layout" content="mimir" />
<mimir:load />
</head>
<body>
	<div class="body">
		<h1>
			M&iacute;mir (
			<mimir:createRootUrl />
			) Security Administration Page
		</h1>

		<g:if test="${flash.message}">
			<div class="message">
				${flash.message}
			</div>
		</g:if>

		<g:form action="savePasswords" method="POST">

			<table>
				<g:if test="${!adminUser?.password?.equals('not set')}">
					<tr>
						<td>
							<p>
								In order to change the security passwords you need to enter to
								current password for the <strong>admin</strong> user.
							</p></td>
					</tr>
					<tr>
						<td>Current admin password: <g:passwordField
								name="oldAdminPass" value="${params.oldAdminPass}" />
						</td>
					</tr>
				</g:if>
				<g:else>
					<tr>
						<td>
							<p>Welcome to your new M&iacute;mir instance! Before you can
								start using it, you need to set a few security options, which
								you can do on this page. Please read the help messages carefully
								and make sure you remember the passwords you set here and you
								will need them to administer, manage and access your
								M&iacute;mir server!</p></td>
					</tr>
				</g:else>

				<tr>
					<td><h2>Server Administration</h2>
						<p>
							The <strong>admin</strong> account has access to all functions of
							this server and <strong>must have a password</strong>. Please 
							choose a strong password and make sure you can remember it, as 
							there is no way to retrieve it if lost!</p>
						<g:if test="${!adminUser?.password?.equals('not set')}">
						  <p>If you don't want to change the previously supplied password 
						  for the admin account, simply leave these fields empty.</p>
						</g:if>	
					  </td>
				</tr>
				<tr>
					<td>New <strong>admin</strong> password: <g:passwordField
							name="newAdminPass" value="${params.newAdminPass}" /> ...and
						again: <g:passwordField name="newAdminPassRep"
							value="${params.newAdminPassRep}" /></td>
				</tr>

				<tr>
					<td><h2>Index Management</h2>
					  <p>The <strong>manager</strong> account is used to manage the 
					  contents of newly-created indexes. These credentials can be be used
					  to add new documents to an index and to close the index after all 
					  the required documents have been added.</p>
					  <p>If you want these operations to <strong>not</strong> be 
					  password-protected, then simply leave the following input fields 
					  empty.</p>
					  <p>If you do supply a password, you will need to use it (along
					  with the <strong>manager</strong> user name) when adding data to any
					  of the indexes, e.g. when running an annotation job with the output
					  being sent to an index running on this server. </p>  
					</td>
				</tr>
				<tr>
					<td>New <strong>manager</strong> password: <g:passwordField
							name="newManagerPass" value="${params.newManagerPass}" /> ...and
						again: <g:passwordField name="newManagerPassRep"
							value="${params.newManagerPassRep}" /></td>
				</tr>

				<tr>
					<td>
						<h2>Access to Search Pages</h2>
						<p>The <strong>user</strong> account can search any of the indexes
						on this server. If you want search functionality to be 
						password-protected then supply a password below, otherwise leave 
						these input fields empty.</p> 
					</td>
				</tr>
				<tr>
					<td>New <strong>user</strong> password: <g:passwordField
							name="newUserPass" value="${params.newUserPass}" /> ...and
						again: <g:passwordField name="newUserPassRep"
							value="${params.newUserPassRep}" />
					</td>
				</tr>
				<tr>
				  <td><g:submitButton name="Save" value="Save" /></td>
				</tr>
			</table>
		</g:form>
	</div>
</body>
</html>
