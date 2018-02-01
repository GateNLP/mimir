/*
 *  IndexUpgrader.java
 *
 *  Copyright (c) 2007-2014, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 27 Feb 2014
 *
 *  $Id: IndexUpgrader.java 20281 2017-12-05 00:35:07Z ian_roberts $
 */
package gate.mimir.util;

import gate.Gate;
import gate.mimir.IndexConfig;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.mimir.MimirIndex;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.AtomicIndex;
import gate.mimir.index.DocumentCollection;
import gate.mimir.index.IndexException;
import it.unimi.di.big.mg4j.index.DiskBasedIndex;
import it.unimi.di.big.mg4j.index.cluster.DocumentalCluster;
import it.unimi.di.big.mg4j.io.IOFactory;
import it.unimi.di.big.mg4j.io.IOFactories;

import it.unimi.dsi.util.Properties;


import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

/**
 * Implementation of an algorithm to upgrade a 4.x or 5.0 M&iacute;mir
 * index to the format used by the current version.
 */
public class IndexUpgrader {
  
  protected static Logger logger = Logger.getLogger(IndexUpgrader.class);
  
  /**
   * A minimal set of files required for a valid index.
   */
  protected static final String[] REQUIRED_INDEX_FILE_EXTENSIONS = new String[] {
    DiskBasedIndex.INDEX_EXTENSION,
    DiskBasedIndex.POSITIONS_EXTENSION,
    DiskBasedIndex.TERMS_EXTENSION,
    DiskBasedIndex.OFFSETS_EXTENSION
  };

  /**
   * A minimal set of files required for a valid quasi-succinct index.
   */
  protected static final String[] REQUIRED_QS_INDEX_FILE_EXTENSIONS = new String[] {
    DiskBasedIndex.COUNTS_EXTENSION,
    DiskBasedIndex.COUNTS_EXTENSION + "offsets",
    DiskBasedIndex.POINTERS_EXTENSIONS,
    DiskBasedIndex.POINTERS_EXTENSIONS + "offsets",
    DiskBasedIndex.POSITIONS_EXTENSION,
    DiskBasedIndex.POSITIONS_EXTENSION + "offsets",
    DiskBasedIndex.TERMS_EXTENSION,
  };
   
  /**
   * A minimal set of files required for a valid direct index.
   */
  protected static final String[] REQUIRED_DIRECT_INDEX_FILE_EXTENSIONS = new String[] {
    DiskBasedIndex.INDEX_EXTENSION,
    DiskBasedIndex.TERMS_EXTENSION,
    DiskBasedIndex.OFFSETS_EXTENSION
  };

  /**
   * A minimal set of files required for a valid quasi-succinct direct index.
   */
  protected static final String[] REQUIRED_QS_DIRECT_INDEX_FILE_EXTENSIONS = new String[] {
    DiskBasedIndex.COUNTS_EXTENSION,
    DiskBasedIndex.COUNTS_EXTENSION + "offsets",
    DiskBasedIndex.POINTERS_EXTENSIONS,
    DiskBasedIndex.POINTERS_EXTENSIONS + "offsets",
    DiskBasedIndex.TERMS_EXTENSION,
  };
  
