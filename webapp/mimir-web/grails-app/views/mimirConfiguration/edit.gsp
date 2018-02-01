

<%@ page import="gate.mimir.web.MimirConfiguration"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<g:set var="entityName"
	value="${message(code: 'mimirConfiguration.label', default: 'MimirConfiguration')}" />
<mimir:load />
<title><g:message code="default.edit.label" args="[entityName]" /></title>
</head>
<body>

  <div class="nav">
    <span class="menuButton">
      <a class="home" href="${createLink(controller:'mimirStaticPages', action:'admin')}">
      Back to Admin Page</a>
    </span>
  </div>

	<div class="body">
		<h1>
			<g:message code="default.edit.label" args="[entityName]" />
		</h1>
		<g:if test="${flash.message}">
			<div class="message">
				${flash.message}
			</div>
		</g:if>
		<g:hasErrors bean="${mimirConfigurationInstance}">
			<div class="errors">
				<g:renderErrors bean="${mimirConfigurationInstance}" as="list" />
			</div>
		</g:hasErrors>
		<g:form method="post">
			<g:hiddenField name="id" value="${mimirConfigurationInstance?.id}" />
			<g:hiddenField name="version"
				value="${mimirConfigurationInstance?.version}" />
			<div class="dialog">
			  <div class="list">
				<table>
				  <thead>
		       <tr><th>Parameter</th><th>Value</th></tr>
		      </thead>
					<tbody>

						<tr class="prop">
							<td valign="top" class="name"><label
								for="indexBaseDirectory"><g:message
										code="mimirConfiguration.indexBaseDirectory.label"
										default="Index Base Directory" /></label>
										(<mimir:revealAnchor  id="indexBaseDirHelp">?</mimir:revealAnchor>)<br />
										</td>
							<td valign="top"
								class="value ${hasErrors(bean: mimirConfigurationInstance, field: 'indexBaseDirectory', 'errors')}">
								<g:textField name="indexBaseDirectory" size="100"
									value="${fieldValue(bean: mimirConfigurationInstance, field: 'indexBaseDirectory')}" />
							</td>
						</tr>
            <tr><td colspan="2"><mimir:revealBlock id="indexBaseDirHelp">
                    <p>Directory used for storing indexes. Pick a location that exists and
                    is writeable by the M&iacute;mir process.</p></mimir:revealBlock></td> </tr>
					</tbody>
				</table>
				</div>
			</div>
			<div class="buttons">
				<span class="button"><g:actionSubmit class="save"
						action="update"
						value="${message(code: 'default.button.update.label', default: 'Update')}" /></span>
        <span class="button"><g:actionSubmit class="delete"
            action="show"
            value="Cancel" /></span>						
			</div>
		</g:form>
	</div>
</body>
</html>
