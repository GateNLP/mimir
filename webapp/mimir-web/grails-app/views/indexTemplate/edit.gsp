
<%@ page import="gate.mimir.web.IndexTemplate" %>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
        <meta name="layout" content="mimir" />
        <title>Edit IndexTemplate</title>
    </head>
    <body>
        <div class="nav">
            <span class="menuButton"><g:link class="home" controller="mimirStaticPages" action="index">Home</g:link></span>
            <span class="menuButton"><g:link class="list" action="list">IndexTemplate List</g:link></span>
            <span class="menuButton"><g:link class="create" action="create">New IndexTemplate</g:link></span>
        </div>
        <div class="body">
            <h1>Edit IndexTemplate</h1>
            <g:if test="${flash.message}">
            <div class="message">${flash.message}</div>
            </g:if>
            <g:hasErrors bean="${indexTemplateInstance}">
            <div class="errors">
                <g:renderErrors bean="${indexTemplateInstance}" as="list" />
            </div>
            </g:hasErrors>
            <g:form method="post" >
                <input type="hidden" name="id" value="${indexTemplateInstance?.id}" />
                <input type="hidden" name="version" value="${indexTemplateInstance?.version}" />
                <div class="dialog">
                    <table>
                        <tbody>
                        
                            <tr class="prop">
                                <td valign="top" class="name">
                                    <label for="name">Name:</label>
                                </td>
                                <td valign="top" class="value ${hasErrors(bean:indexTemplateInstance,field:'name','errors')}">
                                    <input type="text" id="name" name="name" value="${fieldValue(bean:indexTemplateInstance,field:'name')}"/>
                                </td>
                            </tr> 
                        
                            <tr class="prop">
                                <td valign="top" class="name">
                                    <label for="comment">Comment:</label>
                                </td>
                                <td valign="top" class="value ${hasErrors(bean:indexTemplateInstance,field:'comment','errors')}">
                                    <input type="text" id="comment" name="comment" value="${fieldValue(bean:indexTemplateInstance,field:'comment')}"/>
                                </td>
                            </tr> 
                        
                            <tr class="prop">
                                <td valign="top" class="name">
                                    <label for="configuration">Configuration:</label>
                                </td>
                                <td valign="top" class="value ${hasErrors(bean:indexTemplateInstance,field:'configuration','errors')}">
                                    <textarea rows="20" cols="40" name="configuration">${fieldValue(bean:indexTemplateInstance, field:'configuration')}</textarea>
                                </td>
                            </tr> 
                        
                        </tbody>
                    </table>
                </div>
                <div class="buttons">
                    <span class="button"><g:actionSubmit class="save" value="Update" /></span>
                    <span class="button"><g:actionSubmit class="delete" onclick="return confirm('Are you sure?');" value="Delete" /></span>
                </div>
            </g:form>
        </div>
    </body>
</html>
