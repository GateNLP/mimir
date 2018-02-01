package gate.mimir.test;

import gate.Gate;
import gate.mimir.DocumentRenderer;
import gate.mimir.IndexConfig;
import gate.mimir.MimirIndex;
import gate.mimir.index.DocumentData;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

public class RenderZipCollection {
  
  /**
   * @param args
   */
  public static void main(String[] args) throws Exception {
    Gate.setGateHome(new File("gate-home"));
    Gate.setUserConfigFile(new File("gate-home/user-gate.xml"));
    Gate.init();
    // load the tokeniser plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("gate-home/plugins/ANNIE-tokeniser").toURI().toURL());
    // load the DB plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/db-h2").toURI().toURL());
    // load the measurements plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/measurements").toURI().toURL());
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/sparql").toURI().toURL());

    File indexDir = new File(args[0]);
    File outputDir = new File(args[1]);
    // renumbering rules if required
    int multiplier = Integer.getInteger("federatedIndex.size", 1);
    int offset = Integer.getInteger("federatedIndex.offset", 0);
    long minId = Long.getLong("minDocId", 0);
    // load the IndexConfig to obtain the right renderer
    IndexConfig indexConfig =
            IndexConfig.readConfigFromFile(new File(indexDir,
                    MimirIndex.INDEX_CONFIG_FILENAME), indexDir);
    DocumentRenderer renderer = indexConfig.getDocumentRenderer();

    // enumerate the zip collection files
    File[] zipCollectionFiles = indexDir.listFiles(new FilenameFilter() {
      
      @Override
      public boolean accept(File dir, String name) {
        return name.startsWith("mimir-collection-")
                && name.endsWith(".zip");
      }
    });

    
    for(File zf : zipCollectionFiles) {
      // for each input file, create a corresponding output file
      File outFile = new File(outputDir, "rendered-" + zf.getName());
      File metaFile = new File(outputDir, "meta-" + zf.getName());
      try(ZipInputStream collIn = new ZipInputStream(new FileInputStream(zf));
          ZipOutputStream rendOut = new ZipOutputStream(new FileOutputStream(outFile));
          ZipOutputStream metaOut = new ZipOutputStream(new FileOutputStream(metaFile))) {
        ZipEntry inEntry;
        while((inEntry = collIn.getNextEntry()) != null) {
          long docId = renumber(inEntry.getName(), multiplier, offset);
          if(docId >= minId) {
            // for each document, load the DocumentData from the original zip
            DocumentData dd = null;
            try(ObjectInputStream ois = new ObjectInputStream(new CloseShieldInputStream(collIn))) {
              dd = (DocumentData)ois.readObject();
            }
            if(dd != null) {
              // and write the rendered form to the new zip (in UTF-8)
              ZipEntry outEntry = new ZipEntry(String.valueOf(docId));
              rendOut.putNextEntry(outEntry);
              try(BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FilterOutputStream(rendOut) {
                @Override
                public void close() throws IOException {
                  flush();
                  ((ZipOutputStream)out).closeEntry();
                }
                
              }, "UTF-8"))) {
                renderer.render(dd, null, w);
              }
              // write the metadata entry as JSON
              ZipEntry metaEntry = new ZipEntry(docId + ".meta");
              metaOut.putNextEntry(metaEntry);
              try(BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FilterOutputStream(metaOut) {
                @Override
                public void close() throws IOException {
                  flush();
                  ((ZipOutputStream)out).closeEntry();
                }
                
              }, "UTF-8"))) {
                w.write("{\"uri\":\"");
                StringEscapeUtils.escapeJavaScript(w, dd.getDocumentURI());
                w.write("\",\"title\":\"");
                StringEscapeUtils.escapeJavaScript(w, dd.getDocumentTitle());
                w.write("\"}");
              }            
            } else {
              System.out.println("Error converting document " + inEntry.getName());
            }
          }
        }
      }
    }
  }
  
  /**
   * Renumber the original name to match the ID it would have in a federated index
   * if this were the (0-based) <code>offset</code>th index in a federation of size
   * <code>multiplier</code>.
   * @throws NumberFormatException
   */
  public static long renumber(String originalName, int multiplier, int offset) throws NumberFormatException {
    return (Long.parseLong(originalName) * multiplier) + offset;
  }

}
