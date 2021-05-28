/*
 *  FederatedQueryRunner.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html),
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Ian Roberts, 15 Dec 2011
 * 
 *  $Id: FederatedQueryRunner.java 16414 2012-12-10 10:54:47Z valyt $
 */
package gate.mimir.search;

import gate.mimir.index.IndexException;
import gate.mimir.search.query.Binding;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntBigList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.fastutil.longs.LongBigList;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link QueryRunner} that presents a set of sub-indexes (represented by
 * their own QueryRunners) as a single index.
 */
public class FederatedQueryRunner implements QueryRunner {
  
  private static final Logger log = LoggerFactory.getLogger(FederatedQueryRunner.class);
  
  /**
   * The total number of result documents (or -1 if not yet known).
   */
  private long documentsCount = -1;
  
  /**
   * The query runners for the sub-indexes.
   */
  protected QueryRunner[] subRunners;
  
  /**
   * The next rank that needs to be merged from each sub runner.
   */
  protected long[] nextSubRunnerRank;
  
  /**
   * For each result document rank, this list supplies the index for the
   * sub-runner that supplied the document.
   */
  protected IntBigList rank2runnerIndex;
  
  /**
   * Which of the sub-runners has provided the previous document. This is an
   * instance field so that we can rotate the sub-runners (when the scores are
   * equal)
   */
  private int bestSubRunnerIndex = -1;
  
  /**
   * For each result document rank, this list supplies the rank of the document
   * in sub-runner that supplied it.
   */
  protected LongBigList rank2subRank;
  
