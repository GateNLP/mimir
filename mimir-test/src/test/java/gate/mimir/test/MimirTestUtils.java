/*
 *  MimirTestUtils.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id: MimirTestUtils.java 16667 2013-04-29 16:35:35Z valyt $
 */
package gate.mimir.test;

import gate.Gate;
import gate.creole.ANNIEConstants;
import gate.mimir.index.*;
import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.DocumentMetadataHelper;
import gate.mimir.IndexConfig;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.mimir.IndexConfig.TokenIndexerConfig;
import gate.mimir.SemanticAnnotationHelper.Mode;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.query.*;

import it.unimi.di.big.mg4j.index.DowncaseTermProcessor;
import it.unimi.di.big.mg4j.index.NullTermProcessor;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;



/**
 * A collection of utility methods in support of tests. 
 */
public class MimirTestUtils {
  
  public static IndexConfig getTestIndexConfig(File indexDir, 
		  Class<? extends AbstractSemanticAnnotationHelper> helperClass) 
  throws IllegalArgumentException, InstantiationException, 
      IllegalAccessException, InvocationTargetException, SecurityException, 
      NoSuchMethodException, ClassNotFoundException {
    
	Class<? extends SemanticAnnotationHelper> measurementsHelperClass =
	  Class.forName("gate.mimir.measurements.MeasurementAnnotationHelper",
	          true, Gate.getClassLoader()).asSubclass(SemanticAnnotationHelper.class);
	SemanticAnnotationHelper measurementHelper = measurementsHelperClass.newInstance();
	measurementsHelperClass.getMethod("setDelegateHelperType", Class.class)
	  .invoke(measurementHelper, helperClass);
	
    // simple metadata helper for HTML tags
    OriginalMarkupMetadataHelper docHelper = new OriginalMarkupMetadataHelper(
        new HashSet<String>(Arrays.asList(
            new String[] {
              "b", "i", "li", "ol", "p", "sup", "sub", "u", "ul"})));
    
    
    // index configuration used for testing.
    return new IndexConfig(
            indexDir,
            "mimir",
            ANNIEConstants.TOKEN_ANNOTATION_TYPE,
            "mimir",
            new TokenIndexerConfig[]{
                new TokenIndexerConfig(
                        ANNIEConstants.TOKEN_STRING_FEATURE_NAME, 
                        DowncaseTermProcessor.getInstance(), true),
                new TokenIndexerConfig(
                        ANNIEConstants.TOKEN_CATEGORY_FEATURE_NAME, 
                        NullTermProcessor.getInstance(), true),
                new TokenIndexerConfig(
                        "root", 
                        NullTermProcessor.getInstance(), true)
            }, 
            new SemanticIndexerConfig[]{
                new SemanticIndexerConfig(
                    new String[]{"Measurement"}, 
                    new SemanticAnnotationHelper[] {measurementHelper}, true),
                new SemanticIndexerConfig(
                        new String[]{"PublicationAuthor", "PublicationDate",
                                "PublicationLocation", "PublicationPages",
                                "Reference", "Section", "Sentence"}, 
                        new SemanticAnnotationHelper[] {
                            createHelper(helperClass, "PublicationAuthor", null, null, null, null, null, Mode.ANNOTATION),
                            createHelper(helperClass, "PublicationDate", null, null, null, null, null, Mode.ANNOTATION),
                            createHelper(helperClass, "PublicationLocation", null, null, null, null, null, Mode.ANNOTATION),
                            createHelper(helperClass, "PublicationPages", null, null, null, null, null, Mode.ANNOTATION),
                            createHelper(helperClass, "Reference", new String[]{"type"}, null, null, null, null, Mode.ANNOTATION),
                            createHelper(helperClass, "Section", new String[]{"type"}, null, null, null, null, Mode.ANNOTATION),
                            createHelper(helperClass, "Sentence", null, null, null, null, null, Mode.ANNOTATION)}, 
                            true),

                new SemanticIndexerConfig(
                        new String[]{"Abstract", "Assignee",
                                "ClassificationIPCR", "InventionTitle",
                                "Inventor", "Document", "PriorityClaim"}, 
                        new SemanticAnnotationHelper[] {
                          createHelper(helperClass, "Abstract", new String[]{"lang"}, null, null, null, null, Mode.ANNOTATION),
                          createHelper(helperClass, "Assignee", null, null, null, null, null, Mode.ANNOTATION),
                          createHelper(helperClass, "ClassificationIPCR", new String[]{"status"}, null, null, null, null, Mode.ANNOTATION),
                          createHelper(helperClass, "InventionTitle", new String[]{"lang", "status"}, null, null, null, null, Mode.ANNOTATION),
                          createHelper(helperClass, "Inventor", new String[]{"format", "status"}, null, null, null, null, Mode.ANNOTATION),
                          createHelper(helperClass, "Document", null, new String[]{"date"}, null, new String[]{"ucid"}, null, Mode.DOCUMENT),
                          createHelper(helperClass, "PriorityClaim", null, null, null, new String[]{"ucid"}, null, Mode.ANNOTATION)},
                          true)
            },
            new DocumentMetadataHelper[] {docHelper}, 
            docHelper);
  }


