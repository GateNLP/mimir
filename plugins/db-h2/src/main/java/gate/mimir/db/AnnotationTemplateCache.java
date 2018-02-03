/*
 *  AnnotationTemplateCache.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *  
 *  Valentin Tablan, 11 Feb 2011
 *  
 *  $Id: AnnotationTemplateCache.java 16805 2013-08-20 14:35:16Z ian_roberts $
 */
package gate.mimir.db;

import gate.FeatureMap;
import gate.mimir.AbstractSemanticAnnotationHelper;
import it.unimi.dsi.fastutil.objects.Object2ShortMap;
import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

public class AnnotationTemplateCache {

  /**
   * The key that goes into the level 1 cache map.
   */
  protected class Level1Key {
    public Level1Key(FeatureMap annFeats) {
      // nominalValues is guaranteed to be non-null and to have the same size
      // as owner.getNominalFeatureNames() (i.e. 0, if no nominal features)
      features = new short[nominalvalues.length];
      for(int i = 0; i < nominalvalues.length; i++) {
        Object value = annFeats.get(owner.getNominalFeatures()[i]);
        if(value == null) {
          features[i] = NULL;
        } else {
          features[i] = nominalvalues[i].getShort(value.toString());
          if(features[i] == NULL) {
            // new value -> create an ID for it
            features[i] = (short)nominalvalues[i].size();
            nominalvalues[i].put(value.toString(), features[i]);
          }
        }
      }
      // calculate the hashcode
      hashcode = Arrays.hashCode(features);
    }

    @Override
    public boolean equals(Object obj) {
      return Arrays.equals(features, ((Level1Key)obj).features);
    }

    private short[] features;

    private int hashcode;

    @Override
    public int hashCode() {
      return hashcode;
    }
  }

  /**
   * The type of keys that go into the level2 cache.
   */
  protected class Level2Key {
    public Level2Key(long level1id, FeatureMap annFeats) {
      this.level1id = level1id;
      int length = 0;
      if(owner.getIntegerFeatures() != null)
        length += owner.getIntegerFeatures().length;
      if(owner.getFloatFeatures() != null)
        length += owner.getFloatFeatures().length;
      if(owner.getTextFeatures() != null)
        length += owner.getTextFeatures().length;
      if(owner.getUriFeatures() != null)
        length += owner.getUriFeatures().length;
      values = new Object[length + 1];
      int i = 0;
      if(owner.getIntegerFeatures() != null) {
        for(String aFeature : owner.getIntegerFeatures()) {
          values[i++] = annFeats.get(aFeature);
        }
      }
      if(owner.getFloatFeatures() != null) {
        for(String aFeature : owner.getFloatFeatures()) {
          values[i++] = annFeats.get(aFeature);
        }
      }
      if(owner.getTextFeatures() != null) {
        for(String aFeature : owner.getTextFeatures()) {
          values[i++] = annFeats.get(aFeature);
        }
      }
      if(owner.getUriFeatures() != null) {
        for(String aFeature : owner.getUriFeatures()) {
          values[i++] = annFeats.get(aFeature);
        }
      }
      values[i] = level1id;
      // cache the hash code.
      hashcode = Arrays.hashCode(values);
    }

    private Object[] values;

    long level1id;
    
    @Override
    public int hashCode() {
      return hashcode;
    }

    @Override
    public boolean equals(Object obj) {
      return Arrays.equals(values, ((Level2Key)obj).values);
    }

    int hashcode;
  }

  /**
   * Type for keys going in the level 3 cache
   */
  protected class Level3Key {
    public Level3Key(long level1Id, long level2Id, int mentionLength) {
      this.level1Id = level1Id;
      this.level2Id = level2Id;
      this.mentionLength = mentionLength;
      this.hashcode = Arrays.hashCode(new long[]{level1Id, level2Id, mentionLength});
    }

    @Override
    public int hashCode() {
      return hashcode;
    }

    @Override
    public boolean equals(Object obj) {
      Level3Key other = (Level3Key)obj;
      return other != null && 
             level1Id == other.level1Id && 
             level2Id == other.level2Id && 
             mentionLength == other.mentionLength;
    }

    long level1Id;
    
    long level2Id;

    int mentionLength;

    int hashcode;
  }

  /**
   * Value for a Tag's ID when no ID as been set yet.
   */
  public static final long NO_ID = -1;
  
  /**
   * Value for a Tag's ID when no ID exists (i.e. an alternative 
   * representation for when the Tag value should be null).
   */
  public static final long NULL_ID = -2;
  
