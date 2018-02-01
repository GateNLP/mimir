/*
 *  AndTermsQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 17 Jul 2012
 *
 *  $Id: AndTermsQuery.java 16583 2013-03-12 13:07:53Z valyt $
 */
package gate.mimir.search.terms;

import gate.mimir.search.terms.AbstractCompoundTermsQuery.CompoundCountsStrategy;
import it.unimi.dsi.fastutil.Arrays;
import it.unimi.dsi.fastutil.Swapper;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Performs Boolean AND between multiple {@link TermsQuery} instances.
 * The default count strategy used is 
 * {@link AbstractCompoundTermsQuery.CompoundCountsStrategy#FIRST}.
 */
public class AndTermsQuery extends AbstractCompoundTermsQuery {
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -6757669202064075218L;

  /**
   * Constructs a new AND term query.
   * 
   * @param stringsEnabled should terms strings be returned.
   * @param countsEnabled should term counts be returned. Counts are 
   * accumulated across all sub-queries: the count for a term is the sum of all
   * counts for the same term in all sub-queries.  
   * @param limit the maximum number of terms to be returned. 
   * @param subQueries the term queries that form the disjunction.
   */
  public AndTermsQuery(TermsQuery... subQueries) {
    super(subQueries);
    setCountsStrategy(AbstractCompoundTermsQuery.CompoundCountsStrategy.FIRST);
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.terms.CompoundTermsQuery#combine(gate.mimir.search.terms.TermsResultSet[])
   */
  @Override
  public TermsResultSet combine(TermsResultSet... resSets) {
    return andResultSets(resSets, countsStrategy);
  }

  public static TermsResultSet andResultSets(final TermsResultSet[] resSets,
      AbstractCompoundTermsQuery.CompoundCountsStrategy countsStrategy) {
    if(countsStrategy == null) countsStrategy = AbstractCompoundTermsQuery.CompoundCountsStrategy.FIRST;
    boolean lengthsAvailable = false;
    boolean countsAvailable = true;
    boolean descriptionsAvaialble = false;
    boolean origTermsAvailable = false;
    for(int i = 0; i < resSets.length; i++) {
      if(resSets[i].termStrings.length == 0) return TermsResultSet.EMPTY;
      // this implementation requires that all sub-queries return terms in a 
      // consistent order, so we sort them lexicographically by termString
      TermsResultSet.sortTermsResultSetByTermString(resSets[i]);
      // at least one sub-query must provide lengths, for us to be able to
      if(resSets[i].termLengths != null) lengthsAvailable = true;
      // at least one sub-query must provide original terms, for us to be able to
      if(resSets[i].originalTermStrings != null) origTermsAvailable = true;      
      // at least one sub-query must provide descriptions, for us to be able to
      if(resSets[i].termDescriptions != null) descriptionsAvaialble = true;      
      // all sub-queries must provide counts, for us to be able to
      if(resSets[i].termCounts == null) countsAvailable = false;
    }
    if(!countsAvailable || countsStrategy != AbstractCompoundTermsQuery.CompoundCountsStrategy.FIRST) {
      // the sorting of sub-queries is irrelevant
      // optimisation: sort sub-runners by increasing sizes
      Arrays.quickSort(0, resSets.length, new IntComparator() {
        @Override
        public int compare(Integer o1, Integer o2) { 
          return compare(o1.intValue(), o2.intValue());
        }
        @Override
        public int compare(int k1, int k2) {
          return resSets[k1].termStrings.length - resSets[k2].termStrings.length; 
        }
      }, new Swapper() {
        @Override
        public void swap(int a, int b) {
          TermsResultSet trs = resSets[a];
          resSets[a] = resSets[b];
          resSets[b] = trs;
        }
      });      
    }

    // prepare local data
    ObjectArrayList<String> termStrings = new ObjectArrayList<String>();
    ObjectArrayList<String> termDescriptions = descriptionsAvaialble ? 
        new ObjectArrayList<String>() : null;
    ObjectArrayList<String[][]> origTerms = origTermsAvailable ? 
          new ObjectArrayList<String[][]>() : null;        
    IntArrayList termCounts = countsAvailable ? new IntArrayList() : null;
    IntArrayList termLengths = lengthsAvailable ? new IntArrayList() : null;
    // merge the inputs
    int[] indexes = new int[resSets.length]; // initialised with 0s
    int currRunner = 0;
    String termString = resSets[currRunner].termStrings[indexes[currRunner]];
    top:while(currRunner < resSets.length) {
      currRunner++;
      while(currRunner < resSets.length &&
            resSets[currRunner].termStrings[indexes[currRunner]].equals(termString)) {
        currRunner++;
      }
      if(currRunner == resSets.length) {
        // all heads agree:
        // store the term string
        termStrings.add(termString);
        // calculate the term count
        if(countsAvailable) {
          int[] counts = new int[resSets.length];
          for(int i = 0; i < resSets.length; i++) {
            counts[i] = (resSets[i].termCounts != null) ? 
              (resSets[i].termCounts[indexes[i]]): -1;
          }
          termCounts.add(AbstractCompoundTermsQuery.computeCompoundCount(counts, countsStrategy));
        }
        // calculate the term length
        if(lengthsAvailable) {
          int termLength = -1;
          for(int i = 0; 
              i < resSets.length && termLength == -1; 
              i++) {
            if(resSets[i].termLengths != null){
              termLength = resSets[i].termLengths[indexes[i]]; 
            }
          }
          termLengths.add(termLength);
        }
        // extract description
        if(descriptionsAvaialble) {
          String termDescription = null;
          for(int i = 0; 
              i < resSets.length && termDescription == null; 
              i++) {
            if(resSets[i].termDescriptions != null){
              termDescription = resSets[i].termDescriptions[indexes[i]]; 
            }
          }
          termDescriptions.add(termDescription);          
        }
        // extract original terms
        if(origTermsAvailable) {
          String[][] origTerm = null;
          for(int i = 0; 
              i < resSets.length && origTerm == null; 
              i++) {
            if(resSets[i].originalTermStrings != null){
              origTerm = resSets[i].originalTermStrings[indexes[i]]; 
            }
          }
          origTerms.add(origTerm);          
        }
        // and start fresh
        currRunner = 0;
        indexes[currRunner]++;
        if(indexes[currRunner] == resSets[currRunner].termStrings.length) {
          // we're out
          break top;
        } else {
          termString  = resSets[currRunner].termStrings[indexes[currRunner]];
          continue top;
        }
      } else {
        // current runner is wrong
        while(resSets[currRunner].termStrings[indexes[currRunner]].compareTo(termString) < 0) {
          indexes[currRunner]++;
          if(indexes[currRunner] == resSets[currRunner].termStrings.length) {
            // this runner has run out
            break top;
          } else {
            if(resSets[currRunner].termStrings[indexes[currRunner]].compareTo(termString)  > 0) {
              // new term ID
              termString = resSets[currRunner].termStrings[indexes[currRunner]];
              currRunner = -1;
              continue top;
            }
          }
        }
      }
    } // top while
    // construct the result
    TermsResultSet res = new TermsResultSet(
        termStrings.toArray(new String[termStrings.size()]),
        lengthsAvailable? termLengths.toIntArray() : null,
        countsAvailable ? termCounts.toIntArray() : null,
        descriptionsAvaialble ? 
          termDescriptions.toArray(new String[termDescriptions.size()]): null);
    if(origTermsAvailable){
      res.originalTermStrings = origTerms.toArray(
        new String[origTerms.size()][][]);
    }
    return res;
  }
}
