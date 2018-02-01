/*
 *  DocumentCollection.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 15 Apr 2009
 *
 *  $Id: DocumentCollection.java 17466 2014-02-27 12:48:30Z valyt $
 */
package gate.mimir.index;


import gate.mimir.MimirIndex;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.log4j.Logger;



/**
 * A Mimir document collection. Consists of one or more zip files containing 
 * serialised {@link DocumentData} values. Each {@link MimirIndex} contains a
 * document collection.
 */
public class DocumentCollection {
  
  /**
   * The maximum number of documents to be stored in the in-RAM document cache.
   */
  protected static final int DOCUMENT_DATA_CACHE_SIZE = 100;
  
  /**
   * Class representing one of the collection (zip) files.
   */
  public static class CollectionFile implements Comparable<CollectionFile> {
    /**
     * The filename for the zip collection.
     */
    public static final String MIMIR_COLLECTION_BASENAME = "mimir-collection-";
    
    /**
     * The file extension used for the mimir-specific relocatable zip collection
     * definition.
     */
    public static final String MIMIR_COLLECTION_EXTENSION = ".zip";
    
    /**
     * Regex pattern that recognises a valid collection file name and its parts.
     * The following capturing groups can be used when a match occurs:
     * <ul>
     *   <li>1: the collection file ID</li>
     *   <li>2: the collection file number (the numeric part of the ID)</li>
     *   <li>3: (optional) the collection file suffix (the non-numeric part of the ID)</li>
     * </ul>   
     */
    protected static final Pattern MIMIR_COLLECTION_PATTERN = Pattern.compile(
        "\\Q" + MIMIR_COLLECTION_BASENAME + "\\E((\\d+)(?:-([-0-9a-zA-Z]+))?)\\Q"+
        MIMIR_COLLECTION_EXTENSION + "\\E");
    
