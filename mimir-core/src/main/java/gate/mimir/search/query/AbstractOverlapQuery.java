/*
 *  AbstractOverlapQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 21 Apr 2009
 *
 *  $Id: AbstractOverlapQuery.java 20208 2017-04-19 08:35:28Z domrout $
 */
package gate.mimir.search.query;


import gate.mimir.search.QueryEngine;

import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.search.visitor.DocumentIteratorVisitor;

import java.io.IOException;
import java.util.*;


/**
 * Abstract class providing the shared functionality used by both 
 * {@link WithinQuery} and {@link ContainsQuery}.
 */
public abstract class AbstractOverlapQuery implements QueryNode{

  private static final long serialVersionUID = -6787211242951582971L;

  /**
   * @param innerQuery
   * @param outerQuery
   */
  public AbstractOverlapQuery(QueryNode innerQuery, QueryNode outerQuery) {
    this.innerQuery = innerQuery;
    this.outerQuery = outerQuery;
  }
  
  public static class OverlapQueryExecutor extends AbstractQueryExecutor{
    
    /**
     * Which of the sub-queries is the target?
     */
    protected SubQuery targetQuery;
    
    /**
     * @param engine
     * @param query
     * @throws IOException 
     */
    public OverlapQueryExecutor(AbstractOverlapQuery query, QueryEngine engine, 
                                SubQuery target) throws IOException {
      super(engine, query);
      this.targetQuery = target;
      
      innerExecutor = query.innerQuery.getQueryExecutor(engine);
      outerExecutor = query.outerQuery.getQueryExecutor(engine);
      
      hitsOnCurrentDocument = new ArrayList<Binding>();
    }

    protected QueryExecutor innerExecutor;
    
