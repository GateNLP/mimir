/*
 *  AnnotationQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 4 Mar 2009
 *
 *  $Id: AnnotationQuery.java 17236 2014-01-17 15:31:02Z valyt $
 */
package gate.mimir.search.query;


import gate.mimir.Constraint;
import gate.mimir.ConstraintType;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.terms.AnnotationTermsQuery;
import gate.mimir.search.terms.TermsResultSet;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.search.visitor.DocumentIteratorVisitor;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;


/**
 * A query for the annotations index. 
 */
public class AnnotationQuery implements QueryNode {

  private static final long serialVersionUID = 5996543707885867821L;


  public static class AnnotationQueryExecutor extends AbstractQueryExecutor{

    
    /**
     * @param engine
     * @param query
     */
    public AnnotationQueryExecutor(AnnotationQuery query, QueryEngine engine) throws IOException {
      super(engine, query);
      this.query = query;
      buildQuery();
    }
    
    /**
     * The query being executed.
     */
    private AnnotationQuery query;
    
    /**
     * Logger for this class.
     */
    private static Logger logger = Logger.getLogger(AnnotationQueryExecutor.class);

    /**
     * The underlying OrQuery executor that actually does the work.
     */
    private QueryExecutor underlyingExecutor;
    
    private transient boolean isInDocumentMode;
    
    /**
     * Build the underlying OrQuery executor that this annotation query uses.
     */
    protected void buildQuery() throws IOException {
      SemanticAnnotationHelper helper = engine.getAnnotationHelper(query);
      isInDocumentMode = (helper.getMode() == 
          SemanticAnnotationHelper.Mode.DOCUMENT);
      // get the mention URIs
      TermsResultSet trs = new AnnotationTermsQuery(query).execute(engine);
      if(trs.termStrings != null && trs.termStrings.length > 0 && 
         trs.termLengths != null) {
        QueryNode[] disjuncts = new QueryNode[trs.termStrings.length];
        for(int index = 0; index < trs.termStrings.length; index++) {
          // create a term query for the mention URI
          disjuncts[index] = new TermQuery(query.annotationType, 
              trs.termStrings[index], trs.termLengths[index]);
        }
        QueryNode underlyingQuery = new OrQuery(disjuncts);
        underlyingExecutor = underlyingQuery.getQueryExecutor(engine);        
      } else {
        // no results from the helper => no results from us
        latestDocument = -1;
      }
    }
    
    
    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#close()
     */
    public void close() throws IOException {
      if(closed) return;
      super.close();
      if(underlyingExecutor != null) underlyingExecutor.close();
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#getLatestDocument()
     */
    public long getLatestDocument() {
      if(closed || latestDocument == -1) return -1;
      return underlyingExecutor.getLatestDocument();
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextDocument(int)
     */
    public long nextDocument(long greaterThan) throws IOException {
      if(closed || latestDocument == -1) return -1;
      return latestDocument = underlyingExecutor.nextDocument(greaterThan);
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextHit()
     */
    public Binding nextHit() throws IOException {
      if(closed || latestDocument == -1) return null;
      Binding underlyingHit = underlyingExecutor.nextHit();
      if(underlyingHit == null) return null;
      long doc = underlyingHit.getDocumentId();
      if(isInDocumentMode) {
        return new Binding(query, doc, 0, engine.getIndex().getDocumentSize(doc),
          underlyingHit.getContainedBindings());        
      } else {
        return new Binding(query, doc,
          underlyingHit.getTermPosition(),
          underlyingHit.getLength(),
          underlyingHit.getContainedBindings());        
      }
    }
   
    @Override
    public ReferenceSet<Index> indices() {
      if(underlyingExecutor != null) {
        return underlyingExecutor.indices();
      } else {
        return ReferenceSets.EMPTY_SET;
      }
    }
    
    public <T> T accept( final DocumentIteratorVisitor<T> visitor ) throws IOException {
      if(underlyingExecutor == null) return null;
      if ( ! visitor.visitPre( this ) ) return null;
      final T[] a = visitor.newArray( 1 );
      if ( a == null ) {
        if ( underlyingExecutor.accept( visitor ) == null ) return null;
      }
      else {
        if ( ( a[ 0 ] = underlyingExecutor.accept( visitor ) ) == null ) return null;
      }
      return visitor.visitPost( this, a );
    }

    public <T> T acceptOnTruePaths( final DocumentIteratorVisitor<T> visitor ) throws IOException {
      if(underlyingExecutor == null) return null;
      if ( ! visitor.visitPre( this ) ) return null;
      final T[] a = visitor.newArray( 1 );
      if ( a == null ) {
        if ( underlyingExecutor.acceptOnTruePaths( visitor ) == null ) return null;     
      }
      else {
        if ( ( a[ 0 ] = underlyingExecutor.acceptOnTruePaths( visitor ) ) == null ) return null;
      }
      return visitor.visitPost( this, a );
    }
  }
  
  /**
   * Constructs a new {@link AnnotationQuery}.
   * 
   * Convenience variant of {@link #AnnotationQuery(String, List)} 
   * for cases where all predicates are of type 
   * {@link SemanticAnnotationHelper.ConstraintType#EQ}.
   * 
   * @param annotationType the desired annotation type, for the annotations to 
   * be matched.
   * @param featureConstraints the constraints over the features of the 
   * annotations to be found. This is represented as a {@link Map} from feature
   * name (a {@link String}) to feature value (also a {@link String}).
   * 
   * @see AnnotationQuery#AnnotationQuery(String, List)  
   */
  public AnnotationQuery(String annotationType,
          Map<String, String> featureConstraints) {
    if(featureConstraints == null){
      featureConstraints = new HashMap<String, String>();
    }
    this.annotationType = annotationType;
    this.constraints = new ArrayList<Constraint>(featureConstraints.size());
    for(Map.Entry<String, String> entry : featureConstraints.entrySet()){
      this.constraints.add(new Constraint(ConstraintType.EQ,
              entry.getKey(), entry.getValue()));
    }
  }

  /**
   * Constructs a new Annotation Query.
   *  
   * @param annotationType the type of annotation being sought.
   * @param constraints a list of constraints placed on the feature values. An 
   * empty constraints list will make no requests regarding the feature values,
   * hence it will match all annotations of the right type. 
   */
  public AnnotationQuery(String annotationType, List<Constraint> constraints) {
    this.annotationType = annotationType;
    this.constraints = constraints == null ? new ArrayList<Constraint>() :constraints;
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.query.QueryNode#getQueryExecutor(java.util.Map)
   */
  public QueryExecutor getQueryExecutor(QueryEngine engine)
          throws IOException {
    return new AnnotationQueryExecutor(this, engine);
  }
  
  
  /**
   * Gets the annotation type for this query. 
   * @return the annotationType
   */
  public String getAnnotationType() {
    return annotationType;
  }

  /**
   * Gets the feature constraints, represented as a {@link Map} from 
   * feature name (a {@link String}) to feature value (also a {@link String}). 
   * @return the featureConstraints
   */
  public List<Constraint> getConstraints() {
    return constraints;
  }


  /**
   * The annotation type for this query.
   */
  private String annotationType;
  
  /**
   * The constrains over the annotation features.
   */
  private List<Constraint> constraints;
  
  
  public String toString() {
    return "Annotation ( type = " + 
    annotationType + ", features=" + 
    (constraints != null ? constraints.toString() : "[]") +
    ")";
  }

}