  public static void upgradeIndex(File indexDirectory) throws IOException, 
      IndexException {
    File indexConfigFile = new File(indexDirectory, 
        MimirIndex.INDEX_CONFIG_FILENAME);
    IndexConfig indexConfig = IndexConfig.readConfigFromFile(indexConfigFile);
    //test the version
    if(indexConfig.getFormatVersion() == 7) {
      upgradeFromV7(indexDirectory);
    } else {
      if(indexConfig.getFormatVersion() > 6 || indexConfig.getFormatVersion() < 4){
        throw new IndexException(
            "Unsupported index version: " + indexConfig.getFormatVersion());
      }
      
      //check that none of the files to be created exist already
      for(int i = 0 ; i < indexConfig.getTokenIndexers().length; i++) {
        File tokenDir = new File(indexDirectory, "token-" + i);
        if(tokenDir.exists()) {
          throw new IndexException(
              "Location required by upgraded index already exists:" + 
              tokenDir.getAbsolutePath());
        }
      }
      for(int i = 0 ; i < indexConfig.getSemanticIndexers().length; i++) {
        File tokenDir = new File(indexDirectory, "mention-" + i);
        if(tokenDir.exists()) {
          throw new IndexException(
              "Location required by upgraded index already exists:" + 
              tokenDir.getAbsolutePath());
        }
      }
      
      // check access
      File sourceDir = new File(indexDirectory, "mg4j");
      if(!sourceDir.isDirectory()) throw new IndexException(
          "Invalid index: could not find source directory at" + 
          sourceDir.getAbsolutePath());
      if(!sourceDir.canRead()) throw new IndexException(
          "Could not read source directory at" + sourceDir.getAbsolutePath());
      // check that we know how to deal with the S-A-H implementations
      Class<? extends SemanticAnnotationHelper> dbSahClass = null;
      try {
        dbSahClass = Class.forName(
            "gate.mimir.db.DBSemanticAnnotationHelper", 
            true, Gate.getClassLoader()).asSubclass(
                SemanticAnnotationHelper.class);
      } catch(ClassNotFoundException e) {
        throw new IndexException("Could not find the DB S-A-H class. "
            + "Is the 'db-h2' plugin loaded?", e);
      }
      for(int subIndexIdx = 0 ; 
          subIndexIdx < indexConfig.getSemanticIndexers().length; 
          subIndexIdx++) {
        SemanticIndexerConfig sic = indexConfig.getSemanticIndexers()[subIndexIdx];
        for(SemanticAnnotationHelper sah : sic.getHelpers()) {
          while(sah instanceof DelegatingSemanticAnnotationHelper) {
            sah = ((DelegatingSemanticAnnotationHelper)sah).getDelegate();
          }
          if(!dbSahClass.isAssignableFrom(sah.getClass())) {
            throw new IndexException("Cannot convert mentions index mentions-" +
                subIndexIdx + " because it does not use the DB H2 " + 
                "Annotation Helper, which is the only one supported by " + 
                "this automatic upgrade process");
          }
        }
      }
      // move files
      //collection files
      File[] collectionFiles = sourceDir.listFiles(
          DocumentCollection.CollectionFile.FILENAME_FILTER);
      for(File aColFile : collectionFiles) {
        File dest = new File(indexDirectory, aColFile.getName());
        if(! aColFile.renameTo(dest)) {
          throw new IndexException("Could not rename " + 
              aColFile.getAbsolutePath() + " to " + dest.getAbsolutePath());
        }
      }
      //token indexes
      for(int subIndexIdx = 0 ; 
          subIndexIdx < indexConfig.getTokenIndexers().length; 
          subIndexIdx++) {
        upgradeSubIndex(indexDirectory, subIndexIdx, 
            indexConfig.getTokenIndexers()[subIndexIdx].isDirectIndexEnabled(), 
            null);
      }
      // mention indexes
      for(int subIndexIdx = 0 ; 
          subIndexIdx < indexConfig.getSemanticIndexers().length; 
          subIndexIdx++) {
        SemanticIndexerConfig sic = indexConfig.getSemanticIndexers()[subIndexIdx]; 
        upgradeSubIndex(indexDirectory, subIndexIdx, sic.isDirectIndexEnabled(),
            sic);
      }
      // cleanup old dirs (only if empty)
      if(sourceDir.listFiles().length == 0) {
        if(!sourceDir.delete()) {
          logger.info("Could not delete old MG4J directory " + sourceDir + 
              " even though it appears empty.");
        }
      }
      File sourceDBDir = new File(indexDirectory, "db");
      if(sourceDBDir.listFiles().length == 0) {
        if(!sourceDBDir.delete()) {
          logger.info("Could not delete old DB directory " + sourceDBDir + 
              " even though it appears empty.");
        }
      }
    }
    //update the version number in the index config
    indexConfig.setFormatVersion(IndexConfig.FORMAT_VERSION);
    IndexConfig.writeConfigToFile(indexConfig, indexConfigFile);
  }
  
