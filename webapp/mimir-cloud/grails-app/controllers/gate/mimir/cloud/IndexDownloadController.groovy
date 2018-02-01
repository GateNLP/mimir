/*
 *  IndexDownloadController.groovy
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
package gate.mimir.cloud

import javax.servlet.http.HttpServletResponse;

import gate.mimir.util.IndexArchiveState;
import gate.mimir.web.LocalIndex;

class IndexDownloadController {

  static defaultAction = 'download'
  
  def indexArchiveService
  
  def downloadNew() {
    LocalIndex theIndex = LocalIndex.get(params.id)
    if(theIndex) {
      // we were asked to create a new archive
      indexArchiveService.deleteIndexArchive(theIndex)
      redirect (action:download, id:params.id)
      return
    } else {
      flash.message = "No such index"
      redirect (uri:'/')
      return
    }
  }
  
  def download() {
    LocalIndex theIndex = LocalIndex.get(params.id)
    if(theIndex) {
      IndexArchive indexArchive = indexArchiveService.getIndexArchive(theIndex)
      def files = []
      if(indexArchive.state == IndexArchiveState.AVAILABLE) {
        // enumerate the file names
        files = new File(indexArchive.localDownloadDir).listFiles()
            .sort{it.name}.collect { [name:it.name, size: fileSize(it.length())] }
      }
      return [indexArchive:indexArchive, files:files]
    } else {
      flash.message = "No such index"
      redirect (uri:'/')
      return
    }
  }
  
  /**
   * Produces plain text content, with a list of URLs 
   */
  def urlList() {
    LocalIndex theIndex = LocalIndex.get(params.id)
    if(theIndex) {
      IndexArchive indexArchive = indexArchiveService.getIndexArchive(theIndex)
      def files = []
      if(indexArchive.state == IndexArchiveState.AVAILABLE) {
        // enumerate the file names
        response.contentType = "text/plain"
        response.characterEncoding = 'UTF-8'
        response.outputStream.withWriter('UTF-8') {Writer writer ->
          new File(indexArchive.localDownloadDir).listFiles().sort{it.name}
              .each{
            writer << g.createLink(absolute:'true', action:'getFile', 
                id:indexArchive?.id, params:[fileName:it.name])
            writer << '\n'
          }
        }
        return
      } else {
        flash.message = "Index archive not ready"
        redirect (action:'download')
      }
    } else {
      flash.message = "No such index"
      redirect (uri:'/')
      return
    }
  }
  
  def getFile() {
    IndexArchive indexArchive = IndexArchive.get(params.id)
    if(indexArchive) {
      String fileName = params.fileName
      if(fileName) {
        // serve the file
        File indexFile = new File (new File(indexArchive.localDownloadDir), 
            fileName)
        if(!indexFile.exists()) {
          response.outputStream << "Cannot read requested file"
          response.sendError HttpServletResponse.SC_NOT_FOUND
          return
        }
        if(!indexFile.canRead()) {
          response.outputStream << "Cannot read requested file"
          response.sendError HttpServletResponse.SC_FORBIDDEN
          return
        }
        if(indexFile.name.endsWith('.txt')) {
          response.contentType = "text/plain"
          response.characterEncoding = 'UTF-8'
        } else {
          response.contentType = "application/binary"
          // this forces download instead of open in browser
          response.setHeader("Content-Disposition",
              "attachment; filename=${indexFile.name}");
        }
        response.contentLength = indexFile.length()
         
        InputStream is = indexFile.newInputStream()
        try {
          response.outputStream << is
        } finally {
          response.outputStream.flush();
          is?.close();
          return
        }
      } else {
        flash.message = "No file name provided"
        redirect (action:download, params:[id:indexArchive.theIndex.id])
        return
      }
    } else {
      flash.message = "No such index"
      redirect (uri:'/')
      return
    }
  }
  
  /**
   * Returns a human friendly representation for a file size
   * @param size
   * @return
   */
  public static String fileSize(long length) {
    int unit = length / (1024 * 1024 * 1024) 
    if(unit > 0) {
      return String.format("%.2f GiB", ((double)length) / (1024 * 1024 * 1024))
    }
    
    unit = length / (1024 * 1024) 
    if(unit > 0) {
      return String.format("%.2f MiB", ((double)length) / (1024 * 1024))
    }
    
    unit = length / (1024) 
    if(unit > 0) {
      return String.format("%.2f KiB", ((double)length) / 1024)
    }

    return "${length} bytes"    
  }
}
