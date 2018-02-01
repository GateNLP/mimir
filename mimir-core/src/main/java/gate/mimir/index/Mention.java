/*
 *  Mention.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 19 Feb 2009
 *
 *  $Id: Mention.java 14541 2011-11-14 19:31:23Z ian_roberts $
 */
package gate.mimir.index;

/**
 * Simple holder class holding the URI and length of a mention.
 */
public class Mention {
  
  /**
   * Special value used when the mention has no length information (e.g. if it
   * refers to document metadata hit).
   */
  public static final int NO_LENGTH = -1;
  
  private int length;

  private String uri;

  public Mention(String uri, int length) {
    this.uri = uri;
    this.length = length;
  }

  @Override
  public boolean equals(Object obj) {
    if(this == obj) return true;
    if(obj == null) return false;
    Mention other = (Mention)obj;
    if(length != other.length) return false;
    if(uri == null) {
      if(other.uri != null) return false;
    } else if(!uri.equals(other.uri)) return false;
    return true;
  }

  public int getLength() {
    return length;
  }

  public String getUri() {
    return uri;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + length;
    result = prime * result + ((uri == null) ? 0 : uri.hashCode());
    return result;
  }
}