  /**
   * Moves the file belonging to one sub-index. 
   * @param indexDirectory the top level index directory for the M&iacute;mir 
   * index being upgraded. 
   * @param subIndexIdx the index (position) of the sub-index
   * @param mentionsConfig if this is a mentions index, then this parameter
   *  contains the mentions indexer config, null otherwise.
   * @param direct doe this sub-index have a direct index also?
   * @throws IndexException
   * @throws IOException 
   */
  protected static void upgradeSubIndex(File indexDirectory, int subIndexIdx, 
        final boolean direct, SemanticIndexerConfig mentionsConfig) throws IndexException, IOException {
    File sourceDir = new File(indexDirectory, "mg4j");
    // sanity checks
    final String inputFilePrefix = 
        (mentionsConfig != null ? "mimir-mentions-" : "mimir-token-") + 
        subIndexIdx;
    
    File[] atomicIndexFiles = sourceDir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.startsWith(inputFilePrefix + ".") || 
            (direct && name.startsWith(inputFilePrefix + 
             AtomicIndex.DIRECT_INDEX_NAME_SUFFIX + "."));
      }
    });

    Set<String> requiredExtensions = new HashSet<String>();
    Set<String> requiredDirectExtensions = new HashSet<String>();
    
    String indexPropertiesFile = new File(sourceDir, inputFilePrefix + DiskBasedIndex.PROPERTIES_EXTENSION).getAbsolutePath();
    try {
      Properties indexProperties = IOFactories.loadProperties(IOFactory.FILESYSTEM_FACTORY,
          indexPropertiesFile);
      if("it.unimi.di.big.mg4j.index.QuasiSuccinctIndex".equals(indexProperties.getString("indexclass"))) {
        requiredExtensions.addAll(Arrays.asList(REQUIRED_QS_INDEX_FILE_EXTENSIONS));
      } else {
        requiredExtensions.addAll(Arrays.asList(REQUIRED_INDEX_FILE_EXTENSIONS));
      }
    } catch(Exception e) {
      throw new IndexException("Error reading " + indexPropertiesFile, e);
    }

    if(direct) {
      String directPropertiesFile = new File(sourceDir, inputFilePrefix + AtomicIndex.DIRECT_INDEX_NAME_SUFFIX
          + DiskBasedIndex.PROPERTIES_EXTENSION).getAbsolutePath();
      try {
        Properties directProperties = IOFactories.loadProperties(IOFactory.FILESYSTEM_FACTORY,
            directPropertiesFile);
        if("it.unimi.di.big.mg4j.index.QuasiSuccinctIndex".equals(directProperties.getString("indexclass"))) {
          requiredDirectExtensions.addAll(Arrays.asList(REQUIRED_QS_DIRECT_INDEX_FILE_EXTENSIONS));
        } else {
          requiredDirectExtensions.addAll(Arrays.asList(REQUIRED_DIRECT_INDEX_FILE_EXTENSIONS));
        }
      } catch(Exception e) {
        throw new IndexException("Error reading " + directPropertiesFile, e);
      }
    }

    for(File aFile : atomicIndexFiles) {
      String extension = aFile.getName().substring(inputFilePrefix.length());
      if(direct && extension.startsWith(AtomicIndex.DIRECT_INDEX_NAME_SUFFIX)) {
        extension = extension.substring(AtomicIndex.DIRECT_INDEX_NAME_SUFFIX.length());
        requiredDirectExtensions.remove(extension);
      } else {
        requiredExtensions.remove(extension);  
      }
    }
    // check that we've seen all files we wanted
    if(!requiredExtensions.isEmpty() ||
        (direct && ! requiredDirectExtensions.isEmpty())) {
      //not all required files were found
      StringBuilder str = new StringBuilder(
          "Some required files were not found for index '");
      str.append(inputFilePrefix).append("': ");
      for(String extension : requiredExtensions) {
        str.append(new File(sourceDir, 
            inputFilePrefix + extension).getAbsolutePath());
        str.append("\n");
      }
      if(direct) {
        for(String extension : requiredDirectExtensions) {
          str.append(new File(sourceDir, 
              inputFilePrefix + extension).getAbsolutePath());
          str.append("\n");
        } 
      }
      throw new IndexException(str.toString());
    }
    
    // all tests passed - start creating the new directories
    String outputFilePrefix = (mentionsConfig != null ? "mention-" : "token-") + 
        subIndexIdx;
    File atomicIndexDir = new File(indexDirectory, outputFilePrefix);
    File headDir = new File(atomicIndexDir, AtomicIndex.HEAD_FILE_NAME);
    if(!headDir.mkdirs()) {
      throw new IndexException(
          "Location required by upgraded index could not be created:" + 
          headDir.getAbsolutePath());
    }
    for(File sourceFile : atomicIndexFiles) {
      String extension = sourceFile.getName().substring(inputFilePrefix.length());
      File destinationFile = new File(headDir, outputFilePrefix + extension);
      if(!sourceFile.renameTo(destinationFile)) {
        throw new IndexException("Could not rename " + 
            sourceFile.getAbsolutePath() + " to " + 
            destinationFile.getAbsolutePath());
      }
    }
    // create Bloom filter, and regenerate the term map
    File termsFile = new File(headDir, outputFilePrefix + 
        DiskBasedIndex.TERMS_EXTENSION); // guaranteed to exist, as tested already
    File termMapFile = new File(headDir, outputFilePrefix + 
        DiskBasedIndex.TERMMAP_EXTENSION); // may not exist but that's OK
    File bloomFile = new File(headDir, outputFilePrefix + 
        DocumentalCluster.BLOOM_EXTENSION); // guaranteed to exist, as tested already
    if(termMapFile.exists()) {
      if(!termMapFile.renameTo(new File(headDir, outputFilePrefix + 
          DiskBasedIndex.TERMMAP_EXTENSION + ".old"))) {
        logger.warn("Unable to back up old termmap for " + outputFilePrefix + "/" + headDir.getName());
      }
    }
    AtomicIndex.generateTermMap(termsFile, termMapFile, bloomFile);
    
    if(direct) {
      // create the direct.terms file by copying the terms file from 
      // the **inverted** index in head
      File dest = new File(atomicIndexDir, AtomicIndex.DIRECT_TERMS_FILENAME);
      Files.copy(termsFile.toPath(), dest.toPath(), 
          StandardCopyOption.COPY_ATTRIBUTES);
      // create direct Bloom filter and regenerate term map
      File dirTermsFile = new File(headDir, outputFilePrefix + 
          AtomicIndex.DIRECT_INDEX_NAME_SUFFIX + 
          DiskBasedIndex.TERMS_EXTENSION); // guaranteed to exist, as tested already
      File dirTermMapFile = new File(headDir, outputFilePrefix + 
          AtomicIndex.DIRECT_INDEX_NAME_SUFFIX +
          DiskBasedIndex.TERMMAP_EXTENSION); // may not exist but that's OK
      File dirBloomFile = new File(headDir, outputFilePrefix + 
          AtomicIndex.DIRECT_INDEX_NAME_SUFFIX +
          DocumentalCluster.BLOOM_EXTENSION); // guaranteed to exist, as tested already
      if(dirTermMapFile.exists()) {
        if(!dirTermMapFile.renameTo(new File(headDir, outputFilePrefix + 
            AtomicIndex.DIRECT_INDEX_NAME_SUFFIX +
            DiskBasedIndex.TERMMAP_EXTENSION + ".old"))) {
          logger.warn("Unable to back up old direct termmap for " + outputFilePrefix + "/" + headDir.getName());
        }
      }
      AtomicIndex.generateTermMap(dirTermsFile, dirTermMapFile, dirBloomFile);
    }
    
    // move the DB files
    if(mentionsConfig != null) {
      // We know that the DB-H2 S-A-H was used, as we've already tested for that
      File sourceDBDir = new File(indexDirectory, "db");
      File destDBDir = new File(atomicIndexDir, "db");
      if(!destDBDir.mkdirs()) {
        throw new IndexException(
            "Location required by upgraded index could not be created:" + 
            destDBDir.getAbsolutePath());
      }
      for(String annType : mentionsConfig.getAnnotationTypes()) {
        String tableBaseName = annType.replaceAll("[^\\p{Alnum}_]", "_");
        File source = new File(sourceDBDir, tableBaseName + ".h2.db");
        File dest = new File(destDBDir, tableBaseName + ".h2.db");
        if(!source.renameTo(dest)) {
          throw new IndexException("Could not rename " +  
              source.getAbsolutePath() + " to " + dest.getAbsolutePath());
        }
      }
    }
  }
  
  protected static void upgradeFromV7(File indexDirectory) throws IndexException, IOException {
    // just need to regenerate the term maps
    for(File subIndexDirectory : indexDirectory.listFiles((File parent, String name) -> name.matches("(?:token|mention)-\\d+"))) {
      String outputFilePrefix = subIndexDirectory.getName();
      for(File batchDir : subIndexDirectory.listFiles((File parent, String name) -> name.matches("head|tail-\\d+"))) {
        // create termmap and Bloom filter
        File termsFile = new File(batchDir, outputFilePrefix + 
            DiskBasedIndex.TERMS_EXTENSION);
        File termMapFile = new File(batchDir, outputFilePrefix + 
            DiskBasedIndex.TERMMAP_EXTENSION);
        File bloomFile = new File(batchDir, outputFilePrefix + 
            DocumentalCluster.BLOOM_EXTENSION);
        if(!termsFile.exists()) {
          throw new IndexException("No terms file found for " + outputFilePrefix + "/" + batchDir.getName());
        }
        if(termMapFile.exists()) {
          if(!termMapFile.renameTo(new File(batchDir, outputFilePrefix + 
              DiskBasedIndex.TERMMAP_EXTENSION + ".old"))) {
            logger.warn("Unable to back up old termmap for " + outputFilePrefix + "/" + batchDir.getName());
          }
        }
        if(bloomFile.exists()) {
          if(!bloomFile.renameTo(new File(batchDir, outputFilePrefix + 
              DocumentalCluster.BLOOM_EXTENSION + ".old"))) {
            logger.warn("Unable to back up old bloom filter for " + outputFilePrefix + "/" + batchDir.getName());
          }
        }
        AtomicIndex.generateTermMap(termsFile, termMapFile, bloomFile);
        
        // create direct termmap and Bloom filter
        File dirTermsFile = new File(batchDir, outputFilePrefix + 
            AtomicIndex.DIRECT_INDEX_NAME_SUFFIX + 
            DiskBasedIndex.TERMS_EXTENSION);
        if(dirTermsFile.exists()) {
          File dirTermMapFile = new File(batchDir, outputFilePrefix + 
              AtomicIndex.DIRECT_INDEX_NAME_SUFFIX +
              DiskBasedIndex.TERMMAP_EXTENSION);
          File dirBloomFile = new File(batchDir, outputFilePrefix + 
              AtomicIndex.DIRECT_INDEX_NAME_SUFFIX +
              DocumentalCluster.BLOOM_EXTENSION);
          if(dirTermMapFile.exists()) {
            if(!dirTermMapFile.renameTo(new File(batchDir, outputFilePrefix + 
                AtomicIndex.DIRECT_INDEX_NAME_SUFFIX +
                DiskBasedIndex.TERMMAP_EXTENSION + ".old"))) {
              logger.warn("Unable to back up old direct termmap for " + outputFilePrefix + "/" + batchDir.getName());
            }
          }
          if(dirBloomFile.exists()) {
            if(!dirBloomFile.renameTo(new File(batchDir, outputFilePrefix + 
                AtomicIndex.DIRECT_INDEX_NAME_SUFFIX + 
                DocumentalCluster.BLOOM_EXTENSION + ".old"))) {
              logger.warn("Unable to back up old direct bloom filter for " + outputFilePrefix + "/" + batchDir.getName());
            }
          }

          AtomicIndex.generateTermMap(dirTermsFile, dirTermMapFile, dirBloomFile);
        }

      }
    }
  }

}