  private static final int DEFAULT_L1_SIZE = 512;

  private static final int DEFAULT_L2_SIZE = 10240;

  private static final int DEFAULT_L3_SIZE = 1024 * 1024;

  private static final short NULL = -1;

  public AnnotationTemplateCache(AbstractSemanticAnnotationHelper owner) {
    this.owner = owner;
    this.l1CacheSize = DEFAULT_L1_SIZE;
    this.l2CacheSize = DEFAULT_L2_SIZE;
    this.l3CacheSize = DEFAULT_L3_SIZE;
    int length =
            (owner.getNominalFeatures() == null) ? 0 : owner
                    .getNominalFeatures().length;
    nominalvalues = new Object2ShortMap[length];
    for(int i = 0; i < nominalvalues.length; i++) {
      nominalvalues[i] = new Object2ShortOpenHashMap<String>();
      nominalvalues[i].defaultReturnValue(NULL);
    }
    level1Cache = new LinkedHashMap<Level1Key, Long> (){
      private static final long serialVersionUID = -7450031094311786000L;

      @Override
      protected boolean removeEldestEntry(
              Entry<Level1Key, Long> eldest) {
        return size() > l1CacheSize;
      }
    };
            
    level2Cache = new LinkedHashMap<Level2Key, Long> (){
      private static final long serialVersionUID = -4387458661647300503L;

      @Override
      protected boolean removeEldestEntry(
              Entry<Level2Key, Long> eldest) {
        return size() > l2CacheSize;
      }
    };
    
    level3Cache = new LinkedHashMap<Level3Key, Long>() {
      private static final long serialVersionUID = -1341690439603138038L;

      @Override
      protected boolean removeEldestEntry(Entry<Level3Key, Long> eldest) {
        return size() > l3CacheSize;
      }
    };
    
    l1CacheHits = 0;
    l1CacheMisses = 0;
    l2CacheHits = 0;
    l2CacheMisses = 0;
    l3CacheHits = 0;
    l3CacheMisses = 0;
  }

  protected Map<Level1Key, Long> level1Cache;
  
  protected Map<Level2Key, Long> level2Cache;

  protected Map<Level3Key, Long> level3Cache;

  /**
   * The helper using this cache.
   */
  private AbstractSemanticAnnotationHelper owner;

  /**
   * Each element in this array is a map corresponding to one of the nominal
   * features in our owner. In each map, keys are feature values, values are IDs
   * associated with that value.
   */
  private Object2ShortMap<String> nominalvalues[];

  protected int l1CacheSize;

  protected int l2CacheSize;

  protected int l3CacheSize;

  private long l1CacheHits;

  private long l1CacheMisses;

  private long l2CacheHits;

  private long l2CacheMisses;

  private long l3CacheHits;

  private long l3CacheMisses;
  
  /**
   * Given an annotation, obtain the associated Level-1 ID. If a cache miss 
   * occurs, the provided callable will be called to obtain a new ID, which will
   * then be stored in the cache, and returned.
   * @param annFeats the features of the annotation
   * @param idGenerator callable that returns a new ID if one is required
   * @return the level 1 ID for this set of features, either cached or newly generated
   * @throws Exception if the provided callable generates an exception. 
   */
  public long getLevel1Id(FeatureMap annFeats, Callable<Long> idGenerator) throws Exception {
    // build the nominal features value
    Level1Key l1key = new Level1Key(annFeats);
    Long l1Id = level1Cache.get(l1key);
    if(l1Id == null) {
      l1CacheMisses++;
      l1Id = idGenerator.call();
      level1Cache.put(l1key, l1Id);
    } else {
      l1CacheHits++;
    }
    return l1Id;
  }
  
  /**
   * Given an annotation and the level-1 ID obtained previously, obtain the
   * associated level-2 ID. If a cache miss occurs, the provided callable will
   * be used to generate a new ID, which is then stored in the cache and 
   * returned. 
   * @param level1Tag the level-1 ID
   * @param annFeats the features of the annotation
   * @param idGenerator callable that returns a new ID if one is required
   * @return the level 2 ID for this level 1 ID and set of features, either
   *         cached or newly generated
   * @throws Exception if the provided callable generates an exception. 
   */
  public Long getLevel2Id(Long level1Tag, FeatureMap annFeats, 
                          Callable<Long> idGenerator) throws Exception {
    Level2Key nonNonFeats = new Level2Key(level1Tag, annFeats);
    Long level2Tag = level2Cache.get(nonNonFeats);
    if(level2Tag == null) {
      l2CacheMisses++;
      level2Tag = idGenerator.call();
      level2Cache.put(nonNonFeats, level2Tag);
    } else {
      l2CacheHits++;
    }
    return level2Tag;
  }

