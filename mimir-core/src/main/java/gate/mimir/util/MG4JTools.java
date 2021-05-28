/*
 *  Mg4JTools.java
 *
 *  Copyright (c) 2007-2012, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *  
 *  Valentin Tablan, 12 Jul 2012
 *
 *  $Id: MG4JTools.java 17206 2013-12-24 16:30:52Z valyt $
 */
package gate.mimir.util;

import gate.mimir.index.AtomicIndex;
import gate.mimir.search.QueryEngine;
import it.unimi.di.big.mg4j.index.DiskBasedIndex;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.index.Index.UriKeys;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.util.Properties;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class providing utility methods for working with MG4J indexes. 
 */
public class MG4JTools {

  protected static final Logger logger = LoggerFactory.getLogger(MG4JTools.class);
  
  /**
   * Given a index URI (a file URI denoting the index base name for all the 
   * index files), this method checks if the index if an older version, and 
   * upgrades it to the current version, making sure it can be opened. 
   * @param indexUri
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws ConfigurationException 
   */
  public static void upgradeIndex(URI indexUri) throws IOException, 
      ClassNotFoundException, ConfigurationException {
    // check if the term map is 32 bits, and convert if needed.
    File termMapFile = new File(URI.create(indexUri.toString()
          + DiskBasedIndex.TERMMAP_EXTENSION));
    Object termmap = BinIO.loadObject(termMapFile);
    if(termmap instanceof it.unimi.dsi.util.StringMap) {
      // 32 bit index: save the old termmap
      logger.warn("Old index format detected (32 bits term map file); " +
          "converting to new version. Old files will be backed up with " +
          "a .32bit extension.");
      if(termMapFile.renameTo(new File(URI.create(indexUri.toString()
          + DiskBasedIndex.TERMMAP_EXTENSION + ".32bit")))) {
        // and generate the new one
        File termsFile = new File(URI.create(indexUri.toString()
          + DiskBasedIndex.TERMS_EXTENSION));
        AtomicIndex.generateTermMap(termsFile, termMapFile, null);
      } else {
        throw new IOException("Could not rename old termmap file (" + 
            termMapFile.getAbsolutePath() + ").");
      }
    }
    // check if the .properties file contains any mg4j-standard classes,
    // and replace all mentions with the equivalent mg4j-big ones
    File propsFile = new File(URI.create(indexUri.toString()
      + DiskBasedIndex.PROPERTIES_EXTENSION));
    Properties indexProps = new Properties(propsFile);
    indexProps.setAutoSave(false);
    Iterator<String> keysIter = indexProps.getKeys();
    String OLDPKG = "it.unimi.dsi.mg4j";
    String NEWPKG = "it.unimi.dsi.big.mg4j";
    Map<String, String> newVals = new LinkedHashMap<String, String>();
    while(keysIter.hasNext()) {
      String key = keysIter.next();
      Object value = indexProps.getProperty(key);
      if(value instanceof String && ((String)value).indexOf(OLDPKG) >= 0) {
        newVals.put(key, ((String)value).replace(OLDPKG, NEWPKG));
      }
    }
    if(newVals.size() > 0) {
      // save a backup
      logger.warn("Old index format detected (32 bits properties file); " +
          "converting to new version. Old files will be backed up with " +
          "a .32bit extension.");
      if(propsFile.renameTo(new File(URI.create(indexUri.toString()
        + DiskBasedIndex.PROPERTIES_EXTENSION + ".32bit")))) {
        // update the properties values
        for(Map.Entry<String, String> newEntry : newVals.entrySet()) {
          indexProps.setProperty(newEntry.getKey(), newEntry.getValue());
        }
        // save the changed props
        indexProps.save();
      } else {
        throw new IOException("Could not rename old properties file (" + 
            propsFile.getAbsolutePath() + ").");          
      }
    }
  }

  /**
   * Opens one MG4J index.
   * 
   * @param indexUri a URI denoting the basename for the index (a file path 
   * with the correct basename, but no extension). 
   * 
   * @return the MG4J {@link Index} object.
   * @throws ConfigurationException
   * @throws SecurityException
   * @throws IOException
   * @throws URISyntaxException
   * @throws ClassNotFoundException
   * @throws InstantiationException
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   * @throws NoSuchMethodException
   */
  public static Index openMg4jIndex(URI indexUri) 
      throws ConfigurationException, SecurityException, IOException, 
      URISyntaxException, ClassNotFoundException, InstantiationException, 
      IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Index theIndex = null;
    String basename = indexUri.toString();
    try {
      String options = "?" + UriKeys.MAPPED.name().toLowerCase() + "=1;" + 
          UriKeys.OFFSETSTEP.toString().toLowerCase() + "=-" + 
          DiskBasedIndex.DEFAULT_OFFSET_STEP;
      logger.debug("Opening index: " + basename + options);
      theIndex = Index.getInstance(basename + options, true, true);
    } catch(IOException e) {
      // memory mapping failed
      logger.info("Memory mapping failed for index " + basename
              + ". Loading as file index instead");
      // now try to just open it as an on-disk index
      theIndex = Index.getInstance(basename, true, true);
    }
    return theIndex;
  }
  
  
}
