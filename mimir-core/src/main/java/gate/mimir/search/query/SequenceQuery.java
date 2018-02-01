/*
 *  SequenceQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 06 Mar 2009
 *  
 *  $Id: SequenceQuery.java 20208 2017-04-19 08:35:28Z domrout $
 */
package gate.mimir.search.query;


import gate.mimir.search.QueryEngine;

import java.io.IOException;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;


/**
 * Implementation for phrase queries. A phrase query consists of a sequence of 
 * other sub-queries.
 */
public class SequenceQuery implements QueryNode {
  
  private static final long serialVersionUID = -2331332813532064881L;

  /**
   * A gap in a sequence query, represented the number of permitted extra terms
   * between the results of two consecutive sub-queries.
   * These can be used for unconstrained query segments (e.g. {Token}[2,5] in 
   * JAPE notation).
   * A Gap is defined by a minimum and a maximum number of permitted terms, 
   * hence a gap with <code>min=max=n</code> would only allow a fixed number
   * of <code>n</code> terms. A Gap with 
   * <code>max={@link Integer#MAX_VALUE}</code> allows arbitrarily long gaps.
   * A gap with <code>min=max=0</code> allows no space between sub-query 
   * results.      
   */
  public static class Gap implements Serializable {

    public static final long serialVersionUID = 4792642842105791776L;

     /**
     * Creates a new {@link Gap}.
     * @param min the minimum number of terms required. 
     * @param max the maximum number of terms permitted.
     */
    public Gap(int min, int max) {
      if(min > max) throw new IllegalArgumentException(
              "Value for min larger than value for max!");
      this.min = min;
      this.max = max;
    }
    int min;
    int max;
    
    /**
     * Checks whether two given term positions can be the start and the end of
     * this gap (i.e. if the distance between them corresponds to the gap 
     * specification). 
     * The check performed is that the <code>end<code> value needs to be 
     * between (start + min + 1) and (start + max +1). This means a gap of zero
     * requires <code>end == start + 1</code>.
     * 
     * @param start the start position for the gap.
     * @param end the end position for the gap.
     * @return <code>0</code> if the start and end positions are accepted by
     * this gap, a negative number if the end position is too small, a positive
     * number if the end position is too large.
     */
    public int check(int start, int end){
      if(end < start + min + 1) return -1;
      else if(end > start + max + 1) return 1;
      else return 0;
    }

    public int getMin() {
      return min;
    }

    public int getMax() {
      return max;
    }
  }
  
  public static class SequenceQueryExecutor extends AbstractIntersectionQueryExecutor{

    /**
     * @param engine
     * @param query
     * @throws IOException 
     */
    public SequenceQueryExecutor(SequenceQuery query, QueryEngine engine) 
        throws IOException {
      super(engine, query, query.nodes);
      this.query = query;
      //initialise the internal data
      hitsOnCurrentDocument = new LinkedList<Binding[]>();
      candidateHits = new List[executors.length];
    }


    /**
     * The query being executed.
     */
    private SequenceQuery query;
    
    
    /**
     * A list of hits on the current document.
     */
    protected List<Binding[]> hitsOnCurrentDocument;
    
