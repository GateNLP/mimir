/**
 *  GwtRpcService.java
 *
 *  Copyright (c) 1995-2010, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 01 Dec 2011
 */
package gate.mimir.web.server

import grails.converters.JSON;

import java.util.Set;

import gate.mimir.search.QueryRunner;
import gate.mimir.search.query.Binding;
import gate.mimir.web.Index;
import gate.mimir.web.client.DocumentData;
import gate.mimir.web.client.MimirSearchException
import gate.mimir.web.client.ResultsData;
import gate.mimir.web.client.DocumentData;

class GwtRpcService implements gate.mimir.web.client.GwtRpcService {

  static transactional = false

  /**
   * The SearchService that does the actual work (autowired by Grails)
   */
  def searchService

  // expose for GWT RPC
  static expose = ["gwt:gate.mimir.web.client"]
  
  /**
   * Post a query
   */
  String search(String indexId, String query) {
    Index.withTransaction {
      try {
        def index = Index.findByIndexId(indexId)
        if(!index) {
          throw new MimirSearchException("Invalid index ID ${indexId}")
        }
        else if(index.state != Index.READY) {
          throw new MimirSearchException("Index ${indexId} is not ready")
        }
        else {
          String queryId = searchService.postQuery(index, query)
          return queryId
        }
      } catch(MimirSearchException e) {
        log.warn("Exception starting search", e)
        throw e
      } catch(Exception e) {
        log.warn("Exception starting search", e)
        throw new MimirSearchException("Could not start search. Error was:\n${e.message}");
      }
    }
  }

  /**
   * Release the resources associated with a given query.  Does nothing
   * if the given ID does not correspond to a running query.
   */
  void releaseQuery(String id) {
    searchService.closeQueryRunner(id)
  }

  /**
   * Obtains the types of annotation known to the index, and their features.
   * This method supports auto-completion in the GWT UI.
   */
  String[][] getAnnotationsConfig(String indexId){
    Index.withTransaction {
      Index index = Index.findByIndexId(indexId)
      if(index) {
        return index.annotationsConfig()
      }
      else {
        return ([]as String[][])
      }
    }
  }

  @Override
  public ResultsData getResultsData(String queryId,
      int firstDocumentRank, int documentsCount) throws MimirSearchException {
    QueryRunner qRunner = searchService.getQueryRunner(queryId);
    if(qRunner) {
      try {
        ResultsData rData = new ResultsData(
          resultsTotal:qRunner.getDocumentsCount(),
          resultsPartial: qRunner.getDocumentsCurrentCount())
        if(firstDocumentRank >= 0) {
          // also obtain some documents data
          List<DocumentData> documents = []
          int maxRank = Math.min(firstDocumentRank + documentsCount,
            qRunner.getDocumentsCount());
          for(int docRank = firstDocumentRank; docRank < maxRank; docRank++) {
            DocumentData docData = new DocumentData(
                documentRank:docRank,
                documentTitle:qRunner.getDocumentTitle(docRank),
                documentUri:qRunner.getDocumentURI(docRank))
            // create the snippets
            List<String[]> snippets = new ArrayList<String[]>();
            List<Binding> hits = qRunner.getDocumentHits(docRank).collect{it};
            3.times {
              if(hits) {
                String[] snippet = new String[3];
                Binding aHit = hits.remove(0)
                int termPos = Math.max(0, aHit.termPosition - 3)
                if(termPos < aHit.termPosition) {
                  snippet[0] = qRunner.getDocumentText(docRank, termPos,
                    aHit.termPosition - termPos).toList().transpose().inject('') {
                      acc, val -> acc + val[0] + (val[1] ? ' ' : '')
                  }
                } else {
                  snippet[0] = '';
                }
                snippet[1] = qRunner.getDocumentText(docRank, aHit.termPosition,
                  aHit.length).toList().transpose().inject('') {
                    acc, val -> acc + val[0] + (val[1] ? ' ' : '')
                  }
                snippet[2] = qRunner.getDocumentText(docRank,
                  aHit.termPosition + aHit.length, 3).toList().
                  transpose().inject('') {
                    acc, val -> acc + (val[0]?: '') + (val[1] ? ' ' : '')
                  }
                snippets << snippet
              }
            }
            if(hits) {
              // more than 3 hits: show ellipsis
              snippets.add(["   ", "...", "   "]as String[])
            }
            docData.snippets = snippets
            documents.add(docData)
          }
          if(documents) rData.setDocuments(documents)
        }
        return rData
      } catch (Exception e) {
        // we had a problem accessing the data inside the query runner. We'll 
        // just assume the runner is not valid any more
        log.error("Internal error", e)
        throw new MimirSearchException(MimirSearchException.INTERNAL_SERVER_ERROR,
            "Error extracting data from the query runner - " +
            "your session may have expired.");
      }
    } else {
      throw new MimirSearchException(MimirSearchException.QUERY_ID_NOT_KNOWN,
          "Could not find your query. " +
          "Your search session may have expired, in which case you will need " +
          "to start your search again.")
    }
  }

}
