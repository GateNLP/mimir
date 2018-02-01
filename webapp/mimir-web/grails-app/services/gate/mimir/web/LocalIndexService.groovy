/*
 *  LocalIndexService.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Ian Roberts, 21 Dec 2009
 *  
 *  $Id$
 */
package gate.mimir.web;

import java.io.File;
import java.util.concurrent.Callable;

import gate.mimir.web.Index;
import gate.mimir.web.IndexTemplate;
import gate.mimir.web.LocalIndex;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryRunner;
import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.IndexConfig
import gate.mimir.MimirIndex;
import gate.mimir.index.IndexException;
import gate.mimir.index.DocumentData;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.mimir.search.query.parser.ParseException;
import gate.mimir.search.score.MimirScorer
import gate.mimir.util.*

import grails.transaction.Transactional

/**
 * Service for working with local indexes.
 */
@Transactional
class LocalIndexService {
  
  def grailsApplication 

  /**
   * This service is a singleton.
   */
  static scope = "singleton"

  /**
   * Tokeniser for queries (autowired).
   */
  def queryTokeniser
  
  /**
   * Shared thread pool (autowired)
   */
  def searchThreadPool
    
  private Map<Long, MimirIndex> indexes = [:]

  /**
   * At startup, any indexes that are listed in the DB as being in any state
   * other than ready are now invalid, so need to be marked as failed.
   */
  public void init() {
    LocalIndex.withTransaction {
      LocalIndex.list().each {
        if(it.state != Index.READY) {
          it.state = Index.FAILED
        }
      }
    }
  }

  /**
   * Create a new index on disk from the given template in the directory
   * specified by the given LocalIndex, and store the corresponding Indexer for
   * future use.
   */
  public synchronized MimirIndex createIndex(LocalIndex index, IndexTemplate templ) {
    def indexConfig = GroovyIndexConfigParser.createIndexConfig(
        templ.configuration, new File(index.indexDirectory))
    MimirIndex theIndex = new MimirIndex(indexConfig)
    // set up the query engine
    QueryEngine engine = theIndex.getQueryEngine()
    engine.queryTokeniser = queryTokeniser
    engine.executor = searchThreadPool
    engine.setSubBindingsEnabled(index.subBindingsEnabled?:false)

    indexes[index.id] = theIndex
    return theIndex
  }

  public void close(LocalIndex index) {
    if(index.state == Index.READY) {
      index.state = Index.CLOSING
      index.save()
      def indexId = index.id
      try {
        indexes.remove(indexId)?.close()
        index.state = Index.READY
        index.save()
      }
      catch(IndexException e) {
        log.error("Error while closing index ${indexId}", e)
        index.state = Index.FAILED
        index.save()          
      }
    }
  }
  
  public synchronized QueryRunner getQueryRunner(LocalIndex index, String query) 
      throws ParseException {
    return getIndex(index).getQueryEngine().getQueryRunner(query)
  }
  
  public synchronized DocumentData getDocumentData(LocalIndex index, long documentId) {
    return getIndex(index).getQueryEngine().getIndex().getDocumentData(documentId)
  }
  
  public synchronized void renderDocument(LocalIndex index, long documentId, Appendable out) {
    getIndex(index).getQueryEngine().renderDocument(documentId, [], out)
  }
  
  
  public synchronized void deleteDocuments(LocalIndex index, Collection<Long> documentIds) {
    getIndex(index).getQueryEngine().getIndex().deleteDocuments(documentIds)
  }

  public synchronized void undeleteDocuments(LocalIndex index, Collection<Long> documentIds) {
    getIndex(index).getQueryEngine().getIndex().undeleteDocuments(documentIds)
  }
  
  /**
   * Checks if this local index is using an old on-disk format  
   * @param index the index to test
   * @return
   */
  public boolean isOldVersion(LocalIndex index) {
    File indexDirectory = new File(index.indexDirectory)
    File indexConfigFile = new File(indexDirectory,
      MimirIndex.INDEX_CONFIG_FILENAME);
    IndexConfig indexConfig = IndexConfig.readConfigFromFile(indexConfigFile);
    return indexConfig.getFormatVersion() < IndexConfig.FORMAT_VERSION
  }
  
  
  public void upgradeIndex(LocalIndex index) {
    // unload if it was loaded
    if(index) {
      close(index)
      IndexUpgrader.upgradeIndex(new File(index.indexDirectory))
      // and re-open it
      index.state = Index.READY
      index.save(flush:true)
    }
  }
  
