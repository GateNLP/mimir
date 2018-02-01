/*
 *  DefaultMentionDescriber.java
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
 *  $Id: DefaultMentionDescriber.java 16108 2012-10-03 14:12:47Z valyt $
 */
package gate.mimir.util;

import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.AbstractSemanticAnnotationHelper.MentionDescriber;

/**
 * Default implementation of a {@link MentionDescriber} that simply lists
 * the annotation type and the descriptive features (see 
 * {@link AbstractSemanticAnnotationHelper#getDescriptiveFeatures()} and their 
 * values. The generated mention descriptions look like &quot;{AnnType feat1 = 
 * val1, feat2 = val2 ...}&quot;  
 */
public class DefaultMentionDescriber implements MentionDescriber {
  /**
   * 
   */
  private static final long serialVersionUID = 4818070659434784582L;


  /* (non-Javadoc)
   * @see gate.mimir.AbstractSemanticAnnotationHelper.MentionDescriber#describeMention(gate.mimir.AbstractSemanticAnnotationHelper, java.lang.String)
   */
  @Override
  public String describeMention(AbstractSemanticAnnotationHelper helper, 
                                String mentionUri, String[] descriptiveFeatureNames, 
                                String[] descriptiveFeatureValues) {
    if(descriptiveFeatureValues == null || 
       descriptiveFeatureNames == null ||
       descriptiveFeatureValues.length < descriptiveFeatureNames.length) {
      return helper.getAnnotationType();
    } else {
      StringBuilder res = new StringBuilder("{");
      res.append(helper.getAnnotationType());
      for(int i = 0; i < descriptiveFeatureNames.length; i++) {
        if(descriptiveFeatureValues[i] != null && 
           descriptiveFeatureValues[i].length() > 0) {
          res.append(i == 0 ? ' ' : ", ");
          res.append(descriptiveFeatureNames[i]).append(" = ");
          res.append(descriptiveFeatureValues[i]);
        }
      }
      res.append('}');
      return res.toString();
    }
  }
}
