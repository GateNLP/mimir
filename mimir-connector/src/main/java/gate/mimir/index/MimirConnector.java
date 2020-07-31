/*
 *  MimirConnector.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Ian Roberts, 26 Mar 2010
 *  
 *  $Id: MimirConnector.java 18954 2015-10-20 21:13:20Z ian_roberts $
 */
package gate.mimir.index;


import gate.Document;
import gate.mimir.tool.WebUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;

/**
 * Utility class that implements the client side of the Mimir RPC indexing
 * protocol. 
 */
public class MimirConnector {
  
  protected static Logger logger = Logger.getLogger(MimirConnector.class);
  
  /**
   * The maximum size of the {@link #byteBuffer}.
   */
  protected static final int BYTE_BUFFER_SIZE = 8 * 1024 *1024;
  
  /**
   * Timer used to regularly check if there is any data to send to the remote 
   * server.
   */
  protected Timer backgroundTimer;
  
  /**
   * Has this connector been closed?
   */
  protected volatile boolean closed = false;
  
  /**
   * The last time we sent data to the remote server
   */
  protected volatile long lastWrite;
  
  /**
   * A byte buffer accumulating data to be sent to the remote server.
   */
  protected ByteArrayOutputStream byteBuffer;
  
  /**
   * An instance of {@link ObjectOutputStream} used to serialise document for
   * transmission over the wire.
   */
  protected ObjectOutputStream objectOutputStream;
  
  /**
   * The name for the document feature used to hold the document URI.
   */
  public static final String MIMIR_URI_FEATURE = "gate.mimir.uri";
  
  
  
  protected WebUtils webUtils;
  
  /**
   * The number of milliseconds between connections to the remote server. All
   * documents submitted for indexing are locally cached and get sent to the 
   * server in batches, at intervals defined by this value.
   */
  private int connectionInterval = -1;
  
  /**
   * How many documents have been buffered since the last connection.
   */
  private int docsSinceLastConnection = 0;

  /**
   * The URL of the mimir index that is to receive the document.  This would 
   * typically be of the form 
   * <code>http://server:port/mimir/&lt;index UUID&gt;/</code>.
   */
  protected URL indexURL;
  
  /**
   * The default value for the connection interval (see 
   * {@link #setConnectionInterval(int)}).
   */
  public static final int DEFAULT_CONNECTION_INTERVAL = -1;
  
  public MimirConnector(URL indexUrl, WebUtils webUtils) throws IOException {
    this.indexURL = indexUrl;
    this.webUtils = webUtils;
    byteBuffer = new ByteArrayOutputStream(BYTE_BUFFER_SIZE);
    objectOutputStream = new ObjectOutputStream(byteBuffer);
    lastWrite = System.currentTimeMillis();
  }
  
  public MimirConnector(URL indexUrl) throws IOException {
    this(indexUrl, new WebUtils());
  }
  
  /**
   * Pass the given GATE document to the Mimir index at the given URL for
   * indexing.  The document should match the expectations of the Mimir index
   * to which it is being sent, in particular it should include the token and
   * semantic annotation types that the index expects.  This method has no way
   * of checking that those expectations are met, and will not complain if they
   * are not, but in that case the resulting index will not contain any useful
   * information.
   *
   * @param doc the document to index.
   * @param documentURI the URI that should be used to represent the document
   *         in the index.  May be null, in which case the index will assign a
   *         URI itself.
   * @throws IOException if any error has occurred communicating with the Mímir
   *         service.
   * @throws InterruptedException if the current thread is interrupted while
   * waiting to submit the document to the input queue.
   */
  public void sendToMimir(Document doc, String documentURI) throws IOException, InterruptedException {
    if(closed) throw new IOException("This Mímir connector has been closed.");

    boolean uriFeatureWasSet = false;
    Object oldUriFeatureValue = null;

    if(documentURI != null && doc != null) {
      // set the URI as a document feature, saving the old value (if any)
      uriFeatureWasSet = doc.getFeatures().containsKey(MIMIR_URI_FEATURE);
      oldUriFeatureValue = doc.getFeatures().get(MIMIR_URI_FEATURE);
      doc.getFeatures().put(MIMIR_URI_FEATURE, documentURI);
    }
    
    synchronized(this) {
      if(doc != null){
        objectOutputStream.writeUnshared(doc);
        docsSinceLastConnection++;
      }
      if(byteBuffer.size() > BYTE_BUFFER_SIZE) {
        writeBuffer(); // this will also empty (reset) the buffer
      }
      // if too long since last write, write the buffer
      if(System.currentTimeMillis() - lastWrite > connectionInterval) {
        writeBuffer();
      }
    }

    if(documentURI != null && doc != null) {
      // reset the URI feature to the value it had (or didn't have) before
      if(uriFeatureWasSet) {
        doc.getFeatures().put(MIMIR_URI_FEATURE, oldUriFeatureValue);
      } else {
        doc.getFeatures().remove(MIMIR_URI_FEATURE);
      }
    }
  }
  
