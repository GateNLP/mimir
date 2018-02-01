/*
 * DocumentTermQuery.java
 * 
 * Copyright (c) 2007-2011, The University of Sheffield.
 * 
 * This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html),
 * and is free software, licenced under the GNU Lesser General Public License,
 * Version 3, June 2007 (also included with this distribution as file
 * LICENCE-LGPL3.html).
 * 
 * Valentin Tablan, 16 Jul 2012
 * 
 * $Id: DocumentTermsQuery.java 17261 2014-01-30 14:05:14Z valyt $
 */
package gate.mimir.search.terms;

import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.AtomicIndex;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryEngine.IndexType;

import java.io.IOException;

/**
 * A {@link TermsQuery} that returns the terms occurring in a document.
 */
public class DocumentTermsQuery extends AbstractIndexTermsQuery {
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 8020471303382533080L;

  /**
   * Creates a new document term query.
   * 
   * @param documentId
   *          the ID of the document for which the terms are sought
   * @param indexName
   *          the name of the sub-index to be searched. Each Mímir index
   *          includes multiple sub-indexes (some storing tokens, other storing
   *          annotations), identified by a name. For token indexes, the index
   *          name is the name of the token feature being indexed; for
   *          annotation indexes, the index name is the annotation type.
   * @param indexType
   *          The type of index to be searched (tokens or annotations).          
   * @param countsEnabled
   *          should term counts be returned.
   * @param describeAnnotations
   *          If the index being interrogated is of type
   *          {@link IndexType#ANNOTATIONS} then the indexed term strings are
   *          URIs whose format depends on the actual implementation of the
   *          index. These strings make little sense outside of the index. If
   *          this is set to <code>true</code>, then term descriptions are also
   *          included in the results set. See
   *          {@link TermsResultSet#termDescriptions} and
   *          {@link SemanticAnnotationHelper#describeMention(String)}. Setting
   *          this to <code>true</code> has no effect if the index being
   *          interrogated is a {@link IndexType#TOKENS} index.
   */
  public DocumentTermsQuery(String indexName, IndexType indexType,
                            boolean countsEnabled, boolean describeAnnotations,
                            long documentId) {
    super(indexName, indexType, countsEnabled, describeAnnotations, documentId);
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * gate.mimir.search.terms.TermsQuery#execute(gate.mimir.search.QueryEngine)
   */
  @Override
  public TermsResultSet execute(QueryEngine engine) throws IOException {
    prepare(engine);
    return buildResultSet(atomicIndex.getDirectIndex().documents
        (AtomicIndex.longToTerm(documentIds[0])));
  }
}
