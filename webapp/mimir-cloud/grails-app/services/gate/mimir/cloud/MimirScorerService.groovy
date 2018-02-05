package gate.mimir.cloud

import it.unimi.di.big.mg4j.search.score.BM25Scorer
import it.unimi.di.big.mg4j.search.score.CountScorer
import it.unimi.di.big.mg4j.search.score.TfIdfScorer
import gate.mimir.search.score.BindingScorer
import gate.mimir.search.score.DelegatingScoringQueryExecutor as DSQE
import gate.mimir.search.score.MimirScorer
import gate.mimir.web.ScorerSource
import java.util.concurrent.Callable


/**
 * Service that takes a scorer name and returns the corresponding scorer
 * source (a Callable that returns an appropriate new scorer when called).
 */
class MimirScorerService implements ScorerSource {

  @Override
  public Callable<MimirScorer> scorerForName(String name) {
    return scorers[name]
  }

  @Override
  public Collection<String> scorerNames() {
    return scorers.keySet()
  }

  def scorers = [
    'Count Scoring':      { -> new DSQE(new CountScorer()) },
    'TF.IDF':             { -> new DSQE(new TfIdfScorer()) },
    'BM25':               { -> new DSQE(new BM25Scorer()) },
    'Hit Length Scoring': { -> new BindingScorer() },
  ]
}