    /**
     * An array of lists of hits, one list for each of the {@link #executors}.
     */
    protected List<Binding>[] candidateHits;
    
    
    
    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#close()
     */
    public void close() throws IOException {
      super.close();
      //release all pointers
      hitsOnCurrentDocument = null;
      query = null;
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextDocument(int)
     */
    public long nextDocument(long greaterThan) throws IOException {
      if(closed) return latestDocument = -1;
      if(latestDocument == -1) return latestDocument;
      
      //we've just been asked to change documents -> old hits not current any more
      hitsOnCurrentDocument.clear();
      while(hitsOnCurrentDocument.isEmpty()){
        long nextDocFromSuper = super.nextDocument(greaterThan);
        if(nextDocFromSuper < 0){
          //no more documents
          return nextDocFromSuper;
        }else{
          //We have a common document from super.
          //Now confirm if there is a match in that document.
          getHitsOnCurrentDocumentv3();          
        }
      }    
      return latestDocument;
    }
    
    protected void getHitsOnCurrentDocumentv3() throws IOException{
      //For each candidate hit, we keep either:
      //> the index of the candidate in the next list that forms a chain, or
      //> -1, if no such chain is possible.
      int[][] marks = new int[executors.length][];

      //All the hits on the current document, one row for each executor
      Binding[][] hits = new Binding[executors.length][];
      //prepare the candidateHits data
      //for each executor, we accumulate the hits on the current document
      //in its corresponding list.
      for(int i = 0; i < executors.length; i++){
        //get all the hits on the current document
        List<Binding> hitsFromOneExecutor = new ArrayList<Binding>();
        Binding subHit = executors[i].nextHit();
        while(subHit != null){
          hitsFromOneExecutor.add(subHit);
          subHit = executors[i].nextHit();
        }
        hits[i] = hitsFromOneExecutor.toArray(new Binding[hitsFromOneExecutor.size()]);
        //create the marks array for this list of candidates
        marks[i] = new int[hits[i].length];
      }
      //we mark from right to left, starting from the penultimate list
      //for the last list, all elements are marked as valid
      //VT: the following lines removed, as Java initialises arrays with 0
//      for(int i = 0; i < marks[marks.length -1].length; i++){
//        marks[marks.length -1][i] = 0;
//      }
      
      int currentSlot = hits.length - 2;
      while(currentSlot >= 0){
        for(int i = 0 ; i < hits[currentSlot].length; i++ ){
          //for each candidate hit
          Binding aHit = hits[currentSlot][i];
          int minStart = aHit.getTermPosition() + aHit.getLength() + 
              query.gaps[currentSlot].min;
          int maxStart = aHit.getTermPosition() + aHit.getLength() + 
              query.gaps[currentSlot].max;
          //find the first hit in the next list and store its pointer
          marks[currentSlot][i] = computeMark(minStart, maxStart, 
                  hits[currentSlot+1], marks[currentSlot + 1]);
        }
        currentSlot--;
      }
      //we collect results from left to right
      hitsOnCurrentDocument.clear();
      //for each marked starting point on the first slot, start enumerating the 
      //hits
      for(int i = 0; i < marks[0].length; i++){
        if(marks[0][i] >= 0){
          Binding[] slots = new Binding[executors.length];
          extractHitsRec(query, hitsOnCurrentDocument, hits, marks, 0, i, slots);
        }
      }
    }
    
    /**
     * Recursively extracts the hits 
     * @param hits the arrays of hits, one row for each of the {@link #executors}.
     * @param marks the marks for the hits 
     * @param currentSlot which slot to fill at this stage (used for recursion).
     * @param currentHit for the current slot, which candidate hit to start from.
     * @param slots the array of slots that needs filling. 
     * @see #computeMark(int, int, Binding[], int[])
     */
    protected static void extractHitsRec(SequenceQuery query, List<Binding[]> results, Binding[][] hits, int[][] marks,
            int currentSlot, int currentHit, Binding[] slots){
      slots[currentSlot] = hits[currentSlot][currentHit];
      if(currentSlot == slots.length -1){
        //we have a full result, no more recursion needed
        results.add(slots);
      }else{
        //recursive call for the first next candidate
        extractHitsRec(query, results, hits, marks, currentSlot + 1, 
                marks[currentSlot][currentHit], slots);
        //find all other candidates for next slot
        for(int i = marks[currentSlot][currentHit] +1; 
            i < marks[currentSlot + 1].length; i++){
          //if the gap accepts this next candidate, and it is valid,
          //add it to solution and do recursive call
          if(query.gaps[currentSlot].check(
            slots[currentSlot].getTermPosition() + 
                slots[currentSlot].getLength() -1,
            hits[currentSlot + 1][i].getTermPosition()) == 0){
            if(marks[currentSlot + 1][i] >= 0){
              //copy the slots array
              Binding[] newSlots = new Binding[slots.length];
              for(int j = 0; j <= currentSlot; j++){
                newSlots[j] = slots[j];
              }
              extractHitsRec(query, results, hits, marks, currentSlot + 1, i, newSlots);
            }
          }else{
            //no more next candidates
            break;
          }
        }
      }
    }
    
    /**
     * Computes the mark for a current hit. The mark for a hit is:
     * - a pointer to a hit from the next slot candidates (if there is a chain
     * of hits from the current hit to the end)
     * - otherwise, -1
     * The next hit pointed to is the first hit that can cause matches. 
     *  
     * @param minStart the minimum acceptable start position for the next hit
     * @param maxStart the maximum acceptable start position for the next hit
     * @param hits the list of candidate hits (hits on the next slot)
     * @param marks the list of marks for the candidate hits
     * @return the mark for the current hit
     */
    protected static int computeMark(int minStart, int maxStart, 
            Binding[] hits, int[] marks){
      //binary search for the first valid next hit
      int low = 0;
      int high = hits.length -1;
      while(low <= high){
        int mid = (low + high) >>> 1;
        if(hits[mid].getTermPosition() < minStart){
          //mid hit too low -> look in second half
          low = mid + 1;
        }else if(hits[mid].getTermPosition() > maxStart){
          //mid hit too large -> look in first half
          high = mid - 1;
        }else{
          //Mid hit in the right range -> scroll up to find the first good hit.
          //A good hit is one that:
          // - starts between minStart and maxStart, and
          // - has a non-negative mark itself
          int lastValidMark = -1;
          int newMid = mid;
          while(newMid >= 0 && hits[newMid].getTermPosition() >= minStart){
            if(marks[newMid] >= 0) lastValidMark = newMid;
            newMid--;
          }
          //if not found, scroll down while the starting position is still valid
          if(lastValidMark < 0){
            newMid = mid + 1;
            while(newMid < hits.length && 
                  hits[newMid].getTermPosition() <= maxStart){
              if(marks[newMid] >= 0){
                lastValidMark = newMid;
                break;
              }
              newMid++;
            } 
          }
          return lastValidMark;
        }
      }
      return -1;
    }
    
    protected void getHitsOnCurrentDocumentv2() throws IOException{
      //We use the hitsOnCurrentDocument list as a list of possible suffixes, 
      //sorted by start offset. This starts with the hits on the last slot, and 
      //grows by adding new slot fillers at the start.
      //After we've filled the first slot, this list contains
      //the results.
      hitsOnCurrentDocument.clear();
      
      //prepare the candidateHits data
      //for each executor, we accumulate the hits on the current document
      //in its corresponding list.
      for(int i = 0; i < executors.length; i++){
        //get all the hits on the current document
        candidateHits[i] = new LinkedList<Binding>();
        Binding subHit = executors[i].nextHit();
        while(subHit != null){
          candidateHits[i].add(subHit);
          subHit = executors[i].nextHit();
        }
      }
      //start filling slots in the suffixes map.
      //we begin with the list of candidates from the last executor.
      int currentSlot = executors.length -1;
      for(Binding aSlotFiller : candidateHits[currentSlot]){
        Binding[] aSuffix = new Binding[executors.length];
        aSuffix[currentSlot] = aSlotFiller;
        hitsOnCurrentDocument.add(aSuffix);
      }
      //now continue filling the previous slot, until we've reached the 0 
      //position
      while(currentSlot > 0){
        List<Binding[]> newSuffixes = new LinkedList<Binding[]>();
        currentSlot--;
        for(Binding aSlotFiller : candidateHits[currentSlot]){
//          int minStart = aSlotFiller.getTermPosition() + 
//              aSlotFiller.getLength() + query.gaps[currentSlot].min;
//          int maxStart = aSlotFiller.getTermPosition() + 
//              aSlotFiller.getLength() + query.gaps[currentSlot].max;
          //find suffixes that start between minStart and maxStart
          boolean suffixUsed = false;
          for(Binding[] aSuffix : hitsOnCurrentDocument){
            if(query.gaps[currentSlot].check(
                    aSlotFiller.getTermPosition() + aSlotFiller.getLength() -1,
                    aSuffix[currentSlot + 1].getTermPosition()) == 0){
              suffixUsed = true;
              //we managed to left-extend a suffix
              if(aSuffix[currentSlot] == null){
                //this is the first time we're extending this suffix -> 
                //we can re-use the same array
                aSuffix[currentSlot] = aSlotFiller;
                newSuffixes.add(aSuffix);
              }else{
                //we need to create a copy
                Binding[] newSuffix = new Binding[executors.length];
                newSuffix[currentSlot] = aSlotFiller;
                for(int i = currentSlot +1; i < newSuffix.length; i++){
                  newSuffix[i] = aSuffix[i];
                }
                newSuffixes.add(newSuffix);
              }
            }
          }
        }
        hitsOnCurrentDocument = newSuffixes;        
      }//while(currentSlot > 0)
      
    }
    
    /**
     * Attempts to find all the matches on the current document. When this 
     * method is called, all the values in {@link #nextDocument(int)} are the 
     * same (the current document).
     * 
     * This method will use hits from the {@link #candidateHits} array (and 
     * replenish any hit lists as necessary). In the process it will discard
     * any hits from those lists that are not useful (refer to lower documentIDs
     * or are not accepted by the gap restrictions).
     * 
     * When the method returns, all the possible hits on the current document
     * have been saved in the {@link #hitsOnCurrentDocument} list.
     *
     * This method is a non-recursive implementation of the backtracking 
     * algorithm.
     * 
     * @throws IOException 
     */
    protected void getHitsOnCurrentDocumentOldBCK() throws IOException{
      hitsOnCurrentDocument.clear();
      //prepare the candidateHits data
      //for each executor, we accumulate the hits on the current document
      //in its corresponding list.
      for(int i = 0; i < executors.length; i++){
        //get all the hits on the current document
        candidateHits[i] = new LinkedList<Binding>();
        Binding subHit = executors[i].nextHit();
        while(subHit != null){
          candidateHits[i].add(subHit);
          subHit = executors[i].nextHit();
        }
      }
      
      //prepare the backtracking machine
      //get an array of iterators for the sub-hits
      Iterator<Binding>[] hitIters = new Iterator[executors.length];
      for(int i = 0; i < executors.length; i++){
        hitIters[i] = candidateHits[i].iterator();
      }
      
      //start the backtracking machine
      //we try to fill one of these:
      Binding[] slots = new Binding[executors.length];
      //the slot that needs to be filled next
      int currentSlot = 0;
      while(currentSlot >= 0){
        //we try to fill the current slot
        slots[currentSlot] = null;
        if(hitIters[currentSlot].hasNext()){
          slots[currentSlot] = hitIters[currentSlot].next();
          //validate the current slot candidate
          //check document
          if(currentSlot == 0){
            //we just filled the first slot -> no tests to preform
          }else{
            //we 're not on first position
            //Optimisation (beam search):
            //first remove all candidate hits on the current slot for which the
            //starting position is smaller than that of the previous slot.
            //This is safe to do, as the previous slot position will only get 
            //larger.
            
            //VT: if the candidates list is short, this will make it slower 
//            if(candidateHits[currentSlot].size() > 100){
              int lowerBound = slots[currentSlot -1].getTermPosition();
              while(slots[currentSlot].getTermPosition() <= lowerBound){
                hitIters[currentSlot].remove();
                if(hitIters[currentSlot].hasNext()){
                  slots[currentSlot] = hitIters[currentSlot].next();  
                }else{
                  slots[currentSlot] = null;
                  break;
                }
              }
//            }
            //use the gap to check position
            if(slots[currentSlot] != null){
              while(query.gaps[currentSlot - 1].check(
                      slots[currentSlot -1].getTermPosition() + 
                      slots[currentSlot -1].getLength() -1,
                      slots[currentSlot].getTermPosition()) != 0){
                if(hitIters[currentSlot].hasNext()){
                  slots[currentSlot] = hitIters[currentSlot].next();  
                }else{
                  slots[currentSlot] = null;
                  break;
                }
              }
            }
          }
        }
        if(slots[currentSlot] == null){
          //we couldn't fill the current slot -> go back
          //reset the iterator for the current slot
          hitIters[currentSlot] = candidateHits[currentSlot].iterator();
          //...and go back one step
          currentSlot--;
        }else{
          //we successfully filled the current slot
          if(currentSlot == executors.length -1){
            //we have a full hit
            hitsOnCurrentDocument.add(slots);
            //create a new copy of the first length-1 elements
            Binding[] newSlots = new Binding[executors.length];
            System.arraycopy(slots, 0, newSlots, 0, slots.length -1);
            slots = newSlots;
            //..and stay on the same slot
          }else{
            //not a full hit yet ->advance to next slot
            currentSlot++;
          }
        }
      }
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextHit()
     */
    public Binding nextHit() throws IOException {
      if(closed) return null;
      if(hitsOnCurrentDocument.isEmpty()) return null;
      else{
        //get the first hit, and construct the corresponding bindings array for 
        //it
        Binding[] hitSlots = hitsOnCurrentDocument.remove(0);
        Binding[] containedBindings = null;
        
        if(engine.isSubBindingsEnabled()){
          //there will be one contained binding for each sub-query, plus
          //all of their own contained bindings.
          int containedBindsCount = executors.length;
          for(Binding aSubHit : hitSlots){
            Binding[] containedB = aSubHit.getContainedBindings();
            containedBindsCount += containedB == null ? 0 : containedB.length; 
          }
          containedBindings = new Binding[containedBindsCount];
          //position to write into containedBindings
          int cbIdx =0;
          for(Binding aSubHit : hitSlots){
            containedBindings[cbIdx++] = aSubHit;
            if(aSubHit.getContainedBindings() != null){
              System.arraycopy(aSubHit.getContainedBindings(), 0,
                      containedBindings, cbIdx, 
                      aSubHit.getContainedBindings().length);
              cbIdx += aSubHit.getContainedBindings().length;
            }
          }
        }
        
        int length = hitSlots[hitSlots.length -1].getTermPosition() +
                     hitSlots[hitSlots.length -1].getLength() -
                     hitSlots[0].getTermPosition();
        return new Binding(query, hitSlots[0].getDocumentId(), 
                hitSlots[0].getTermPosition(), length, containedBindings);
      }
    }
  }
  
  /**
   * Constructor from an array of {@link QueryNode}s.
   * @param nodes the array of {@link QueryNode} representing the sequence of
   * sub-queries.
   * @param gaps an array of {@link Gap} objects, representing the gaps permitted 
   * between the sub-query results. The gap at position <code>n</code> in this 
   * array represents a space permitted <b>after</b> the sub-query at position
   * <code>n</code> in the <code>nodes<code> array. This means that the length 
   * of the <code>gaps<code> array should be <code>nodes.length -1</code>. If 
   * the provided parameter value is a shorter array, it will be padded with
   * zero-length gaps; if it is longer, the extra gaps at the end will be 
   * ignored. If this parameter is set to <code>null</code>, then no gaps are 
   * permitted: the result for sub-query <code>n+1</code> needs to start with 
   * the term following the one where the result for sub-query <code>n</code> 
   * ends. This is equivalent to providing an array filled with zero-length 
   * gaps.  
   */
  public SequenceQuery(Gap[] gaps, QueryNode... nodes) {
    this.nodes = nodes;
    this.gaps = gaps;
    //normalise the gaps array
    normaliseGaps();
  }

  /**
   * Makes sure all required Gap values are defined.
   */
  protected void normaliseGaps(){
    if(gaps == null){
      //no gaps provided -> create a new array of nulls.
      gaps = new Gap[nodes.length -1];
    } else if(gaps.length != (nodes.length -1)){
      //wrong size -> create a new array and copy all available useful elements
      Gap[] oldGaps = gaps;
      gaps = new Gap[nodes.length -1];
      System.arraycopy(oldGaps, 0, gaps, 0, 
              Math.min(oldGaps.length, gaps.length));
    }
    //now replace all nulls with zero gaps.
    for(int i = 0; i < gaps.length; i++){
      if(gaps[i] == null) gaps[i] = ZERO_GAP;
    }
  }

  /**
   * The sub-queries of this sequence.
   */
  private QueryNode[] nodes;
  
  /**
   * The gaps for this query.
   */
  private Gap[] gaps;
  
  /**
   * A cached copy of the zero-length gap, which is ferquently used. 
   */
  private static final Gap ZERO_GAP = new Gap(0, 0);
  
  /* (non-Javadoc)
   * @see gate.mimir.search.query.QueryNode#getQueryExecutor(gate.mimir.search.QueryEngine)
   */
  public QueryExecutor getQueryExecutor(QueryEngine engine) throws IOException {
    return new SequenceQueryExecutor(this, engine);
  }


  /**
   * @return the nodes
   */
  public QueryNode[] getNodes() {
    return nodes;
  }
  
  /**
   * Obtains a {@link Gap} that allows a number of terms between the min and 
   * max values (inclusive).
   * @param min the minimum number of term required for this gap.
   * @param max the maximum number of terms permitted for this gap.
   * @return an appropriately built {@link Gap} object.
   */
  public static Gap getGap(int min, int max){
    return new Gap(min, max);
  }
  
  public String toString() {
    StringBuilder str = new StringBuilder("SEQUENCE (");
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

  public Gap[] getGaps() {
    return gaps;
  }

}
