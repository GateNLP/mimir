/*
 *  TruncateIndex.java
 *
 *  Copyright (c) 2007-2016, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Ian Roberts, 1st September 2016
 *
 *  $Id: TruncateIndex.java 19708 2016-10-29 16:23:28Z ian_roberts $
 */
package gate.mimir.util;

import gate.Gate;
import gate.mimir.IndexConfig;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.mimir.IndexConfig.TokenIndexerConfig;
import gate.mimir.MimirIndex;
import gate.mimir.index.AtomicIndex;
import gate.mimir.index.DocumentCollection;
import it.unimi.di.big.mg4j.index.CompressionFlags;
import it.unimi.di.big.mg4j.index.CompressionFlags.Coding;
import it.unimi.di.big.mg4j.index.CompressionFlags.Component;
import it.unimi.di.big.mg4j.index.DiskBasedIndex;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.index.IndexIterator;
import it.unimi.di.big.mg4j.index.IndexReader;
import it.unimi.di.big.mg4j.index.QuasiSuccinctIndex;
import it.unimi.di.big.mg4j.index.QuasiSuccinctIndexWriter;
import it.unimi.di.big.mg4j.index.cluster.DocumentalCluster;
import it.unimi.di.big.mg4j.io.IOFactory;
import it.unimi.di.big.mg4j.tool.Scan;
import it.unimi.dsi.big.io.FileLinesCollection;
import it.unimi.dsi.big.io.FileLinesCollection.FileLinesIterator;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.io.OutputBitStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.util.BloomFilter;
import it.unimi.dsi.util.Properties;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.log4j.Logger;

/**
 * Utility class to fix up a Mimir index that has been corrupted, e.g.
 * by an unclean shutdown or out-of-memory condition. The index must be
 * closed to use this tool, which either means the Mimir webapp is not
 * running, or the index has been deleted from the running Mimir. It is
 * very very strongly recommended to back up an index before attempting
 * this procedure. The clean up process will unavoidably remove some
 * number of documents from the tail of the index, but will attempt to
 * keep the number of lost documents to a minimum.
 * 
 * @author ian
 *
 */
public class TruncateIndex {

  private static final Logger log = Logger.getLogger(TruncateIndex.class);

  /**
   * Comparator that orders mimir zip collection files by number (e.g.
   * mimir-collection-16.zip comes after mimir-collection-12-15.zip but
   * before mimir-collection-100-120.zip)
   */
  public static final Comparator<File> ZIP_COLLECTION_COMPARATOR =
          new Comparator<File>() {
            public int compare(File a, File b) {
              int numA =
                      Integer.parseInt(a.getName().substring(
                              a.getName().lastIndexOf('-') + 1,
                              a.getName().length() - 4));
              int numB =
                      Integer.parseInt(b.getName().substring(
                              b.getName().lastIndexOf('-') + 1,
                              b.getName().length() - 4));
              return numA - numB;
            }
          };

  public static final Comparator<String> BATCH_COMPARATOR =
          new Comparator<String>() {
            public int compare(String a, String b) {
              if(a.equals("head")) {
                if(b.equals("head")) {
                  // both heads
                  return 0;
                } else {
                  // head before tail
                  return -1;
                }
              } else {
                if(b.equals("head")) {
                  // tail after head
                  return 1;
                } else {
                  // both tails, compare by number
                  int numA =
                          Integer.parseInt(a.substring(a.lastIndexOf('-') + 1));
                  int numB =
                          Integer.parseInt(b.substring(b.lastIndexOf('-') + 1));
                  return numA - numB;
                }
              }
            }
          };

  public static final FilenameFilter INDEX_NAME_FILTER = new FilenameFilter() {
    private Pattern pat = Pattern.compile("(?:token|mention)-\\d+");

    @Override
    public boolean accept(File dir, String name) {
      return pat.matcher(name).matches();
    }
  };

  public static final FilenameFilter BATCH_NAME_FILTER = new FilenameFilter() {
    private Pattern pat = Pattern.compile("head|tail-\\d+");

    @Override
    public boolean accept(File dir, String name) {
      return pat.matcher(name).matches();
    }
  };