  public synchronized MimirIndex getIndex (LocalIndex index){
    MimirIndex mIndex = indexes[index.id]
    QueryEngine engine = null
    if(mIndex) {
      engine = mIndex.getQueryEngine()
    } else {
      // index not yet open
      try {
        mIndex = new MimirIndex(new File(index.indexDirectory))
        indexes[index.id] = mIndex
        engine = mIndex.getQueryEngine()
        engine.queryTokeniser = queryTokeniser
        engine.executor = searchThreadPool
        engine.setSubBindingsEnabled(index.subBindingsEnabled?:false)
      } catch (Exception e) {
        log.error("Cannot open local index at ${index?.indexDirectory}", e)
        LocalIndex.withTransaction {
          index.state = Index.FAILED
          index.save(flush:true)
        }
        return null
      }
    }
 
    // the scorer may have changed, so we update it every time
    if(index.scorer) {
      engine.setScorerSource(grailsApplication.config.gate.mimir.scorers[index.scorer] as Callable<MimirScorer>)
    } else {
      engine.setScorerSource(null)
    }
    return mIndex    
  }
  
  public String[][] annotationsConfig(LocalIndex index) {
    IndexConfig indexConfig = null
    if(index.state == Index.READY) {
      indexConfig = getIndex(index)?.indexConfig
    }
    if(indexConfig) {
      SemanticIndexerConfig[] semIndexers = indexConfig.getSemanticIndexers();
      List<String[]> rows = new ArrayList<String[]>();
      for(SemanticIndexerConfig semConf : semIndexers){
        String[] types = semConf.getAnnotationTypes();
        SemanticAnnotationHelper[] helpers = semConf.getHelpers();
        for(int i = 0; i < types.length; i++){
          List<String> row = new ArrayList<String>();
          //first add the ann type
          row.add(types[i]);
          //next, add its features, if known
          if(helpers[i] instanceof AbstractSemanticAnnotationHelper){
            AbstractSemanticAnnotationHelper helper = 
            (AbstractSemanticAnnotationHelper)helpers[i];
            for(String feat : helper.getNominalFeatures()){
              row.add(feat);
            }
            for(String feat : helper.getIntegerFeatures()){
              row.add(feat);
            }
            for(String feat : helper.getFloatFeatures()){
              row.add(feat);
            }
            for(String feat : helper.getTextFeatures()){
              row.add(feat);
            }
            for(String feat : helper.getUriFeatures()){
              row.add(feat);
            }
          }
          rows.add(row.toArray(new String[row.size()]));
        }
      }
      return rows.toArray(new String[rows.size()][]);
    }
    else {
      return ([] as String[][])
    }
  }
  
  
  public void deleteIndex(LocalIndex index, deleteFiles) throws IOException {
    String indexDirectory = index.indexDirectory
    // stop the index
    try{
      indexes.remove(index.id)?.close()
    } catch(Exception e) {
      log.warn("Exception while trying to close index, prior to deletion", e)
    }
    // delete the index fromDB
    try {
      log.warn("Deleting index from DB!")
      index.attach()
      index.delete(flush:true)
    }
    catch(Exception e) {
      throw new IOException("Index deletion failed (${e.message})")
    }
    if(deleteFiles) {
      log.warn("Deleting files!")
      if(!(new File(indexDirectory).deleteDir())) {
        throw new IOException("Index deleted, but could not delete index files at ${indexDirectory}")
      }
    }
  } 
  
  @PreDestroy
  public void destroy() {
    // close the local indexes in a civilised fashion
    // (for indexes that are not in READY mode, there is no civilised way!)
    log.info("Closing all open indexes")
    indexes.each{ id, MimirIndex mIndex ->
      try {
        mIndex.close()
      } catch (Throwable t) {
        log.error("Error while closing index at ${mIndex.getIndexDirectory()}", t)
      }
    }
  }
}
