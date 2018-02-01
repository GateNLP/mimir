/*
 *  DelegatingSemanticAnnotationHelper.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Ian Roberts, 28 Mar 2011
 *  
 *  $Id: DelegatingSemanticAnnotationHelper.java 18571 2015-02-12 18:18:17Z ian_roberts $
 */
package gate.mimir.util;

import java.util.List;
import java.util.Map;

import gate.Annotation;
import gate.Document;
import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.Constraint;
import gate.mimir.MimirIndex;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.AtomicAnnotationIndex;
import gate.mimir.index.Mention;
import gate.mimir.search.QueryEngine;

/**
 * <p>{@link SemanticAnnotationHelper} that simply delegates all method calls to
 * another helper object. Use this as a base class for helpers that provide
 * search enhancements (converting higher-level search expressions into
 * appropriate low-level constraints) on top of any generic helper.</p>
 * 
 * <p>The default implementation of the "business" methods init/close,
 * documentStart/End and getMentions/getMentionUris is to simply call
 * the delegate's equivalent method.  
 * 
 * The logic for the "info" methods 
 * getNominal/Integer/Float/Text/UriFeatureNames is:
 * <ul>
 *   <li>if the required value is known locally (e.g. an explicit value was set 
 *   by a setter), then simply return it;</li>
 *   <li>if no explicit value is set, and if the delegate helper is an instance 
 *   of {@link AbstractSemanticAnnotationHelper} (which can supply appropriate 
 *   information), then the value is obtained from the delegate, stored 
 *   locally, and returned. Future calls to the same method will use the locally
 *   stored value.</li>
 *   <li>if no local value exists, and the delegate cannot supply the 
 *   information, then <code>null</code> is returned.</li>
 * </ul> 
 * This allows sub-classes of this class to override the appropriate getters so 
 * that they change the set of supported features. This can be useful in order 
 * to claim support for additional features not provided by the delegate, and/or
 * to hide features that the delegate supports if, at search time the helper 
 * will be faking these features in terms of other features.
 * 
 * <p><b>Note</b> this class does <b>not</b> override the convenience method
 * {@link AbstractSemanticAnnotationHelper#getMentions(String, Map, QueryEngine)},
 * so this method is implemented as a call to
 * <code>this.getMentions(String, List&lt;Constraint&gt;, QueryEngine)</code>, 
 * not to <code>delegate.getMentions(String, Map, QueryEngine)</code>.
 */
public abstract class DelegatingSemanticAnnotationHelper extends
                                                        AbstractSemanticAnnotationHelper {
  private static final long serialVersionUID = 458089145672457600L;

  /**
   * Map key for the Groovy-friendly constructor in subclasses.
   */
  public static final String DELEGATE_KEY = "delegate";

  protected SemanticAnnotationHelper delegate;

  public SemanticAnnotationHelper getDelegate() {
    return delegate;
  }

  public void setDelegate(SemanticAnnotationHelper delegate) {
    this.delegate = delegate;
    this.mode = delegate.getMode();
  }

  
  
  /* (non-Javadoc)
   * @see gate.mimir.AbstractSemanticAnnotationHelper#getAnnotationType()
   */
  @Override
  public String getAnnotationType() {
    if(annotationType == null) {
      // not explicitly set -> calculate it
      if(delegate instanceof AbstractSemanticAnnotationHelper) {
        annotationType = 
            ((AbstractSemanticAnnotationHelper)delegate).getAnnotationType();
      }
    }
    return annotationType;
  }
  

  /* (non-Javadoc)
   * @see gate.mimir.AbstractSemanticAnnotationHelper#getNominalFeatures()
   */
  @Override
  public String[] getNominalFeatures() {
    if(nominalFeatureNames == null) {
      // not explicitly set -> calculate the value
      if(delegate instanceof AbstractSemanticAnnotationHelper) {
        nominalFeatureNames = 
            ((AbstractSemanticAnnotationHelper)delegate).getNominalFeatures();
      }
    }
    return nominalFeatureNames;
  }

  /* (non-Javadoc)
   * @see gate.mimir.AbstractSemanticAnnotationHelper#getIntegerFeatures()
   */
  @Override
  public String[] getIntegerFeatures() {
    if(integerFeatureNames == null) {
      // not explicitly set -> calculate the value
      if(delegate instanceof AbstractSemanticAnnotationHelper) {
        integerFeatureNames = 
            ((AbstractSemanticAnnotationHelper)delegate).getIntegerFeatures();
      }
    }
    return integerFeatureNames;
  }

  /* (non-Javadoc)
   * @see gate.mimir.AbstractSemanticAnnotationHelper#getFloatFeatures()
   */
  @Override
  public String[] getFloatFeatures() {
    if(floatFeatureNames == null) {
      // not explicitly set -> calculate the value
      if(delegate instanceof AbstractSemanticAnnotationHelper) {
        floatFeatureNames = 
            ((AbstractSemanticAnnotationHelper)delegate).getFloatFeatures();
      }
    }
    return floatFeatureNames;  
  }

  /* (non-Javadoc)
   * @see gate.mimir.AbstractSemanticAnnotationHelper#getTextFeatures()
   */
  @Override
  public String[] getTextFeatures() {
    if(textFeatureNames == null) {
      // not explicitly set -> calculate the value
      if(delegate instanceof AbstractSemanticAnnotationHelper) {
        textFeatureNames = 
            ((AbstractSemanticAnnotationHelper)delegate).getTextFeatures();
      }
    }
    return textFeatureNames;
  }

  /* (non-Javadoc)
   * @see gate.mimir.AbstractSemanticAnnotationHelper#getUriFeatures()
   */
  @Override
  public String[] getUriFeatures() {
    if(uriFeatureNames == null) {
      // not explicitly set -> calculate the value
      if(delegate instanceof AbstractSemanticAnnotationHelper) {
        uriFeatureNames = 
            ((AbstractSemanticAnnotationHelper)delegate).getUriFeatures();
      }
    }
    return uriFeatureNames;
  }

  
  /**
   * Always return the delegate's mode, as it makes no sense for a delegating
   * helper to operate in a different mode from its underlying delegate.
   */
  @Override
  public Mode getMode() {
    return delegate.getMode();
  }

  @Override
  public void init(AtomicAnnotationIndex index) {
    super.init(index);
    delegate.init(index);
  }

  @Override
  public void documentStart(Document document) {
    delegate.documentStart(document);
  }

  @Override
  public String[] getMentionUris(Annotation annotation, int length,
      AtomicAnnotationIndex index) {
    return delegate.getMentionUris(annotation, length, index);
  }

  @Override
  public List<Mention> getMentions(String annotationType,
          List<Constraint> constraints, QueryEngine engine) {
    return delegate.getMentions(annotationType, constraints, engine);
  }

  
  /* (non-Javadoc)
   * @see gate.mimir.AbstractSemanticAnnotationHelper#describeMention(java.lang.String)
   */
  @Override
  public String describeMention(String mentionUri) {
    return delegate.describeMention(mentionUri);
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.SemanticAnnotationHelper#isMentionUri(java.lang.String)
   */
  @Override
  public boolean isMentionUri(String mentionUri) {
    return delegate.isMentionUri(mentionUri);
  }

  @Override
  public void documentEnd() {
    delegate.documentEnd();
  }


  @Override
  public void close(AtomicAnnotationIndex index) {
    delegate.close(index);
  }

  @Override
  public void close(QueryEngine qEngine) {
    delegate.close(qEngine);
  }
  
}
