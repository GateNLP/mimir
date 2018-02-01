/*
 *  MimirTagLib.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id$
 */
package gate.mimir.web;

import gate.mimir.search.QueryRunner;

import java.util.Locale;
import java.text.NumberFormat;

class MimirTagLib {
  
  def searchService
  
  static namespace = "mimir"

  /**
   * Pointer to the Grails plugin manager.
   */
  def pluginManager

  /**
   * Autowired
   */
  def mimirIndexService

  static NumberFormat percentNumberInstance = NumberFormat.getPercentInstance(Locale.US)

  static{
    percentNumberInstance.setMaximumFractionDigits(2)
    percentNumberInstance.setMinimumFractionDigits(2)
  }
  
  /**
   * Any page that uses the Mimir taglib must include this tag in its head 
   * section (in order to load the required supporting JS scripts). 
   */
  def load = { attrs, body ->
    out << 
'''
<script type="text/javascript">
<!--
    function toggle_visibility(id) {
       var e = document.getElementById(id);
       if(e.style.display == 'block')
          e.style.display = 'none';
       else
          e.style.display = 'block';
    }
//-->
</script>
'''
  }
  
  /**
   * Creates an Index URL value by generating a link to the &quot;index&quot; 
   * action of the &quot;indexManagement&quot; controller.
   * 
   * If provided, the <code>urlBase</code> attribute is used to override the 
   * the scheme, host name, and port parts of the produced URL.
   */
  def createIndexUrl = { attrs, body ->
    out << mimirIndexService.createIndexUrl(attrs)
  }

  /**
   * Creates an absolute URL pointing to the home page of the web app.
   */
  def createRootUrl = { attrs, body ->
    out << mimirIndexService.createRootUrl()
  }

  /**
   * Creates a span containing a progress bar.
   * The following attributes can be used:
   * <ul>
   *   <li><b>value</b>: the amount of progress that should be shown. This is a
   *   String representing a numeric value (parseable by Double.parseDouble) 
   *   representing a percentage.</li>
   *   <li><b>height</b>: the height of the progress bar. The value is a String
   *   in the style of the height CSS attribute, and defaults to 
   *   &quot;1em&quot;</li>
   *   <li><b>width</b>: the width of the progress bar. The value is a String
   *   in the style of the height CSS attribute, and defaults to 
   *   &quot;20em&quot;</li>
   *   <li><b>showtext</b>: should the value of the progress (as a percentage)
   *   be shown? Permitted values are <tt>true</tt> (default) and 
   *   <tt>false</tt>.</li> 
   *   <li><b>id</b>: a value for the id attribute of the span element created.
   *   If this is provided, the nested span class="progress" will also be given
   *   an id of &quot;<i>id</i>-bar&quot; and the span showing the percentage
   *   as text will be given an id of &quot;<i>id</i>-value&quot;, allowing
   *   them to be easily updated dynamically using JavaScript.</li> 
   * </ul>
   */
  def progressbar = { attrs, body ->
/* What we're trying to create looks like this:
<span id="myProgress">
  <div class="progressbar" style="width:20em; height:1em; display:inline-block; margin-right:5px;vertical-align:middle;">
    <span id="myProgress-bar" class="progress" style="width:30%">
    </span>
  </div><span id="myProgress-value" style="vertical-align:middle;">30%</span>
</span>    
*/
    double value = attrs.value
    String heightStr = attrs.height
    String widthStr = attrs.width
    String idStr = attrs.id
    boolean showText = true
    if(attrs.showText){
      showText = Boolean.parseBoolean(attrs.showText)
    }
    
    out << "<span"
    if(idStr) out << " id=\"" + idStr + "\""
    out << "> <div class=\"progressbar\" style=\"display:inline-block; "
    out << "height:" + (heightStr ? heightStr : "1em") + "; "
    out << "width:" + (widthStr ? widthStr : "20em") + "; "
    out << (showText ? "margin-right:5px; vertical-align:middle;\">" : "\">")
    out << "<span"
    if(idStr) out << " id=\"" + idStr + "-bar\""
    out << " class=\"progress\" style=\"width:"
    out << percentNumberInstance.format(value)
    out << "\"></span></div>"
    if(showText){
      out << "<span"
      if(idStr) out << " id=\"" + idStr + "-value\""
      out << " style=\"vertical-align:middle;\">"
      out << percentNumberInstance.format(value)
      out << "</span>"
    }
    out << "</span>"
  }
  