    public static FilenameFilter FILENAME_FILTER = new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return MIMIR_COLLECTION_PATTERN.matcher(name).matches();
      }
    };
    
    
    protected File file;
    
	  protected ZipFile zipFile;
	  
	  protected long firstEntry;
	  
	  protected long lastEntry;

	  /**
	   * Each collection file has a number, and optionally a suffix. For example
	   * in &quot;mimir-collection-0-a.zip&quot;, the number is 0, and the suffix
	   * is a.
	   */
	  protected int collectionFileNumber;
	  
	  
	  /**
	   * The size in bytes of the underlying file.
	   */
	  protected long length;
	  
	  /**
	   * The number of documents contained.
	   */
	  protected int documentCount;

	  /**
     * Given the name of a zip file, this method returns its ID: the part of the 
     * file name between the prefix ({@value DocumentCollection#MIMIR_COLLECTION_BASENAME}) and
     * the suffix ({@value DocumentCollection#MIMIR_COLLECTION_EXTENSION}), or <code>null</code> if 
     * the name is not that of a valid collection file.
     * @param fileName the file name to be parsed.
     * @return the ID of the file, or <code>null</code>.
     */
    protected static String getCollectionFileId(String fileName){
      Matcher m = MIMIR_COLLECTION_PATTERN.matcher(fileName);
      return m.matches() ? m.group(1) : null;
    }

    protected static int getCollectionFileNumber(String fileName){
      Matcher m = MIMIR_COLLECTION_PATTERN.matcher(fileName);
      return m.matches() ? Integer.parseInt(m.group(2)) : -1;
    }
    
    public static String getCollectionFileName(String id) {
      return MIMIR_COLLECTION_BASENAME + id + MIMIR_COLLECTION_EXTENSION;
    }
    
	  public CollectionFile(File file) throws ZipException, IOException {
	    this.file = file;
      zipFile = new ZipFile(file);
      collectionFileNumber = getCollectionFileNumber(file.getName());
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      firstEntry = Long.MAX_VALUE;
      lastEntry = -1;
      documentCount = 0;
      while(entries.hasMoreElements()) {
        ZipEntry anEntry = entries.nextElement();
        String entryName = anEntry.getName();
        try {
          long entryId = Long.parseLong(entryName);
          //update the current maximum and minimum
          if(entryId > lastEntry) lastEntry = entryId;
          if(entryId < firstEntry) firstEntry = entryId;
          documentCount++;
        } catch(NumberFormatException e) {
          //not parseable -> we'll ignore this entry.
          logger.warn("Unparseable zip entry name: " + entryName);
        }
      }
      if(firstEntry == Long.MAX_VALUE) firstEntry = -1;
      length = file.length();
	  }
	  
    @Override
    public int compareTo(CollectionFile o) {
      return Long.compare(firstEntry, o.firstEntry);
    }
    
    public boolean containsDocument(long documentID) {
      return firstEntry <= documentID && 
          documentID <= lastEntry &&
          zipFile.getEntry(Long.toString(documentID)) != null;
    }
    
    public DocumentData getDocumentData(Long documentID) throws IOException {
      ZipEntry entry = zipFile.getEntry(Long.toString(documentID));
      if(entry == null) throw new NoSuchElementException(
          "No entry found for document ID " + documentID);
      CustomObjectInputStream ois = null;
      try {
        ois = new CustomObjectInputStream(zipFile.getInputStream(entry));
        return (DocumentData) ois.readObject();
      } catch(ClassNotFoundException e) {
        //invalid data read from the zip file
        throw new IOException("Invalid data read from zip file!", e);
      } finally {
        if(ois != null) ois.close();
      }
    }
    
    
    public void close() throws IOException {
      zipFile.close();
    }
  }

  /**
   * Custom implementation of {@link ObjectInputStream} that handles reading
   * old mimir archive files where the contents include serialised classes
   * with old (pre-Mímir-5) class names.
   */
  protected static class CustomObjectInputStream extends ObjectInputStream {

    public CustomObjectInputStream() throws IOException, SecurityException {
      super();
    }

    public CustomObjectInputStream(InputStream in) throws IOException {
      super(in);
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException,
        ClassNotFoundException {
      if("gate.mimir.index.mg4j.zipcollection.DocumentData".equals(desc.getName())) {
        desc = ObjectStreamClass.lookup(Class.forName("gate.mimir.index.DocumentData"));
      }
      return super.resolveClass(desc);
    }
  }
  
  /**
   * Class that handles the creation of collection files.
   */
  protected static class CollectionFileWriter {
    /**
     * The number of documents kept in memory until a new zip file is written. As 
     * new documents are submitted, they get written to the currently open zip 
     * file but they cannot be read from the file. To account for this, we keep 
     * them in memory, in the {@link #inputBuffer} structure.
     */
    protected static final int INPUT_BUFFER_SIZE = 1000;
    
    
    /**
     * Document data objects that have been written to the zip file currently 
     * being created and have to be kept in RAM until the file is closed and can 
     * be open in read mode. 
     */
    protected Long2ObjectLinkedOpenHashMap<DocumentData> inputBuffer;
    
    /**
     * The zip file managed by this collection.
     */
    protected ZipOutputStream zipOuputStream;
    
    /**
     * The zip file to which we are currently writing.
     */
    protected File zipFile;
    
    /**
     * The number of entries written so far to the current zip file.
     */
    protected int currentEntries;
    
    /**
     * The amount of bytes written so far to the current zip file.
     */
    protected long currentLength;
    
    /**
     * A {@link ByteArrayOutputStream} used to temporarily store serialised 
     * document data objects.
     */
    protected ByteArrayOutputStream byteArrayOS;
    
    public CollectionFileWriter(File file) throws IndexException {
      this.zipFile = file;
      if(zipFile.exists()) throw new IndexException("Collection zip file (" + 
          file.getAbsolutePath() + ") already exists!");
      byteArrayOS = new ByteArrayOutputStream();
      
      try {
        zipOuputStream = new ZipOutputStream(new BufferedOutputStream(
                new  FileOutputStream(zipFile)));
      } catch(FileNotFoundException e) {
        throw new IndexException("Cannot write to collection zip file (" + 
                zipFile.getAbsolutePath() + ")", e);
      }
      currentEntries = 0;
      currentLength = 0;
      inputBuffer = new Long2ObjectLinkedOpenHashMap<DocumentData>();
    }
    
    /**
     * 
     * @param entryName
     * @param document
     * @return true if the document was written successfully, false if this 
     * collection file is full and cannot take the extra content.
     * 
     * @throws IOException
     */
    public boolean writeDocumentData(long documentId, DocumentData document) throws IOException {
      //write the new document to the byte array
      ObjectOutputStream objectOutStream = new ObjectOutputStream(byteArrayOS);
      objectOutStream.writeObject(document);
      objectOutStream.close();

      // check if this will take us over size
      if(currentLength + byteArrayOS.size() > ZIP_FILE_MAX_SIZE ||
         currentEntries >= ZIP_FILE_MAX_ENTRIES ||
         inputBuffer.size() >= INPUT_BUFFER_SIZE) return false;
      
      // create a new entry in the current zip file
      ZipEntry entry = new ZipEntry(Long.toString(documentId));
      zipOuputStream.putNextEntry(entry);
      //write the data
      byteArrayOS.writeTo(zipOuputStream);
      zipOuputStream.closeEntry();
      currentLength += entry.getCompressedSize();
      
      //clean up the byte array for next time
      byteArrayOS.reset();
      currentEntries++;
      // save the document data to the input buffer
      inputBuffer.put(documentId, document);
      return true;
    }

    public void close() throws IOException {
      if(zipOuputStream != null) zipOuputStream.close();
      inputBuffer.clear();
    }
  }
  
  /**
   * The zip files containing the document collection.
   */
  protected List<CollectionFile> collectionFiles = null;
  
  protected CollectionFileWriter collectionFileWriter;
  
  
  private static Logger logger = Logger.getLogger(DocumentCollection.class);
  
  /**
   * The top level directory for the index.
   */
  protected File indexDirectory;
  
  /**
   * A cache of {@link DocumentData} values used for returning the various
   * document details (title, URI, text).
   */
  protected Long2ObjectLinkedOpenHashMap<DocumentData> documentCache;
  

  
  /**
   * Flag that gets set to true when the collection is closed (and blocks all 
   * subsequent operations).
   */
  private volatile boolean closed = false; 
  
  /**
   * The maximum number of bytes to write to a single zip file.
   */
  public static final long ZIP_FILE_MAX_SIZE = 2 * 1000 * 1000 * 1000; 
    
  /**
   * The maximum number of entries to write to a single zip file.
   * Java 1.5 only support 2^16 entries.
   * If running on Java 1.6, this limit can safely be increased, however, the
   * total size of the file (as specified by {@link #ZIP_FILE_MAX_SIZE}) should 
   * not be greater than 4GB, in either case.
   */
  public static final int ZIP_FILE_MAX_ENTRIES = 250000;
  

  
  /**
   * The ID for the next document to be written in this collection. This value 
   * is initialised to 0 and then is automatically incremented whenever a new 
   * document is written.
   */
  protected long nextDocumentId;
  
  
  /**
   * Creates a DocumentCollection object for accessing the document data.
   * @param indexDirectory
   * @throws IOException 
   */
  public DocumentCollection(File indexDirectory) throws IOException {
    this.indexDirectory = indexDirectory;
    
    collectionFiles = new ArrayList<CollectionFile>();
    // prepare for reading
    for(File aCollectionFile : indexDirectory.listFiles(CollectionFile.FILENAME_FILTER)) {
      collectionFiles.add(new CollectionFile(aCollectionFile));
    }
    Collections.sort(collectionFiles);
    // sanity check
    for(int i = 0;  i < collectionFiles.size() - 1; i++) {
      CollectionFile first = collectionFiles.get(i);
      CollectionFile second = collectionFiles.get(i + 1);
      if(first.lastEntry >= second.firstEntry) {
        throw new IOException(
            "Invalid entries distribution: collection file " + 
            second.zipFile.getName() + 
            " contains an entry named \"" + second.firstEntry + 
            "\", but an entry with a larger-or-equal ID was " +
            "already seen in a previous collection file!");          
      }
    }
    documentCache = new Long2ObjectLinkedOpenHashMap<DocumentData>();
    
    // prepare for writing
    nextDocumentId = collectionFiles.isEmpty() ? 0 : 
        (collectionFiles.get(collectionFiles.size() - 1).lastEntry + 1);
    
  }
  

  /**
   * Gets the document data for a given document ID.
   * @param documentID the ID of the document to be retrieved.
   * @return a {@link DocumentData} object for the requested document ID.
   * @throws IOException if there are problems accessing the underlying zip file; 
   * @throws NoSuchElementException if the requested document ID is not found.
   */
  public DocumentData getDocumentData(long documentID) throws IOException{
    if(closed) throw new IllegalStateException(
            "This document collection has already been closed!");
    DocumentData documentData = null;
    if(collectionFiles.isEmpty() ||
       documentID > collectionFiles.get(collectionFiles.size() - 1).lastEntry) {
      // it's a new document that's not yet available from the zip files
      documentData = collectionFileWriter != null ?
          collectionFileWriter.inputBuffer.get(documentID):
          null;
    } else {
      // it's an old document. Try the cache first
      documentData = documentCache.getAndMoveToFirst(documentID);
      if(documentData == null) {
        // cache miss: we need to actually load it
        //locate the right zip file
        synchronized(collectionFiles) {
          files: for(CollectionFile aColFile : collectionFiles) {
            if(aColFile.containsDocument(documentID)) {
              // we found the collection file containing the document
              documentData = aColFile.getDocumentData(documentID);
              documentCache.putAndMoveToFirst(documentID, documentData);
              if(documentCache.size() > DOCUMENT_DATA_CACHE_SIZE) {
                documentCache.removeLast();
              }
              break files;
            }
          } 
        }
      }
    }
    if(documentData == null) throw new NoSuchElementException(
        "No entry found for document ID " + documentID);
    return documentData;  
  }
  
  /**
   * Writes a new document to the underlying zip file. The documents added 
   * through this method will get automatically generated names starting from 
   * &quot;0&quot;, and continuing with &quot;1&quot;, &quot;2&quot;, etc.   
   * @param document
   * @throws IndexException if there are any problems while accessing the zip 
   * collection file(s).
   */
  public void writeDocument(DocumentData document) throws IndexException{
    if(collectionFileWriter == null) openCollectionWriter();
    
    try{
      boolean success = false;
      while(!success) {
        success = collectionFileWriter.writeDocumentData(nextDocumentId, 
            document);
        if(!success) {
          // the current collection file is full: close it
          collectionFileWriter.close();
          synchronized(collectionFiles) {
            // open the newly saved zip file
            collectionFiles.add(new CollectionFile(collectionFileWriter.zipFile));
          }
          // open a new one and try again
          openCollectionWriter();
        }   
      }
    } catch(IOException e){
      throw new IndexException("Problem while accessing the collection file", e);
    } finally {
      nextDocumentId++;
    }
  }
  
  /**
   * Opens the current zip file and sets the {@link #zipFile} and 
   * {@link #zipOuputStream} values accordingly. 
   * @throws IndexException if the collection zip file already exists, or cannot
   * be opened for writing.
   */
  protected void openCollectionWriter() throws IndexException{
    int zipFileNumber = 0;
    synchronized(collectionFiles) {
      zipFileNumber = collectionFiles.isEmpty() ? 0 :
        collectionFiles.get(collectionFiles.size() - 1).collectionFileNumber + 1; 
    }
    collectionFileWriter = new CollectionFileWriter(
        new File(indexDirectory,
            CollectionFile.getCollectionFileName(
                Integer.toString(zipFileNumber))));
  }
  
  /**
   * Close this document collection and release all allocated resources (such 
   * as open file handles). 
   * @throws IOException 
   * @throws IndexException 
   */
  public void close() throws IOException {
    // close the writer
    if(collectionFileWriter != null) collectionFileWriter.close();
    // close the reader
    closed = true;
    if(collectionFiles != null){
      for(CollectionFile colFile : collectionFiles){
        try {
          colFile.close();
        } catch(IOException e) {
          // ignore
        }
      }
      collectionFiles.clear();
      collectionFiles = null;      
    }
    documentCache.clear();
  }
  
  
  /**
   * Returns the number of archive files in this collection.
   * @return
   */
  public int getArchiveCount() {
    return collectionFiles.size();
  }
  
  /**
   * Combines multiple smaller collection files into larger ones. If multiple
   * consecutive collection files can be combined without exceeding the maximum
   * permitted sizes ({@link #ZIP_FILE_MAX_ENTRIES} and 
   * {@link #ZIP_FILE_MAX_SIZE}), then they are combined.
   * 
   * @throws ZipException
   * @throws IOException
   * @throws IndexException 
   */
  public synchronized void compact() throws ZipException, IOException, IndexException {
    logger.debug("Starting collection compact.");
    // find an interval of files that can be joined together
    // we search from the end toward the start so that we can modify the 
    // list without changing the yet-unvisited IDs.
    CollectionFile[] colFilesArr = collectionFiles.toArray(
        new CollectionFile[collectionFiles.size()]);
    int intervalEnd = -1;
    int intervalLength = 0;
    int intervalEntries = 0;
    long intervalBytes = 0;
    for(int i = colFilesArr.length -1; i >= 0; i--) {
      // is the current file small?
      boolean smallFile = 
          colFilesArr[i].documentCount < ZIP_FILE_MAX_ENTRIES &&
          colFilesArr[i].length < ZIP_FILE_MAX_SIZE;
      if(intervalEnd < 0) { // we're looking for the first 'small' file
        if(smallFile) {
          // we found a small file: start a new interval
          intervalEnd = i;
          intervalLength = 1;
          intervalEntries = colFilesArr[i].documentCount;
          intervalBytes = colFilesArr[i].length;
        }
      } else { // we're trying to extend the current interval
        boolean currentFileAccepted = 
            intervalEntries + colFilesArr[i].documentCount < ZIP_FILE_MAX_ENTRIES &&
            intervalBytes + colFilesArr[i].length < ZIP_FILE_MAX_SIZE;
        if(currentFileAccepted) {
          // extend the current interval
          intervalEntries += colFilesArr[i].documentCount;
          intervalBytes += colFilesArr[i].length;
          intervalLength = intervalEnd - i + 1;
        }
        if(!currentFileAccepted || i == 0) {
          // end the current interval
          if(intervalLength > 1){
            int intervalStart = intervalEnd - intervalLength + 1;
            // combine the files
            // create the new file
            String newFileName = "temp-" + 
                CollectionFile.MIMIR_COLLECTION_BASENAME + 
                colFilesArr[intervalStart].collectionFileNumber + 
                "-" + 
                colFilesArr[intervalEnd].collectionFileNumber + 
                CollectionFile.MIMIR_COLLECTION_EXTENSION;
            File newZipFile = new File(indexDirectory, newFileName);
            ZipOutputStream  zos = new ZipOutputStream(new BufferedOutputStream(
                      new  FileOutputStream(newZipFile)));
            byte[] buff = new byte[1024 * 1024];
            for(int j = intervalStart; j <= intervalEnd; j++) {
              Enumeration<? extends ZipEntry> entries = 
                  colFilesArr[j].zipFile.entries();
              while(entries.hasMoreElements()) {
                ZipEntry anEntry = entries.nextElement();
                zos.putNextEntry(new ZipEntry(anEntry));
                InputStream is = colFilesArr[j].zipFile.getInputStream(anEntry);
                int read = is.read(buff);
                while(read >= 0){
                  zos.write(buff, 0, read);
                  read = is.read(buff);
                }
                zos.closeEntry();
              }
            }
            zos.close();
            
            //update the collection
            synchronized(colFilesArr) {
              //confirm that the collection files have not changed since we started
              for(int j = intervalStart; j <= intervalEnd; j++) {
                if(colFilesArr[j] != collectionFiles.get(j)) {
                  logger.warn("Collection files have changed since the "
                      + "compacting operation started. Compact aborted." + 
                      "Details: " + colFilesArr[j].file.getAbsolutePath() + 
                      " not the same as " + 
                      collectionFiles.get(j).file.getAbsolutePath());

                  // delete the newly created collection file
                  newZipFile.delete();
                  return;
                }
              }
              // build name for new collection file
              File newCollectionFile = new File(indexDirectory, 
                  CollectionFile.getCollectionFileName(
                      Integer.toString(colFilesArr[intervalStart].collectionFileNumber) + 
                      "-" + 
                      Integer.toString(colFilesArr[intervalEnd].collectionFileNumber)));
              // delete the old files
              for(int j = intervalStart; j <= intervalEnd; j++) {
                CollectionFile oldColFile = collectionFiles.remove(intervalStart);
                if(!oldColFile.file.delete()) {
                  throw new IndexException(
                      "Could not delete old collection file " + 
                      oldColFile.file + "! " + 
                      "Document collection now inconsistent.");
                }
              }
              // rename temp file to new name
              newZipFile.renameTo(newCollectionFile);
              // add new collection file
              collectionFiles.add(intervalStart, new CollectionFile(newCollectionFile));
            }
          }
          // we found and merged an interval, 
          // or we only found an interval of length 1: start again
          intervalEnd = -1;
          intervalLength = 0;
          intervalEntries = 0;
          intervalBytes = 0;
        }
      }
    }
  }
}