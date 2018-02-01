package gate.mimir.web

/*
 *  MimirUrlMappings.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id$
 */
class MimirUrlMappings {
  static mappings = {
    // static view mappings
    "/"(controller:"mimirStaticPages", action:"index")
    "500"(controller:"mimirStaticPages", action:"error")

    // Searching
    //
    // action names that do not start "gus" are mapped to the search
    // controller (the XML search service and the back-end used by
    // RemoteQueryRunner)
    "/$indexId/search/$action?"(controller:"search", parseRequest:true)

    // the top-level "index URL" for a given index *must* be mapped to this
    // action (the plugin assumes this and uses it to generate reverse
    // mappings).
    "/$indexId"(controller:"indexManagement", action:"index")

    // Index management actions (adding documents, closing index, etc.)
    "/$indexId/manage/$action?"(controller:"indexManagement")

    // admin-only actions - CRUD controllers plus index administration
    "/admin"(controller:"mimirStaticPages", action:"admin")
    "/admin/$controller/$action?/$id?"{
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
    "/admin/actions/$indexId/$action?"(controller:"indexAdmin")
  }
}
