/*
 *  MimirScorer.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 14 September 2011
 *
 *  $Id: MimirScorer.java 16667 2013-04-29 16:35:35Z valyt $
 */
package gate.mimir.search.score;

import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;
import it.unimi.di.big.mg4j.search.DocumentIterator;
import it.unimi.di.big.mg4j.search.score.DelegatingScorer;

import java.io.IOException;

/**
 * Base interface for scorers in Mímir.
 */
public interface MimirScorer extends DelegatingScorer {
  
  public abstract Binding nextHit() throws IOException;

  
  /**
   * Wraps a {@link QueryExecutor} allowing this scorer to provide scoring 
   * functionality on top of it. The parameter provided is declared as a 
   * {@link DocumentIterator} to satisfy the extended interface, but the
   * value provided <b>must</b> be a {@link QueryExecutor}.
   */
  @Override
  public void wrap(DocumentIterator queryExecutor) throws IOException;
  
  
  public long nextDocument(long greaterThan) throws IOException;
  
}