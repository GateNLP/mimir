/**
 *  GwtRpcService.java
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

import com.google.gwt.user.client.rpc.RemoteService;

public interface GwtRpcService extends RemoteService {
  public String search(String indexId, String query)
    throws MimirSearchException;

  public ResultsData getResultsData(String queryId, int firstDocumentRank, 
      int documentsCount) throws MimirSearchException;

  public void releaseQuery(String queryId);
  
  public String[][] getAnnotationsConfig(String indexId);
  
}
