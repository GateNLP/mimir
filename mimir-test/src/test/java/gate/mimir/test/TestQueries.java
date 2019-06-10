/*
 *  TestQueries.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id: TestQueries.java 17484 2014-02-27 17:30:57Z valyt $
 */
package gate.mimir.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import gate.Document;
import gate.Gate;
import gate.creole.Plugin;
import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.IndexConfig;
import gate.mimir.MimirIndex;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.IndexException;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.query.AndQuery;
import gate.mimir.search.query.AnnotationQuery;
import gate.mimir.search.query.Binding;
import gate.mimir.search.query.GapQuery;
import gate.mimir.search.query.OrQuery;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.RepeatsQuery;
import gate.mimir.search.query.SequenceQuery;
import gate.mimir.search.query.TermQuery;
import gate.mimir.search.query.WithinQuery;
import gate.mimir.search.query.parser.ParseException;
import gate.mimir.search.query.parser.QueryParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A class with tests for the various Mimir query operators.
 */
public class TestQueries {
  
  private static final String resultsPath = "target/query-results";
  private static final String NEW_LINE = System.getProperty("line.separator");

  private static Logger logger = Logger.getLogger(TestQueries.class.getName());
  
  
  public static final String[] helperTypes = System.getProperty(
    "helpers.to.test", "gate.mimir.db.DBSemanticAnnotationHelper").split(
      "\\s*,\\s*");
  
  /**
   * The indexes being tested against
   */
  private static File[] indexDirs;
  
  
  /**
   * Prepares the QueryEngine used by all tests.
   */
  @BeforeClass
  public static void oneTimeSetUp() throws Exception {
    Gate.init();
    // load the tokeniser plugin
    Gate.getCreoleRegister().registerPlugin(new Plugin.Maven("uk.ac.gate.plugins", "annie", "8.6"));
    // load the DB plugin
    Gate.getCreoleRegister().registerPlugin(new Plugin.Maven("uk.ac.gate.mimir", "mimir-plugin-dbh2", "6.1"));
    // load the measurements plugin
    Gate.getCreoleRegister().registerPlugin(new Plugin.Maven("uk.ac.gate.mimir", "mimir-plugin-measurements", "6.1"));
    
    
    indexDirs = new File[helperTypes.length];
    for(int i = 0; i < helperTypes.length; i++) {
      indexDirs[i] = File.createTempFile("mimir-index", null);
      indexDirs[i].delete();
      IndexConfig indexConfig = MimirTestUtils.getTestIndexConfig(indexDirs[i], 
        Class.forName(helperTypes[i], true, Gate.getClassLoader()).asSubclass(
          AbstractSemanticAnnotationHelper.class));
      
      // now start indexing the documents
      MimirIndex index = new MimirIndex(indexConfig);
      String pathToZipFile = "src/test/resources/gatexml-output.zip";
      File zipFile = new File(pathToZipFile);
      String fileURI = zipFile.toURI().toString();
      ZipFile zip = new ZipFile(pathToZipFile);
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while(entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if(entry.isDirectory()) {
          continue;
        }
        URL url = new URL("jar:" + fileURI + "!/" + entry.getName());
        Document doc = gate.Factory.newDocument(url, "UTF-8");
        index.indexDocument(doc);
      }
      index.close();      
    }
  }

  /**
   * Closes the shared QueryEngine.
   */
  @AfterClass
  public static void oneTimeTearDown() {
    boolean keepIndex = Boolean.parseBoolean(
      System.getProperty("keepTestIndex", "false"));
    if(!keepIndex) {
      for(File indexDir : indexDirs) {
        // recursively delete index dir
        if(!MimirTestUtils.deleteDir(indexDir)) {
          System.err.println("Could not delete index directory " + indexDir);
        }      
      }      
    }
  }

