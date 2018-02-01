
<%@ page import="gate.mimir.web.Index" %>
<%@ page import="gate.mimir.web.LocalIndex" %>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
        <meta name="layout" content="mimir" />
        <title>Show LocalIndex</title>
    </head>
    <body>
        <div class="body">
            <h1>Delete Local Index?</h1>
            <g:if test="${message}">
            <div class="message">${message}</div>
            </g:if>
            <g:form action="delete">
            <div class="dialog">
                <table>
                    <tbody>
                        <tr class="prop">
                            <td valign="top" class="name">Id:</td>
                            
                            <td valign="top" class="value">${fieldValue(bean:localIndexInstance, field:'id')}</td>
                            
                        </tr>
                    
                        <tr class="prop">
                            <td valign="top" class="name">Name:</td>
                            
                            <td valign="top" class="value">${fieldValue(bean:localIndexInstance, field:'name')}</td>
                            
                        </tr>
                    
                        <tr class="prop">
                            <td valign="top" class="name">Index UUID:</td>
                            
                            <td valign="top" class="value">${fieldValue(bean:localIndexInstance, field:'indexId')}</td>
                            
                        </tr>

<%--
                        <tr class="prop">
                            <td valign="top" class="name">Index URL:</td>
                            
                            <td valign="top" class="value">
                              <mimir:createIndexUrl indexId="${localIndexInstance.indexId}" />
                            </td>
                        </tr>
--%>
                    
                        <tr class="prop">
                            <td valign="top" class="name">State:</td>
                            
                            <td valign="top" class="value">${fieldValue(bean:localIndexInstance, field:'state')}</td>
                            
                        </tr>
                    
                        <tr class="prop">
                            <td valign="top" class="name">Index Directory:</td>
                            
                            <td valign="top" class="value">${fieldValue(bean:localIndexInstance, field:'indexDirectory')}</td>
                            
                        </tr>
                        
                        <tr class="prop">
                            <td colspan="2" valign="top" class="name">
                              <g:checkBox name="deleteFiles" value="${false}"
                                onclick="return confirm('This will delete the index from disk and cannot be undone. Are you sure?');"/>
                              <label for="deleteFiles">Delete this directory from disk?</label>
                            </td>
                        </tr>
                    
                    </tbody>
                </table>
            </div>
            <div class="buttons">
                    <span class="button"><g:submitButton class="delete" onclick="return confirm('Click OK to perform deletion!');" name="delete" value="Delete" /></span>
                    <span class="button"><g:submitButton class="show" name="cancel" value="Cancel" /></span>
            </div>
            </g:form>
        </div>
    </body>
</html>
