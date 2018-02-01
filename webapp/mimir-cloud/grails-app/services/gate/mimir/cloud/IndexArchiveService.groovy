/*
 *  IndexDownloadService.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan 26 Apr 2011
 *  
 *  $Id$
 */
package gate.mimir.cloud

import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import grails.util.Holders;

import gate.mimir.util.IndexArchiveState;
import gate.mimir.util.MultiFileOutputStream;
import gate.mimir.web.Index;
import gate.mimir.web.LocalIndex;

class IndexArchiveService {

  def servletContext = Holders.grailsApplication.parentContext.servletContext
  
  def executorService

  def localIndexService
  
  static transactional = true

  def config = Holders.config
  
  public IndexArchive getIndexArchive(LocalIndex theIndex) {
    IndexArchive indexArchive = IndexArchive.findByTheIndex(theIndex)
    if(indexArchive) {
      if(indexArchive.state == IndexArchiveState.AVAILABLE) {
        // check that the files are still there
        File localDir = new File(indexArchive.localDownloadDir)
        if(!localDir.exists()) {
          // local files have been deleted (server restart?)
          // request that they get recreated
          indexArchive.state = IndexArchiveState.PENDING
          indexArchive.cause = null
          indexArchive.localDownloadDir = null
          indexArchive.save(failOnError:true)
        }
      } else {
        // try to recreate the local archive
        indexArchive.state = IndexArchiveState.PENDING
        indexArchive.cause = null
        indexArchive.localDownloadDir = null
        indexArchive.save(failOnError:true) 
      }
    } else {
      indexArchive = new IndexArchive(theIndex:theIndex, 
        state:IndexArchiveState.PENDING, 
        localDownloadDir:null)
      indexArchive.save(flush:true, failOnError:true)
    }
    if(indexArchive.state != IndexArchiveState.AVAILABLE) {
      // we need to re create the index archive
      executorService.execute({
        packageIndex(indexArchive)
      } as Runnable)
    }
    return indexArchive
  }
  
