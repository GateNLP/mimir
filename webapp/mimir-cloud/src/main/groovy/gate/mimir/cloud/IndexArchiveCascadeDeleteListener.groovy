/*
*  IndexArchiveCascadeDeleteListener.groovy
*
*  Copyright (c) 2011, The University of Sheffield.
*
*  Ian Roberts, 28 Oct 2011
*
*  $Id$
*/
package gate.mimir.cloud

import gate.mimir.web.LocalIndex
import org.slf4j.Logger
import org.hibernate.event.spi.PreDeleteEvent
import org.hibernate.event.spi.PreDeleteEventListener
import org.slf4j.LoggerFactory

/**
 * Hibernate event listener to delete the index archive (if any)
 * belonging to a LocalIndex when the index is deleted. We cannot
 * get the delete to cascade in the normal way because that would
 * require a change to the LocalIndex class (from the mimir-web
 * plugin) to add the hasMany.
 */
public class IndexArchiveCascadeDeleteListener implements PreDeleteEventListener {
  protected final Logger log = LoggerFactory.getLogger(this.getClass())
  
  // we want to inject the indexArchiveService but this introduces
  // a circular reference in the ApplicationContext.  So we inject
  // the grailsApplication and get the service from it later.
  def grailsApplication
  
  public boolean onPreDelete(PreDeleteEvent e) {
    boolean returnVal = false
    // we only care about LocalIndexes
    if(e.entity instanceof LocalIndex) {
      def localIndex = e.entity
      log.debug("LocalIndex {} being deleted, deleting its archives", localIndex.id)
      // have to do the delete in a new session, but we will share the
      // containing database transaction, if any
      IndexArchive.withNewSession {
        try {
          grailsApplication.mainContext.indexArchiveService.deleteIndexArchive(localIndex)
        } catch(Exception ex) {
          log.error("Failed to delete index archive for index ${localIndex.id}", ex)
          // veto the delete
          returnVal = true
        }
      }
    }
    
    return returnVal
  }
}
