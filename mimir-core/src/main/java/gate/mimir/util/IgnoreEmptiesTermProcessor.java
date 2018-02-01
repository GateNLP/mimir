/*
 *  IgnoreEmptiesTermProcessor.java
 *
 *  Copyright (c) 2012, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Mark A. Greenwood, 5 January 2012
 *
 *  $Id: IgnoreEmptiesTermProcessor.java 19443 2016-06-28 12:42:13Z ian_roberts $
 */
package gate.mimir.util;

import it.unimi.dsi.lang.MutableString;
import it.unimi.di.big.mg4j.index.TermProcessor;

/**
 * Term processor that completely ignores null or empty-string terms rather
 * than indexing them as the empty string.  This is useful when you want to
 * index token features that are not present on every token.
 */
public class IgnoreEmptiesTermProcessor implements TermProcessor
{
  private final static IgnoreEmptiesTermProcessor INSTANCE = new IgnoreEmptiesTermProcessor();

  public final static TermProcessor getInstance() {
    return INSTANCE;
  }

  private IgnoreEmptiesTermProcessor() {
    //nothing to do but this method forces people to use the getInstance() method;
  }

  @Override
  public boolean processTerm(final MutableString term) {
    return (term != null && term.length() > 0);
  }

  @Override
  public boolean processPrefix(final MutableString prefix) {
    return processTerm(prefix);
  }

  private Object readResolve() {
    return INSTANCE;
  }

  @Override
  public String toString() {
    return this.getClass().getName();
  }

  @Override
  public IgnoreEmptiesTermProcessor copy() {
    return this;
  }
}
