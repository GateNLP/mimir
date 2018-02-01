package gate.mimir.test;

import it.unimi.dsi.fastutil.IndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntArrayPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntHeapIndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntIndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntPriorityQueue;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.index.IndexIterator;
import it.unimi.di.big.mg4j.index.IndexReader;
import it.unimi.di.big.mg4j.search.DocumentIterator;
import it.unimi.di.big.mg4j.search.score.BM25FScorer;
import it.unimi.di.big.mg4j.search.score.BM25Scorer;
import it.unimi.di.big.mg4j.search.score.CountScorer;
import it.unimi.di.big.mg4j.search.score.DelegatingScorer;
import it.unimi.di.big.mg4j.search.score.Scorer;
import it.unimi.di.big.mg4j.search.score.TfIdfScorer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.configuration.ConfigurationException;

import gate.Document;
import gate.Factory;
import gate.Gate;
import gate.corpora.DocumentImpl;
import gate.creole.ResourceData;
import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.IndexConfig;
import gate.mimir.MimirIndex;
import gate.mimir.index.AtomicIndex;
import gate.mimir.index.AtomicTokenIndex;
import gate.mimir.index.GATEDocument;
import gate.mimir.index.IndexException;
import gate.mimir.search.IndexReaderPool;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryEngine.IndexType;
import gate.mimir.search.QueryRunner;
import gate.mimir.search.RankingQueryRunnerImpl;
import gate.mimir.search.RemoteQueryRunner;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.parser.QueryParser;
import gate.mimir.search.score.BindingScorer;
import gate.mimir.search.score.DelegatingScoringQueryExecutor;
import gate.mimir.search.score.MimirScorer;
import gate.mimir.search.terms.AndTermsQuery;
import gate.mimir.search.terms.DocumentTermsQuery;
import gate.mimir.search.terms.DocumentsAndTermsQuery;
import gate.mimir.search.terms.DocumentsOrTermsQuery;
import gate.mimir.search.terms.LimitTermsQuery;
import gate.mimir.search.terms.OrTermsQuery;
import gate.mimir.search.terms.SortedTermsQuery;
import gate.mimir.search.terms.TermTypeTermsQuery;
import gate.mimir.search.terms.TermsQuery;
import gate.mimir.search.terms.TermsResultSet;
import gate.mimir.tool.WebUtils;
import gate.mimir.util.IndexUpgrader;
import gate.mimir.util.MG4JTools;
import gate.util.GateException;

public class Scratch {

  public static void main (String[] args) throws Exception {
//    mainIndexConvert(args);
//    mainIndexer5(args);
    
     mainSimple(args);
    
//    mainDirectIndexes(args);
//    mainBuildDirectIndex(args);
//    mainQueryIndex(args);
//    mainRemote(args);
  }
  
  public static final void mainIndexConvert(String[] args) throws Exception {
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
    
    if(args.length != 1) throw new RuntimeException(
        "You need to provide a single commnad line parameter, which should "
        + "be the path to a pre-5.0 Mímir index.");
    File indexDir = new File(args[0]);
    IndexUpgrader indexUpgrader =  new IndexUpgrader();
    indexUpgrader.upgradeIndex(indexDir);
  }
  
  
  /**
   * Interactive tool for querying a MG4J index (e.g. a Mímir sub-index, or a 
   * Mímir sub-index batch).
   * 
   * @param args
   * @throws NoSuchMethodException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws InstantiationException 
   * @throws ClassNotFoundException 
   * @throws URISyntaxException 
   * @throws IOException 
   * @throws SecurityException 
   * @throws ConfigurationException 
   */
  public static void mainQueryIndex(String[] args) throws ConfigurationException, SecurityException, IOException, URISyntaxException, ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    IndexReaderPool termSource = null;
    