  /**
   * Executes two sequence queries, one with gaps and one without and checks
   * that the results of one are included in the other one.
   * 
   * @throws IOException
   * @throws IndexException 
   */
  @Test
  public void testSequenceQueryGaps() throws IOException, IndexException {
    for(File indexDir : indexDirs) {
      QueryEngine engine = new MimirIndex(indexDir).getQueryEngine();
      TermQuery tq1 = new TermQuery("string", "up");
      TermQuery tq2 = new TermQuery("string", "to");
      TermQuery tq3 = new TermQuery("string", "the");
      SequenceQuery sQuery = new SequenceQuery(null, tq1, tq2, tq3);
      SequenceQuery sQueryGaps =
              new SequenceQuery(new SequenceQuery.Gap[]{SequenceQuery
                      .getGap(1, 1)}, tq1, tq3);
      List<Binding>[] diff = MimirTestUtils.calculateDiff(sQuery, sQueryGaps, engine);
      // second query is more permissive than first
      assertNotNull("The two queries returned the same result set!", diff);
      assertTrue("The non gaps query has results not included in the gaps one!",
              diff[0].isEmpty());
      assertTrue("The gaps query returned no additional hits!",
              diff[1].size() > 0);
      engine.close();
    }
  }

  /**
   * Executes two equivalent queries using {@link RepeatsQuery} and
   * {@link OrQuery} and compares the results.
   * 
   * @throws IndexException
   * @throws IOException
   */
  @Test
  public void testRepeatsAndOrQueries() throws IndexException, IOException {
    for(File indexDir : indexDirs) {
      QueryEngine engine = new MimirIndex(indexDir).getQueryEngine();;
      Map<String, String> empty = Collections.emptyMap();
      AnnotationQuery annQuery = new AnnotationQuery("Measurement", empty);
      RepeatsQuery rQuery = new RepeatsQuery(annQuery, 1, 3);
      OrQuery orQuery =
              new OrQuery(annQuery, new SequenceQuery(null, annQuery, annQuery),
                      new SequenceQuery(null, annQuery, annQuery, annQuery));
      List<Binding>[] diff = MimirTestUtils.calculateDiff(rQuery, orQuery, engine);
      if(diff != null) {
        System.err.println(MimirTestUtils.printDiffResults(diff, engine));
      }
      assertNull("Repeats query result different from equivalent OR query. "
              + "See system.err for details!", diff);      
      engine.close();
    }
  }

  /**
   * Executes two equivalent queries using {@link SequenceQuery} and
   * {@link RepeatsQuery} and compares the results.
   * 
   * @throws IndexException
   * @throws IOException
   */
  @Test
  public void testSequenceAndRepeatsQueries() throws IndexException,
          IOException {
    for(File indexDir : indexDirs) {
      QueryEngine engine = new MimirIndex(indexDir).getQueryEngine();;
      Map<String, String> empty = Collections.emptyMap();
      AnnotationQuery annQuery = new AnnotationQuery("Measurement", empty);
      SequenceQuery sQuery =
              new SequenceQuery(null, annQuery, annQuery, annQuery);
      RepeatsQuery rQuery = new RepeatsQuery(annQuery, 3, 3);
      List<Binding>[] diff = MimirTestUtils.calculateDiff(sQuery, rQuery, engine);
      if(diff != null) {
        System.err.println(MimirTestUtils.printDiffResults(diff, engine));
      }
      assertNull("Repeats query result different from equivalent OR query. "
              + "See system.err for details!", diff);
      engine.close();
    }
  }

  /**
   * Executes three equivalent queries using different gap implementations
   * coming from {@link SequenceQuery}, {@link TermQuery} and {@link GapQuery}
   * and compares the results.
   * 
   * @throws IndexException
   * @throws IOException
   * @throws IOException
   */
  @Test
  public void testGapImplementations() throws IndexException, IOException {
    for(File indexDir : indexDirs) {
      QueryEngine engine = new MimirIndex(indexDir).getQueryEngine();;    
      TermQuery tq1 = new TermQuery("string", "up");
      TermQuery tq3 = new TermQuery("root", "the");
      SequenceQuery sQuery1 =
              new SequenceQuery(new SequenceQuery.Gap[]{SequenceQuery
                      .getGap(1, 1)}, tq1, tq3);
      TermQuery tq1Gap = new TermQuery(QueryEngine.IndexType.TOKENS, "string", "up", 2);
      SequenceQuery sQuery2 = new SequenceQuery(null, tq1Gap, tq3);
      GapQuery gQ1 = new GapQuery(tq1, 1);
      SequenceQuery sQuery3 = new SequenceQuery(null, gQ1, tq3);
      assertTrue("Not all results are the same!", MimirTestUtils.allEqual(engine,
              sQuery1, sQuery2, sQuery3));
      engine.close();
    }
  }

