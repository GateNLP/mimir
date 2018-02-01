
<%@ page import="gate.mimir.web.LocalIndex"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<title>Edit LocalIndex</title>
</head>
<body>
<div class="nav">
  <span class="menuButton"> <g:link class="home"
        controller="mimirStaticPages" action="admin">Admin Home</g:link>
  </span>
</div>
<div class="body">
<h1>Edit LocalIndex</h1>
<g:if test="${flash.message}">
	<div class="message">
	${flash.message}
	</div>
</g:if> <g:hasErrors bean="${localIndexInstance}">
	<div class="errors"><g:renderErrors bean="${localIndexInstance}"
		as="list" /></div>
</g:hasErrors> <g:form method="post">
	<input type="hidden" name="id" value="${localIndexInstance?.id}" />
	<input type="hidden" name="version"
		value="${localIndexInstance?.version}" />
	<div class="dialog">
	<table>
		<tbody>

			<g:set var="i" value="${0}" />
			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="indexId">Index ID:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:localIndexInstance,field:'indexId','errors')}">
				<input type="text" id="indexId" name="indexId"
					value="${fieldValue(bean:localIndexInstance,field:'indexId')}" />
				</td>
			</tr>

			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="name">Name:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:localIndexInstance,field:'name','errors')}">
				<input type="text" id="name" name="name"
					value="${fieldValue(bean:localIndexInstance,field:'name')}" /></td>
			</tr>

			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="state">State:</label>
				</td>
				<td valign="top"
					class="value ${hasErrors(bean:localIndexInstance,field:'state','errors')}">
				${fieldValue(bean:localIndexInstance,field:'state')}
				</td>
			</tr>

			<tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
				<td valign="top" class="name"><label for="indexDirectory">Index
				Directory:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:localIndexInstance,field:'indexDirectory','errors')}">
				<input type="text" id="indexDirectory" name="indexDirectory"
					value="${fieldValue(bean:localIndexInstance,field:'indexDirectory')}" />
				</td>
			</tr>

      <tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
        <td valign="top" class="name"><label for="scorer">Scorer:</label></td>
        <td valign="top"
          class="value ${hasErrors(bean:localIndexInstance,field:'scorer','errors')}">
        <g:select from="${grailsApplication.config.gate.mimir.scorers.keySet()}"
            value="${fieldValue(bean:localIndexInstance,field:'scorer')}" 
            noSelection="${[null:'No Scoring']}" 
            name="scorer"/>
      </tr>
			
      <tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
        <td valign="top" class="name"><label for="uriIsExternalLink">Document
      URIs are external links:</label></td>
        <td valign="top" class="value"><g:checkBox
          name="uriIsExternalLink"
          value="${localIndexInstance.uriIsExternalLink}" /></td>
      </tr>

      <tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
        <td valign="top" class="name"><label for="subBindingsEnabled">Sub-bindings enabled:</label></td>
        <td valign="top" class="value"><g:checkBox
          name="subBindingsEnabled"
          value="${localIndexInstance.subBindingsEnabled?:false}" /></td>
      </tr>
      
      <tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
        <td valign="top" class="name"><label for="timeBetweenBatches">Time (in milliseconds) between batches:</label></td>
        <td valign="top" class="value"><g:textField
          name="timeBetweenBatches" value="${timeBetweenBatches}" /></td>
      </tr>
      
      <tr class="prop ${(++i % 2) ? 'even' : 'odd'}">
        <td valign="top" class="name"><label for="css">Custom CSS styles:</label></td>
        <td valign="top"
          class="value ${hasErrors(bean:localIndexInstance,field:'css','errors')}">
        <textarea id="css" name="css" rows="20" cols="50"
          placeholder="CSS styles used when displaying search results"
          >${fieldValue(bean:localIndexInstance,field:'css')}</textarea>
        </td>
      </tr>

		</tbody>
	</table>
	</div>
	<div class="buttons"><span class="button"> <g:actionSubmit
		class="save" value="Save" action="Update"
		title="Click to save your changes." /> </span> <span class="button"> <g:actionSubmit
		class="delete" onclick="return confirm('Are you sure?');"
		value="Delete" /> </span></div>
</g:form></div>
</body>
</html>
