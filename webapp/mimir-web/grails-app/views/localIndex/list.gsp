<%@ page import="gate.mimir.web.LocalIndex" %>
<%@ page import="gate.mimir.web.Index" %>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
        <meta name="layout" content="mimir" />
        <title>Local Index List</title>
    </head>
    <body>
        <div class="nav">
            <span class="menuButton"><g:link class="home" controller="mimirStaticPages" action="admin">Admin Home</g:link></span>
            <span class="menuButton"><g:link class="create" action="create">Create New Local Index</g:link></span>
            <span class="menuButton"><g:link class="create" action="importIndex">Import Existing Index</g:link>
        </div>
        <div class="body">
            <h1>Local Index List</h1>
            <g:if test="${flash.message}">
            <div class="message">${flash.message}</div>
            </g:if>
            <div class="list">
                <table>
                    <thead>
                        <tr>
                   	        <g:sortableColumn property="name" title="Name" />
                   	        <g:sortableColumn property="state" title="State" />
                   	        <g:sortableColumn property="indexDirectory" title="Index Directory" />
                            <g:sortableColumn property="scorer" title="Scorer" />                   	        
                            <th>&nbsp;</th>
                        </tr>
                    </thead>
                    <tbody>
                    <g:each in="${localIndexInstanceList}" status="i" var="localIndexInstance">
                        <tr class="${(i % 2) == 0 ? 'odd' : 'even'}">
                            <td><g:link action="show" id="${localIndexInstance.id}">${fieldValue(bean:localIndexInstance, field:'name')}</g:link></td>
                            <td>${fieldValue(bean:localIndexInstance, field:'state')}</td>
                            <td>${fieldValue(bean:localIndexInstance, field:'indexDirectory')}</td>
                            <td>${fieldValue(bean:localIndexInstance, field:'scorer')}</td>
                        		<td>
						                  <g:if test='${localIndexInstance.state == Index.READY}'>
						                    <g:link controller="gus" action="search"
						                      params="[indexId:localIndexInstance.indexId]">Search this index.</g:link>
						                  
						                  </g:if>
						                  <g:else>
						                    &nbsp;
						                  </g:else>       
                        		
                        		</td>
                        </tr>
                    </g:each>
                    </tbody>
                </table>
            </div>
            <div class="paginateButtons">
                <g:paginate total="${localIndexInstanceTotal}" />
            </div>
        </div>
    </body>
</html>