  /**
   * Given a Level-1, (optionally) a Level-2 ID, and a mention length, obtain 
   * the Level-3 ID associated with the desired mention. If a cache miss occurs,
   * the provided callable will be used to generate a new ID, which is then 
   * stored in the cache and returned.
   *  
   * @param level1tag the level-1 ID
   * @param level2tag the level-2 ID
   * @param length the mention length in tokens
   * @param idGenerator callable that returns a new ID if one is required
   * @return the level 3 mention ID for this combination of levels 1, 2 and
   *         length.
   * @throws Exception if the provided callable generates an exception. 
   */
  public Long getLevel3Id(Long level1tag, Long level2tag, int length, 
                          Callable<Long> idGenerator) throws Exception {
    Level3Key key = new Level3Key(
        level1tag, (level2tag == null ? NULL_ID : level2tag), length);
    Long l3Tag = level3Cache.get(key);
    if(l3Tag == null) {
      l3CacheMisses++;
      l3Tag = idGenerator.call();
      level3Cache.put(key, l3Tag);
    } else {
      l3CacheHits++;
    }
    return l3Tag;
  }

  /**
   * Returns the current size of the Level1 cache.
   * 
   * @return an int value.
   */
  public int size() {
    return level1Cache.size();
  }

  public long getL1CacheHits() {
    return l1CacheHits;
  }

  public long getL1CacheMisses() {
    return l1CacheMisses;
  }

  public long getL2CacheHits() {
    return l2CacheHits;
  }

  public long getL2CacheMisses() {
    return l2CacheMisses;
  }

  public long getL3CacheHits() {
    return l3CacheHits;
  }

  public long getL3CacheMisses() {
    return l3CacheMisses;
  }

  /**
   * Gets the ratio of level 1 cache hits from all accesses.
   * 
   * @return the proportion of L1 ID requests that could be serviced by the
   *         cache rather than by generating a new ID - the closer this number
   *         is to 1 the "denser" the level 1 feature space.
   */
  public double getL1CacheHitRatio() {
    if(l1CacheHits == 0 && l1CacheMisses == 0) {
      return Double.NaN;
    } else {
      return (double)l1CacheHits / (l1CacheHits + l1CacheMisses);
    }
  }

  /**
   * Gets the ratio of level 2 cache hits from all accesses.
   * 
   * @return the proportion of L2 ID requests that could be serviced by the
   *         cache rather than by generating a new ID - the closer this number
   *         is to 1 the "denser" the level 2 feature space.
   */
  public double getL2CacheHitRatio() {
    if(l2CacheHits == 0 && l2CacheMisses == 0) {
      return Double.NaN;
    } else {
      return (double)l2CacheHits / (l2CacheHits + l2CacheMisses);
    }
  }

  /**
   * Gets the ratio of level 3 (mentions) cache hits from all accesses.
   * 
   * @return the proportion of L3 ID requests that could be serviced by the
   *         cache rather than by generating a new ID.
   */
  public double getL3CacheHitRatio() {
    if(l3CacheHits == 0 && l3CacheMisses == 0) {
      return Double.NaN;
    } else {
      return (double)l3CacheHits / (l3CacheHits + l3CacheMisses);
    }
  }

  /**
   * @return the l1CacheSize
   */
  public int getL1CacheSize() {
    return l1CacheSize;
  }

  /**
   * @param l1CacheSize the l1CacheSize to set
   */
  public void setL1CacheSize(int l1CacheSize) {
    this.l1CacheSize = l1CacheSize > 0 ? l1CacheSize : DEFAULT_L1_SIZE;
  }

  /**
   * @return the l2CacheSize
   */
  public int getL2CacheSize() {
    return l2CacheSize;
  }

  /**
   * @param l2CacheSize the l2CacheSize to set
   */
  public void setL2CacheSize(int l2CacheSize) {
    this.l2CacheSize = l2CacheSize > 0 ? l2CacheSize : DEFAULT_L2_SIZE;
  }

  /**
   * @return the l3CacheSize
   */
  public int getL3CacheSize() {
    return l3CacheSize;
  }

  /**
   * @param l3CacheSize the l3CacheSize to set
   */
  public void setL3CacheSize(int l3CacheSize) {
    this.l3CacheSize = l3CacheSize > 0 ? l3CacheSize : DEFAULT_L3_SIZE;
  }
  
}
