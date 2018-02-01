/*
 *  Binding.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 4 Mar 2009
 *
 *  $Id: Binding.java 15767 2012-05-11 15:45:23Z valyt $
 */
package gate.mimir.search.query;

import gate.mimir.search.QueryEngine;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * A binding used for representing search results. When a query has found a hit,
 * each {@link QueryNode} in the query is bound to some interval on a document.
 * This binding is represented through objects of this class.
 */
public class Binding implements Comparable<Binding>, Serializable {

  private static final long serialVersionUID = -4892765737583819336L;

  /**
   * The document ID for this binding.
   */
  protected long documentID;
  
  /**
   * The term position for this binding.
   */
  protected int termPosition;
  
  /**
   * The length of this binding.
   */
  protected int length;
  
  /**
   * The {@link QueryNode} for this binding.
   */
  protected QueryNode queryNode;
  
  /**
   * The bindings for the sub-query nodes.
   */
  protected Binding[] containedBindings;
  
  /**
   * @param queryNode
   * @param documentID
   * @param termPosition
   * @param length
   * @param containedBindings
   */
  public Binding(QueryNode queryNode, long documentID, int termPosition,
          int length, Binding[] containedBindings) {
    this.queryNode = queryNode;
    this.documentID = documentID;
    this.termPosition = termPosition;
    this.length = length;
    this.containedBindings = containedBindings;
  }

  
  /* (non-Javadoc)
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  public int compareTo(Binding o) {
    long res = getDocumentId() - o.getDocumentId();
    if(res == 0){
      res = getTermPosition() - o.getTermPosition();
    }
    if(res == 0){
      res = getLength() - o.getLength();
    }
    return res > 0 ? 1 : (res == 0 ? 0 : -1);
  }

  /**
   * Gets the documentID for this binding.
   * @return
   */
  public long getDocumentId(){
    return documentID;
  }
  
  /**
   * Gets the term position where this binding starts, in the document wth the 
   * ID returned by {@link #getDocumentId()}.
   * @return
   */
  public int getTermPosition(){
    return termPosition;
  }
  
  /**
   * Gets the length (the number of terms) for this binding.
   * @return
   */
  public int getLength(){
    return length;
  }
  
  /**
   * The {@link QueryNode} representing the query segment that this binding is
   * assigned to.
   * @return
   */
  public QueryNode getQueryNode(){
    return queryNode;
  }
  
  
  /**
   * Gets the bindings corresponding to all the sub-nodes of the query node for 
   * this binding. This value is only populated if the sub-bindings are enabled
   * in the {@link QueryEngine} that produced it (see 
   * {@link QueryEngine#setSubBindingsEnabled(boolean)}).
   * To save memory, in the case of compound {@link QueryNode}s (i.e. nodes that
   * contain other nodes), only the top node will contain this array of 
   * bindings, which will include the bindings for the entire hierarchy of nodes 
   * (but not including the binding for the top node).
   * @return an array of {@link Binding} values.
   */
  public Binding[] getContainedBindings(){
    return containedBindings;
  }

  /**
   * @param containedBindings the containedBindings to set
   */
  public void setContainedBindings(Binding[] containedBindings) {
    this.containedBindings = containedBindings;
  }
}
