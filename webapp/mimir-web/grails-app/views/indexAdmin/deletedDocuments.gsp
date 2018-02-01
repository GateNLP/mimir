<%@ page import="gate.mimir.web.Index" %>
<html>
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="layout" content="mimir" />
    <title>Manage deleted documents - Mimir index &quot;${indexInstance.name}&quot;</title>
  </head>
  <body>
    <div class="nav">
      <span class="menuButton">
        <g:link class="home" controller="mimirStaticPages" action="index">Home</g:link>
      </span>
      <span class="menuButton">
        <g:link class="list" action="admin" params="[indexId:indexInstance.indexId]">Back to index admin page</g:link>
      </span>
    </div>
  
    <div class="body">
      <g:if test="${flash.message}">
        <div class="message">${flash.message}</div>
      </g:if>
      <h1>Mimir index &quot;${indexInstance.name}&quot;</h1>
      <h2>Manage deleted documents</h2>
      <g:hasErrors bean="${documents}">
      	<div class="errors">
          <g:renderErrors bean="${documents}" as="list" />
      	</div>
      </g:hasErrors>
      <g:form method="post" action="doDeleteOrUndelete" params="[indexId:indexInstance.indexId]">
        <div class="dialog">
          <table>
            <tbody>
              <tr class="prop">
                <td valign="top" class="name"><label for="documentIds"
                  title="Separate individual IDs with spaces or newlines">Document IDs:</label></td>
                <td valign="top"
                  class="value ${hasErrors(bean:documents,field:'documentIds','errors')}">
                  <g:textArea name="documentIds" value="${fieldValue(bean:documents,field:'documentIds')}"
                    rows="10" cols="40" title="Separate individual IDs with spaces or newlines" />
				</td>
              </tr>
              <tr class="prop">
                <td valign="top" class="name"><label for="operation" >Mark documents as:</label></td>
                <td valign="top"
                  class="value ${hasErrors(bean:documents,field:'operation','errors')}">
                  <g:radioGroup name="operation" values="['delete', 'undelete']" labels="['deleted', 'not deleted']"
                    value="${fieldValue(bean:documents,field:'operation') ?: 'delete'}">
                    ${it.radio} ${it.label}<br>
                  </g:radioGroup>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="buttons">
          <span class="button"><g:submitButton name="submit" value="Submit" class="save" /></span>
        </div>
      </g:form>
    </div>
  </body>
</html>