  public void deleteIndexArchive(LocalIndex theIndex) {
    IndexArchive indexArchive = IndexArchive.findByTheIndex(theIndex)
    if(indexArchive) {
      // delete the old files
      if(indexArchive.localDownloadDir) {
        File arcDir = new File(indexArchive.localDownloadDir)
        executorService.execute({
          if(arcDir.exists() && arcDir.isDirectory() && arcDir.canWrite()) {
            arcDir.deleteDir()
          }
        } as Runnable)
      }
      indexArchive.delete(flush:true, failOnError:true)
    }
  }
    
  
  /**
   * Given a  LocalIndex instance, this method packages it into a set of 
   * archive files and returns the list of created output files. 
   * @param index
   * @param temporaryDirectory
   * @return
   */
  public void packageIndex(IndexArchive indexArchive) {
    // This gets called from the background thread!
    // all writes to the DB must be transactionalised
    
    // make a target directory
    try {
      if(!config.gate.mimir.tempDir) {
        throw new RuntimeException(
          "Temporary directory for index archives has not been configured.")
      }
      File tempDir = new File(config.gate.mimir.tempDir)
      if(tempDir.exists()){
        if(tempDir.isDirectory()) {
          if(!tempDir.canWrite()) {
            throw new RuntimeException(
              "Configured location for index archives temporary directory is not writeable.")
          }
        } else {
          throw new RuntimeException(
            "Configured location for index archives temporary directory already exists and is not a directory.")
        }
      } else {
        if(!tempDir.mkdirs()) {
          throw new RuntimeException(
            "Temporary directory for index archives does not exist and could not be created.")
        }
      }
      
      LocalIndex index = indexArchive.theIndex
      File outDir = File.createTempFile(index.name, ".mimir", tempDir)
      outDir.delete()
      outDir.mkdir()
      IndexArchive.withTransaction() {
        // stop the index temporarily
        index.refresh()
        localIndexService.close(index)
        index.state = Index.WORKING
        index.save()
        indexArchive.refresh()
        indexArchive.state = IndexArchiveState.PENDING
        indexArchive.localDownloadDir = outDir.absolutePath
        indexArchive.progress = 10
        indexArchive.save(flush:true, failOnError:true)
      }
      
      // make the index name safe as a file name
      String indexName = index.name.replaceAll("[^a-zA-Z0-9]", '_')
      // write out the interrupted TAR content
      MultiFileOutputStream mfos = new MultiFileOutputStream(outDir,
          indexName, ".mimir", 500 * 1024 * 1024 )
      TarArchiveOutputStream taos = new TarArchiveOutputStream(mfos)
      File indexDir = new File(index.indexDirectory)
      String indexPath = indexDir.absolutePath
      // calculate the total size of the index
      long totalSize = 0
      indexDir.traverse { File aFile ->
        if(!aFile.isDirectory() && !aFile.name.endsWith('.lock.db')) {
          totalSize += aFile.length()
        }
      }
      long bytesOut = 0;
      indexDir.traverse { File aFile ->
        if(!aFile.name.endsWith('.lock.db')) {
          taos.putArchiveEntry(taos.createArchiveEntry(aFile,
              indexName + (aFile.absolutePath - indexPath)))
          if(!aFile.isDirectory()){
            taos << aFile.newInputStream()
          }
          taos.closeArchiveEntry()
        }
        bytesOut += aFile.length()
        // update the progress
        IndexArchive.withTransaction() {
          indexArchive.refresh()
          // maximum is 90%
          indexArchive.progress = 10 + (80 * bytesOut) / totalSize
          indexArchive.save(failOnError:true)
        }
      }
      taos.close()
      // write out the unpack jar
      ZipInputStream zis = new ZipInputStream(new BufferedInputStream(
          servletContext.getResourceAsStream("WEB-INF/unpack.jar")))
      
      ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(
          new File(outDir, "UNPACK.jar")))
      ZipEntry zipEntry = zis.getNextEntry()
      while(zipEntry) {
        zos.putNextEntry(zipEntry)
        zos << zis
        zipEntry = zis.getNextEntry()
      }
      // add the properties file
      Properties indexProps = new Properties()
      indexProps.setProperty('Index Name', index.name)
      int fileIndex = 0
      mfos.getOutputFiles().each{File aFile ->
        indexProps.setProperty("archive file ${fileIndex++}", aFile.name)
      }
      indexProps.setProperty("CRC32", Long.toString(mfos.getCRC()))
      zos.putNextEntry(new ZipEntry("index-properties.xml"))
      indexProps.storeToXML( zos, "Index archive for \"${index.name}\"")
      // close the zip
      zos.close()
      // write out the README.txt file
      new FileOutputStream(new File(outDir, "README.txt")).withWriter("UTF-8"){
        Writer writer ->
        writer.write("""\
This is an archive containing the Mímir index named \"${index.name}\".

To extract the index you will need to execute the UNPACK.jar executable
JAR file. On most platforms this can be done by double-clicking the file.
You will also need to have a Java Runtime Environment installed. If don't
already have it, you can get one from http://www.java.com/.

On some platforms (e.g Ubuntu Linux) you will need to make the UNPACK.jar
file executable before you can run it.

If double-clicking on the jar file does not work, you can execute it on
the command line by navigating to the directory containing the files and
calling:

java -jar UNPACK.jar""")
      }
      // we're done!
      IndexArchive.withTransaction() {
        indexArchive.refresh()
        indexArchive.state = IndexArchiveState.AVAILABLE
        indexArchive.progress = 0
        indexArchive.cause = null
        indexArchive.save(flush:true, failOnError:true)
      }
    } catch (Exception e) {
    log.error("Exception while preparing index archive", e)
      IndexArchive.withTransaction() {
        indexArchive.refresh()
        indexArchive.state = IndexArchiveState.FAILED
        indexArchive.cause = e.getMessage()
        indexArchive.progress = 0
        indexArchive.save(flush:true, failOnError:true)
      }
    } finally {
      IndexArchive.withTransaction() {
        // re-start the index in searching mode
        indexArchive.theIndex.state = Index.READY
        indexArchive.theIndex.save(flush:true, failOnError:true)
      }
    }
  }
}
