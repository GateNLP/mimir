/*
 *  PatternMentionDescriber.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 06 Feb 2014
 *
 *  $Id: PatternMentionDescriber.java 20279 2017-12-04 22:58:58Z ian_roberts $
 */
package gate.mimir.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.AbstractSemanticAnnotationHelper.MentionDescriber;


/**
 * A {@link MentionDescriber} that uses a user-given pattern to describe 
 * annotation mentions. 
 */
public class PatternMentionDescriber implements MentionDescriber{

  private static final long serialVersionUID = -3310472212294302781L;

  /**
   * Regex used to find feature names in the pattern.
   */
  protected static final Pattern FEATURE_FINDER = Pattern.compile("\\$\\{(.+?)\\}");
  
  /**
   * The pattern used to generate mention descriptions.
   */
  protected String pattern;
  
  /**
   * The set of feature names that actually occur in the pattern.
   */
  protected Set<String> featureNames;

  
  /**
   * Construct a new pattern-based mention describer. When using this 
   * constructor make sure to alter set the pattern by calling 
   * {@link #setPattern(String)}.
   */
  public PatternMentionDescriber() {
    featureNames = new HashSet<String>();
  }
  
  
  /**
   * Construct a new pattern-based mention describer.
   * 
   * @param pattern the pattern used to generate mentions. To describe an 
   * annotation mention, all occurrences of &quot;${name}&quot; in the pattern
   * are replaced with the values of the <code>name</code> feature for the given 
   * annotation.
   */
  public PatternMentionDescriber(String pattern) {
    this();
    setPattern(pattern);
  }


  public String getPattern() {
    return pattern;
  }

  /**
   * Sets the pattern to be used when describing annotation mentions.
   * 
   * @param pattern the pattern used to generate mentions. To describe an 
   * annotation mention, all occurrences of &quot;${name}&quot; in the pattern
   * are replaced with the values of the <code>name</code> feature for the given 
   * annotation.   */
  public void setPattern(String pattern) {
    this.pattern = pattern;
    int pos = 0;
    Matcher m = FEATURE_FINDER.matcher(pattern);
    while(m.find(pos)) {
      featureNames.add(m.group(1));
      pos = m.end();
    }
  }

  @Override
  public String describeMention(AbstractSemanticAnnotationHelper helper,
      String mentionUri, String[] descriptiveFeatureNames,
      String[] descriptiveFeatureValues) {
    String res = pattern;
    for(int i = 0; i < descriptiveFeatureNames.length; i++) {
      if(featureNames.contains(descriptiveFeatureNames[i])) {
        res = res.replace("${" + descriptiveFeatureNames[i] + "}", 
            (descriptiveFeatureValues[i] != null ? 
             descriptiveFeatureValues[i] : ""));
      }
    }
    if(res.length() == 0) {
      res = "{" + helper.getAnnotationType() + "}";
    }
    return res;
  }
  
}
