// minimal default configuration
gate {
  mimir {
    queryTokeniserGapp = "classpath:gate/mimir/query/default-query-tokeniser.xgapp"

    pluginCache {
      main = "WEB-INF/gate/plugin-cache"
    }
  }
}
