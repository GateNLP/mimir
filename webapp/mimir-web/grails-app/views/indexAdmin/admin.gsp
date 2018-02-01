<%@ page import="gate.mimir.web.Index"%>
<%@ page import="gate.mimir.web.LocalIndex"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<link rel="stylesheet" href="${resource(dir:'css',file:'progressbar.css')}" />
<r:require module="jquery"/>
<title>Mimir index &quot;${indexInstance.name}&quot;</title>
<mimir:load/>

</head>
<body>
  <div class="nav">
    <span class="menuButton"> <g:link class="home"
        controller="mimirStaticPages" action="admin">Admin Home</g:link>
   </span>
  </div>
  <div class="body">
    <g:if test="${flash.message}">
      <div class="message">
        ${flash.message}
      </div>
    </g:if>
    <h1>
      Mimir index &quot;${indexInstance.name}&quot;
    </h1>
    <div class="dialog">
      <table>
        <tbody>
          <tr class="prop">
            <td valign="top" class="name">Index Name:</td>
            <td valign="top" class="value">
              ${indexInstance.name}
            </td>
          </tr>
          <tr class="prop">
            <td valign="top" class="name">Index URL:</td>
            <td valign="top" class="value"><mimir:createIndexUrl
                indexId="${indexInstance.indexId}" /></td>
          </tr>
          <tr class="prop">
            <td valign="top" class="name">State:</td>
            <td valign="top" class="value">
              ${indexInstance.state}
            </td>
          </tr>
          <tr style="vertical-align:top">
            <td>Annotations indexed:</td>
            <td><mimir:revealAnchor id="annotsConf">Detail...</mimir:revealAnchor>
            <mimir:revealBlock id="annotsConf"><mimir:indexAnnotationsConfig index="${indexInstance}"/></mimir:revealBlock>
            </td>
          </tr>
          <tr style="vertical-align:top">
            <td>Custom CSS styles:</td>
            <td>
            <g:if test="${indexInstance.css}">
              <mimir:revealAnchor id="customCss">Detail...</mimir:revealAnchor>
              <mimir:revealBlock id="customCss"><pre>${indexInstance.css}</pre></mimir:revealBlock>            
            </g:if>
            <g:else>none</g:else>
            </td>
          </tr>
                    
          <g:if test="${indexInstance instanceof LocalIndex}" >
            <tr class="prop">
              <td valign="top" class="name">Scorer:</td>
              <td valign="top" class="value">
               ${indexInstance.scorer?:'No Scoring'}</td>
            </tr>          
          </g:if>          
          <g:if test="${indexInstance.state == Index.READY}">
            <tr class="prop">
              <td colspan="2">
                <g:link controller="search" action="index"
                  params="[indexId:indexInstance.indexId]"
                  title="Search this index">Search this index using the web interface.</g:link><br />
                <g:link controller="search"
                  action="help" params="[indexId:indexInstance.indexId]"
                  title="Search this index">Search this index using the XML service interface.</g:link><br />
                <g:link action="deletedDocuments"
                  params="[indexId:indexInstance.indexId]"
                  title="Add or remove 'deleted' markers for documents">Manage deleted documents.</g:link></td>
            </tr>
          </g:if>
        </tbody>
      </table>
    </div>
    <div class="buttons">
      <table>
        <tr>
          <g:form
            controller="${grails.util.GrailsNameUtils.getPropertyNameRepresentation(indexInstance.getClass())}">
            <input type="hidden" name="id" value="${indexInstance?.id}" />
            <td><span class="button"> <g:actionSubmit class="show"
                  action="Show" value="Details"
                  title="Click to see more information about this index." /> </span>
            </td>
            <td><span class="button"> <g:actionSubmit class="edit"
                  value="Edit" title="Click to modify this index." /> </span>
            </td>
            <td><span class="button"> <g:actionSubmit class="delete"
                  title="Click to delete this index." value="Delete" />
            </span>
            </td>
          </g:form>
          <g:if test="${indexInstance.state == Index.READY}">
            <g:form action="sync" controller="indexAdmin"
                  params="[indexId:indexInstance.indexId]" method="POST">
              <td><span class="button"><input type="submit" class="save" value="Sync to Disk" 
              title="Request all documents in memory are saved to disk ASAP." />
              </span></td>
            </g:form>
          </g:if>            
        </tr>
      </table>
    </div>
  </div>
</body>
</html>
