/*
 *  AndQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 04 Jul 2009
 *  
 *  $Id: AndQuery.java 15767 2012-05-11 15:45:23Z valyt $
 */
package gate.mimir.search.query;


import gate.mimir.search.QueryEngine;
import it.unimi.dsi.fastutil.objects.ObjectHeapSemiIndirectPriorityQueue;

import java.io.IOException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query Node for AND queries. Finds the shortest document intervals that
 * contain hits from several sub-queries.
 */
public class AndQuery implements QueryNode {

  private static final long serialVersionUID = -5565830708202297074L;


  public static class AndQueryExecutor extends AbstractIntersectionQueryExecutor {
    /**
     * Creates a query executor for an AND query.
     * 
     * @param engine
     *          the {@link QueryEngine} to be used.
     * @param query
     *          the query being executed.
     * @throws IOException
     *           if there are problems accessing the index.
     */
    public AndQueryExecutor(AndQuery query, QueryEngine engine)
            throws IOException {
      super(engine, query, query.nodes);
      this.query = query;
    }

    /**
     * Static logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(AndQueryExecutor.class);
    


    /*
     * (non-Javadoc)
     * 
     * @see gate.mimir.search.query.QueryExecutor#close()
     */
    public void close() throws IOException {
      super.close();
      hitsOnCurrentDocument = null;
      query = null;
    }


    /*
     * (non-Javadoc)
     * 
     * @see gate.mimir.search.query.QueryExecutor#nextDocument(int)
     */
    public long nextDocument(long greaterThan) throws IOException {
      hitsOnCurrentDocument = null;
      return super.nextDocument(greaterThan);
    }

