/*
 *  AbstractSemanticAnnotationHelper.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 5 Aug 2009
 *
 *  $Id: AbstractSemanticAnnotationHelper.java 17261 2014-01-30 14:05:14Z valyt $
 */
package gate.mimir;

import gate.Document;
import gate.mimir.index.AtomicAnnotationIndex;
import gate.mimir.index.Mention;
import gate.mimir.search.QueryEngine;
import gate.mimir.util.DefaultMentionDescriber;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 * Simple abstract class that provides:
 * <ul>
 *   <li>an empty implementation for {@link #documentStart(Document)}</li>
 *   <li>an empty implementation for {@link #documentEnd()}</li>
 *   <li>an implementation for {@link #getMentions(String, Map, QueryEngine)},
 *   which simply calls {@link #getMentions(String, List, QueryEngine)} after
 *   creating the appropriate constraints.</li>
 * </ul>
 * 
 * Subclasses are required to provide a no-argument constructor. When 
 * implementing the {@link #init(Indexer)} and {@link #init(QueryEngine)} 
 * methods, they must first call super.init(...). 
 * 
 * <p>If a particular helper does not support certain feature types it should
 * use the {@link #concatenateArrays} method to combine those features with
 * another kind that it can support, during the init(Indexer) call.  For example, 
 * helpers that do not have access to a full semantic repository may choose to 
 * treat URI features as if they were simply text features with no semantics.</p>
 */
public abstract class AbstractSemanticAnnotationHelper implements
    SemanticAnnotationHelper {
	
  /**
   * Interface for supporting classes used by 
   * {@link AbstractSemanticAnnotationHelper} and its sub-classes to provide a 
   * pluggable implementation for 
   * {@link SemanticAnnotationHelper#describeMention(String)}. 
   */
  public static interface MentionDescriber extends Serializable {
    public String describeMention(AbstractSemanticAnnotationHelper helper, 
        String mentionUri, String[] descriptiveFeatureNames, 
        String[] descriptiveFeatureValues);
  }
  
	private static final long serialVersionUID = -5432862771431426914L;

  private transient boolean isInited = false;
  
  
  protected final boolean isInited() {
    return isInited;
  }

  /**
   * The list of names for the nominal features.
   */
  protected String[] nominalFeatureNames;

  /**
   * The list of names for the numeric features.
   */
  protected String[] integerFeatureNames;

  /**
   * The list of names for the numeric features.
   */
  protected String[] floatFeatureNames;
  
  /**
   * The list of names for the text features.
   */
  protected String[] textFeatureNames;
	
  /**
   * The list of names for the URI features.
   */
  protected String[] uriFeatureNames;
  
  /**
   * The type of the annotations handled by this helper.
   */
  protected String annotationType;
	
  /**
   * The list of names for all the features that should be used when describing
   * an annotation mention (see {@link #describeMention(String)}).
   */
  protected String[] descriptiveFeatures;
  
  protected MentionDescriber mentionDescriber;
  
  
  /**
   * The working mode for this helper (defaults to {@link Mode#ANNOTATION}).
   */
  protected Mode mode = Mode.ANNOTATION;
  
  public Mode getMode() {
    return mode;
  }

  public void setMode(Mode mode) {
    this.mode = mode;
  }

  public String getAnnotationType() {
    return annotationType;
  }

  public void setAnnotationType(String annotationType) {
    this.annotationType = annotationType;
  }  
  
  public void setAnnType(String annotationType) {
    setAnnotationType(annotationType);
  }

  public String[] getNominalFeatures() {
    return nominalFeatureNames;
  }

  public void setNominalFeatures(String[] nominalFeatureNames) {
    this.nominalFeatureNames = nominalFeatureNames;
  }

  public String[] getIntegerFeatures() {
    return integerFeatureNames;
  }



  public void setIntegerFeatures(String[] integerFeatureNames) {
    this.integerFeatureNames = integerFeatureNames;
  }
  
  public String[] getFloatFeatures() {
    return floatFeatureNames;
  }

  public void setFloatFeatures(String[] floatFeatureNames) {
    this.floatFeatureNames = floatFeatureNames;
  }

  
  public String[] getTextFeatures() {
    return textFeatureNames;
  }

  public void setTextFeatures(String[] textFeatureNames) {
    this.textFeatureNames = textFeatureNames;
  }
  
  /**
   * @return the uriFeatureNames
   */
  public String[] getUriFeatures() {
    return uriFeatureNames;
  }

  public void setUriFeatures(String[] uriFeatureNames) {
    this.uriFeatureNames = uriFeatureNames;
  }
  
  /**
   * Gets the names of features that should be used when describing an
   * annotation mention.
   *  
   * @return the descriptiveFeatures
   */
  protected String[] getDescriptiveFeatures() {
    return descriptiveFeatures;
  }

  /**
   * Sets the names of features that should be used when describing an
   * annotation mention. This should be called <strong>before</strong> the 
   * helper is initialised (i.e. before calling {@link #init(QueryEngine)}).
   * 
   * If no custom value has been set before {@link #init(QueryEngine)} is 
   * called, then all features are used as descriptive features.
   * 
   * @param descriptiveFeatures the descriptiveFeatures to set
   */
  public void setDescriptiveFeatures(String[] descriptiveFeatures) {
    this.descriptiveFeatures = descriptiveFeatures;
  }

  
  
  /**
   * @return the mentionDescriber
   */
  public MentionDescriber getMentionDescriber() {
    return mentionDescriber;
  }

  /**
   * Sets the {@link MentionDescriber} to be used as the implementation of
   * {@link SemanticAnnotationHelper#describeMention(String)}.
   * If set to <code>null</code>, then an instance of 
   * {@link DefaultMentionDescriber} is automatically created and used.
   *  
   * @param mentionDescriber the custom mentionDescriber to use
   */
  public void setMentionDescriber(MentionDescriber mentionDescriber) {
    this.mentionDescriber = mentionDescriber;
  }

  /* (non-Javadoc)
   * @see gate.mimir.SemanticAnnotationHelper#documentEnd()
   */
  public void documentEnd() {}

  public void documentStart(Document document) { }


  /* (non-Javadoc)
   * @see gate.mimir.SemanticAnnotationHelper#getMentions(java.lang.String, java.util.Map, gate.mimir.search.QueryEngine)
   */
  public List<Mention> getMentions(String annotationType,
          Map<String, String> constraints, QueryEngine engine) {
    //convert the simple constraints to actual implementations.
    List<Constraint> predicates = new ArrayList<Constraint>(constraints.size());
    for(Entry<String, String> entry : constraints.entrySet()){
      predicates.add(new Constraint(ConstraintType.EQ, entry.getKey(), entry.getValue()));
    }
    return getMentions(annotationType, predicates, engine);
  }

  
  
  /* (non-Javadoc)
   * @see gate.mimir.SemanticAnnotationHelper#describeMention(java.lang.String)
   */
  @Override
  public String describeMention(String mentionUri) {
    if(mentionDescriber == null) {
      mentionDescriber = new DefaultMentionDescriber();
    }
    return mentionDescriber.describeMention(this, mentionUri, 
      getDescriptiveFeatures(), getDescriptiveFeatureValues(mentionUri));
  }
  
  /**
   * Calculates the textual representations for the values of features that are
   * part of the description of an annotation mention. The list of features for
   * which the values should be returned is {@link #descriptiveFeatures}.
   * 
   * This implementation always returns <code>null</code> as the abstract class
   * has no way of accessing the actual feature values. Subclasses should 
   * provide an actual implementation to support proper mention descriptions. 
   * 
   * @param mentionUri the URI for the mention that needs to be described.
   * 
   * @return an array of strings parallel with {@link #descriptiveFeatures}, or
   * null if the feature values are not known.
   */
  protected String[] getDescriptiveFeatureValues(String mentionUri) {
    return null;
  }
  
  /**
   * Helper method to concatenate a number of arrays into one, for helpers
   * that don't support all the feature types and want to combine some of
   * them together.
   * @return null if all the supplied arrays are either null or empty,
   *        otherwise a single array containing the concatenation of
   *        all the supplied arrays in order.
   */
  protected static String[] concatenateArrays(String[]... arrays) {
    int totalLength = 0;
    for(String[] arr : arrays) {
      if(arr != null) totalLength += arr.length;
    }
    if(totalLength == 0) return null;
    String[] concat = new String[totalLength];
    int start = 0;
    for(String[] arr : arrays) {
      if(arr != null) {
        System.arraycopy(arr, 0, concat, start, arr.length);
        start += arr.length;
      }
    }
    return concat;
  }

  private void checkInit() {
    if(isInited) throw new IllegalStateException(
      "This helper has already been initialised!");
    
    isInited = true;
  }

  
  @Override
  public void init(AtomicAnnotationIndex index) {
    checkInit();
    // calculate the list of descriptive features if needed
    if(descriptiveFeatures == null) {
      List<String> featNames = new ArrayList<String>();
      if(nominalFeatureNames != null){
        Collections.addAll(featNames, nominalFeatureNames);
      }
      if(integerFeatureNames != null) {
        Collections.addAll(featNames, integerFeatureNames);
      }
      if(floatFeatureNames != null) {
        Collections.addAll(featNames, floatFeatureNames);
      }
      if(textFeatureNames != null) {
        Collections.addAll(featNames, textFeatureNames);
      }
      if(uriFeatureNames != null) {
        Collections.addAll(featNames, uriFeatureNames);
      }
      descriptiveFeatures = featNames.toArray(new String[featNames.size()]);
    }
  }
}
