/*
*  MultiFileOutputStream.java
*
*  Copyright (c) 2011, The University of Sheffield.
*
*  Valentin Tablan, 26 Apr 2011
*  
*  $Id: MultiFileOutputStream.java 14531 2011-11-14 12:01:21Z ian_roberts $
*/
package gate.mimir.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * An output stream that splits its data into a set of files inside a given 
 * directory. When each file reaches a size limit, it is closed and a new file 
 * is opened to write data to.  
 */
public class MultiFileOutputStream extends OutputStream {
  
  /**
   * The directory where all the output files are written to.
   */
  private File outputDirectory;
  
  /**
   * The prefix used for all output files.
   */
  private String filePrefix;

  /**
   * The suffix used for all output files.
   */
  private String fileSuffix;
  
  /**
   * The maximum size permitted for an output file.
   */
  private long maximumFileSize;
  
  /**
   * The actual output stream for the current file.
   */
  protected OutputStream currentOutputStream;
  
  /**
   * The file currently being written to.
   */
  protected File currentOutputFile;
  
  /**
   * The number of bytes written to the current output stream so far.
   */
  protected long currentBytes;
  
  /**
   * The current sequence number.
   */
  protected int currentSeqNumber = -1;
  
  protected boolean closed = false;
  
  /**
   * The list of output files that were created and closed so far.
   */
  protected List<File> outputFiles;
  
  protected CRC32 crc;
  
  /**
   * Creates a new multi-file output stream. Each output file will be created 
   * inside the supplied output directory, and will have a name comprising the 
   * filePrefix value (if one was given), followed by a sequence number, 
   * followed by the fileSuffinx value (if one was given).  
   * @param outputDirectory the directory in which all output files are created.
   * The {@link File} value supplied must point to an existing directory. 
   * @param filePrefix the prefix used for creating output file names.
   * @param fileSuffix the suffix used for creating output file names.
   * @param maximumFileSize the maximum file size (number of bytes) for the 
   * output files. When the current output file reaches this size, it is closed
   * and a new output file is created.
   * @throws IOException 
   */
  public MultiFileOutputStream(File outputDirectory, String filePrefix,
      String fileSuffix, long maximumFileSize) throws IOException {
    if(outputDirectory != null) {
      if(outputDirectory.canWrite()) {
        if(outputDirectory.isDirectory()) {
          this.outputDirectory = outputDirectory;          
        } else {
          throw new IllegalArgumentException("Provided output directory (" + 
              outputDirectory.getAbsolutePath() + ") is not a directory!");
        }
      } else {
        throw new IllegalArgumentException("Provided output directory (" + 
            outputDirectory.getAbsolutePath() + 
            ") does not exist (or is not writeable)!");
      }
    } else {
      throw new IllegalArgumentException("Output directory cannot be null!");
    }
    
    this.filePrefix = filePrefix;
    this.fileSuffix = fileSuffix;
    if(maximumFileSize > 0) {
      this.maximumFileSize = maximumFileSize;
    } else throw new IllegalArgumentException(
        "Maximum file size must be positive!");
    outputFiles = new LinkedList<File>();
    crc = new CRC32();
    try {
      // write out the signature
      byte[] signature = "MMFA".getBytes("UTF-8");
      write(signature, 0, signature.length);
    } catch(UnsupportedEncodingException e) {
      throw new RuntimeException("This JVM does not support UTF-8!");
    }
  }

  /**
   * Closes the current output file and opens the next one.
   * @throws IOException 
   */
  protected void nextFile() throws IOException {
    if(currentOutputStream != null) {
      currentOutputStream.flush();
      currentOutputStream.close();
      outputFiles.add(currentOutputFile);
    }
    currentSeqNumber++;
    currentBytes = 0;
    StringBuilder fileName = new StringBuilder();
    if(filePrefix != null) fileName.append(filePrefix);
    fileName.append(String.format("%04d", currentSeqNumber));
    if(fileSuffix != null) fileName.append(fileSuffix);
    currentOutputFile = new File(outputDirectory, fileName.toString());
    currentOutputStream = new BufferedOutputStream(
        new FileOutputStream(currentOutputFile));
    
  }
  
  /**
   * Gets the current value for the maximum file size.
   * @return
   */
  public long getMaximumFileSize() {
    return maximumFileSize;
  }

  /**
   * Gets the directory in which output files are created.
   * @return
   */
  public File getOutputDirectory() {
    return outputDirectory;
  }


  public String getFilePrefix() {
    return filePrefix;
  }


  public String getFileSuffix() {
    return fileSuffix;
  }

  
  /**
   * Gets the output files that were created, written to, and closed so far. 
   * The returned array is only guaranteed to include all output files if this 
   * method is called after the stream was closed.  
   * @return
   */
  public File[] getOutputFiles() {
    return outputFiles.toArray(new File[outputFiles.size()]);
  }

  /**
   * Gets the CRC32 sum calculated for the bytes written so far. It probably 
   * makes sense to only call this after closing the steam, so that the returned
   * value refer to the entire data stream.   
   * @return
   */
  public long getCRC() {
    return crc.getValue();
  }
  
  @Override
  public void write(int b) throws IOException {
    if(closed) throw new IOException("Stream already closed!");
    // this may be the first write
    if(currentOutputStream == null) nextFile();
    //we may have reached the limit
    if(currentBytes >= maximumFileSize) nextFile();
    currentOutputStream.write(b);
    crc.update(b);
    currentBytes += 1;
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if(closed) throw new IOException("Stream already closed!");
    // this may be the first write
    if(currentOutputStream == null) nextFile();
    if(currentBytes + len <= maximumFileSize) {
      currentOutputStream.write(b, off, len);
      crc.update(b, off, len);
      currentBytes += len;
    } else {
      // we cannot write all the bytes
      int newLen = (int)(maximumFileSize - currentBytes);
      currentOutputStream.write(b, off, newLen);
      crc.update(b, off, newLen);
      currentBytes += newLen;
      nextFile();
      // recursive all, in case len is really large and maximumFileSize is 
      // really small
      write(b, off + newLen, len - newLen);
    }
  }

  @Override
  public void flush() throws IOException {
    if(closed) throw new IOException("Stream already closed!");
    if(currentOutputStream != null) currentOutputStream.flush();
  }

  @Override
  public void close() throws IOException {
    if(closed) return;
    if(currentOutputStream != null){
      currentOutputStream.close();
      outputFiles.add(currentOutputFile);
    }
    currentOutputStream = null;
    currentOutputFile = null;
    closed = true;
  }
}