  public static void main(String... args) throws Exception {
    Gate.runInSandbox(true);
    Gate.init();
    int i = 0;
    while(i < args.length && "-p".equals(args[i])) {
      Gate.getCreoleRegister().registerDirectories(new File(args[++i]).toURI().toURL());
      i++;
    }
    truncateIndex(new File(args[i]));
  }

  /**
   * Attempt to fix up a corrupted Mimir index by truncating some number
   * of documents off the end. There will be a certain number of
   * documents in complete index batches, and a (possibly different)
   * number of documents successfully persisted to disk in the zip files
   * of the DocumentCollection, the index will be truncated to the
   * smaller of those two numbers.
   * 
   * @param indexDirectory the top-level directory of the Mimir index
   *          (containing config.xml)
   */
  public static void truncateIndex(File indexDirectory) throws Exception {
    // 1. Repair the last zip file in the DocumentCollection
    repairLastZip(indexDirectory);

    // 2. Determine the last "good" batch (the greatest numbered head or
    // tail that is fully written to disk in every AtomicIndex) and
    // stash the bad ones
    String lastGoodBatch = determineLastGoodBatch(indexDirectory);

    if(lastGoodBatch == null) {
      throw new RuntimeException(
              "All batches are corrupt, sorry, this index is a write-off");
    }

    // 3. If the zip collection is at least as long as the sum of the
    // good batches, truncate it to match the batches and we're done.
    BatchDetails batches = batchEndPoints(indexDirectory);
    long totalDocsInBatches = batches.endPoints[batches.endPoints.length - 1];
    long totalDocsInZips = totalDocumentsInZipCollection(indexDirectory);

    if(totalDocsInBatches == totalDocsInZips) {
      log.info("We're in luck, the batches and zips line up exactly");
      return;
    } else if(totalDocsInZips > totalDocsInBatches) {
      truncateZipCollectionTo(indexDirectory, totalDocsInBatches);
      return;
    } else if(totalDocsInZips == 0) {
      throw new RuntimeException("Zip collection is empty");
    }

    // 4. Otherwise, the zip collection stops in the middle of a batch B
    int endBatch = -1;
    for(int i = 0; i < batches.names.length; i++) {
      if(batches.endPoints[i] >= totalDocsInZips) {
        endBatch = i;
        break;
      }
    }
    log.info("Zip collection ends within " + batches.names[endBatch]);
    if(batches.endPoints[endBatch] == totalDocsInZips) {
      // special case - zip collection ends exactly at the end of a
      // batch. Stash subsequent batches and we're done
      log.info("Zip collection ends exactly at the end of batch "
              + batches.names[endBatch]);
      log.info("Stashing subsequent batches");
      stashBatches(indexDirectory, java.util.Arrays.asList(batches.names)
              .subList(endBatch + 1, batches.endPoints.length));
      log.info("Done");
      return;
    }
    // 4.1. Stash B (for every AtomicIndex) and any batches beyond it.
    stashBatches(indexDirectory, java.util.Arrays.asList(batches.names)
            .subList(endBatch, batches.endPoints.length));

    // 4.2. Read each stashed B and re-write it but with documents
    // beyond the end of the zip collection omitted
    long endOfPreviousBatch = 0L;
    if(endBatch > 0) {
      endOfPreviousBatch = batches.endPoints[endBatch - 1];
    }
    trimBatch(indexDirectory, batches.names[endBatch], totalDocsInZips
            - endOfPreviousBatch);

    // 4.3. Truncate the direct indexes for those AtomicIndexes that
    // require it
    IndexConfig indexConfig =
            IndexConfig.readConfigFromFile(new File(indexDirectory,
                    MimirIndex.INDEX_CONFIG_FILENAME));
    TokenIndexerConfig[] tokenIndexes = indexConfig.getTokenIndexers();
    for(int i = 0; i < tokenIndexes.length; i++) {
      if(tokenIndexes[i].isDirectIndexEnabled()) {
        truncateDirectIndex(indexDirectory, "token-" + i,
                batches.names[endBatch], totalDocsInZips - 1);
      }
    }
    SemanticIndexerConfig[] semanticIndexes = indexConfig.getSemanticIndexers();
    for(int i = 0; i < semanticIndexes.length; i++) {
      if(semanticIndexes[i].isDirectIndexEnabled()) {
        truncateDirectIndex(indexDirectory, "mention-" + i,
                batches.names[endBatch], totalDocsInZips - 1);
      }
    }
  }

