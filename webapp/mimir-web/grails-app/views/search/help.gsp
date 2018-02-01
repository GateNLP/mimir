<%@ page import="gate.mimir.web.SearchController" %>
<html>
	<head>
		<title>
			M&iacute;mir XML Service (<mimir:createRootUrl />)
		</title>
		<meta name="layout" content="mimir" />
	</head>
	<body>
		<div class="body">
			<h1>M&iacute;mir XML Service (<mimir:createRootUrl />)</h1>
			<g:if test="${flash.message}">
				<div class="message">${flash.message}</div>
			</g:if>

			<p>This is the M&iacute;mir search Web Service on 
			<mimir:createRootUrl />, searching index <b>&quot;${index.name}&quot;</b>.</p>
	<p>You can also <g:link controller="search" action="index" 
	  params="[indexId:index.indexId]" 
	  title="Search this index">search this index using the web interface</g:link>.</p>
	<p>A call to this service consists of a normal HTTP connection to a URL like:
	<mimir:createIndexUrl indexId="${index.indexId}" />/search/<b>action</b>, 
	where the action value is the name of one of the supported actions, described below. 
	</p>	
  <p>
	Parameters may be supplied as query parameters with a GET request or in
	normal application/x-www-form-urlencoded form in a POST request.
	Alternatively, they may be supplied as XML (if the request content type
	is
	text/xml or application/xml) of the form:</p>
  <pre>
&lt;request xmlns=&quot;${SearchController.MIMIR_NAMESPACE}&quot;&gt;
  &lt;firstParam&gt;value&lt;/firstParam&gt;
  &lt;secondParam&gt;value&lt;/secondParam&gt;
&lt;/request&gt;</pre>

  <p>The first request to the service will return a session cookie, which
      must be passed back with all subsequent requests.</p>

  <div class="action-box">
    <span class="action-name">help</span>
    <span class="action-desc">Prints this help message.</span>
    <div class="list"><b>Parameters:</b> none</div>
    <b>Returns:</b> this help page.
  </div>
          
  <div class="action-box">
    <span class="action-name">postQuery</span>
    <span class="action-desc">Action for starting a new query.</span>
    <div class="list"><b>Parameters:</b>
	    <table>
	      <tr>
	      <td>queryString</td>
	      <td>the text of the query.</td>
	      </tr>
	    </table>
    </div>
    <b>Returns:</b> the ID of the new query, if successful.
  </div>

  <div class="action-box">
    <span class="action-name">documentsCount</span>
    <span class="action-desc">Gets the number of result documents.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
      </table>
    </div>
    <b>Returns:</b> <code>-1</code> if the search has not yet completed, or the 
    total number of result documents otherwise. 
  </div>

  <div class="action-box">
    <span class="action-name">documentsCurrentCount</span>
    <span class="action-desc">Gets the number of result documents found so far.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
      </table>
    </div>
    <b>Returns:</b> the number of result documents found so far. After the search 
   completes, the result returned by this call is identical to that of 
   <code>documentsCount</code>.
  </div>

  <div class="action-box">
    <span class="action-name">documentId</span>
    <span class="action-desc">Obtains the document ID for the document at a
    given rank (position in the results list).</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
        <tr>
        <td>rank</td>
        <td>the rank (position in the results list) for the requested document.</td>
        </tr>        
      </table>
    </div>
    <b>Returns:</b> ID of the requested document (an integer value).
  </div>

  <div class="action-box">
    <span class="action-name">documentScore</span>
    <span class="action-desc">Obtains the score for the document at a
    given rank (position in the results list).</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
        <tr>
        <td>rank</td>
        <td>the rank (position in the results list) for the requested document.</td>
        </tr>
      </table>
    </div>
    <b>Returns:</b> the score for the requested document, a floating point 
    (double precision) value.
  </div>
  
  <div class="action-box">
    <span class="action-name">documentHits</span>
    <span class="action-desc">Action for obtaining the hits for a given 
    document.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
        <tr>
        <td>rank</td>
        <td>the rank (position in the results list) for the requested document.</td>
        </tr>
      </table>
    </div>
    <p><b>Returns:</b> a list of hits, each defined by a document ID, a 
    termPosition, and a length.</p>
  </div>
  
  <div class="action-box">
    <span class="action-name">documentText</span>
    <span class="action-desc">Action for obtaining [a segment of] the text of a document.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of an active query, to be used as a context for this call.</td>
        </tr>
        <tr>
        <td>rank</td>
        <td>the rank (position in the results list) for the requested document.</td>
        </tr>
        <tr>
        <td>termPosition</td>
        <td>(optional) the index of the first token to be returned,
      defaults to 0 if omitted, i.e. start from the beginning of the
      document.</td>
        </tr>
        <tr>
        <td>length</td>
        <td>(optional) the number of tokens (and spaces) to be returned.
      If omitted, all tokens from position to the end of the document will
      be returned.</td>
        </tr>          
      </table>
      <p><b>Returns:</b> the text of the document [segment] requested, as a 
      list of tokens and space pairs.</p>
    </div>
  </div>

  <div class="action-box">
    <span class="action-name">documentMetadata</span>
    <span class="action-desc">Action for obtaining the document metadata.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of an active query, to be used as a context for this call.</td>
        </tr>
        <tr>
        <td>rank</td>
        <td>the rank (position in the results list) for the requested document.</td>
        </tr>
        <tr>
        <td>fieldNames</td>
        <td>(optional) a comma-separated list of other field names to be returned.</td>
        </tr>        
      </table>
      <p><b>Returns:</b></p> 
      <ul>
        <li>the document URI</li>
        <li>the document title</li>
        <li>the values for the other field names, if requested and present</li>
      </ul>
    </div>
  </div>  
  
  <div class="action-box">
    <span class="action-name">documentMetadata</span>
    <span class="action-desc">Action for obtaining the document metadata.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>documentId</td>
        <td>the document ID (as obtained from a call to the 
        <strong>documentId</strong> action) for the requested document.</td>
        </tr>
        <tr>
        <td>fieldNames</td>
        <td>(optional) a comma-separated list of other field names to be returned.</td>
        </tr>        
      </table>
      <p><b>Returns:</b></p> 
      <ul>
        <li>the document URI</li>
        <li>the document title</li>
        <li>the values for the other field names, if requested and present</li>
      </ul>
    </div>
  </div>    
  
  <div class="action-box">
    <span class="action-name">renderDocument</span>
    <span class="action-desc">Renders the document text and hits, in the context
    of a given query. The html of the document is rendered directly to the 
    response stream of this connection.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
        <tr>
        <td>rank</td>
        <td>the rank (position in the results list) for the requested document.</td>
        </tr>
      </table>
    </div>
    <b>Returns:</b> the HTML source of the rendered document.
  </div>

  <div class="action-box">
    <span class="action-name">renderDocument</span>
    <span class="action-desc">Renders the document text outside of the context 
    of any given query. The html of the document is rendered directly to the 
    response stream of this connection.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>documentId</td>
        <td>the document ID (as obtained from a call to the 
        <strong>documentId</strong> action) for the requested document. Finding
        documents by ID is outside the scope of any query, so there will be no
        hit highlights.</td>
        </tr>
      </table>
    </div>
    <b>Returns:</b> the HTML source of the rendered document.
  </div>

  <div class="action-box">
    <span class="action-name">close</span>
    <span class="action-desc">Action for releasing a query.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
      </table>
    </div>
    <b>Returns:</b> the exit state (success or error).
  </div>

</div>
</body>
</html>
			