  /**
   * Creates the anchor that can be used to hide/unhide a reveal block.
   * Required attributes:
   * id: the id of the block to be hidden/revealed.
   */
  def revealAnchor = { attrs, body ->
    
    out << "<a href=\"#\" onClick=\"toggle_visibility('${attrs.id}')\">"
    out << body()
    out << "</a>"
  }

  /**
   * Creates a div that can be hidden/revealed by a revealAnchor.
   * Required attributes:
   * id: the same ID as used for the corresponding reveal anchor.
   */
  def revealBlock = { attrs, body ->
    out << "<div id=\"${attrs.id}\" style=\"display:none\">"  
    out << body()
    out << "</div>"
  }
  
  /**
   * Prints out the version of the M&iacute;mir plugin 
   */
  def version = {
    out << pluginManager.getGrailsPlugin("mimir-web").version
  }
  
  /**
   * Render the annotation types and features known to a particular
   * index as an HTML table.
   */
  def indexAnnotationsConfig = { attrs, body ->
    def index = attrs.index
    String[][] annotConf = index.annotationsConfig()
    if(annotConf) {
      out << "<table>\n";
      out << "  <thead><tr><td><b>Annotation type</b></td><td><b>Features</b></td></thead>\n"
      out << "  <tbody>\n"
      annotConf.each { String[] ann ->
        def annType = ann.head()
        def features = ann.tail()
        out << "  <tr><td>${annType}</td><td>";
        if(features) {
          out << features.join(', ')
        } else {
          out << "<i>&lt;none&gt;</i>";
        }
        out << "</td></tr>\n"
      }
      out << "</table>\n";
    } else {
      out << "<p><i>Information not available</i></p>\n"
    }
  }
  
  /**
   * Renders the contents of a document.
   * Parameters are either:
   * <ul>
   *   <li>queryId, and documentRank</li>
   *   <li>indexId, and documentId</li>
   * </ul>
   */
  def documentContent = { attrs, body ->
    try {
      if(attrs.queryId) {
        def queryId = attrs.queryId
        def documentRank = attrs.documentRank as long
        QueryRunner qRunner =  searchService.getQueryRunner(queryId)
        if(qRunner) {
          qRunner.renderDocument(documentRank, out)
        } else {
          out << g.message(code:"gus.bad.query.id", args:[queryId])
        }
      } else if(attrs.indexId) {
        def indexId = attrs.indexId
        def documentId = attrs.documentId as long
        Index theIndex = Index.findByIndexId(indexId)
        if(theIndex) {
          theIndex.renderDocument(documentId, out)
        } else {
          out << g.message(code:"gus.bad.indexId", args:[indexId])
        }
      } else {
        out << g.message(code:"gus.no.query.index.id")
      }
      
    } catch(Exception ex) {
      log.error("Exception rendering document ${documentRank}", ex)
      out << g.message(code:"gus.renderDocument.exception", args:[ex.message])
    }
  }

  def logo = { attrs, body ->
    def logoUri = grailsApplication.config.gate.mimir.logo.main
    if(logoUri) {
      if(logoUri =~ /^https?:/) {
        out << "<img src=\"${logoUri}\" alt=\"Logo\">"
      } else {
        out << asset.image(src:logoUri, alt:'Logo')
      }
    } else {
      out << asset.image(src:'logo.png', alt:'Logo')
    }
  }

  def powered = { attrs, body ->
    def logoUri = grailsApplication.config.gate.mimir.logo.powered
    if(logoUri) {
      if(logoUri =~ /^https?:/) {
        out << "<img src=\"${logoUri}\" alt=\"Powered by M&iacute;mir\">"
      } else {
        out << asset.image(src:logoUri, alt:'Powered by M&iacute;mir')
      }
    } else {
      out << asset.image(src:'logo-poweredby.png', alt:'Powered by M&iacute;mir')
    }
  }
}