    protected QueryExecutor outerExecutor;


    
    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#close()
     */
    public void close() throws IOException {
      if(closed) return;
      super.close();
      innerExecutor.close();
      outerExecutor.close();
    }


    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextDocument(int)
     */
    public long nextDocument(long greaterThan) throws IOException {
      if(closed) return latestDocument = -1;
      if(latestDocument == -1) return latestDocument;
      hitsOnCurrentDocument.clear();
      while(hitsOnCurrentDocument.isEmpty() && latestDocument != -1){
        //find a document on which both sub-executors agree
        long outerNext = outerExecutor.nextDocument(greaterThan);
        long innerNext = innerExecutor.nextDocument(greaterThan);
        while(outerNext != innerNext){
          if(outerNext == -1 || innerNext == -1){
            //one executor has run out -> we're done!
            return  latestDocument = -1;
          }
          //advance the smallest one 
          while(outerNext < innerNext){
            outerNext = outerExecutor.nextDocument(innerNext - 1);
            if(outerNext == -1) return -1;
          }
          while(innerNext < outerNext){
            innerNext = innerExecutor.nextDocument(outerNext -1);
            if(innerNext == -1) return -1;
          }
        }
        //at this point, the next docs are the same
        latestDocument = outerNext;
        //now check that there are actual hits on the current doc
        if(latestDocument != -1){
          getHitsOnCurrentDocument();
        }
      }
      return latestDocument;
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextHit()
     */
    public Binding nextHit() throws IOException {
      if(closed) return null;
      return hitsOnCurrentDocument.isEmpty() ?  null : hitsOnCurrentDocument.remove(0);
    }
  
    protected List<Binding> hitsOnCurrentDocument;
    
    protected void getHitsOnCurrentDocument() throws IOException{
      hitsOnCurrentDocument.clear();
      //get the hits from each of the sub-executors
      List<Binding> outerHits = new LinkedList<Binding>();
      Binding aHit = outerExecutor.nextHit();
      while(aHit != null){
        outerHits.add(aHit);
        aHit = outerExecutor.nextHit();
      }
      List<Binding> innerHits = new LinkedList<Binding>();
      aHit = innerExecutor.nextHit();
      while(aHit != null){
        innerHits.add(aHit);
        aHit = innerExecutor.nextHit();
      }
      //now find the overlaps
      outer:for(Binding outerHit : outerHits){
        Iterator<Binding> innerIter = innerHits.iterator();
        while(innerIter.hasNext()){
          Binding innerHit = innerIter.next();
          if(innerHit.getTermPosition() < outerHit.getTermPosition()){
            //inner not useful any more
            innerIter.remove();
          }else if((innerHit.getTermPosition() + innerHit.getLength()) <= 
                   (outerHit.getTermPosition() + outerHit.getLength())){
            //good hit:
            // inner.start not smaller than outer.start && 
            // inner.end smaller than outer.end
            switch(targetQuery){
              case INNER:
                hitsOnCurrentDocument.add(buildBinding(innerHit, outerHit));
                //hit returned, cannot be used any more
                innerIter.remove();
                break;
              case OUTER:
                hitsOnCurrentDocument.add(buildBinding(outerHit,innerHit));
                //hit returned, move to next one
                continue outer;
            }
          }else if(innerHit.getTermPosition() > 
                   (outerHit.getTermPosition() + outerHit.getLength())){
            //all remaining inners are later than current outer
            //move to next outer
            continue outer;
          }else{
            //current inner starts inside current outer, but ends outside
            //it may still be useful for other outers
            //we just ignore it, and move to the next one
          }
        }
      }
    }
    
    /**
     * Builds the Binding instance we will return, including populating the
     * sub-bindings if enabled at the index level
     *
     * @param main
     *          the main Binding that is the actual hit of the query
     * @param filter
     *          the Binding used to filter the query hits
     * @return the main Binding with it's sub-bindings filled in as required
     */
    private Binding buildBinding(Binding main, Binding filter) {
      // if we don't want sub-bindings then just return the main binding
      if(!engine.isSubBindingsEnabled()) return main;

      Binding[] bindings;
      int bindingsCount = 1;

      // count the number of sub bindings we need to copy from both the main and
      // filter binding instances
      if(main.getContainedBindings() != null)
        bindingsCount += main.getContainedBindings().length;
      if(filter.getContainedBindings() != null)
        bindingsCount += filter.getContainedBindings().length;

      // create the array to hold all the sub-bindings
      bindings = new Binding[bindingsCount];

      // copy in any sub-bindings from the main query binding
      if(main.getContainedBindings() != null) {
        System.arraycopy(main.getContainedBindings(), 0, bindings, 0,
            main.getContainedBindings().length);
      }

      // copy in any sub-bindings from the filter query binding
      if(filter.getContainedBindings() != null) {
        System.arraycopy(filter.getContainedBindings(), 0, bindings,
            bindingsCount - filter.getContainedBindings().length,
            filter.getContainedBindings().length);
      }

      // now we've used the sub-bindings from the filter remove them
      filter.setContainedBindings(null);

      // we want to store the filter binding itself as well
      if(main.getContainedBindings() != null) {
        bindings[main.getContainedBindings().length] = filter;
      } else {
        bindings[0] = filter;
      }

      // set the contained bindings on the main query binding
      main.setContainedBindings(bindings);

      // return the binding
      return main;
    }

    public <T> T accept( final DocumentIteratorVisitor<T> visitor ) throws IOException {
      if ( ! visitor.visitPre( this ) ) return null;
      final T[] a = visitor.newArray( 2 );
      if ( a == null ) {
        if ( innerExecutor.accept( visitor ) == null ) return null;
        if ( outerExecutor.accept( visitor ) == null ) return null;
      }
      else {
        if ( ( a[ 0 ] = innerExecutor.accept( visitor ) ) == null ) return null;
        if ( ( a[ 1 ] = outerExecutor.accept( visitor ) ) == null ) return null;
      }
      return visitor.visitPost( this, a );
    }

    public <T> T acceptOnTruePaths( final DocumentIteratorVisitor<T> visitor ) throws IOException {
      if ( ! visitor.visitPre( this ) ) return null;
      final T[] a = visitor.newArray( 1 );
      QueryExecutor executor = targetQuery == SubQuery.INNER ?
          innerExecutor : outerExecutor;
      if ( a == null ) {
        if ( executor.acceptOnTruePaths( visitor ) == null ) return null;
      }
      else {
        if ( ( a[ 0 ] = executor.acceptOnTruePaths( visitor ) ) == null ) return null;
      }
      return visitor.visitPost( this, a );
    }
    
    
    @Override
    public ReferenceSet<Index> indices() {
      if(indices == null) {
        // we keep all indexes (even though only the ones on one branch will be 
        // needed) so that the visiting doesn't need to care about the orientation. 
        indices = new ReferenceArraySet<Index>();
        indices.addAll(innerExecutor.indices());
        indices.addAll(outerExecutor.indices());
      }
      return indices;
    }
    
    protected ReferenceSet<Index> indices;
  }

  /**
   * A simple enum used to identify the two sub-queries.
   */
  protected enum SubQuery{
    INNER, OUTER
  }

  /**
   * The query providing the inner intervals.
   */
  protected QueryNode innerQuery;
  
  /**
   * The query providing the outer intervals.
   */  
  protected QueryNode outerQuery;

  public QueryNode getInnerQuery() {
    return innerQuery;
  }

  public QueryNode getOuterQuery() {
    return outerQuery;
  }
}
