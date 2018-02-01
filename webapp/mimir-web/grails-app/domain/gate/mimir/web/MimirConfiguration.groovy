package gate.mimir.web

/**
 * Singleton storing configuration parameters for the current Mímir instance.
 */
class MimirConfiguration {

  String indexBaseDirectory
  
  static constraints = {
    indexBaseDirectory( nullable:false, validator:{String path ->
      if(path != null) {
        File indexDir = new File(path)
        if(!indexDir.exists()) return 'notExists'
        if(!indexDir.isDirectory()) return 'notDirectory'
        if(!indexDir.canWrite()) return 'notWriteable'
      }
      return true
    })
  }
}
