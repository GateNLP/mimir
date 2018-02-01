<%@ page import="gate.mimir.web.Index" %>
<%@ page import="gate.mimir.web.LocalIndex" %>
<%@ page import="gate.mimir.web.RemoteIndex" %>
<%@ page import="gate.mimir.web.FederatedIndex" %>
<%@ page import="gate.mimir.web.MimirConfiguration" %>

<g:set var="localIdxCnt" value="${LocalIndex.count()}" />
<g:set var="remoteIdxCnt" value="${RemoteIndex.count()}" />
<g:set var="fedIdxCnt" value="${FederatedIndex.count()}" />

<html>
  <head>
    <title>
      M&iacute;mir (<mimir:createRootUrl />) Administration Page
    </title>
    <meta name="layout" content="mimir" />
    <mimir:load />
  </head>
  <body>
    <div class="body">
    <h1>M&iacute;mir (<mimir:createRootUrl />) Administration Page</h1>
    
    <g:if test="${flash.message}">
      <div class="message">${flash.message}</div>
    </g:if>
    
    <g:if test="${MimirConfiguration.count() == 0}">
      <h2>Configuration</h2>
      <p>You need to <g:link controller="mimirConfiguration" action="edit" id="">configure</g:link> your local M&iacute;mir instance.</p>
    </g:if>
    <g:else>
    <h2>Configuration</h2>
    <p><g:link controller="mimirConfiguration" action="show" id="">Show/edit</g:link> 
    the configuration for your M&iacute;mir instance.</p>
    
    <h2>Indexes <span style="font-size:small;" title="Click for more information!">(<mimir:revealAnchor id="help1">?</mimir:revealAnchor>)</span></h2>
    <mimir:revealBlock id="help1">
    <p class="help">There are several types of indexes. On this page you can 
    get an overview of all the indexes available to this M&iacute;mir instance.
    You can also manage the indexes by clicking on the appropriate links: click 
    on an index type heading to manage all indexes of that type; click on the 
    link for a particular index, to manage that index.</p></mimir:revealBlock>
    <p>Below you can see all the indexes configured for this instance of 
    M&iacute;mir, listed by type.</p>
    
    
    <h3>Local Indexes <span style="font-size:small;" title="Click for more information!">(<mimir:revealAnchor id="help2">?</mimir:revealAnchor>)</span></h3>
    <mimir:revealBlock id="help2">
    <p class="help">Local indexes are indexes managed by this instance, and 
    stored on the same server. Click the link above to manage the list of
    local indexes; click on any index name below to manage that particular 
    index.</p></mimir:revealBlock>
    <g:if test="${localIdxCnt > 0}">
      <p>The following local indexes are configured:</p>
      <g:render template="/common/indexLinks" plugin="mimir-web"
                model="[indexes:LocalIndex.list()]" />
    </g:if>
    <g:else>
      <p>There are no local indexes configured in this M&iacute;mir instance.</p>
    </g:else>
    <p>You can <g:link controller="localIndex" action="create">create  a new local index</g:link>, or
    <g:link controller="localIndex" action="importIndex">import an existing index</g:link>.</p>
    
    <h3>Remote Indexes <span style="font-size:small;" title="Click for more information!">(<mimir:revealAnchor id="help3">?</mimir:revealAnchor>)</span></h3>
    <mimir:revealBlock id="help3"><p class="help">Remote indexes are indexes managed by a different 
    M&iacute;mir instance, that are made available in this instance through a 
    remote connection. Click the title above to manage the list of
    remote indexes; click on any index name below to manage that particular 
    index.</p></mimir:revealBlock>
        
    <g:if test="${remoteIdxCnt > 0}">
      <p>The following remote indexes are configured:</p>
      <g:render template="/common/indexLinks" plugin="mimir-web"
                model="[indexes:RemoteIndex.list()]" />
    </g:if>
    <g:else>
      <p>There are no remote indexes configured in this M&iacute;mir instance.</p>
    </g:else>
    <p>You can <g:link controller="remoteIndex" action="create">connect to a new remote index</g:link>.</p>
    
    <h3>Federated Indexes <span style="font-size:small;" title="Click for more information!">(<mimir:revealAnchor id="help4">?</mimir:revealAnchor>)</span></h3>
    <mimir:revealBlock id="help4"><p class="help">Federated indexes are indexes comprising a set of other 
    sub-indexes. The sub-indexes may be local, remote, or even federated 
    indexes themselves. Click the title above to manage the list of
    remote indexes; click on any index name below to manage that particular 
    index.</p></mimir:revealBlock>
    <g:if test="${fedIdxCnt > 0}">
      <p>The following federated indexes are configured:</p>
      <g:render template="/common/indexLinks" plugin="mimir-web"
                model="[indexes:FederatedIndex.list()]" />
    </g:if>
    <g:else>
      <p>There are no federated indexes configured in this M&iacute;mir instance.</p>
    </g:else>
    <p>You can <g:link controller="federatedIndex" action="create">create a new federated index</g:link>.</p>
    
    <h2>Index Templates <span style="font-size:small;" title="Click for more information!">(<mimir:revealAnchor id="help5">?</mimir:revealAnchor>)</span></h2>
    <mimir:revealBlock id="help5"><p class="help">Index templates are configurations for creating new 
    indexes.</p></mimir:revealBlock>    
    <p><g:link controller="indexTemplate" action="list">
              <span title="Click to edit index templates">Click here</span>
            </g:link>to manage the index templates.</p>    
    </g:else>

    </div>    
  </body>
</html>
