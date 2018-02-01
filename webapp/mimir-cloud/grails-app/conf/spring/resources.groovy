import org.grails.orm.hibernate.HibernateEventListeners
import gate.mimir.cloud.IndexArchiveCascadeDeleteListener;

// Place your Spring DSL code here
beans = {

  // register the Hibernate event listener to delete archives
  // when their corresponding indexes are deleted

  indexArchiveCascadeDeleteListener(IndexArchiveCascadeDeleteListener) {
    grailsApplication = ref('grailsApplication')
  }
  
  hibernateEventListeners(HibernateEventListeners) {
    listenerMap = ['pre-delete':indexArchiveCascadeDeleteListener]
  }
}