  public static SemanticAnnotationHelper createHelper(Class<? extends AbstractSemanticAnnotationHelper> helperClass, String annType,
                                               String[] nominalFeatures, String[] integerFeatures, String[] floatFeatures,
                                               String[] textFeatures, String[] uriFeatures, SemanticAnnotationHelper.Mode mode) throws InstantiationException, IllegalAccessException {
    AbstractSemanticAnnotationHelper helper = helperClass.newInstance();
    helper.setAnnotationType(annType);
    helper.setNominalFeatures(nominalFeatures);
    helper.setIntegerFeatures(integerFeatures);
    helper.setFloatFeatures(floatFeatures);
    helper.setTextFeatures(textFeatures);
    helper.setUriFeatures(uriFeatures);
    helper.setMode(mode);
    return helper;
  }
  

  
  /**
   * Executes two different queries and returns two lists of results: one with
   * hits that only appear in the first query, the other with hits that only 
   * appear in the second.
   * 
   * The hits from the two queries are only compared in terms of document id, 
   * term position, and hit length (i.e. the sub-bindings are ignored).
   * 
   * @param left the first query to be executed.
   * @param right the second query to be executed.
   * @return an array containing two lists. The first element is a list with 
   * hits that only occur in the <code>left</code> query; the second element is
   * a list of hits that only occur in the <code>right</code> query. If the diff
   * result is empty (the two query gave rise to identical results) then 
   * <code>null</code> is returned instead. 
   * @throws IOException 
   */
  public static List<Binding>[] calculateDiff(QueryNode left, 
          QueryNode right, QueryEngine engine) throws IOException{
    List<Binding> onlyInLeft = new ArrayList<Binding>();
    List<Binding> onlyInRight = new ArrayList<Binding>();
    
    QueryExecutor leftExecutor = left.getQueryExecutor(engine);
    QueryExecutor rightExecutor = right.getQueryExecutor(engine);
    
    long leftDoc = leftExecutor.nextDocument(-1);
    long rightDoc = rightExecutor.nextDocument(-1);
    while(leftDoc != -1 || rightDoc != -1){
      //at least one doc is not -1
      if(leftDoc == -1){
        //extra document in right
        Binding aHit = rightExecutor.nextHit();
        while(aHit != null){
          onlyInRight.add(aHit);
          aHit = rightExecutor.nextHit();
        }
        //move right to next doc
        rightDoc = rightExecutor.nextDocument(-1);        
      }else if(rightDoc == -1){
        //extra document in left -> add all hits from this document
        Binding aHit = leftExecutor.nextHit();
        while(aHit != null){
          onlyInLeft.add(aHit);
          aHit = leftExecutor.nextHit();
        }
        //move left to next document
        leftDoc = leftExecutor.nextDocument(-1);        
      }else if(leftDoc < rightDoc){
        //extra document in left -> add all hits from this document
        Binding aHit = leftExecutor.nextHit();
        while(aHit != null){
          onlyInLeft.add(aHit);
          aHit = leftExecutor.nextHit();
        }
        //move left to next document
        leftDoc = leftExecutor.nextDocument(-1);
      }else if(leftDoc > rightDoc){
        //extra document in right
        Binding aHit = rightExecutor.nextHit();
        while(aHit != null){
          onlyInRight.add(aHit);
          aHit = rightExecutor.nextHit();
        }
        //move right to next doc
        rightDoc = rightExecutor.nextDocument(-1);
      }else{
        //both left and right on the same document -> compare the hits
        //first collect all hits on this document, for each executor
        List<Binding> leftHits = new ArrayList<Binding>();
        Binding leftHit = leftExecutor.nextHit();
        while(leftHit != null){
          leftHits.add(leftHit);
          leftHit = leftExecutor.nextHit();
        }
        Collections.sort(leftHits);
        List<Binding> rightHits = new ArrayList<Binding>();
        Binding rightHit = rightExecutor.nextHit();
        while(rightHit != null){
          rightHits.add(rightHit);
          rightHit = rightExecutor.nextHit();
        }
        Collections.sort(rightHits);
        Iterator<Binding> leftIter = leftHits.iterator();
        Iterator<Binding> rightIter = rightHits.iterator();
        leftHit = leftIter.hasNext() ? leftIter.next() : null;
        rightHit = rightIter.hasNext() ? rightIter.next(): null;
        while(leftHit != null || rightHit != null){
          //at least one of the hits is non-null!
          if(rightHit == null){
            //extra hit in left
            onlyInLeft.add(leftHit);
            leftHit = rightIter.hasNext() ? rightIter.next(): null;            
          }else if(leftHit == null){
            //extra hit in right
            onlyInRight.add(rightHit);
            rightHit = rightIter.hasNext() ? rightIter.next(): null;            
          }else if(leftHit.getTermPosition() < rightHit.getTermPosition()){
            //extra hit in left
            onlyInLeft.add(leftHit);
            leftHit = leftIter.hasNext() ? leftIter.next() : null;
          }else if (rightHit.getTermPosition() < leftHit.getTermPosition()){
            //extra hit in right
            onlyInRight.add(rightHit);
            rightHit = rightIter.hasNext() ? rightIter.next(): null;
          }else{
            //same term position -> compare length
            if(leftHit.getLength() < rightHit.getLength()){
              //extra hit in left
              onlyInLeft.add(leftHit);
              leftHit = leftIter.hasNext() ? leftIter.next() : null;              
            }else if(leftHit.getLength() > rightHit.getLength()){
              //extra hit in right
              onlyInRight.add(rightHit);
              rightHit = rightIter.hasNext() ? rightIter.next(): null;
            }else{
              //same hits -> advance both
              leftHit = leftIter.hasNext() ? leftIter.next() : null;
              rightHit = rightIter.hasNext() ? rightIter.next(): null;              
            }
          }
        }
        //advance both left and right to next docs
        leftDoc = leftExecutor.nextDocument(-1);
        rightDoc = rightExecutor.nextDocument(-1);
      }
    }//while(leftDoc != -1 || rightDoc != -1)
    leftExecutor.close();
    rightExecutor.close();
    return (onlyInLeft.size() + onlyInRight.size() == 0) ? 
           null :  
           new List[]{onlyInLeft, onlyInRight};
  }
  
  
  /**
   * Compares the results from a set of query executors. It uses all the results 
   * from each of the executors, and closes them. 
   */
  public static boolean allEqual(QueryEngine engine, QueryNode... nodes) 
      throws IOException{
    QueryExecutor[] executors = new QueryExecutor[nodes.length];
    File[] files = new File[executors.length];
    BufferedReader[] readers = new BufferedReader[executors.length];
    for(int i = 0; i< executors.length; i++){
      executors[i] = nodes[i].getQueryExecutor(engine);
      files[i] = File.createTempFile("query-" + i, ".txt");
      dumpResultsToFile(executors[i], files[i]);
      readers[i] = new BufferedReader(new FileReader(files[i]));
    }
    //now compare the results
    boolean finished = false;
    boolean equal = true;
    String oldLine = null;
    String line = null;
    while(!finished){
      for(int i = 0; i < readers.length; i++){
        if(i == 0){
          oldLine = readers[i].readLine();
        }else{
          line = readers[i].readLine();
          if(oldLine == null){
            if(line != null){
              finished = true;
              equal = false;
            }
          }else{
            //oldLine not null
            if(line == null){
              finished = true;
              equal = false;              
            }else if(!oldLine.equals(line)){
              finished = true;
              equal = false;
            }
          }
        }
      }
      if(!finished && oldLine == null){
        finished = true;
      }
    }
    //close all resources and delete all the files
    for(int i = 0; i < files.length; i++){
      readers[i].close();
      files[i].delete();
    }
    return equal;
  }
  