  public static void repairLastZip(File indexDirectory) throws IOException {
    log.info("Ensuring last zip file in " + indexDirectory.getAbsolutePath()
            + " is complete");
    File[] zipCollectionFiles =
            indexDirectory
                    .listFiles(DocumentCollection.CollectionFile.FILENAME_FILTER);
    if(zipCollectionFiles.length > 0) {
      java.util.Arrays.sort(zipCollectionFiles, ZIP_COLLECTION_COMPARATOR);
      File lastZip = zipCollectionFiles[zipCollectionFiles.length - 1];
      log.info("Last zip is " + lastZip.getName());
      File brokenBatches = new File(indexDirectory, "broken-batches");
      brokenBatches.mkdirs();
      File movedLastZip = new File(brokenBatches, lastZip.getName());
      if(movedLastZip.exists()) {
        movedLastZip.delete();
      }
      if(!lastZip.renameTo(movedLastZip)) {
        throw new RuntimeException("Could not stash " + lastZip.getName()
                + " in broken-batches");
      }
      log.debug("Moved " + lastZip.getName() + " to broken-batches");
      String lastGoodDoc = null;
      try(FileInputStream oldIn = new FileInputStream(movedLastZip);
              ZipInputStream zipIn = new ZipInputStream(oldIn);
              FileOutputStream newOut = new FileOutputStream(lastZip);
              ZipOutputStream zipOut = new ZipOutputStream(newOut)) {
        ZipEntry entry = null;
        try {
          while((entry = zipIn.getNextEntry()) != null) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            IOUtils.copy(zipIn, data);
            // if we get here the input zip was not truncated mid-entry,
            // so it's safe to write this entry
            zipOut.putNextEntry(entry);
            IOUtils.write(data.toByteArray(), zipOut);
            zipOut.closeEntry();
            lastGoodDoc = entry.getName();
          }
        } catch(EOFException eof) {
          // this is expected, if the zip was not properly closed
        }
      }
      log.info("Last good document ID was " + lastGoodDoc);
    } else {
      log.warn("No files in zip collection");
    }
  }

  /**
   * Determines the last "good" batch name (head or tail-N) for the
   * given index, and stashes any bad batches in the broken-batches
   * directory.
   * 
   * @param indexDirectory
   * @return
   * @throws IOException
   */
  public static String determineLastGoodBatch(File indexDirectory)
          throws IOException {
    String lastGood = null;

    File[] subIndexes = indexDirectory.listFiles(INDEX_NAME_FILTER);
    if(subIndexes.length == 0) {
      throw new RuntimeException("Index has no AtomicIndexes!");
    }
    String[] batches = subIndexes[0].list(BATCH_NAME_FILTER);
    java.util.Arrays.sort(batches, BATCH_COMPARATOR);
    BATCH: for(String batch : batches) {
      for(File subIndex : subIndexes) {
        if(!new File(new File(subIndex, batch), subIndex.getName()
                + ".properties").exists()) {
          break BATCH;
        }
      }
      // if we get to here we know this batch exists in all sub-indexes
      lastGood = batch;
    }

    if(lastGood != null) {
      File brokenBatches = new File(indexDirectory, "broken-batches");
      // stash bad batches
      for(File subIndex : subIndexes) {
        File[] thisIndexBatches = subIndex.listFiles(BATCH_NAME_FILTER);
        for(File b : thisIndexBatches) {
          if(BATCH_COMPARATOR.compare(lastGood, b.getName()) < 0) {
            // this is a bad batch, stash it
            File movedB =
                    new File(brokenBatches, subIndex.getName() + "-"
                            + b.getName());
            if(movedB.exists()) {
              FileUtils.deleteDirectory(movedB);
            }
            if(!b.renameTo(movedB)) {
              throw new RuntimeException("Could not stash " + movedB.getName());
            }
          }
        }
      }
    }

    return lastGood;
  }

  public static class BatchDetails {
    String[] names;

    long[] endPoints;
  }

  public static BatchDetails batchEndPoints(File indexDirectory)
          throws IOException, ConfigurationException {
    BatchDetails details = new BatchDetails();
    long totalDocs = 0;
    File[] subIndexes = indexDirectory.listFiles(INDEX_NAME_FILTER);
    if(subIndexes.length == 0) {
      throw new RuntimeException("Index has no AtomicIndexes!");
    }
    details.names = subIndexes[0].list(BATCH_NAME_FILTER);
    java.util.Arrays.sort(details.names, BATCH_COMPARATOR);

    details.endPoints = new long[details.names.length];
    for(int i = 0; i < details.names.length; i++) {
      Properties batchProps = new Properties();
      try(FileInputStream propsIn =
              new FileInputStream(new File(new File(subIndexes[0],
                      details.names[i]), subIndexes[0].getName()
                      + ".properties"))) {
        batchProps.load(propsIn);
      }
      totalDocs += batchProps.getLong("documents");
      details.endPoints[i] = totalDocs;
    }

    return details;
  }

  public static long totalDocumentsInZipCollection(File indexDirectory)
          throws IOException {
    long totalDocs = 0;
    File[] zipCollectionFiles =
            indexDirectory
                    .listFiles(DocumentCollection.CollectionFile.FILENAME_FILTER);
    for(File zip : zipCollectionFiles) {
      try(ZipFile zf = new ZipFile(zip)) {
        totalDocs += zf.size();
      }
    }

    return totalDocs;
  }

  public static void truncateZipCollectionTo(File indexDirectory, long numDocs)
          throws IOException {
    File[] zipCollectionFiles =
            indexDirectory
                    .listFiles(DocumentCollection.CollectionFile.FILENAME_FILTER);
    java.util.Arrays.sort(zipCollectionFiles, ZIP_COLLECTION_COMPARATOR);
    // the truncation point is somewhere within the last zip file whose
    // first entry is less than numDocs (document IDs are zero based, so
    // the document named numDocs is actually the (numDocs+1)th one).
    int targetFile = -1;
    for(int i = 0; i < zipCollectionFiles.length; i++) {
      try(FileInputStream fis = new FileInputStream(zipCollectionFiles[i]);
              ZipInputStream zipIn = new ZipInputStream(fis)) {
        ZipEntry firstEntry = zipIn.getNextEntry();
        if(firstEntry != null) {
          long documentId = Long.parseLong(firstEntry.getName());
          if(documentId >= numDocs) {
            break;
          } else {
            targetFile = i;
          }
        }
      }
    }

    if(targetFile < 0) {
      throw new RuntimeException(
              "Zip collection broken beyond repair - there is no zip file containing the cut point");
    }

    // we know that document (numDocs-1) is somewhere in
    // zipCollectionFiles[targetFile]. Move that file out of the way and
    // rewrite it, truncated appropriately.
    File origFile = zipCollectionFiles[targetFile];
    File brokenBatches = new File(indexDirectory, "broken-batches");
    brokenBatches.mkdirs();
    File movedFile =
            new File(brokenBatches, "to-truncate-" + origFile.getName());
    if(movedFile.exists()) {
      movedFile.delete();
    }
    if(!origFile.renameTo(movedFile)) {
      throw new RuntimeException("Could not stash " + origFile.getName()
              + " in broken-batches");
    }
    String lastEntryName = String.valueOf(numDocs - 1);
    try(FileInputStream oldIn = new FileInputStream(movedFile);
            ZipInputStream zipIn = new ZipInputStream(oldIn);
            FileOutputStream newOut = new FileOutputStream(origFile);
            ZipOutputStream zipOut = new ZipOutputStream(newOut)) {
      ZipEntry entry = null;
      try {
        while((entry = zipIn.getNextEntry()) != null) {
          ByteArrayOutputStream data = new ByteArrayOutputStream();
          IOUtils.copy(zipIn, data);
          // if we get here the input zip was not truncated mid-entry,
          // so it's safe to write this entry
          zipOut.putNextEntry(entry);
          IOUtils.write(data.toByteArray(), zipOut);
          zipOut.closeEntry();
          if(lastEntryName.equals(entry.getName())) {
            // reached the cut point, stop copying
            break;
          }
        }
      } catch(EOFException eof) {
        // this is expected, if the zip was not properly closed
      }
    }
    log.info("Truncated zip collection file " + origFile + " to document "
            + lastEntryName);
  }

  public static void stashBatches(File indexDirectory, List<String> batches)
          throws IOException {
    File brokenBatches = new File(indexDirectory, "broken-batches");
    File[] subIndexes = indexDirectory.listFiles(INDEX_NAME_FILTER);

    for(File subIndex : subIndexes) {
      for(String batchName : batches) {
        File b = new File(subIndex, batchName);
        File movedB =
                new File(brokenBatches, subIndex.getName() + "-" + batchName);
        if(movedB.exists()) {
          FileUtils.deleteDirectory(movedB);
        }
        if(!b.renameTo(movedB)) {
          throw new RuntimeException("Could not stash " + movedB.getName());
        }
      }
    }
  }

  /**
   * Trim the given batch in all sub-indexes to the given length in
   * documents. Assumes the batch has already been stashed as
   * broken-batches/subindex-batchName.
   * 
   * @param indexDirectory top level index directory
   * @param batchName name of the batch to trim
   * @param numDocs number of documents to which the batch should be
   *          trimmed.
   */
  public static void trimBatch(File indexDirectory, String batchName,
          long numDocs) throws Exception {
    File brokenBatches = new File(indexDirectory, "broken-batches");
    File[] subIndexes = indexDirectory.listFiles(INDEX_NAME_FILTER);

    for(File subIndex : subIndexes) {
      File stashedBatch =
              new File(brokenBatches, subIndex.getName() + "-" + batchName);
      if(!stashedBatch.exists()) {
        throw new RuntimeException("Stashed batch " + stashedBatch
                + " not found");
      }
      File batchDir = new File(subIndex, batchName);
      batchDir.mkdirs();
      log.info("Trimming batch " + batchDir);
      String stashedIndexBasename =
              new File(stashedBatch, subIndex.getName()).getAbsolutePath();
      String outputIndexBasename =
              new File(batchDir, subIndex.getName()).getAbsolutePath();

      Index stashedIndex = Index.getInstance(stashedIndexBasename, true, true);

      // when you read through an index sequentially, the IndexIterators
      // don't tell you what term they were for, so we need to read the
      // .terms file from the stashed batch in step with the index
      // reader.
      File stashedTermsFile =
              new File(stashedIndexBasename + DiskBasedIndex.TERMS_EXTENSION);
      FileLinesCollection termsColl =
              new FileLinesCollection(stashedTermsFile.getAbsolutePath(),
                      "UTF-8");
      long numTerms = termsColl.size64();
      Iterator<MutableString> termsIter = termsColl.iterator();
      File newTermsFile =
              new File(outputIndexBasename + DiskBasedIndex.TERMS_EXTENSION);

      // there will certainly be no *more* than numTerms terms in the
      // final index, there may be fewer
      BloomFilter<Void> termFilter = BloomFilter.create(Math.max(numTerms, 1));

      Properties writerProperties = null;
      long writtenBits = 0;
      int maxDocSize = 0;
      int maxCount = 0;
      long totalOccurrences = 0;
      try(IndexReader indexReader = stashedIndex.getReader();
              FileOutputStream termsOS = new FileOutputStream(newTermsFile);
              OutputStreamWriter termsOSW =
                      new OutputStreamWriter(termsOS, "UTF-8");
              PrintWriter termsWriter = new PrintWriter(termsOSW)) {
        QuasiSuccinctIndexWriter indexWriter =
                new QuasiSuccinctIndexWriter(
                        IOFactory.FILESYSTEM_FACTORY,
                        outputIndexBasename,
                        numDocs,
                        Fast.mostSignificantBit(QuasiSuccinctIndex.DEFAULT_QUANTUM),
                        QuasiSuccinctIndexWriter.DEFAULT_CACHE_SIZE,
                        CompressionFlags.DEFAULT_QUASI_SUCCINCT_INDEX,
                        ByteOrder.nativeOrder());

        IndexIterator iter;
        while((iter = indexReader.nextIterator()) != null) {
          MutableString term = termsIter.next();
          // we can't stream the inverted list, because we need to know
          // up front how many documents the term is found in so we can
          // write that number before writing the positions.
          LongList docPointers = new LongArrayList();
          IntList counts = new IntArrayList();
          List<IntArrayList> positions = new ArrayList<>();
          long frequency = 0;
          long curPointer;
          long occurrences = 0;
          long sumMaxPos = 0;
          while((curPointer = iter.nextDocument()) != IndexIterator.END_OF_LIST) {
            if(curPointer < numDocs) {
              frequency++;
              docPointers.add(curPointer);
              counts.add(iter.count());
              IntArrayList thisDocPositions = new IntArrayList(iter.count());
              positions.add(thisDocPositions);
              occurrences += iter.count();
              totalOccurrences += iter.count();
              if(iter.count() > maxCount) {
                maxCount = iter.count();
              }
              int pos;
              int lastPos = 0;
              while((pos = iter.nextPosition()) != IndexIterator.END_OF_POSITIONS) {
                thisDocPositions.add(pos);
                lastPos = pos;
              }
              sumMaxPos += lastPos;
              if(lastPos > maxDocSize) {
                maxDocSize = lastPos;
              }
            } else {
              break;
            }
          }

          if(frequency > 0) {
            // this term occurred in at least one document that we're
            // not truncating, so now we know it's safe to write the
            // (truncated) inverted list to the new index and the term
            // to the terms file.

            term.println(termsWriter);
            termFilter.add(term);

            indexWriter.newInvertedList(frequency, occurrences, sumMaxPos);
            indexWriter.writeFrequency(frequency);
            for(int i = 0; i < frequency; i++) {
              OutputBitStream obs = indexWriter.newDocumentRecord();
              indexWriter.writeDocumentPointer(obs, docPointers.get(i));
              indexWriter.writePositionCount(obs, counts.get(i));
              indexWriter.writeDocumentPositions(obs, positions.get(i)
                      .elements(), 0, positions.get(i).size(), -1);
            }
          }
        }

        indexWriter.close();
        writerProperties = indexWriter.properties();
        // write stats file
        try(PrintStream statsPs =
                new PrintStream(new File(outputIndexBasename
                        + DiskBasedIndex.STATS_EXTENSION))) {
          indexWriter.printStats(statsPs);
        }
        writtenBits = indexWriter.writtenBits();
      }

      // regenerate the term map from the (possibly shorter) terms file
      AtomicIndex.generateTermMap(new File(outputIndexBasename
              + DiskBasedIndex.TERMS_EXTENSION), new File(outputIndexBasename
              + DiskBasedIndex.TERMMAP_EXTENSION), null);

      // write the bloom filter
      BinIO.storeObject(termFilter, new File(outputIndexBasename
              + DocumentalCluster.BLOOM_EXTENSION));

      // write the truncated sizes file
      File stashedSizesFile =
              new File(stashedIndexBasename + DiskBasedIndex.SIZES_EXTENSION);
      File sizesFile =
              new File(outputIndexBasename + DiskBasedIndex.SIZES_EXTENSION);
      try(InputBitStream stashedSizesStream =
              new InputBitStream(stashedSizesFile);
              OutputBitStream sizesStream = new OutputBitStream(sizesFile)) {
        for(long i = 0; i < numDocs; i++) {
          sizesStream.writeGamma(stashedSizesStream.readGamma());
        }
      }

      // generate the index properties
      Properties stashedProps = new Properties();
      try(FileInputStream stashedPropsStream =
              new FileInputStream(stashedIndexBasename
                      + DiskBasedIndex.PROPERTIES_EXTENSION)) {
        stashedProps.load(stashedPropsStream);
      }
      Properties newProps = new Properties();
      newProps.setProperty(Index.PropertyKeys.TERMPROCESSOR,
              stashedProps.getProperty(Index.PropertyKeys.TERMPROCESSOR));
      newProps.setProperty(Index.PropertyKeys.SIZE, writtenBits);
      // -1 means unknown
      newProps.setProperty(Index.PropertyKeys.MAXDOCSIZE, maxDocSize);
      newProps.setProperty(Index.PropertyKeys.MAXCOUNT, maxCount);
      newProps.setProperty(Index.PropertyKeys.OCCURRENCES, totalOccurrences);
      writerProperties.addAll(newProps);
      Scan.saveProperties(IOFactory.FILESYSTEM_FACTORY, writerProperties,
              outputIndexBasename + DiskBasedIndex.PROPERTIES_EXTENSION);
    }
  }

  /**
   * Truncate the given direct index to remove documents beyond the
   * given lastDocId. The original version of the batch is assumed to
   * have been stashed as broken-batches/subIndexName-batchName
   * 
   * @param indexDirectory the top-level index directory
   * @param subIndexName the name of the sub-index (token-N or
   *          mention-N)
   * @param batchName the name of the batch (head or tail-N)
   * @param lastDocId the last valid document ID
   */
  public static void truncateDirectIndex(File indexDirectory,
          String subIndexName, String batchName, long lastDocId)
          throws Exception {
    File brokenBatches = new File(indexDirectory, "broken-batches");
    File stashedBatch = new File(brokenBatches, subIndexName + "-" + batchName);
    if(!stashedBatch.exists()) {
      throw new RuntimeException("Stashed batch " + stashedBatch + " not found");
    }
    File batchDir = new File(new File(indexDirectory, subIndexName), batchName);
    batchDir.mkdirs();
    log.info("Trimming direct index for batch " + batchDir);

    String stashedIndexBasename =
            new File(stashedBatch, subIndexName
                    + AtomicIndex.DIRECT_INDEX_NAME_SUFFIX).getAbsolutePath();
    String outputIndexBasename =
            new File(batchDir, subIndexName
                    + AtomicIndex.DIRECT_INDEX_NAME_SUFFIX).getAbsolutePath();

    // A direct index is modelled in Mimir as an inverted index where
    // the terms are documents and vice versa. The "term string" is a
    // zero-padded hex representation of the document ID, so we simply
    // need to stream "inverted" lists from the stashed index to the new
    // one until we reach the term string that is the hex representation
    // of lastDocId.

    MutableString lastDocIdAsHex =
            new MutableString(AtomicIndex.longToTerm(lastDocId));

    // determine the number of documents in this direct index (i.e. the
    // number of entries in the .terms file that are lexicographically
    // <= lastDocIdAsHex)
    long numDocsInIndex = 0;

    File stashedTermsFile =
            new File(stashedIndexBasename + DiskBasedIndex.TERMS_EXTENSION);
    FileLinesCollection termsColl =
            new FileLinesCollection(stashedTermsFile.getAbsolutePath(), "UTF-8");
    try(FileLinesIterator docIdsIter = termsColl.iterator()) {
      while(docIdsIter.hasNext()
              && docIdsIter.next().compareTo(lastDocIdAsHex) <= 0) {
        numDocsInIndex++;
      }
    }
    log.info("Trimmed index will contain " + numDocsInIndex + " documents");

    // write the truncated "terms" file, term map and bloom filter
    BloomFilter<Void> docBloomFilter = BloomFilter.create(numDocsInIndex);

    try(FileLinesIterator docIdsIter = termsColl.iterator();
            PrintWriter pw =
                    new PrintWriter(new OutputStreamWriter(
                            new FastBufferedOutputStream(new FileOutputStream(
                                    outputIndexBasename
                                            + DiskBasedIndex.TERMS_EXTENSION),
                                    64 * 1024), "UTF-8"))) {
      for(long i = 0; i < numDocsInIndex; i++) {
        MutableString t = docIdsIter.next();
        t.println(pw);
        docBloomFilter.add(t);
      }
    }
    AtomicIndex.generateTermMap(new File(outputIndexBasename
            + DiskBasedIndex.TERMS_EXTENSION), new File(outputIndexBasename
            + DiskBasedIndex.TERMMAP_EXTENSION), null);
    BinIO.storeObject(docBloomFilter, new File(outputIndexBasename
            + DocumentalCluster.BLOOM_EXTENSION));

    // stream "inverted lists" (i.e. documents) from the stashed to the
    // new index, and build up a cache of "document sizes" (i.e. the
    // number of documents that contain each term referenced in this
    // index). We can't simply use the sizes from the stashed index
    // because they will include the counts from the inverted lists
    // we're trimming off.
    Long2IntOpenHashMap termSizes = new Long2IntOpenHashMap();
    termSizes.defaultReturnValue(0);

    // we need the total potential number of direct terms to create the
    // index writer
    File directTermsFile =
            new File(new File(indexDirectory, subIndexName),
                    AtomicIndex.DIRECT_TERMS_FILENAME);
    FileLinesCollection directTerms =
            new FileLinesCollection(directTermsFile.getAbsolutePath(), "UTF-8");

    Index stashedIndex = Index.getInstance(stashedIndexBasename, true, false);

    int maxCount = 0;
    long totalOccurrences = 0;
    long writtenBits = 0;
    int maxTermSize = -1; // -1 means unknown
    Properties writerProperties;
    
    try(IndexReader indexReader = stashedIndex.getReader()) {
      // copy the default compression flags, and remove positions
      Map<Component, Coding> flags =
              new HashMap<Component, Coding>(
                      CompressionFlags.DEFAULT_QUASI_SUCCINCT_INDEX);
      flags.remove(Component.POSITIONS);
      QuasiSuccinctIndexWriter directIndexWriter =
              new QuasiSuccinctIndexWriter(
                      IOFactory.FILESYSTEM_FACTORY,
                      outputIndexBasename,
                      directTerms.size64(),
                      Fast.mostSignificantBit(QuasiSuccinctIndex.DEFAULT_QUANTUM),
                      QuasiSuccinctIndexWriter.DEFAULT_CACHE_SIZE, flags,
                      ByteOrder.nativeOrder());
      IndexIterator iter;
      int docCounter = 0;
      long occurrences = 0;
      while((iter = indexReader.nextIterator()) != null
              && ++docCounter <= numDocsInIndex) {
        // annoyingly we can't stream straight from the old inverted
        // list to the new one, as we need to know up front the total
        // occurrences value which is not exposed through any public
        // API.
        LongList docPointers = new LongArrayList();
        IntList counts = new IntArrayList();
        long frequency = iter.frequency();
        long curPointer;
        while((curPointer = iter.nextDocument()) != IndexIterator.END_OF_LIST) {
          docPointers.add(curPointer);
          counts.add(iter.count());
          termSizes.put(curPointer, termSizes.get(curPointer) + iter.count());
          occurrences += iter.count();
          totalOccurrences += iter.count();
          if(iter.count() > maxCount) {
            maxCount = iter.count();
          }
        }
        directIndexWriter.newInvertedList(frequency, occurrences, 0);
        directIndexWriter.writeFrequency(frequency);
        for(int i = 0; i < frequency; i++) {
          OutputBitStream obs = directIndexWriter.newDocumentRecord();
          directIndexWriter.writeDocumentPointer(obs, docPointers.get(i));
          directIndexWriter.writePositionCount(obs, counts.get(i));
          // no positions in a direct index
        }
      }
      directIndexWriter.close();
      writtenBits = directIndexWriter.writtenBits();
      
      // write the new sizes file
      File sizesFile = new File(outputIndexBasename + DiskBasedIndex.SIZES_EXTENSION);
      try(OutputBitStream sizesStream = new OutputBitStream(sizesFile)) {
        for(long i = 0; i < directTerms.size64(); i++) {
          int termSize = termSizes.get(i);
          sizesStream.writeGamma(termSize);
          if(termSize > maxTermSize) {
            maxTermSize = termSize;
          }
        }
      }
      writerProperties = directIndexWriter.properties();
      // write stats file
      try(PrintStream statsPs =
              new PrintStream(new File(outputIndexBasename
                      + DiskBasedIndex.STATS_EXTENSION))) {
        directIndexWriter.printStats(statsPs);
      }
    }
    
    // generate the index properties
    Properties stashedProps = new Properties();
    try(FileInputStream stashedPropsStream =
            new FileInputStream(stashedIndexBasename
                    + DiskBasedIndex.PROPERTIES_EXTENSION)) {
      stashedProps.load(stashedPropsStream);
    }
    Properties newProps = new Properties();
    newProps.setProperty(Index.PropertyKeys.TERMPROCESSOR,
            stashedProps.getProperty(Index.PropertyKeys.TERMPROCESSOR));
    newProps.setProperty(Index.PropertyKeys.SIZE, writtenBits);
    // -1 means unknown
    newProps.setProperty(Index.PropertyKeys.MAXDOCSIZE, maxTermSize);
    newProps.setProperty(Index.PropertyKeys.MAXCOUNT, maxCount);
    newProps.setProperty(Index.PropertyKeys.OCCURRENCES, totalOccurrences);
    writerProperties.addAll(newProps);
    Scan.saveProperties(IOFactory.FILESYSTEM_FACTORY, writerProperties,
            outputIndexBasename + DiskBasedIndex.PROPERTIES_EXTENSION);
  }
}
