package ${packageName}

class MimirUrlMappings {
  static mappings = {
    // static view mappings
    "/"(controller:"mimirStaticPages", action:"index")
    "500"(controller:"mimirStaticPages", action:"error")

    // This mapping for the indexManagement controller's "index" action
    // is considered to be the top-level "index URL" for each index.
    // It *must* be mapped to a url path that includes \$indexId
    // and the search and manage actions defined below must be at the
    // right locations relative to this, as this structure is assumed
    // by the mimir-connector and mimir-client packages.
    "/\$indexId"(controller:"indexManagement", action:"index")

    // Searching
    //
    // This *must* be mapped to /search/\$action? underneath the base
    // index URL mapping above.
    "/\$indexId/search/\$action?"(controller:"search", parseRequest:true)

    // Index management actions (adding documents, closing index, etc.).
    //
    // This *must* be mapped to /manage/\$action? underneath the base
    // index URL mapping above.
    "/\$indexId/manage/\$action?"(controller:"indexManagement")

    // admin-only actions - CRUD controllers plus index administration.
    //
    // By default these are mapped under a common URL prefix of /admin
    // to make it easy to secure them all in one go with an authentication
    // plugin or configuration in the front-end Apache/nginx/etc.
    "/admin"(controller:"mimirStaticPages", action:"admin")
    "/admin/\$controller/\$action?/\$id?"{
      constraints {
        controller(inList:[
          "federatedIndex",
          "localIndex",
          "remoteIndex",
          "indexTemplate",
          "mimirConfiguration"
        ])
      }
    }
    "/admin/actions/\$indexId/\$action?"(controller:"indexAdmin")
  }
}