  /**
   * Tests the functionality of the result set diff algorithm in
   * {@link MimirTestUtils}.
   * 
   * @throws IOException
   * @throws IndexException
   */
  @Test
  public void testDiffer() throws IOException, IndexException {
    File indexDir = indexDirs[0];
    QueryEngine engine = new MimirIndex(indexDir).getQueryEngine();;
    String[] terms = new String[]{"up", "to", "the"};
    TermQuery[] tqs = new TermQuery[terms.length];
    for(int i = 0; i < terms.length; i++) {
      tqs[i] = new TermQuery("string", terms[i]);
    }
    SequenceQuery seqQuery = new SequenceQuery(null, tqs);
    List<Binding>[] res = MimirTestUtils.calculateDiff(seqQuery, seqQuery, engine);
    assertNull("Different results from the same query!", res);
    engine.close();
  }
  
  @Test
  public void annotationQuery() throws IndexException, IOException {
    String[] qResNames = new String[indexDirs.length]; 
    for(int  i = 0; i <  indexDirs.length; i++) {
      QueryEngine engine = new MimirIndex(indexDirs[i]).getQueryEngine();;
      Map<String, String> constraints = new HashMap<String, String>();
      constraints.put("spec", "1 to 32 degF");
      AnnotationQuery annQuery = new AnnotationQuery("Measurement", constraints);
      qResNames[i] = "annotation-" + i;  
      performQuery(qResNames[i], annQuery, engine);
      engine.close();
    }
    if(qResNames.length > 1) {
      assertTrue("Got different results from different helpers", identical(qResNames));
    }
  }
  
  @Test
  public void testStringSequenceQuery() throws IndexException, IOException {
    String[] qResNames = new String[indexDirs.length]; 
    for(int  i = 0; i <  indexDirs.length; i++) {
      QueryEngine engine = new MimirIndex(indexDirs[i]).getQueryEngine();
      String[] terms = new String[] {"up", "to", "the"};
      // String[] terms = new String[]{"ability", "of", /*"the", "agent",*/ "to",
      // "form", "an", "acid", "or", "base", "upon", "heating", "whereby",
      // "dehydrating", "cellulose", "at", "a", "low", "temperature",
      // "within", "a", "short", "period", "to", "yield", "water", "and",
      // "carbon"};
      TermQuery[] termQueries = new TermQuery[terms.length];
      for(int j = 0; j < terms.length; j++) {
        termQueries[j] = new TermQuery("string", terms[j]);
      }
      SequenceQuery.Gap[] gaps = new SequenceQuery.Gap[28];
      gaps[1] = SequenceQuery.getGap(2, 3);
      SequenceQuery sequenceQuery = new SequenceQuery(null/* gaps */, termQueries);
      qResNames[i] = "termSequence-" + i; 
      performQuery(qResNames[i], sequenceQuery, engine);
      engine.close();
    }
    if(qResNames.length > 1) {
      assertTrue("Got different results from different helpers", identical(qResNames));
    }
  }
  
  @Test
  public void testCategorySequenceQuery() throws IndexException, IOException {
    String[] qResNames = new String[indexDirs.length]; 
    for(int  i = 0; i <  indexDirs.length; i++) {
      QueryEngine engine = new MimirIndex(indexDirs[i]).getQueryEngine();
      String[] terms = new String[]{"NN", "NN", "NN"};
      TermQuery[] termQueries = new TermQuery[terms.length];
      for(int j = 0; j < terms.length; j++) {
        termQueries[j] = new TermQuery("category", terms[j]);
      }
      SequenceQuery.Gap[] gaps = new SequenceQuery.Gap[28];
      gaps[1] = SequenceQuery.getGap(2, 3);
      SequenceQuery sequenceQuery = new SequenceQuery(null/* gaps */, termQueries);
      qResNames[i] = "categorySequence-" + i;
      performQuery(qResNames[i], sequenceQuery, engine);
      engine.close();
    }
    if(qResNames.length > 1) {
      assertTrue("Got different results from different helpers", identical(qResNames));
    }
  }
  
  @Test
  public void testAnnotationSequenceQuery() throws IndexException, IOException {
    String[] qResNames = new String[indexDirs.length]; 
    for(int  i = 0; i <  indexDirs.length; i++) {
      QueryEngine engine = new MimirIndex(indexDirs[i]).getQueryEngine();
      Map<String, String> empty = Collections.emptyMap();
      AnnotationQuery annQuery = new AnnotationQuery("Measurement", empty);
      SequenceQuery sequenceQuery = new SequenceQuery(null/* gaps */, annQuery, annQuery, annQuery);
      qResNames[i] = "annotationSequence-" + i;
      performQuery(qResNames[i], sequenceQuery, engine);
      engine.close();
    }
    if(qResNames.length > 1) {
      assertTrue("Got different results from different helpers", identical(qResNames));
    }    
  }
  