    // open the term supplying index
    URI termsIndexUri = new File("/data/mimir-indexes/index-fastvac-1M.mimir/mg4j/mimir-token-2").toURI();
    Index termsIndex = MG4JTools.openMg4jIndex(termsIndexUri);
    termSource = new IndexReaderPool(termsIndex, termsIndexUri);

    
    if(args == null || args.length < 2) {
      System.out.println("Usage:\njava Scratch indexDir indexName...\n" +
      		"where indexDir is a mimir index directory, indexName is the basename of an index file (the file name without any extension).");
      return;
    }
    // open the MG4J index
    URI[] indexURIs = new URI[args.length - 1];
    File mg4jDir = new File(new File(args[0]), "mg4j");
    IndexReaderPool[] readerPools = new IndexReaderPool[args.length - 1];
    for(int i = 0; i < indexURIs.length; i++) {
      indexURIs[i] = new File(mg4jDir, args[i + 1]).toURI();
      Index theIndex = MG4JTools.openMg4jIndex(indexURIs[i]);
      readerPools[i] = new IndexReaderPool(theIndex, indexURIs[i]);      
    }
    
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    System.out.print("Query:");
    String line = in.readLine();
    while(line != null && line.length() > 0) {
      for(int i = 0; i < readerPools.length; i++) {
        System.out.print("From " + indexURIs[i] + ":\n\t");
        
        IndexReader indexReader = readerPools[i].borrowReader();
        try {
          IndexIterator indexIter = indexReader.documents(Long.parseLong(line));
          long docId = indexIter.nextDocument();
          boolean first = true;
          while(docId > 0 && docId != IndexIterator.END_OF_LIST) {
            if(first) first = false;
            else System.out.print(", ");
            System.out.print(Long.toString(docId));
            if(termSource != null) {
              System.out.print("('" + termSource.getTerm(docId) + "')");
            }
            docId = indexIter.nextDocument();
          }
          System.out.println("\n");
        } finally {
          readerPools[i].returnReader(indexReader);
        }
      }
      System.out.print("Query:");
      line = in.readLine();
    }
  }
  
  public static void mainSimple(String[] args) throws Exception {
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
    MimirIndex mainIndex = new MimirIndex(new File(args[0]));
    QueryEngine qEngine = mainIndex.getQueryEngine();
    
//    String query = "electrical";
//    String query = "{Document date > 20070000}";
//    String query = "{Abstract}";
   
    String[] queries = new String[] {"{Mention inst=\"http://dbpedia.org/resource/Sean_Bean\"}"}; //new String[]{"electrical", "the", "{Document date > 20070000}"};
    long start = System.currentTimeMillis();
    NumberFormat nf = NumberFormat.getNumberInstance();
    for(String query : queries) {
      System.out.println("Query: " + query);
      QueryNode qNode = QueryParser.parse(query);
      long startLocal = System.currentTimeMillis();
      QueryExecutor qExecutor = qNode.getQueryExecutor(qEngine);
      long latestDoc = qExecutor.nextDocument(-1);
      int totalHitCount = 0;
      int docCount = 0;
      while(latestDoc >= 0) {
        docCount++;
        int hitCount = 0;
        while(qExecutor.nextHit() != null) hitCount++;
        totalHitCount += hitCount;
        System.out.println("Doc " + latestDoc + ", hits: " + hitCount);
        latestDoc = qExecutor.nextDocument(-1);
      }
      System.out.println("Found " + nf.format(totalHitCount) + " hits in " +
        nf.format(docCount) + " documents, in " +
        nf.format(System.currentTimeMillis() - startLocal) + " ms.\n" +
        "========================================================\n" + 
        "========================================================");
      qExecutor.close();      
    }
    System.out.println("Total time " +
      nf.format(System.currentTimeMillis() - start) + " ms.");
    mainIndex.close();
  }
  
  
  /**
   * Scratch code to exercise the 5.0 indexer framework 
   */
  public static void mainIndexer5(String[] args) throws Exception {
    Gate.setGateHome(new File("gate-home"));
    Gate.setUserConfigFile(new File("gate-home/user-gate.xml"));
    Gate.init();
    // load the tokeniser plugin
    Gate.getCreoleRegister().registerDirectories(new File("gate-home/plugins/ANNIE-tokeniser").toURI().toURL());
    // load the DB plugin
    Gate.getCreoleRegister().registerDirectories(new File("../plugins/db-h2").toURI().toURL());
    // load the measurements plugin
    Gate.getCreoleRegister().registerDirectories(new File("../plugins/measurements").toURI().toURL());
    
    File indexDir = new File(args[0]);
    
    IndexConfig indexConfig = MimirTestUtils.getTestIndexConfig(indexDir,
        Class.forName("gate.mimir.db.DBSemanticAnnotationHelper", 
            true, Gate.getClassLoader()).asSubclass(
        AbstractSemanticAnnotationHelper.class));
    
    
    MimirIndex mainIndex = new MimirIndex(indexConfig);
    mainIndex.setOccurrencesPerBatch(1000000);
    // index some documents
    File zipFile = new File(args[1]);
    String fileURI = zipFile.toURI().toString();
    ZipFile zip = new ZipFile(args[1]);
    Enumeration<? extends ZipEntry> entries = zip.entries();
    
    int copies = 10;
    boolean compress = true;
    ResourceData docRd = Gate.getCreoleRegister().get(DocumentImpl.class.getName());
    while(entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      if(entry.isDirectory()) {
        continue;
      }
      URL url = new URL("jar:" + fileURI + "!/" + entry.getName());
      Document doc = gate.Factory.newDocument(url, "UTF-8");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(doc);
      oos.close();
      Factory.deleteResource(doc);
      byte[] docBytes = baos.toByteArray();
      for(int i = 0;  i < copies; i++) {
        doc = (Document) new ObjectInputStream(new ByteArrayInputStream(docBytes)).readObject();
        docRd.addInstantiation(doc);
        mainIndex.indexDocument(doc);
      }
    }
    
    if(compress){
      mainIndex.requestSyncToDisk();
      mainIndex.requestCompactIndex();
    }
    mainIndex.compactDocumentCollection();
    mainIndex.close();
  }
  
  /**
   * Scratch code to exercise the 5.0 indexer framework 
   */
  public static void mainAtomicTokenIndexer5(String[] args) throws Exception {
    Gate.setGateHome(new File("gate-home"));
    Gate.setUserConfigFile(new File("gate-home/user-gate.xml"));
    Gate.init();
    // load the tokeniser plugin
    Gate.getCreoleRegister().registerDirectories(new File("gate-home/plugins/ANNIE-tokeniser").toURI().toURL());
    // load the DB plugin
    Gate.getCreoleRegister().registerDirectories(new File("../plugins/db-h2").toURI().toURL());
    // load the measurements plugin
    Gate.getCreoleRegister().registerDirectories(new File("../plugins/measurements").toURI().toURL());
    
    File indexDir = new File(args[0]);
    
    IndexConfig indexConfig = MimirTestUtils.getTestIndexConfig(indexDir,
        Class.forName("gate.mimir.db.DBSemanticAnnotationHelper", 
            true, Gate.getClassLoader()).asSubclass(
        AbstractSemanticAnnotationHelper.class));
    
    MimirIndex mainIndex = new MimirIndex(indexDir);
    
    // build a token indexer
    BlockingQueue<GATEDocument> inputQueue = new LinkedBlockingQueue<GATEDocument>();
    BlockingQueue<GATEDocument> outputQueue = new LinkedBlockingQueue<GATEDocument>();
    AtomicTokenIndex ati = new AtomicTokenIndex(mainIndex, "tokens-0", false, 
        inputQueue, outputQueue, 
        indexConfig.getTokenIndexers()[0], 
        false);
    
    File zipFile = new File(args[1]);
    String fileURI = zipFile.toURI().toString();
    ZipFile zip = new ZipFile(args[1]);
    Enumeration<? extends ZipEntry> entries = zip.entries();
    
    int copies = 100;
    while(entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      if(entry.isDirectory()) {
        continue;
      }
      URL url = new URL("jar:" + fileURI + "!/" + entry.getName());
      Document doc = gate.Factory.newDocument(url, "UTF-8");
      for(int i = 0;  i < copies; i++) {
        GATEDocument gateDoc = new GATEDocument(doc, indexConfig);
        inputQueue.put(gateDoc);
      }
      // now let's do some searches
      Index index = ati.getIndex();
      int resDocs = 0;
      if(index != null) {
        IndexIterator indexIter = index.getReader().documents("patent");
        long docId = indexIter.nextDocument();
        while(docId != DocumentIterator.END_OF_LIST) {
          resDocs ++;
          docId = indexIter.nextDocument();
        }
      }
      System.out.println("=================================\n" + 
          "Matched " + resDocs + " documents.\n" +
          "=================================\n");
    }

    // compress the index
    ati.requestCompactIndex();
    System.out.println("=================================\n" + 
        "Compressing index.\n" +
        "=================================\n");
    Thread.sleep(5000);
    
    // and search one last time
    Index index = ati.getIndex();
    int resDocs = 0;
    if(index != null) {
      IndexIterator indexIter = index.getReader().documents("patent");
      long docId = indexIter.nextDocument();
      while(docId != DocumentIterator.END_OF_LIST) {
        resDocs ++;
        docId = indexIter.nextDocument();
      }
    }
    System.out.println("=================================\n" + 
        "Matched " + resDocs + " documents.\n" +
        "=================================\n");    
    
    ati.close();
  }
  
  /**
   * Version that exercises the scorers 
   * @param args
   */
  public static void mainScorers(String[] args) throws Exception {
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
    // load the SPARQL plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/sparql").toURI().toURL());
    
    QueryEngine qEngine = new MimirIndex(new File(args[0])).getQueryEngine();
    qEngine.setScorerSource(new Callable<MimirScorer>() {
      @Override
      public MimirScorer call() throws Exception {
        return new BindingScorer(2, 0.9);
        // return new DelegatingScoringQueryExecutor(new TfIdfScorer());
        // return new DelegatingScoringQueryExecutor(new CountScorer());
        // return new DelegatingScoringQueryExecutor(new BM25Scorer());
      }
    });
    BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
    String query = null;
    do {
      try{
        System.out.print("? ");
        query = input.readLine();
        long start = System.currentTimeMillis();
        if(query == null || query.trim().length() == 0) break;
        QueryRunner qRunner = qEngine.getQueryRunner(query);
        
        while(qRunner.getDocumentsCount() < 0) {
          Thread.sleep(100);
        }
        double minScore = Double.MAX_VALUE;
        double maxScore = Double.MIN_VALUE;
        long docCount = qRunner.getDocumentsCount();
        for(int i = 0;  i < docCount; i++) {
          double score = qRunner.getDocumentScore(i);
          if(score < minScore) minScore = score;
          if(score > maxScore) maxScore = score;
          // exercise the runner
          qRunner.getDocumentID(i);
          qRunner.getDocumentTitle(i);
          qRunner.getDocumentURI(i);
          qRunner.getDocumentHits(i);
        }
        
        System.out.println(String.format(
          "Matched %d documents, scores %02.4f - %02.4f, in %02.2f seconds", 
          docCount, minScore, maxScore, 
          (double)(System.currentTimeMillis() - start)/1000));
        qRunner.close();
      }catch(Exception e) {
        e.printStackTrace(System.err);
      }
    } while (query != null);
    qEngine.close();
  }  
  
  /**
   * Main version for testing direct indexes
   * @param args
   * @throws Exception sometimes 
   */
  public static void mainDirectIndexes(String[] args) throws Exception {
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
    
    
    MimirIndex mainIndex = new MimirIndex(new File(args[0]));
    QueryEngine qEngine = mainIndex.getQueryEngine();
    
    TermsQuery query = null;
    
//    query = new DocumentTermsQuery("root", IndexType.TOKENS, true, true, 0);
//    printTermQuery(query, qEngine);
//    System.out.println("\n=======================================");
//    query = new DocumentTermsQuery("root", IndexType.TOKENS, 
//      true, true, DocumentTermsQuery.NO_LIMIT, 1);
//    printTermQuery(query, qEngine);
//    System.out.println("\n=======================================");
    
//    query = new DocumentsOrTermsQuery("root", IndexType.TOKENS, 
//      true, true, TermsQuery.NO_LIMIT, 0, 1);
//    printTermQuery(query, qEngine);
//    System.out.println("\n=======================================");
    
//    TermsQuery q1 = new DocumentTermsQuery("root", IndexType.TOKENS, 
//        true, true, TermsQuery.NO_LIMIT, 0);
//    TermsQuery q2 = new DocumentTermsQuery("root", IndexType.TOKENS, 
//      true, true, TermsQuery.NO_LIMIT, 1);
//    query = new OrTermsQuery(true, true, TermsQuery.NO_LIMIT, q1, q2);
//    
//    query = new LimitTermsQuery(new SortedTermsQuery(query), 100);
    
//    query = new LimitTermsQuery(
//      new SortedTermsQuery(
//      new DocumentsOrTermsQuery("root", IndexType.TOKENS, true, false, 0, 1, 2))
//      , 100);
//    printTermQuery(query, qEngine);
//    
//    System.out.println("\n=======================================");
    
    query = new LimitTermsQuery(
        new SortedTermsQuery(
//        new TermTypeTermsQuery("root", IndexType.TOKENS))
            new DocumentTermsQuery("root", IndexType.TOKENS, true, true, 1)
        ), 100);
      printTermQuery(query, qEngine);
      
      System.out.println("\n=======================================");
      
    
    mainIndex.close();
  }
  
  /**
   * Scratch code for using the remote query runner
   * @param args 2 string: index URL and query
   * @throws Exception
   */
  public static void mainRemote(String[] args) throws Exception {
    if(args.length != 2) {
      System.out.println("Usage: Scratch indexUrl queryString");
      return;
    }
    RemoteQueryRunner rqr = new RemoteQueryRunner(args[0], args[1], null, new WebUtils());
    long docCount = rqr.getDocumentsCount();
    while(docCount < 0) {
      System.out.println("Working (found " + rqr.getDocumentsCurrentCount() + 
        " documents so far)");
      Thread.sleep(1000);
      docCount = rqr.getDocumentsCount();
    }
    System.out.println("Search complete; found: " + docCount + " documents.");
  }
  
  private static NumberFormat nf = NumberFormat.getNumberInstance();

  private static void printTermQuery(TermsQuery query, QueryEngine qEngine) throws IOException {

    long start = System.currentTimeMillis();
    TermsResultSet res = query.execute(qEngine);
    for(int  i = 0; i < res.termStrings.length; i++) {
      System.out.print("\"" + res.termStrings[i] + "\"\t");
      if(res.termLengths != null) {
        System.out.print("len:" + res.termLengths[i] + "\t");
      }
      if(res.termCounts != null) {
        System.out.print("cnt:" + res.termCounts[i]);
      }
      System.out.println();
    }
    
    System.out.println("Found " + nf.format(res.termStrings.length)
        + " hits in " + 
        nf.format(System.currentTimeMillis() - start) + " ms.");
  }
  
}
