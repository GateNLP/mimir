/*
 *  MimirIndexingPR.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 01 Dec 2011
 *  
 *  $Id: MimirIndexingPR.java 18954 2015-10-20 21:13:20Z ian_roberts $
 */
package gate.mimir.index;

import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.mimir.tool.WebUtils;
import gate.util.GateRuntimeException;

import org.apache.log4j.Logger;

import java.net.URL;


/**
 * A simple PR for sending documents to a Mímir index.
 */
@CreoleResource(comment="A PR that sends documents to a Mímir server for indexing.",
 name="Mímir Indexing PR")
public class MimirIndexingPR extends AbstractLanguageAnalyser {

  private static final long serialVersionUID = 3291873032301133998L;

  private static final Logger log = Logger.getLogger(MimirIndexingPR.class);

  private URL mimirIndexUrl;
  
  private String mimirUsername;
  
  private String mimirPassword;

  private Integer connectionInterval;

  protected MimirConnector mimirConnector;
  
  
  
  public URL getMimirIndexUrl() {
    return mimirIndexUrl;
  }

  @CreoleParameter (comment="The Index URL, as obtained from the Mímir Server.")
  @RunTime
  public void setMimirIndexUrl(URL mimirIndexUrl) {
    this.mimirIndexUrl = mimirIndexUrl;
  }

  public String getMimirUsername() {
    return mimirUsername;
  }

  @CreoleParameter(comment="Username for authenticating to the Mímir server. Leave empty if no authentication is required.")
  @Optional
  @RunTime
  public void setMimirUsername(String mimirUsername) {
    this.mimirUsername = mimirUsername;
    closeConnector();
  }

  public String getMimirPassword() {
    return mimirPassword;
  }

  @CreoleParameter(comment="Password for authenticating to the Mímir server. Leave empty if no authentication is required.")
  @Optional
  @RunTime
  public void setMimirPassword(String mimirPassword) {
    this.mimirPassword = mimirPassword;
    closeConnector();
  }

  public Integer getConnectionInterval() {
    return connectionInterval;
  }

  @CreoleParameter(comment="Interval between connections to the Mímir server.  -1 causes each document "
      + "to be sent immediately, positive values cause documents to be buffered and sent to the server in "
      + "batches, which is generally much more efficient, particularly when the documents are short.",
      defaultValue = "1000")
  @Optional
  @RunTime
  public void setConnectionInterval(Integer connectionInterval) {
    this.connectionInterval = connectionInterval;
    closeConnector();
  }

  @Override
  public void cleanup() {
    closeConnector();
  }

  protected void closeConnector() {
    if(mimirConnector != null) {
      try {
        mimirConnector.close();
      } catch(Exception e) {
        log.warn("Execption while closing Mímir connector", e);
      }
    }
    mimirConnector = null;
  }

  @Override
  public void execute() throws ExecutionException {
    try {
      if(mimirConnector == null) {
        // first run or config has changed: [re-]create
        if(mimirUsername != null && mimirUsername.length() > 0) {
          mimirConnector = new MimirConnector(mimirIndexUrl,
            new WebUtils(mimirUsername, mimirPassword));          
        } else {
          mimirConnector = new MimirConnector(mimirIndexUrl);  
        }
        if(connectionInterval != null) {
          mimirConnector.setConnectionInterval(connectionInterval.intValue());
        }
      }
      mimirConnector.sendToMimir(getDocument(), null);
    } catch(Exception e) {
      throw new ExecutionException(
        "Error communicating with the Mímir server", e);
    }
  }
  
}