  @Test
  public void testRepeatsQuery() throws IndexException, IOException {
    String[] qResNames = new String[indexDirs.length];
    for(int  i = 0; i <  indexDirs.length; i++) {
      QueryEngine engine = new MimirIndex(indexDirs[i]).getQueryEngine();
      Map<String, String> empty = Collections.emptyMap();
      AnnotationQuery annQuery = new AnnotationQuery("Measurement", empty);
      RepeatsQuery repeatsQuery = new RepeatsQuery(annQuery, 3, 3);
      qResNames[i] = "repeats-" + i; 
      performQuery(qResNames[i], repeatsQuery, engine);
      engine.close();
    }
    if(qResNames.length > 1) {
      assertTrue("Got different results from different helpers", identical(qResNames));
    }    
  }
  
  @Test
  public void testWithinQuery() throws IndexException, IOException {
    String[] qResNames = new String[indexDirs.length];
    for(int  i = 0; i <  indexDirs.length; i++) {
      QueryEngine engine = new MimirIndex(indexDirs[i]).getQueryEngine();
      AnnotationQuery intervalQuery = new AnnotationQuery("Measurement", Collections.singletonMap("type", "interval"));
      TermQuery toQuery = new TermQuery("string", "to");
      WithinQuery withinQuery = new WithinQuery(toQuery, intervalQuery);
      qResNames[i] = "within-" + i; 
      performQuery(qResNames[i], withinQuery, engine);
      engine.close();
    }
    if(qResNames.length > 1) {
      assertTrue("Got different results from different helpers", identical(qResNames));
    }    
  }
  
  @Test
  public void testInAndQuery() throws IndexException, IOException {
    String[] qResNames = new String[indexDirs.length];
    for(int  i = 0; i <  indexDirs.length; i++) {
      QueryEngine engine = new MimirIndex(indexDirs[i]).getQueryEngine();
      QueryNode inAndQuery = new WithinQuery(new AndQuery(new TermQuery(null, "London"),
                new TermQuery(null, "press")), new AnnotationQuery(
                "Reference", new HashMap<String, String>()));
      qResNames[i] = "inAnd-" + i; 
      performQuery(qResNames[i], inAndQuery, engine);
      engine.close();
    }
    if(qResNames.length > 1) {
      assertTrue("Got different results from different helpers", identical(qResNames));
    }        
  }
  
  @Test
  public void testMeasurementSpecQuery() throws IndexException, IOException {
    String[] qResNames = new String[indexDirs.length];
    for(int  i = 0; i <  indexDirs.length; i++) {
      QueryEngine engine = new MimirIndex(indexDirs[i]).getQueryEngine();
      AnnotationQuery specQuery = new AnnotationQuery("Measurement", Collections.singletonMap("spec", "5 cm"));
      qResNames[i] = "measurementSpec-" + i; 
      performQuery(qResNames[i], specQuery, engine);
      engine.close();
    }
    if(qResNames.length > 1) {
      assertTrue("Got different results from different helpers", identical(qResNames));
    }            
  }
  
  @Test
  public void testQueryEngineRenderDocument() throws IndexException, IOException {
    for(File indexDir : indexDirs) {
      QueryEngine engine = new MimirIndex(indexDir).getQueryEngine();
      List<Binding> hits = new ArrayList<Binding>();
      hits.add(new Binding(null, 0, 100, 5, null));
      hits.add(new Binding(null, 0, 110, 4, null));
      try {
        engine.renderDocument(0, hits, new FileWriter(resultsPath + "/renderDocumentResult.txt"));
      } catch(Exception e) {
        fail(e.getMessage());
      }
      engine.close();
    }
  }
  