  /**
   * Creates a {@link QueryExecutor} for the given {@link QueryNode}, obtains 
   * all the hits from it, represents them string containing document ID, term
   * position and length, sorts all the hit strings, and saves them to a file,
   * one on each line. 
   * @param query
   * @param engine
   * @param file
   * @throws IOException
   */
  public static void dumpResultsToFile(QueryExecutor executor, File file) throws IOException{
    Writer writer = new BufferedWriter(new FileWriter(file));
    writer.write("Doc ID, Position, Length\n");
    List<String> lines = new ArrayList<String>();
    long docId = executor.nextDocument(-1);
    while(docId  != -1){
      Binding aHit = executor.nextHit();
      while(aHit != null){
        lines.add(aHit.getDocumentId() + ", " + 
                + aHit.getTermPosition() + ", " + 
                aHit.getLength());
        aHit = executor.nextHit();
      }
      //we have all the hits on a document
      Collections.sort(lines);
      for(String line : lines){
        writer.write(line);
        writer.write("\n");
      }      
      docId = executor.nextDocument(-1);
    }
    executor.close();
    writer.close();
  }


  /**
   * Creates a textual representation for a diff result.
   * @param diff
   * @param engine
   * @return
   * @throws IOException
   */
  public static String printDiffResults(List<Binding>[] diff, 
          QueryEngine engine) throws IndexException{
    StringBuilder diffStr = new StringBuilder();
    diffStr.append("Only in LEFT Query\n");
    for(Binding aHit : diff[0]){
      diffStr.append("Document " + aHit.getDocumentId() + 
              "(" + aHit.getTermPosition() + ", " + aHit.getLength() + "): ");
      String[][] hitText = engine.getHitText(aHit, 0,0);
      String word = null;
      String nonWord = null;
      for(int i = 0; i < hitText[0].length; i++){
        word = i < hitText[0].length ? hitText[0][i] : "";
        nonWord = i < hitText[1].length ? hitText[1][i] : "";
        diffStr.append(word == null ? "" : word );
        diffStr.append(nonWord == null ? "" : nonWord);
      }
      diffStr.append('\n');
    }
    diffStr.append("<<<<<<<<<<<<<<!>>>>>>>>>>>>>>>>>\n");
    diffStr.append("Only in RIGHT Query\n");
    for(Binding aHit : diff[1]){
      diffStr.append("Document " + aHit.getDocumentId() + 
              "(" + aHit.getTermPosition() + ", " + aHit.getLength() + "): ");
      String[][] hitText = engine.getHitText(aHit, 0,0);
      String word = null;
      String nonWord = null;
      for(int i = 0; i < hitText[0].length; i++){
        word = i < hitText[0].length ? hitText[0][i] : "";
        nonWord = i < hitText[1].length ? hitText[1][i] : "";
        diffStr.append(word == null ? "" : word );
        diffStr.append(nonWord == null ? "" : nonWord);
      }
      diffStr.append('\n');
    }
    return diffStr.toString();
  }
  
  /**
   * Deletes a directory recursively. Use with caution!
   * @param directory the directory to be deleted.
   * @return <code>true</code> if the directory was deleted successfully.
   */
  public static boolean deleteDir(File directory){
    boolean success = true;
    if(directory.isDirectory()){
      File[] files = directory.listFiles();
      for(File aFile : files){
        if(aFile.isFile()){
          success &= aFile.delete();
        }else{
          success &= deleteDir(aFile);
        }
      }
      //now the dir should be empty
      success &= directory.delete();
    }else{
      success = directory.delete();
    }
    return success;
  }
  
}
