/*
 *  DelegatingScoringQueryExecutor.java
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
 *  $Id: DelegatingScoringQueryExecutor.java 16667 2013-04-29 16:35:35Z valyt $
 */
package gate.mimir.search.score;

import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMap;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.search.DocumentIterator;
import it.unimi.di.big.mg4j.search.score.DelegatingScorer;

import java.io.IOException;

/**
 * Implementation of {@link MimirScorer} that delegates the scoring work to an 
 * MG4J {@link DelegatingScorer}.
 */
public class DelegatingScoringQueryExecutor implements MimirScorer {
  
  public DelegatingScoringQueryExecutor(DelegatingScorer scorer)
    throws IOException {
    this.underlyingScorer = scorer;
  }
  
  public long nextDocument(long greaterThan) throws IOException {
    return underlyingExecutor.nextDocument(greaterThan);
  }
  
  
  /* (non-Javadoc)
   * @see gate.mimir.search.query.MimirScorer#nextHit()
   */
  @Override
  public Binding nextHit() throws IOException {
    return underlyingExecutor.nextHit();
  }
  
  public double score() throws IOException {
    return underlyingScorer.score();
  }

  private DelegatingScorer underlyingScorer;
  
  private QueryExecutor underlyingExecutor;



  public double score(Index index) throws IOException {
    return underlyingScorer.score(index);
  }

  public boolean setWeights(Reference2DoubleMap<Index> index2Weight) {
    return underlyingScorer.setWeights(index2Weight);
  }



  public Reference2DoubleMap<Index> getWeights() {
    return underlyingScorer.getWeights();
  }


  public long nextDocument() throws IOException {
    return underlyingScorer.nextDocument();
  }



  public DelegatingScorer copy() {
    try {
      return new DelegatingScoringQueryExecutor(
          (DelegatingScorer)underlyingScorer.copy());
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
  }


  public void wrap(DocumentIterator queryExecutor) throws IOException {
    underlyingExecutor = (QueryExecutor)queryExecutor;
    underlyingScorer.wrap(queryExecutor);
  }

  public boolean usesIntervals() {
    return underlyingScorer.usesIntervals();
  }
}
