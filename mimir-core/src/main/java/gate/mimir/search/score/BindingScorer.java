/*
 *  BindingScorer.java
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
 *  $Id: BindingScorer.java 16667 2013-04-29 16:35:35Z valyt $
 */
package gate.mimir.search.score;

import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.search.DocumentIterator;
import it.unimi.di.big.mg4j.search.score.AbstractWeightedScorer;

import java.io.IOException;

public class BindingScorer  extends AbstractWeightedScorer implements MimirScorer {
  public BindingScorer() {
    this(16, 0.9);
  }
  
  
  public BindingScorer(int h, double alpha) {
    super();
    this.h = h;
    this.alpha = alpha;
  }

  @Override
  public double score(Index index) throws IOException {
    return score();
  }
  
  @Override
  public double score() throws IOException {
    double score= 0.0;
    Binding aHit = nextHit();
    while(aHit != null) {
      int length = aHit.getLength();
      score += length < h ? 1 : Math.pow((double)h / length,  alpha);
      aHit = nextHit();
    }
    return score;
  }
  
  @Override
  public boolean usesIntervals() {
    return true;
  }

  @Override
  public BindingScorer copy() {
    return new BindingScorer();
  }

  @Override
  public void wrap(DocumentIterator documentIterator) throws IOException {
    super.wrap(documentIterator);
    this.underlyingExecutor = (QueryExecutor)documentIterator;
  }

  protected QueryExecutor underlyingExecutor;
  
  protected int h;
  
  protected double alpha;

  public long nextDocument(long greaterThan) throws IOException {
    return underlyingExecutor.nextDocument(greaterThan);
  }

  public Binding nextHit() throws IOException {
    return underlyingExecutor.nextHit();
  }


}
