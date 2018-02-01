/*
 *  NormalizingTermProcessor.java
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
 *  $Id: NormalizingTermProcessor.java 16667 2013-04-29 16:35:35Z valyt $
 */
package gate.mimir.util;

import it.unimi.dsi.lang.MutableString;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.index.TermProcessor;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.text.Normalizer;

public class NormalizingTermProcessor implements TermProcessor
{
  private final static NormalizingTermProcessor INSTANCE = new NormalizingTermProcessor();

  public final static TermProcessor getInstance() {
    return INSTANCE;
  }

  private NormalizingTermProcessor() {
    //nothing to do but this method forces people to use the getInstance() method;
  }

  @Override
  public boolean processTerm(final MutableString term) {
    if (term == null) return false;

    term.toLowerCase();

    //http://glaforge.appspot.com/article/how-to-remove-accents-from-a-string
    term.replace(Normalizer.normalize(term, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", ""));

    return true;
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
  public NormalizingTermProcessor copy() {
    return this;
  }
}
