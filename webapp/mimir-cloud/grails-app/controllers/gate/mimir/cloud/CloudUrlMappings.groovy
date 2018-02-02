package gate.mimir.cloud

class UrlMappings {

  static mappings = {
    // all other mappings are handled by the MimirUrlMappings file.
    // Here we only need to take care of the actions we added to the ones 
    // provided by the Mimir plugin
    "/admin/passwords"(controller:"mimirStaticPages", action:"passwords")
    "/admin/savePasswords"(controller:"mimirStaticPages", action:"savePasswords")
    
    // Put indexDownload under /admin so it will be protected by the S2
    // requestmap
    "/admin/indexDownload/$action?/$id?/$fileName?" (controller:'indexDownload', params:[fileName:fileName])
    
    // spring security
    "/login/$action?"(controller: "login")
    "/logout/$action?"(controller: "logout")

    "/_gcn_active.html" (view:'/WEB-INF/grails-app/views/status.jsp')
  }
}