  /**
   * Test the semantic annotation helpers used for indexing document features
   * @throws IndexException
   * @throws ParseException 
   * @throws IOException 
   */
  @Test
  public void testDocumentMode() throws IndexException, ParseException, IOException {
    String[] qResNames = new String[indexDirs.length];
    String[] qResNames2 = new String[indexDirs.length];
    for(int  i = 0; i <  indexDirs.length; i++) {
      QueryEngine engine = new MimirIndex(indexDirs[i]).getQueryEngine();
      QueryNode qNode = QueryParser.parse("{Document}");
      qResNames[i] = "doc-" + i;
      int hits = performQuery(qResNames[i], qNode, engine);
      
      qNode = QueryParser.parse("{Document date > 20070000}");
      qResNames2[i] = "docFeats-" + i;
      int hits2 = performQuery(qResNames2[i], qNode, engine);
      assertTrue("Feature filtering does not reduce the result set!", 
        hits2 < hits);
      engine.close();
    }
    if(qResNames.length > 1) {
      assertTrue("Got different results from different helpers", identical(qResNames));
      assertTrue("Got different results from different helpers", identical(qResNames2));
    }
  } 
  
  private int performQuery(String name, QueryNode query, QueryEngine engine) {
    QueryExecutor executor = null;
    int hitCount = 0;
    BufferedWriter writer = null;
    try {
      File resultsDirectory = new File(resultsPath);
      if (!resultsDirectory.exists()) resultsDirectory.mkdirs();
      executor = query.getQueryExecutor(engine);
      
      writer = new BufferedWriter(new FileWriter(resultsPath + "/" + name + "QueryResult.xml"));
      writer.write("<query query=\"" + query.toString() + "\">");
      writer.newLine();
      writer.write("\t<hits>");
      writer.newLine();
      
      while (executor.nextDocument(-1) != -1) {
        Binding hit = executor.nextHit();
        while(hit != null) {
          hitCount++;
          writer.write("\t\t<hit number=\"" + hitCount + "\">");
          writer.write(getHitString(hit, engine));
          writer.write("</hit>\n");  
          hit = executor.nextHit();
        }
      }
      writer.write("\t</hits>");
      writer.newLine();
      writer.write("</query>");
      writer.newLine();
      writer.flush();
    } catch(Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    } finally {
      try {
        if (writer != null) writer.close();
        if(executor != null) executor.close();
      } catch(Exception e) {
        fail(e.getMessage());
      }
    }
    return hitCount;
  }

  /**
   * Compares the files resulting from the execution of two or more queries. 
   * The queries must have been performed previously, by calling 
   * {@link #performQuery(String, QueryNode, QueryEngine)}.
   * @param queryNames the names of the queries to compare.
   * @return
   * @throws IOException 
   */
  private static boolean identical(String... queryNames) throws IOException {
    BufferedReader[] readers = new BufferedReader[queryNames.length];
    for(int i = 0; i < queryNames.length; i++) {
      readers[i] = new BufferedReader(new FileReader(resultsPath + "/" + 
          queryNames[i] + "QueryResult.xml"));
    }
    String line = null;
    do {
      line = readers[0].readLine();
      for(int  i = 1; i < readers.length; i++) {
        String anotherLine = readers[i].readLine();
        if(line != null) {
          if(anotherLine == null || !line.equals(anotherLine)){
            logger.warning("Assersion error: result sets not identical. " +
            		"First difference:\n Line (0):" + line + 
            		"\nLine (" + i + "):" + (anotherLine == null ? "null" : anotherLine));
            return false;
          }
        } else {
          if(anotherLine != null){
            logger.warning("Assersion error: result sets not identical. " +
                    "First difference:\n Line (0): null" + line + 
                    "\nLine (" + i + "):" + anotherLine);               
            return false;
          }
        }
      }
    } while(line != null);
    return true;
  }
  
  private String getHitString(Binding hit, QueryEngine searcher) throws IndexException
  {
    StringBuilder sb = new StringBuilder();
    String[][] text = searcher.getLeftContext(hit, 2);
    appendHitText(hit, text, sb);
    text = searcher.getHitText(hit);
    appendHitText(hit, text, sb);
    text = searcher.getRightContext(hit, 2);
    appendHitText(hit, text, sb);
    return sb.toString().replace(NEW_LINE, " ");
  }

  private void appendHitText(Binding hit, String[][] text, StringBuilder sb)
  {
    int length = Math.min(text[0].length, text[1].length);
    for (int i = 0; i < length; ++i)
    {
      final String token = text[0][i];
      final String space = text[1][i];
      sb.append(token != null ? token : "").append(space != null ? space : " ");
    }
  }
  
}
