package gate.mimir.web;

import java.util.Collection;
import java.util.concurrent.Callable;
import gate.mimir.search.score.MimirScorer;

/**
 * Simple interface to access scorers, host applications should declare a
 * Grails service that implements this interface.
 */
public interface ScorerSource {

  public Collection<String> scorerNames();

  public Callable<MimirScorer> scorerForName(String name);
}
