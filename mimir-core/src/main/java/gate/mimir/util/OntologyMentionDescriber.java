/*
 *  OntologyMentionDescriber.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 2 Oct 2012
 *
 *  $Id: OntologyMentionDescriber.java 16108 2012-10-03 14:12:47Z valyt $
 */
package gate.mimir.util;

import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.AbstractSemanticAnnotationHelper.MentionDescriber;

/**
 * A {@link MentionDescriber} for annotations that represent ontology entities.
 * The generated description for a given entity looks like 
 * &quot;Class (Instance)&quot;, e.g. &quot;City (London)&quot;.
 * 
 * For this describer to work, the array of descriptive features (see 
 * {@link AbstractSemanticAnnotationHelper#getDescriptiveFeatures()}) must start
 * with the name of the <code>class</code> feature, followed optionally by the 
 * name of the <code>feature</code> feature. All subsequent feature names are
 * ignored.
 */
public class OntologyMentionDescriber implements MentionDescriber {

  protected String nameSpaceSeparator = "#";
  
  protected boolean localNamesOnly = true;
  
  /**
   * Gets the string used to split the ontology URIs into name space and local 
   * name. Defaults to &quot;#&quot;.
   *  
   * @return the nameSpaceSeparator
   */
  public String getNameSpaceSeparator() {
    return nameSpaceSeparator;
  }

  /**
   * When this describer is set to use {@link #localNamesOnly} (<code>true<code>
   * by default), the name space separator is used to split ontology URIs into 
   * name space and local name.
   * 
   * Call this method to change the separator string used if the default 
   * separator (&quot;#&quot;) is not suitable.
   *  
   * @param nameSpaceSeparator the new separator to use.
   */
  public void setNameSpaceSeparator(String nameSpaceSeparator) {
    this.nameSpaceSeparator = nameSpaceSeparator;
  }

  /**
   * Is this describer set to use local (short) names only?
   * @return <code>true</code> if local names should be used instead of full 
   * URIs.
   */
  public boolean isLocalNamesOnly() {
    return localNamesOnly;
  }

  /**
   * Set this to <code>true</code> to use local (short) names in the 
   * description, or <code>false</code> to use full URIs. Defaults to 
   * <code>true</code>.
   * 
   * @param the new value.
   */
  public void setLocalNamesOnly(boolean localNamesOnly) {
    this.localNamesOnly = localNamesOnly;
  }

  /**
   * 
   */
  private static final long serialVersionUID = 7995628810321612593L;

  /* (non-Javadoc)
   * @see gate.mimir.AbstractSemanticAnnotationHelper.MentionDescriber#describeMention(gate.mimir.AbstractSemanticAnnotationHelper, java.lang.String, java.lang.String[], java.lang.String[])
   */
  @Override
  public String describeMention(AbstractSemanticAnnotationHelper helper,
                                String mentionUri,
                                String[] descriptiveFeatureNames,
                                String[] descriptiveFeatureValues) {
    if(descriptiveFeatureValues == null || 
        descriptiveFeatureNames == null ||
        descriptiveFeatureValues.length < descriptiveFeatureNames.length ||
        descriptiveFeatureNames.length == 0 ||
        descriptiveFeatureValues[0] == null) {
      return helper.getAnnotationType();
    } else {
      StringBuilder res = new StringBuilder(
        getName(descriptiveFeatureValues[0]));
      if(descriptiveFeatureValues.length > 1 && 
         descriptiveFeatureValues[1] != null) {
        res.append(" (");
        res.append(getName(descriptiveFeatureValues[1]));
        res.append(')');
      }
      return res.toString();
    }
  }
  
  /**
   * Calculates the class/instance name according to the settings of this 
   * describer.
   * @param uri
   * @return
   */
  protected String getName(String uri) {
    if(localNamesOnly) {
      // we need to shorten the full name
      int pos = uri.indexOf(nameSpaceSeparator);
      return pos < 0 ? uri : uri.substring(pos + nameSpaceSeparator.length());
    } else {
      return uri;
    }
  }
}