  public FederatedQueryRunner(QueryRunner[] subrunners) {
    this.subRunners = subrunners;
    this.nextSubRunnerRank = null;
    this.rank2runnerIndex = new IntBigArrayBigList();
    this.rank2subRank = new LongBigArrayBigList();
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentsCount()
   */
  @Override
  public long getDocumentsCount() {
    if(documentsCount < 0) {
      long newDocumentsCount = 0;
      for(QueryRunner subRunner : subRunners) {
        long subDocumentsCount = subRunner.getDocumentsCount();
        if(subDocumentsCount < 0) {
          return -1;
        } else {
          newDocumentsCount += subDocumentsCount;
        }
      }
      synchronized(this) {
        // initialize the nextSubRunnerRank array
        nextSubRunnerRank = new long[subRunners.length];
        for(int i = 0; i < nextSubRunnerRank.length; i++) {
          if(subRunners[i].getDocumentsCount() == 0) {
            nextSubRunnerRank[i] = -1;
          }
        }
        documentsCount = newDocumentsCount;
      }
    }
    return documentsCount;
  }

  
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentsCountSync()
   */
  @Override
  public long getDocumentsCountSync() {
    for(QueryRunner subRunner : subRunners) {
      subRunner.getDocumentsCountSync();
    }
    return getDocumentsCount();
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getCurrentDocumentsCount()
   */
  @Override
  public long getDocumentsCurrentCount() {
    if(documentsCount >= 0) {
      return documentsCount;
    } else {
      int newDocumentsCount = 0;
      for(QueryRunner subRunner : subRunners) {
        newDocumentsCount += subRunner.getDocumentsCurrentCount();
      }
      return newDocumentsCount;
    }
  }
  
  /**
   * Ensure that the given rank is resolved to the appropriate sub-runner rank.
   * @throws IndexOutOfBoundsException if rank is beyond the last document.
   */
  private final synchronized void checkRank(long rank) throws IndexOutOfBoundsException, IOException {
    if(rank < 0) throw new IndexOutOfBoundsException(
      "Document rank " + rank + " is negative.");
    long maxRank = getDocumentsCount(); 
    if(rank >= maxRank) throw new IndexOutOfBoundsException(
        "Document rank too large (" + rank + " >= " + maxRank + ").");    
    // quick check to see if we need to do anything else
    if(rank < rank2runnerIndex.size64()) {
      return;
    }
    for(long nextRank = rank2runnerIndex.size64(); nextRank <= rank; nextRank++) {
      boolean allOut = true;
      // start with the runner next the previously chosen one
      bestSubRunnerIndex = (bestSubRunnerIndex + 1) % subRunners.length;
      double maxScore = Double.NEGATIVE_INFINITY;
      if(nextSubRunnerRank[bestSubRunnerIndex] >= 0) {
        maxScore = subRunners[bestSubRunnerIndex].getDocumentScore(
            nextSubRunnerRank[bestSubRunnerIndex]);
        allOut = false;
      }
      // now check all remaining runners, in round-robin fashion
      final int from = bestSubRunnerIndex + 1;
      final int to = bestSubRunnerIndex + subRunners.length;
      for(int bigI = from; bigI < to; bigI++) {
        int i = bigI % subRunners.length;
        if(nextSubRunnerRank[i] >= 0) {
          allOut = false;
          if(subRunners[i].getDocumentScore(nextSubRunnerRank[i]) > maxScore) {
            bestSubRunnerIndex = i;
            maxScore = subRunners[i].getDocumentScore(nextSubRunnerRank[i]);
          }
        }
      }
      if(allOut) {
        // we ran out of docs
        throw new IndexOutOfBoundsException("Requested rank was " + rank +
          " but ran out of documents at " + nextRank + "!");
      }
      // consume the next doc from bestSubRunnerIndex
      rank2runnerIndex.add(bestSubRunnerIndex);
      rank2subRank.add(nextSubRunnerRank[bestSubRunnerIndex]);
      if(nextSubRunnerRank[bestSubRunnerIndex] <
          subRunners[bestSubRunnerIndex].getDocumentsCount() -1) {
        nextSubRunnerRank[bestSubRunnerIndex]++;
      } else {
        // this runner has run out of documents
        nextSubRunnerRank[bestSubRunnerIndex] = -1;
      }
    }
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentID(int)
   */
  @Override
  public long getDocumentID(long rank) throws IndexOutOfBoundsException,
    IOException {
    checkRank(rank);
    long subId = subRunners[rank2runnerIndex.getInt(rank)].getDocumentID(
      rank2subRank.getLong(rank));
    return subId * subRunners.length + rank2runnerIndex.getInt(rank);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentScore(int)
   */
  @Override
  public double getDocumentScore(long rank) throws IndexOutOfBoundsException,
    IOException {
    checkRank(rank);
    return subRunners[rank2runnerIndex.getInt(rank)].getDocumentScore(
        rank2subRank.getLong(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentHits(int)
   */
  @Override
  public List<Binding> getDocumentHits(long rank)
    throws IndexOutOfBoundsException, IOException {
    checkRank(rank);
    return subRunners[rank2runnerIndex.getInt(rank)].getDocumentHits(
        rank2subRank.getLong(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentText(int, int, int)
   */
  @Override
  public String[][] getDocumentText(long rank, int termPosition, int length)
    throws IndexException, IndexOutOfBoundsException, IOException {
    checkRank(rank);
    return subRunners[rank2runnerIndex.getInt(rank)].getDocumentText(
        rank2subRank.getLong(rank), termPosition, length);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentURI(int)
   */
  @Override
  public String getDocumentURI(long rank) throws IndexException,
    IndexOutOfBoundsException, IOException {
    checkRank(rank);
    return subRunners[rank2runnerIndex.getInt(rank)].getDocumentURI(
        rank2subRank.getLong(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentTitle(int)
   */
  @Override
  public String getDocumentTitle(long rank) throws IndexException,
    IndexOutOfBoundsException, IOException {
    checkRank(rank);
    return subRunners[rank2runnerIndex.getInt(rank)].getDocumentTitle(
        rank2subRank.getLong(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataField(int, java.lang.String)
   */
  @Override
  public Serializable getDocumentMetadataField(long rank, String fieldName)
    throws IndexException, IndexOutOfBoundsException, IOException {
    checkRank(rank);
    return subRunners[rank2runnerIndex.getInt(rank)].getDocumentMetadataField(
        rank2subRank.getLong(rank), fieldName);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataFields(int, java.util.Set)
   */
  @Override
  public Map<String, Serializable> getDocumentMetadataFields(long rank,
                                                             Set<String> fieldNames)
    throws IndexException, IndexOutOfBoundsException, IOException {
    checkRank(rank);
    return subRunners[rank2runnerIndex.getInt(rank)].getDocumentMetadataFields(
          rank2subRank.getLong(rank), fieldNames);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#renderDocument(int, java.lang.Appendable)
   */
  @Override
  public void renderDocument(long rank, Appendable out) throws IOException,
    IndexException {
    checkRank(rank);
    subRunners[rank2runnerIndex.getInt(rank)].renderDocument(
        rank2subRank.getLong(rank), out);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#close()
   */
  @Override
  public void close() throws IOException {
    for(QueryRunner r : subRunners) {
      try{
        r.close();
      } catch (Throwable t) {
        log.error("Error while closing sub-runner", t);
      }
    }
  }
}