    /**
     * @throws IOException
     */
    protected void getHitsOnCurrentDocument() throws IOException {
      // start with an empty list
      hitsOnCurrentDocument = new LinkedList<Binding[]>();
      // The algorithm is:
      // 1) obtain the hit list from each sub-executor
      // 2) filter non-minimal intervals
      // 3) apply the algorithm from
      // http://www.springerlink.com/content/1267481874833210/
      // an array of hit lists, one from each executor. LinkedLists are used
      // explicitly for performance reasons.
      LinkedList<Binding>[] subHits = new LinkedList[executors.length];
      for(int i = 0; i < executors.length; i++) {
        LinkedList<Binding> hits = new LinkedList<Binding>();
        Binding aHit = executors[i].nextHit();
        if(aHit == null){
          logger.warn("Malfunction in AND operator (or one of the sub-nodes):\n" +
                  "No input sub-hits from " + nodes[i].toString() + 
                  " on document " + latestDocument + "!");        
        }
        while(aHit != null) {
          // filter all the non minimal intervals, i.e.:
          // remove all previous elements (if any) that have a greater end
          // offset
          while(!hits.isEmpty() && 
                (hits.getLast().getTermPosition() + hits.getLast().getLength() >= 
                aHit.getTermPosition() + aHit.getLength())) {
            hits.removeLast();
          }
          hits.add(aHit);
          aHit = executors[i].nextHit();
        }
        subHits[i] = hits;
        // if one of the sub-executors has no results, return
        if(subHits[i].isEmpty()){
          // this should never happen
          logger.warn("Malfunction in AND operator (or one of the sub-nodes):\n" +
                  "No ouput sub-hits from " + nodes[i].toString() + "!");
          return;
        }
      }
      // The algorithm is:
      // function next begin
      // 1 if !(Q is full) then return null;
      // 2 do
      // 3 c = span(Q);
      // 4 advance(Q)
      // 5 while Q is full and span(Q) in c ;
      // 6
      // 7 while Q is full and c in span(Q) do
      // 8 advance(Q)
      // 9 end;
      // 10 return c
      // 11 end;
      // Where:
      // The queue in the algorithm is a double priority queue (a queue with two
      // order relations). However, the second order relation is based on
      // interval end-offsets, we only ever use it to obtain the last element,
      // and the intervals come in ascending order. So we can replace the second
      // order by simply keeping track of the maximum end offset.
      // - advance(Q) means replacing the top element with a new one from the
      // same
      // source sub-executor;
      // - span(Q) is the span covered by all elements in the queue (defined as
      // the left extreme of the queue primary top, and the right extreme of
      // the queue secondary top.
      // construct the initial candidate solution (by taking the first sub-hit
      // from each list
      // the candidate result hit (contains one hit hit from each sub-executor)
      Binding[] candidateHits = new Binding[executors.length];
      // create the queue
      ObjectHeapSemiIndirectPriorityQueue<Binding> queue =
              new ObjectHeapSemiIndirectPriorityQueue<Binding>(candidateHits,
                      new Comparator<Binding>() {
                        /**
                         * Compares intervals (bindings). An interval is smaller
                         * than another if it starts before or it extends it. In
                         * other words, the top interval in the queue will
                         * always be one that potentially extends the candidate
                         * span either to the left or right.
                         * 
                         * @param o1
                         * @param o2
                         * @return
                         */
                        public int compare(Binding b1, Binding b2) {
                          int start1 = b1.getTermPosition();
                          int start2 = b2.getTermPosition();
                          if(start1 < start2) {
                            return -1;
                          } else if(start1 == start2) {
                            int end1 = start1 + b1.getLength();
                            int end2 = start2 + b2.getLength();
                            // note the inversion!
                            return end2 - end1;
                          } else {
                            // start2 > start1
                            return 1;
                          }
                        }
                      });
      int maxRight = Integer.MIN_VALUE;
      for(int i = 0; i < subHits.length; i++) {
        // first hit from each sub-executor becomes part of the candidate
        // solution
        candidateHits[i] = subHits[i].removeFirst();
        int currentRight =
                candidateHits[i].getTermPosition()
                        + candidateHits[i].getLength();
        if(currentRight > maxRight) maxRight = currentRight;
        queue.enqueue(i);
      }
      boolean done = false;
      // each loop is a call to the "next" function above
      bigwhile: while(!done) {
        // step 1 -> improve the current candidate solution
        int solutionLeft;
        int solutionRight;
        int first;
        step1: while(true) {
          first = queue.first();
          if(subHits[first].isEmpty()) {
            // no more inputs -> save current solution and exit
            hitsOnCurrentDocument.add(candidateHits);
            break bigwhile;
          } else {
            // advance Queue
            Binding oldTop = candidateHits[first];
            int oldFirst = first;
            candidateHits[first] = subHits[first].removeFirst();
            queue.changed();
            first = queue.first();
            // if span(Q) > candidate, exit step1
            //the span(Q) is defined by the left extreme of the new top, and 
            //the right extreme of the newly added element (the one at oldFirst) 
            if(candidateHits[first].getTermPosition() < oldTop.getTermPosition()
                || 
               candidateHits[oldFirst].getTermPosition() + 
               candidateHits[oldFirst].getLength() > maxRight) {
              // cannot improve current solution any more
              // save the solution
              Binding[] solution = new Binding[candidateHits.length];
              for(int i = 0; i < solution.length; i++) {
                solution[i] = (i == oldFirst) ? oldTop : candidateHits[i];
              }
              solutionLeft = oldTop.getTermPosition();
              solutionRight = maxRight;
              hitsOnCurrentDocument.add(solution);
              // update maxRight
              int currentRight = candidateHits[first].getTermPosition()
                              + candidateHits[first].getLength();
              if(currentRight > maxRight) maxRight = currentRight;
              // and exit step1
              break step1;
            }
          }
        }
        // step 2 while c in Span(Q)
        while(candidateHits[first].getTermPosition() <= solutionLeft
                && candidateHits[first].getTermPosition()
                        + candidateHits[first].getLength() >= solutionRight) {
          if(subHits[first].isEmpty()) {
            // no more input hits -> we're done
            break bigwhile;
          } else {
            // advance the queue
            candidateHits[first] = subHits[first].removeFirst();
            queue.changed();
            first = queue.first();
          }
        }// step2 while
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see gate.mimir.search.query.QueryExecutor#nextHit()
     */
    public Binding nextHit() throws IOException {
      if(closed) return null;
      if(hitsOnCurrentDocument == null){
        getHitsOnCurrentDocument();
      }
      if(hitsOnCurrentDocument.isEmpty())
        return null;
      else {
        // get the first hit, and construct the corresponding bindings array for
        // it
        // note that the sub-hits are in no particular order
        int start = Integer.MAX_VALUE;
        int end = Integer.MIN_VALUE;
        Binding[] hitSlots = hitsOnCurrentDocument.remove(0);
        // there will be one contained binding for each sub-query, plus
        // all of their own contained bindings.
        int containedBindsCount = executors.length;
        for(Binding aSubHit : hitSlots) {
          Binding[] containedB = aSubHit.getContainedBindings();
          containedBindsCount += containedB == null ? 0 : containedB.length;
          if(aSubHit.getTermPosition() < start) {
            start = aSubHit.getTermPosition();
          }
          if(aSubHit.getTermPosition() + aSubHit.getLength() > end) {
            end = aSubHit.getTermPosition() + aSubHit.getLength();
          }
        }
        
        Binding[] containedBindings = null;
        if(engine.isSubBindingsEnabled()){
          containedBindings = new Binding[containedBindsCount];
          // position to write into containedBindings
          int cbIdx = 0;
          for(Binding aSubHit : hitSlots) {
            containedBindings[cbIdx++] = aSubHit;
            if(aSubHit.getContainedBindings() != null) {
              System.arraycopy(aSubHit.getContainedBindings(), 0,
                      containedBindings, cbIdx,
                      aSubHit.getContainedBindings().length);
              cbIdx += aSubHit.getContainedBindings().length;
            }
          }
        }
        return new Binding(query, hitSlots[0].getDocumentId(), start, end
                - start, containedBindings);
      }
    }

    /**
     * The query being executed.
     */
    private AndQuery query;




    /**
     * The list of available hits for the current document (i.e. the latest
     * document returned by {@link #nextDocument(int)}. Each entry is an array
     * of {@link Binding} values, one from each sub-query.
     */
    protected List<Binding[]> hitsOnCurrentDocument;
  }// public static class AndQueryExecutor

  /**
   * Constructs a new short-AND operator from an array of sub-nodes.
   * 
   * @param nodes
   *          the sub-queries for this operator.
   */
  public AndQuery(QueryNode... nodes) {
    this.nodes = nodes;
  }

  /*
   * (non-Javadoc)
   * 
   * @see gate.mimir.search.query.QueryNode#getQueryExecutor(gate.mimir.search.
   * QueryEngine)
   */
  public QueryExecutor getQueryExecutor(QueryEngine engine) throws IOException {
    return new AndQueryExecutor(this, engine);
  }

  /**
   * Gets the sub-queries for this AND query.
   * 
   * @return the nodes
   */
  public QueryNode[] getNodes() {
    return nodes;
  }

  
  public String toString() {
    StringBuilder str = new StringBuilder("AND (");
    if(nodes != null){
      for(int  i = 0; i < nodes.length -1; i++){
        str.append(nodes[i].toString());
        str.append(", ");
      }
      str.append(nodes[nodes.length -1].toString());
    }
    str.append(")");
    return str.toString();
  }


  /**
   * The list of sub-queries.
   */
  private QueryNode[] nodes;
}