  /**
   * Writes the current contents of the byte buffer to the remote server. 
   * @throws IOException 
   */
  protected synchronized void writeBuffer() throws IOException {
    if(docsSinceLastConnection > 0) {
      StringBuilder indexURLString = new StringBuilder(indexURL.toExternalForm());
      if(indexURLString.length() == 0) {
        throw new IllegalArgumentException("No index URL specified");
      }
      if(indexURLString.charAt(indexURLString.length() - 1) != '/') {
        // add a slash if necessary
        indexURLString.append('/');
      }
      indexURLString.append("manage/indexUrl");

      // close the object OS so that it writes its coda
      objectOutputStream.close();
      try {
        // first phase - call the indexUrl action to find out where to post the
        // data
        StringBuilder postUrlBuilder = new StringBuilder();
        webUtils.getText(postUrlBuilder, indexURLString.toString());
        // second phase - post to the URL we were given
        webUtils.postData(postUrlBuilder.toString(), byteBuffer);
      } finally {
        byteBuffer.reset();
        objectOutputStream = new ObjectOutputStream(byteBuffer);
        docsSinceLastConnection = 0;
      }
    }
    
    lastWrite = System.currentTimeMillis();
  }
  
  /**
   * Gets the current value for the connection interval (see 
   * {@link #setConnectionInterval(int)}). 
   * @return
   */
  public int getConnectionInterval() {
    return connectionInterval;
  }

  
  /**
   * Sets the number of milliseconds between connections to the remote server. 
   * All documents submitted for indexing are locally cached and get sent to the 
   * server in batches, at intervals defined by this value. Negative values mean
   * that no local caching should take place, and documents should be sent to 
   * the remote server as soon as possible. Defaults to 
   * {@value #DEFAULT_CONNECTION_INTERVAL}.

   * @param connectionInterval
   */
  public synchronized void setConnectionInterval(int connectionInterval) {
    this.connectionInterval = connectionInterval;
    if(connectionInterval <= 0 && backgroundTimer != null) {
      backgroundTimer.cancel();
    } else {
      if(backgroundTimer != null) {
        backgroundTimer.cancel();
      }
      backgroundTimer = new Timer(getClass().getName() +  " background timer");
      // we set a timer task that regularly submits a null value. This causes
      // the connector to flush any data that is getting too old.
      backgroundTimer.schedule(new TimerTask() {
        @Override
        public void run() {
          try {
            sendToMimir(null, null);
          } catch(Exception e) {
            // this should never happen
            logger.error(MimirConnector.class.getName() + " internal error", e);
          }
        }
      }, connectionInterval, connectionInterval);
    }
  }

  /**
   * Notifies this Mímir connector that no more documents remain to be sent.
   * At this point any locally cached documents are submitted to the remote
   * server, after which the remote connection is closed. This method then 
   * returns.
   * @throws IOException if the background thread used for the communication 
   * with the remote end point has encountered a problem.
   * @throws InterruptedException if the current thread is interrupted while
   * waiting to notify the background thread of the termination.
   */
  public void close() throws IOException, InterruptedException {
    closed = true;
    if(backgroundTimer != null){
      backgroundTimer.cancel();
    }
    // flush all cached content one last time
    writeBuffer();
  }
}

