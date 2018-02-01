/*
 *  TermsResultSet.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *  
 *  Valentin Tablan, 13 Jul 2012
 *
 *  $Id: TermsResultSet.java 19444 2016-06-28 16:38:18Z ian_roberts $
 */
package gate.mimir.search.terms;

import gate.mimir.SemanticAnnotationHelper;
import it.unimi.dsi.fastutil.Arrays;
import it.unimi.dsi.fastutil.ints.AbstractIntComparator;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Class representing the results of a {@link TermsQuery}. 
 * A terms result set is a set of terms, represented by their 
 * {@link #termStrings}. Optionally {@link #termCounts}, 
 * {@link #termDescriptions}, and {@link #termLengths} may also be available.
 */
public class TermsResultSet implements Serializable {
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -7722325563637139625L;

  
  /**
   * The lengths (number of tokens) for the terms. Array parallel with 
   * {@link #termStrings}, and {@link #termDescriptions}.
   */
  public final int[] termLengths;
  
  /**
   * The strings for the terms. Array parallel with 
   * {@link #termCounts} and {@link #termDescriptions}.
   */
  public final String[] termStrings;

  /**
   * This field is populated by the 
   * {@link #groupByDescription(TermsResultSet...)} method. It contains term 
   * strings from the original result sets indexed by position in this result 
   * set, and the index of the results set. For example 
   * originalTermStrings[i][j] is a String[], containing all the term strings
   * associated with termDescriptions[i] in the j<sup>th</sup> result set.  
   */
  public String[][][] originalTermStrings;
  
  /**
   * For annotation indexes, the term string is simply a URI in whatever format
   * is used by the {@link SemanticAnnotationHelper} that was used to index the
   * annotations. These URIs are not useful outside of the annotation helper 
   * and index, so term descriptions can be requested. If term descriptions were
   * produced during the search, they are stored in this array (which is aligned
   *  with {@link #termIds} and {@link #termCounts}).
   */
  public final String[] termDescriptions;
  
  /**
   * The counts (numbers of occurrences) for the terms. Array parallel with 
   * {@link #termStrings} and {@link #termIds}.
   */
  public final int[] termCounts;
  
  public TermsResultSet(String[] termStrings, int[] termLengths, 
                        int[] termCounts, String[] termDescriptions) {
    super();
    this.termStrings = termStrings;
    this.termLengths = termLengths;
    this.termCounts = termCounts;
    this.termDescriptions = termDescriptions;
  }
  
  /**
   * Constant representing the empty result set.
   */
  public static final TermsResultSet EMPTY = new TermsResultSet(
      new String[]{}, new int[] {}, new int[]{}, new String[]{});
  
  
  /**
   * Given a position in {@link #termDescriptions}, this method computes all 
   * term strings that had that description in each of the sub-indexes of the
   * federated index that produced this result set. 
   * @param termPosition the term for which the original term strings are being
   * requested.
   * @return An array where element at position i is an array containing all the
   * term strings (in the dictionary of sub-index i) that had the given term
   * description when the original query was answered by sub-index i, or null
   * if original terms strings are not available.
   */
  public String[][] getSubIndexTerms(int termPosition) {
    return (originalTermStrings != null) ?
        originalTermStrings[termPosition] : null;
  }
  
  /**
   * Tries to locate the correct term position and calls 
   * {@link #getSubIndexTerms(int)}. 
   * @param termString
   * @return
   */
  public String[][] getSubIndexTerms(String termString) {
    int termPos = -1;
    try {
      termPos = Integer.parseInt(termString);
    } catch (Exception e) {}
    if(termStrings[termPos].equals(termString)) {
      return getSubIndexTerms(termPos);
    } else{
      // could not convert it: leave it unchanged
      return new String[][]{{termString}};
    }
  }
  
  /**
   * Sorts the arrays inside a {@link TermsResultSet} using the termString for
   * comparison.
   * @param trs
   */
  public static void sortTermsResultSetByTermString(final TermsResultSet trs) {
    Arrays.quickSort(0, trs.termStrings.length, new AbstractIntComparator() {
      @Override
      public int compare(int k1, int k2) {
        return trs.termStrings[k1].compareTo(trs.termStrings[k2]);
      }
    }, new Swapper(trs));
  }

  /**
   * Enumerates a result set and produces a new one after removing all the terms
   * with descriptions in the banned list.
   * @param bannedDescriptions A String array containing all the banned term 
   * descriptions.
   * @param setToFilter the terms result set to filter
   * @return the filtered result set.
   */
  public static TermsResultSet filterByDescriptionNot(TermsResultSet setToFilter, String... bannedDescriptions) {
    final boolean descriptionsAvailable = setToFilter.termDescriptions != null;
    if(!descriptionsAvailable) return setToFilter;
    
    final boolean countsAvailable = setToFilter.termCounts != null;
    final boolean lengthsAvailable = setToFilter.termLengths != null;
    final boolean origTermsAvailable = setToFilter.originalTermStrings != null;
    
    IntArrayList counts = countsAvailable ? new IntArrayList() : null;
    IntArrayList lengths = lengthsAvailable ? new IntArrayList() : null;
    ObjectArrayList<String> strings = new ObjectArrayList<String>();
    ObjectArrayList<String> descriptions = new ObjectArrayList<String>();
    ObjectArrayList<String[][]> origTerms = new ObjectArrayList<String[][]>();
    ObjectOpenHashSet<String> bannedSet = new ObjectOpenHashSet<String>(bannedDescriptions);
    
    for(int i = 0; i < setToFilter.termDescriptions.length; i++) {
      if(!bannedSet.contains(setToFilter.termDescriptions[i])) {
        descriptions.add(setToFilter.termDescriptions[i]);
        strings.add(setToFilter.termStrings[i]);
        if(countsAvailable) counts.add(setToFilter.termCounts[i]);
        if(lengthsAvailable) lengths.add(setToFilter.termLengths[i]);
        if(origTermsAvailable)origTerms.add(setToFilter.originalTermStrings[i]);
      }
    }
    int size = descriptions.size();
    TermsResultSet res = new TermsResultSet(
      strings.toArray(new String[size]),
      lengthsAvailable ? lengths.toArray(new int[size]) : null,
      countsAvailable ? counts.toArray(new int[size]) : null,
      descriptions.toArray(new String[size]));
    if(origTermsAvailable) res.originalTermStrings = 
        origTerms.toArray(new String[size][][]);
    return res;
  }
  
  /**
   * This method re-arranges the data included in one or more 
   * {@link TermsResultSet} values so that each term description occurs only
   * once in the {@link #termDescriptions} array.
   * 
   * A {@link TermsResultSet} obtained when calling 
   * {@link TermsQuery#execute(gate.mimir.search.QueryEngine)} may include the 
   * same description for multiple term strings: depending on the implementation
   * used to describe terms, distinct terms may end up with the same 
   * description. This could cause confusion when the output is presented to 
   * the user, as they would have no way to distinguish between the different 
   * terms.
   * 
   * When executing a terms query against a federated index, each sub-index 
   * returns its own result set. Terms originating in different sub-indexes can
   * have the same description.
   * 
   * This method combines these into a unified result set that preserves the 
   * right term ID to term description mappings by populating the 
   * {@link #originalTermStrings} array.
   * 
   * @param resSets the result sets produced by the sub-indexes of a federated
   * index.
   * @return the combined result set.
   */
  public static TermsResultSet groupByDescription(TermsResultSet... resSets) {
    boolean descriptionsAvaialble = true;
    boolean countsAvaialble = true;
    boolean lengthsAvaialble = false;
    for(TermsResultSet trs : resSets) {
      if(trs.termDescriptions == null) {
        descriptionsAvaialble = false;
      }
      if(trs.termCounts == null) {
        countsAvaialble = false;
      }
      if(trs.termLengths != null) {
        lengthsAvaialble = true;
      }
    }

    Object2ObjectOpenHashMap<String, TermData> desc2TermData = 
        new Object2ObjectOpenHashMap<String, TermData>();
    
    for(int subIndexPos = 0; subIndexPos < resSets.length; subIndexPos++) {
      TermsResultSet trs = resSets[subIndexPos];
      for(int i = 0; i < trs.termStrings.length; i++) {
        String description = descriptionsAvaialble ? 
            trs.termDescriptions[i] : trs.termStrings[i];
//          String string = descriptionsAvaialble ? trs.termStrings[i] : null;
        // get all the strings describing the current term
        String[] strings = null;
        if(trs.originalTermStrings != null) {
          // old TRS already has original term strings 
          if(trs.originalTermStrings[i].length == 1) {
            // old TRS was not federated
            strings = trs.originalTermStrings[i][0];
          } else {
            // old TRS was federated: get the term strings from the correct sub-index
            strings = trs.originalTermStrings[i][subIndexPos];
          }
        } else {
          // no old original term strings: use the actual term string
          strings = descriptionsAvaialble ? 
              new String[]{trs.termStrings[i]} : null;
        }
        
        TermData tData = desc2TermData.get(description);
        if(tData == null) {
          tData = new TermData(description, resSets.length);
          desc2TermData.put(description, tData);
        }
        if(descriptionsAvaialble && strings != null){
          for(String s : strings) tData.addString(subIndexPos, s);
//          tData.addString(subIndexPos, string);
        }
        if(countsAvaialble) {
          tData.count += trs.termCounts[i];
        }
        if(lengthsAvaialble && trs.termLengths != null && tData.length < 0) {
          tData.length = trs.termLengths[i];
        }
      }
    }
    // produce the compound result set
    String[] newStrings = new String[desc2TermData.size()];
    String[] newDescriptions = descriptionsAvaialble ? 
        new String[desc2TermData.size()] : null;
    int[] newCounts = countsAvaialble ? new int[desc2TermData.size()] : null;
    int[] newLenghts = lengthsAvaialble ? new int[desc2TermData.size()] : null;
    String[][][] originalTermStrings = descriptionsAvaialble ?
      new String[desc2TermData.size()][][] : null;
    ObjectIterator<Object2ObjectMap.Entry<String, TermData>> iter = 
        desc2TermData.object2ObjectEntrySet().fastIterator();    
    int pos = 0;
    while(iter.hasNext()) {
      TermData tData = iter.next().getValue();
      if(descriptionsAvaialble) {
        newDescriptions[pos] = tData.description;
        originalTermStrings[pos] = tData.getStrings();
        // term string does not actually mean anything; 
        // we use the term position instead
        // newStrings[pos] = Integer.toString(pos);
        Set<String> uniq = new HashSet<String>();
        for(String[] terms : originalTermStrings[pos]) {
          for(String term : terms) {
            uniq.add(term);
          }
        }
        if(uniq.isEmpty()) {
          newStrings[pos] = Integer.toString(pos);
        } else {
          List<String> termList= new ArrayList<String>(uniq);
          Collections.sort(termList);
          StringBuilder strb = new StringBuilder(termList.get(0));
          for(int i = 1; i < termList.size(); i++) {
            strb.append(" | ").append(termList.get(i));
          }
          newStrings[pos] = strb.toString();          
        }
      } else {
        newStrings[pos] = tData.description;
      }
      if(countsAvaialble) newCounts[pos] = tData.count;
      if(lengthsAvaialble) newLenghts[pos] = tData.length;
      pos++;
    }
    
    TermsResultSet res = new TermsResultSet(newStrings, newLenghts, newCounts, 
      newDescriptions);
    res.originalTermStrings = originalTermStrings;
    return res;
  }
  
  /**
   * Class used internally to store the term data when grouping terms results sets.
   * See {@link TermsResultSet#groupByDescription(TermsResultSet...)}.
   */
  private static class TermData {
    private String description;
    private int count;
    private int length;
    
    /**
     * The number of result sets being combined 
     */
    private int arity;
    
    /**
     * An array of size {@link #arity}, element at position i containing the 
     * term strings in the result set at position i, for this term description.
     */
    private ObjectArrayList<String>[] strings;

    public TermData(String description, int arity) {
      super();
      this.description = description;
      this.arity = arity;
      strings = new ObjectArrayList[arity];
      this.count = 0;
      this.length = -1;
    }
    
    /**
     * Adds a new term string for the sub-index at a given position.
     * @param position
     * @param string
     */
    public void addString(int position, String string) {
      if(strings[position] == null) {
        strings[position] = new ObjectArrayList<String>();
      }
      strings[position].add(string);
    }
    
    public String[][] getStrings() {
      String[][] res = new String[strings.length][];
      for(int i = 0; i < strings.length; i++) {
        if(strings[i] == null) {
          res[i] = new String[0];
        } else {
          res[i] = strings[i].toArray(new String[strings[i].size()]);
        }
      }
      return res;
    }
  }
  
  /**
   * A {@link it.unimi.dsi.fastutil.Swapper} implementation for 
   * {@link TermsResultSet}s. 
   */
  public static class Swapper implements it.unimi.dsi.fastutil.Swapper {
    private TermsResultSet trs;
    
    public Swapper(TermsResultSet trs) {
      this.trs = trs;
    }
    
    @Override
    public void swap(int a, int b) {
      String termString = trs.termStrings[a];
      trs.termStrings[a] = trs.termStrings[b];
      trs.termStrings[b] = termString;
      if(trs.termCounts != null) {
        int termCount = trs.termCounts[a];
        trs.termCounts[a] = trs.termCounts[b];
        trs.termCounts[b] = termCount;
      }
      if(trs.termLengths != null) {
        int termLength = trs.termLengths[a];
        trs.termLengths[a] = trs.termLengths[b];
        trs.termLengths[b] = termLength;
      }
      if(trs.termDescriptions != null) {
        String termDesc = trs.termDescriptions[a];
        trs.termDescriptions[a] = trs.termDescriptions[b];
        trs.termDescriptions[b] = termDesc;
      }
      if(trs.originalTermStrings != null) {
        String[][] origTSs = trs.originalTermStrings[a];
        trs.originalTermStrings[a] = trs.originalTermStrings[b];
        trs.originalTermStrings[b] = origTSs;
      }
    }
  }
}
