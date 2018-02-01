/**
 *  MimirSearchException.java
 * 
 *  Copyright (c) 1995-2010, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 01 Dec 2011 
 */
package gate.mimir.web.client;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Exceptions that can occur on the server side as an effect of remote GWT RPC
 * calls (and that can be serialised and sent back to the client).
 */
public class MimirSearchException extends Exception implements IsSerializable {
  
  private static final long serialVersionUID = 1L;

  public static final int QUERY_ID_NOT_KNOWN = 1;
  
  public static final int INTERNAL_SERVER_ERROR = 1;
  
  public static final int OTHER = 0;
  
  private int errorCode;
  
  
  public MimirSearchException(int errorCode) {
    super();
    this.errorCode = errorCode;
  }

  public MimirSearchException(int errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
  
  public MimirSearchException() {
    errorCode = OTHER;
  }

  public MimirSearchException(String message) {
    super(message);
    errorCode = OTHER;
  }

  public int getErrorCode() {
    return errorCode;
  }
  
}
