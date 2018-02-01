/**
 *  ResultsData.java
 * 
 *  Copyright (c) 1995-2010, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 05 Dec 2011 
 */
package gate.mimir.web.client;

import java.util.List;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Class representing data about a running query.  
 */
public class ResultsData implements IsSerializable {
  
  private int resultsPartial;
  
  private int resultsTotal;

  private List<DocumentData> documents;

  public int getResultsPartial() {
    return resultsPartial;
  }

  public void setResultsPartial(int resultsPartial) {
    this.resultsPartial = resultsPartial;
  }

  public int getResultsTotal() {
    return resultsTotal;
  }

  public void setResultsTotal(int resultsTotal) {
    this.resultsTotal = resultsTotal;
  }

  public List<DocumentData> getDocuments() {
    return documents;
  }

  public void setDocuments(List<DocumentData> documents) {
    this.documents = documents;
  }
  
}
