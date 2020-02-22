/*
 *  DBSemanticAnnotationHelper.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *  
 *  Valentin Tablan, 08 Feb 2011
 *   
 *  $Id: DBSemanticAnnotationHelper.java 19452 2016-07-05 16:48:40Z ian_roberts $
 */
package gate.mimir.db;

import gate.Annotation;
import gate.Document;
import gate.FeatureMap;
import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.Constraint;
import gate.mimir.ConstraintType;
import gate.mimir.IndexConfig;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.AtomicAnnotationIndex;
import gate.mimir.index.Mention;
import gate.mimir.search.QueryEngine;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.log4j.Logger;

/**
 * A Semantic annotation helper that uses an embedded RDBMS for storing 
 * annotation data. 
 */
public class DBSemanticAnnotationHelper extends AbstractSemanticAnnotationHelper{

  /**
   * A callable that generates Level 1 IDs given a set of features.
   */
  protected class Level1IdGenerator implements Callable<Long> {
    
    public Level1IdGenerator(FeatureMap features) {
      this.features = features;
    }

    protected FeatureMap features;
    
    /**
     * Retrieves the level1 ID for the given set of features. If no ID can be 
     * found (i.e. this combination of features has not been seen before), it 
     * inserts a new row in the level 1 table, and returns the ID for it.  
     * @see java.util.concurrent.Callable#call()
     * @return the ID, either looked up in the DB or freshly generated.
     */
    @Override
    public Long call() throws Exception {
      setStatementParameters(level1SelectStmt, features);
      ResultSet res = level1SelectStmt.executeQuery();
      if(!res.next()) {
        // no results found -> insert the new row
        setStatementParameters(level1InsertStmt, features);
        if(level1InsertStmt.executeUpdate() != 1) {
          // the update failed
          throw new RuntimeException("Error while inserting into database. Annotation was lost!");
        }
        res = level1InsertStmt.getGeneratedKeys();
        if(!res.next()) throw new RuntimeException(
          "Could not insert new Level 1 row for features: " + features);
      }
      
      // we have found the level 1 ID
      Long level1id = res.getLong(1);
      // sanity check
      if(res.next()) throw new RuntimeException(
              "Multiple Unique IDs foud in Level 1 table for features: " + 
              features.toString());      
      return level1id;
    }
  }
  
  /**
   * A callable that generates Level 2 IDs given a set of features.
   */
  protected class Level2IdGenerator implements Callable<Long> {
    
    private Long level1Id;
    
    public Level2IdGenerator(Long level1Id, FeatureMap features) {
      this.level1Id = level1Id;
      this.features = features;
    }

    protected FeatureMap features;
    
    /**
     * Retrieves the level2 ID for the given set of features. If no ID can be 
     * found (i.e. this combination of features has not been seen before), it 
     * inserts a new row in the level 2 table, and returns the ID for it.  
     * @see java.util.concurrent.Callable#call()
     * @return the ID, either looked up in the DB or freshly generated.
     */
    @Override
    public Long call() throws Exception {
      level2SelectStmt.setLong(1, level1Id);
      setStatementParameters(level2SelectStmt, features);
      ResultSet res = level2SelectStmt.executeQuery();
      if(!res.next()) {
        // no results -> insert new row
        level2InsertStmt.setLong(1, level1Id);
        setStatementParameters(level2InsertStmt, features);
        if(level2InsertStmt.executeUpdate() != 1) {
          // the update failed
          throw new RuntimeException(
            "Could not insert new Level 2 row for Level 1 ID: \"" + level1Id + 
            "\" and features: " + features);
        }
        res = level2InsertStmt.getGeneratedKeys();
        if(!res.next()) throw new RuntimeException(
          "Could not insert new Level 2 row for Level 1 ID: \"" + level1Id + 
          "\" and features: " + features);
      }
      
      // we have found the level 2 ID
      Long level2Id = res.getLong(1);
      // sanity check
      if(res.next()) {
        throw new RuntimeException(
          "Multiple Unique IDs found in Level 2 table for  Level 1 ID: \"" + 
          level1Id + "\" and features: " + features);
      }
      return level2Id;
    }
  }
  
  /**
   * A callable that generates Level 3 IDs (i.e. mention IDs) given a Level 1 
   * ID and/or a Level 2 ID, and a mention length.
   */
  protected class Level3IdGenerator implements Callable<Long> {

    private Long level1Id;
    
    private Long level2Id;
    
    private int mentionLength;
    
    public Level3IdGenerator(Long level1Id, Long level2Id, int mentionLength) {
      super();
      this.level1Id = level1Id;
      this.level2Id = level2Id;
      this.mentionLength = mentionLength;
    }


    /* (non-Javadoc)
     * @see java.util.concurrent.Callable#call()
     */
    @Override
    public Long call() throws Exception {
      mentionsSelectStmt.setLong(1, level1Id);
      if(level2Used) {
        if(level2Id != null) {
          mentionsSelectStmt.setLong(2, level2Id);  
        } else {
          mentionsSelectStmt.setNull(2, Types.BIGINT);
        }
        mentionsSelectStmt.setInt(3, mentionLength);        
      } else {
        mentionsSelectStmt.setInt(2, mentionLength);
      }

      ResultSet res = mentionsSelectStmt.executeQuery(); 
      if(!res.next()) {
        // no results -> insert new row
        mentionsInsertStmt.setLong(1, level1Id);
        if(level2Used) {
          if(level2Id != null) {
            mentionsInsertStmt.setLong(2, level2Id);  
          } else {
            mentionsInsertStmt.setNull(2, Types.BIGINT);
          } 
          mentionsInsertStmt.setInt(3, mentionLength);          
        } else {
          mentionsInsertStmt.setInt(2, mentionLength);          
        }

        if(mentionsInsertStmt.executeUpdate() != 1) {
          // the update failed
          throw new RuntimeException(
            "Could not insert new mention ID for Level 1 ID: " + level1Id + 
            ", Level 2 ID: " + level2Id + ", and mention length: " + 
            mentionLength);
        }
        res = mentionsInsertStmt.getGeneratedKeys();
        if(!res.next()) {
          throw new RuntimeException(
            "Could not insert new mention ID for Level 1 ID: " + level1Id + 
            ", Level 2 ID: " + level2Id + ", and mention length: " + 
            mentionLength);
        }
      }
      
      // we have found the level 3 (mention) ID
      Long mentionId = res.getLong(1);
      // sanity check
      if(res.next()){ 
        throw new RuntimeException(
            "Multiple Unique IDs foud in mentions table for  Level 1 ID: " + 
             level1Id +  ", Level 2 ID: " + level2Id + 
             ", and mention length: " + mentionLength);
      }
      return mentionId;
    }
  }
  
  private static final long serialVersionUID = 2734946594117068194L;

  /**
   * Empty array to return when there are no mention URIs.
   */
  private static final String[] EMPTY_STRING_ARRAY = new String[0];

  /**
   * The directory name for the database data (relative to the top level index
   * directory).
   */
  public static final String DB_DIR_NAME = "db";

  /**
   * Key used to retrieve the {@link List} of table base names (see 
   * {@link #tableBaseName}) from the {@link IndexConfig#getContext()} context.
   */
  public static final String DB_NAMES_CONTEXT_KEY = 
      DBSemanticAnnotationHelper.class.getName() + ":dbNames";
  
  protected static final String L1_TABLE_SUFFIX = "L1";
  
  protected static final String L2_TABLE_SUFFIX = "L2";
  
  protected static final String MENTIONS_TABLE_SUFFIX = "Mentions";

  /**
   * The key in the {@link IndexConfig#getOptions()} Map for the size of the 
   * memory cache to be used by the database. The cache size defaults to 1 GB.
   * Too small a cache size can lead to out of memory errors during indexing!
   */
  public static final String DB_CACHE_SIZE_OPTIONS_KEY = "databaseCacheSize";
  
  /**
   * The base name (prefix) used for all tables created by this helper.
   * The name is derived from the annotation name.
   */
  protected String tableBaseName;
  

  
  /**
   * Flag showing if the second level model is need (i.e. if the annotation
   * has any non-nominal features)
   */
  protected boolean level2Used = true;
  
  
  protected int level1CacheSize = -1;
  protected int level2CacheSize = -1;
  protected int level3CacheSize = -1;

  /**
   * Should we index "null" instances where all the configured features are
   * null or missing?  Normally this would be true for a normal annotation-mode
   * helper but false for a document-mode helper.
   */
  protected boolean indexNulls = true;

  /**
   * Should this helper index "null" instances, where none of the configured
   * features has a value set in the target (annotation or document) feature
   * map?  Default is true, both for backwards compatibility and because this
   * is the only value that makes sense for normal annotation-mode helpers, but
   * it may be useful to set it to false for document-mode helpers where not
   * every document has the target feature(s).
   *
   * @param indexNulls should "null" instances be indexed (true) or ignored
   *         (false)
   */
  public void setIndexNulls(boolean indexNulls) {
    this.indexNulls = indexNulls;
  }

  /**
   * Should this helper index "null" instances, where none of the configured
   * features has a value set in the target (annotation or document) feature
   * map?
   *
   * @return should this helper index "null" instances?
   */
  public boolean isIndexNulls() {
    return indexNulls;
  }

  /**
   * Additional parameters to pass in the H2 database URL, for example to
   * disable the MVStore and use the older PageStore instead.
   */
  protected Map<String, Object> urlParams;

  /**
   * Supply additional parameters that will be appended to the JDBC URL when
   * connecting to the H2 database.  This can include things like
   * "mv_store=false" to disable the default MVStore storage engine in favour
   * of the older PageStore.  See
   * <a href="https://h2database.com/javadoc/org/h2/engine/DbSettings.html">the H2 documentation</a>
   * for more details.
   *
   * @param urlParams parameters to pass in the H2 JDBC URL.
   */
  public void setUrlParams(Map<String, Object> urlParams) {
    this.urlParams = urlParams;
  }
  
  /**
   * Additional parameters that will be passed to H2 as part of the JDBC
   * connection URL.
   *
   * @return the URL parameters, or null if none.
   */
  public Map<String, Object> getUrlParams() {
    return urlParams;
  } 
  
  /**
   * Prepared statement used to obtain the Level-1 ID based on the values of 
   * nominal features. Only used at indexing time. 
   */
  protected transient PreparedStatement level1SelectStmt;

  
  /**
   * Prepared statement used to obtain the Level-1 feature values based on a 
   * mention ID. Only used at search time. 
   */
  protected transient PreparedStatement level1DescribeStmt;

  /**
   * Prepared statement used to obtain the Level-1 and Level-2 feature values 
   * based on a mention ID. Only used at search time. 
   */
  protected transient PreparedStatement level1And2DescribeStmt;
  
  /**
   * Prepared statement used to insert anew row into the Level-1 table. 
   * Only used at indexing time. 
   */
  protected transient PreparedStatement level1InsertStmt;
  
  /**
   * Prepared statement used to obtain the Level-2 ID based on the values of 
   * non-nominal features. Only used at indexing time.
   */  
  protected transient PreparedStatement level2SelectStmt;
  
  /**
   * Prepared statement used to insert anew row into the Level-2 table. 
   * Only used at indexing time. 
   */
  protected transient PreparedStatement level2InsertStmt;
  
  /**
   * Prepared statement used to obtain the Mention ID based on the Level-1 ID, 
   * the Level-2 ID and the annotation length. Only used at indexing time.
   */
  protected transient PreparedStatement mentionsSelectStmt;
  
  /**
   * Prepared statement used to insert anew row into the mentions table. 
   * Only used at indexing time. 
   */
  protected transient PreparedStatement mentionsInsertStmt;

  /**
   * The set of feature names for all the nominal features. 
   */
  protected transient Set<String> nominalFeatureNameSet;  
  
  /**
   * The set of feature names for all the non-nominal features. 
   */
  protected transient Set<String> nonNominalFeatureNameSet;
  
  /**
   * A cached connection used throughout the life of this helper.
   */
  protected transient Connection dbConnection;
  
  protected transient AnnotationTemplateCache cache;
  
  private transient int docsSoFar = 0;
  
  private static transient NumberFormat percentFormat = NumberFormat.getPercentInstance();
  
  private static transient Logger logger = Logger.getLogger(DBSemanticAnnotationHelper.class);
  
  /**
   * When in document mode (see
   *  {@link SemanticAnnotationHelper#isInDocumentMode()}), stores the features 
   *  for the current document.
   */
  private transient FeatureMap documentFeatures;
  
  @Override
  public void init(AtomicAnnotationIndex index) {
    super.init(index);
    if(getUriFeatures() != null && getUriFeatures().length > 0) {
      logger.warn(
              "This helper type does not fully support URI features, "
              + "they will be indexed but only as text literals!");
    }
    setTextFeatures(concatenateArrays(getTextFeatures(), getUriFeatures()));
    setUriFeatures(new String[0]);

    cache = new AnnotationTemplateCache(this);
    cache.setL1CacheSize(level1CacheSize);
    cache.setL2CacheSize(level2CacheSize);
    cache.setL3CacheSize(level3CacheSize);
    
    // calculate the basename
    // to avoid inter-locking between the multiple SB-based indexers, they each 
    // create their own database.
    tableBaseName = annotationType.replaceAll("[^\\p{Alnum}_]", "_");
    List<String> baseNames = (List<String>)index.getParent().getIndexConfig()
        .getContext().get(DB_NAMES_CONTEXT_KEY);
    if(baseNames == null) {
      baseNames = new LinkedList<String>();
      index.getParent().getIndexConfig().getContext().put(DB_NAMES_CONTEXT_KEY, baseNames);
    }
    while(baseNames.contains(tableBaseName)) {
      tableBaseName += "_";
    }
    baseNames.add(tableBaseName);
    
    File dbDir = new File(index.getIndexDirectory(), DB_DIR_NAME);
    try {
      Class.forName("org.h2.Driver");
      String cacheSizeStr = index.getParent().getIndexConfig().getOptions().get(
              DB_CACHE_SIZE_OPTIONS_KEY);
      // default to 100 MiB, if not provided
      if(cacheSizeStr == null) cacheSizeStr = Integer.toString(100 *1024);
      String jdbcUrl = "jdbc:h2:file:" + dbDir.getAbsolutePath() + 
              "/" + tableBaseName + ";CACHE_SIZE=" + cacheSizeStr;
      // add any extra URL parameters
      if(urlParams != null) {
        for(Map.Entry<String, Object> param : urlParams.entrySet()) {
          jdbcUrl += ";" + param.getKey() + "=" + param.getValue();
        }
      }
      dbConnection = DriverManager.getConnection(jdbcUrl, "sa", "");
      dbConnection.setAutoCommit(true);
      dbConnection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      createDb(index);
    } catch(SQLException e) {
      throw new RuntimeException("Error while initialising the database", e);
    } catch(ClassNotFoundException e) {
      throw new RuntimeException("Database driver not loaded.", e);
    }
    

    nominalFeatureNameSet = new HashSet<String>();
    if(nominalFeatureNames != null){
      for(String name : nominalFeatureNames) nominalFeatureNameSet.add(name);
    }
    
    nonNominalFeatureNameSet = new HashSet<String>();
    if(integerFeatureNames != null){
      for(String name : integerFeatureNames) nonNominalFeatureNameSet.add(name);
    }
    if(floatFeatureNames != null){
      for(String name : floatFeatureNames) nonNominalFeatureNameSet.add(name);
    }
    if(textFeatureNames != null){
      for(String name : textFeatureNames) nonNominalFeatureNameSet.add(name);
    }
    try {
      constructDescriptionStatements();
    } catch(SQLException e) {
      throw new RuntimeException("Error while opening database", e);
    }
  }

  protected void constructDescriptionStatements() throws SQLException {
    // level 1 query
    List<String> nomFeatNames = new ArrayList<String>(
        Arrays.asList(descriptiveFeatures));
    nomFeatNames.retainAll(nominalFeatureNameSet);

    StringBuilder stmt = new StringBuilder("SELECT DISTINCT ");
    stmt.append(tableName(null, MENTIONS_TABLE_SUFFIX)).append(".\"ID\"");
    for(int i = 0; i < nomFeatNames.size(); i++) {
      String featName = nomFeatNames.get(i);
      stmt.append(", ").append(tableName(null, L1_TABLE_SUFFIX))
          .append(".\"").append(featName).append("\" AS \"").append(featName).append("\"");
    }
    stmt.append(" FROM ")
        .append(tableName(null, MENTIONS_TABLE_SUFFIX))
        .append(", ").append(tableName(null, L1_TABLE_SUFFIX))
        .append(" WHERE ").append(tableName(null, MENTIONS_TABLE_SUFFIX))
        .append(".\"ID\" IS ?")
        .append(" AND ").append(tableName(null, MENTIONS_TABLE_SUFFIX))
        .append(".\"L1_ID\" = ").append(tableName(null, L1_TABLE_SUFFIX))
        .append(".\"ID\"");
    if(level2Used){
      stmt.append(" AND ").append(tableName(null, MENTIONS_TABLE_SUFFIX))
          .append(".\"L2_ID\" IS NULL;");
    }else {
      stmt.append(";");
    }
//    logger.debug("L1 description statement: " + stmt.toString());
    level1DescribeStmt = dbConnection.prepareStatement(stmt.toString());

    if(level2Used) {
      // levels 1 and 2 query
      List<String> nonNomFeatNames = new ArrayList<String>(
          Arrays.asList(descriptiveFeatures));
      nonNomFeatNames.retainAll(nonNominalFeatureNameSet);
      stmt = new StringBuilder("SELECT DISTINCT ");
      stmt.append(tableName(null, MENTIONS_TABLE_SUFFIX)).append(".\"ID\"");
      for(int i = 0; i < nomFeatNames.size(); i++) {
        String featName = nomFeatNames.get(i);
        stmt.append(", ").append(tableName(null, L1_TABLE_SUFFIX))
            .append(".\"").append(featName).append("\" AS \"").append(featName).append('"');
      }
      for(int i = 0; i < nonNomFeatNames.size(); i++) {
        String featName = nonNomFeatNames.get(i);
        stmt.append(", ").append(tableName(null, L2_TABLE_SUFFIX))
            .append(".\"").append(featName).append("\" AS \"").append(featName).append('"');
      }    
      stmt.append(" FROM ")
          .append(tableName(null, MENTIONS_TABLE_SUFFIX))
          .append(", ").append(tableName(null, L1_TABLE_SUFFIX))
          .append(", ").append(tableName(null, L2_TABLE_SUFFIX))
          .append(" WHERE ").append(tableName(null, MENTIONS_TABLE_SUFFIX))
          .append(".\"ID\" IS ?")
          .append(" AND ").append(tableName(null, MENTIONS_TABLE_SUFFIX))
          .append(".\"L1_ID\" = ").append(tableName(null, L1_TABLE_SUFFIX))
          .append(".\"ID\" AND ").append(tableName(null, MENTIONS_TABLE_SUFFIX))
          .append(".\"L2_ID\" = ").append(tableName(null, L2_TABLE_SUFFIX))
          .append(".\"ID\";");
//      logger.debug("L1+2 description statement: " + stmt.toString());
      level1And2DescribeStmt = dbConnection.prepareStatement(stmt.toString());      
    } else {
      level1And2DescribeStmt = null;
    }

  }
  
  /**
   * Creates in the database the tables required by this helper for indexing.
   * Called at index creation, during the initialisation process.
   * 
   * During indexing, the only tests are equality tests (to check we're not 
   * inserting duplicate rows). To support those in the most efficient way 
   * possible, we're creating MEMORY tables (with the indexes stored in RAM) 
   * and HASH indexes for all data columns.
   * 
   * @param indexer the AtomicIndex that "owns" this annotation helper.
   * @throws SQLException if an error occurs when creating the DB.
   */
  protected void createDb(AtomicAnnotationIndex indexer) throws SQLException {
    Statement stmt = dbConnection.createStatement();
    // ////////////////////////////////
    // create the Level 1 table
    // ////////////////////////////////
    StringBuilder createStr = new StringBuilder();
    StringBuilder selectStr = new StringBuilder();
    StringBuilder insertStr = new StringBuilder();
    createStr.append("CREATE TABLE IF NOT EXISTS " + tableName(null, L1_TABLE_SUFFIX) +
            " (ID IDENTITY NOT NULL PRIMARY KEY");
    selectStr.append("SELECT ID FROM " + tableName(null, L1_TABLE_SUFFIX));
    insertStr.append("INSERT INTO " + tableName(null, L1_TABLE_SUFFIX) + " VALUES(DEFAULT");
    if(nominalFeatureNames != null && nominalFeatureNames.length > 0) {
      selectStr.append(" WHERE");
      boolean firstWhere = true;
      for(String aFeatureName : nominalFeatureNames) {
        createStr.append(", \"" + aFeatureName + "\" VARCHAR(255)");
        if(firstWhere) firstWhere = false; else selectStr.append(" AND");
        selectStr.append(" \"" + aFeatureName + "\" IS ?");
        insertStr.append(", ?");
      }
    }
    createStr.append(")");
    insertStr.append(")");
    logger.debug("Create statement:\n" + createStr.toString());
    stmt.execute(createStr.toString());
    logger.debug("Select Level 1:\n" + selectStr.toString());
    level1SelectStmt = dbConnection.prepareStatement(selectStr.toString());
    level1InsertStmt = dbConnection.prepareStatement(insertStr.toString());
    
    // ////////////////////////////////
    // create the Level 2 table
    // ////////////////////////////////
    int nonNominalFeats = 0;
    if(integerFeatureNames != null) nonNominalFeats += integerFeatureNames.length;
    if(floatFeatureNames != null) nonNominalFeats += floatFeatureNames.length;
    if(textFeatureNames != null) nonNominalFeats += textFeatureNames.length;
    level2Used = (nonNominalFeats > 0);
    if(level2Used) {
      createStr = new StringBuilder(
          "CREATE TABLE IF NOT EXISTS " + tableName(null, L2_TABLE_SUFFIX) + 
          " (ID IDENTITY NOT NULL PRIMARY KEY, L1_ID BIGINT," +
          " FOREIGN KEY(L1_ID) REFERENCES " + 
          tableName(null, L1_TABLE_SUFFIX) + "(ID)"  );
      selectStr = new StringBuilder(
          "SELECT ID FROM " + tableName(null, L2_TABLE_SUFFIX) + " WHERE L1_ID IS ?");
      insertStr = new StringBuilder(
              "INSERT INTO " + tableName(null, L2_TABLE_SUFFIX) + " VALUES(DEFAULT, ?");
      if(integerFeatureNames != null && integerFeatureNames.length > 0) {
        for(String aFeatureName : integerFeatureNames) {
          createStr.append(", \"" + aFeatureName + "\" BIGINT");
          selectStr.append(" AND \"" + aFeatureName + "\" IS ?");
          insertStr.append(", ?");
        }
      }
      if(floatFeatureNames != null && floatFeatureNames.length > 0) {
        for(String aFeatureName : floatFeatureNames) {
          createStr.append(", \"" + aFeatureName + "\" DOUBLE");
          selectStr.append(" AND \"" + aFeatureName + "\" IS ?");
          insertStr.append(", ?");
        }
      }
      if(textFeatureNames != null && textFeatureNames.length > 0) {
        for(String aFeatureName : textFeatureNames) {
          createStr.append(", \"" + aFeatureName + "\" VARCHAR(255)");
          selectStr.append(" AND \"" + aFeatureName + "\" IS ?");
          insertStr.append(", ?");
        }
      }
      createStr.append(")");
      insertStr.append(")");
      logger.debug("Create statement:\n" + createStr.toString());
      stmt.execute(createStr.toString());
      
      logger.debug("Select Level 2:\n" + selectStr.toString());
      level2SelectStmt = dbConnection.prepareStatement(selectStr.toString());
      level2InsertStmt = dbConnection.prepareStatement(insertStr.toString());
    }
    // /////////////////////////////
    // create the Mentions table
    // /////////////////////////////
    createStr = new StringBuilder(
        "CREATE TABLE IF NOT EXISTS " + tableName(null, MENTIONS_TABLE_SUFFIX) + 
        " (ID IDENTITY NOT NULL PRIMARY KEY, L1_ID BIGINT," +
        " FOREIGN KEY (L1_ID) REFERENCES " + 
        tableName(null, L1_TABLE_SUFFIX) + "(ID)");

    selectStr = new StringBuilder(
            "SELECT ID FROM " + tableName(null, MENTIONS_TABLE_SUFFIX) + " WHERE L1_ID IS ?");
    insertStr = new StringBuilder(
            "INSERT INTO " + tableName(null, MENTIONS_TABLE_SUFFIX) + " VALUES(DEFAULT, ?");
    if(level2Used) {
      createStr.append(", L2_ID BIGINT, FOREIGN KEY (L2_ID) REFERENCES " + 
          tableName(null, L2_TABLE_SUFFIX) + "(ID)");
      selectStr.append(" AND L2_ID IS ?");
      insertStr.append(", ?");
    }
    createStr.append(", Length INT)");
    selectStr.append(" AND Length IS ?");
    insertStr.append(", ?)");
    logger.debug("Create statement:\n" + createStr.toString());
    stmt.execute(createStr.toString());
    
    logger.debug("Select Mentions:\n" + selectStr.toString());
    mentionsSelectStmt = dbConnection.prepareStatement(selectStr.toString());
    mentionsInsertStmt = dbConnection.prepareStatement(insertStr.toString());
    
    // create all the indexes
    createIndexes(stmt);
    dbConnection.commit();
  }
  
  protected void createIndexes(Statement stmt) throws SQLException {
    // ////////////////////////////////
    // Level 1 table
    // ////////////////////////////////
    List<String> indexStatements = new LinkedList<String>();
    if(nominalFeatureNames != null && nominalFeatureNames.length > 0) {
      for(String aFeatureName : nominalFeatureNames) {
        // create the index statement
        indexStatements.add(
            "CREATE INDEX IF NOT EXISTS "+ tableName("IDX-", L1_TABLE_SUFFIX + aFeatureName)  + 
            " ON " + tableName(null, L1_TABLE_SUFFIX) + "(\"" + aFeatureName + "\")");
      }
    }
    for(String aStmt : indexStatements) {
      logger.debug("Index statement:\n" + aStmt);
      stmt.execute(aStmt);  
    }
    
    // ////////////////////////////////
    // Level 2 table
    // ////////////////////////////////
    if(level2Used) {
      indexStatements.clear();
      if(integerFeatureNames != null && integerFeatureNames.length > 0) {
        for(String aFeatureName : integerFeatureNames) {
          indexStatements.add(
              "CREATE INDEX IF NOT EXISTS " + tableName("IDX", L2_TABLE_SUFFIX + aFeatureName) +  
              " ON " + tableName(null, L2_TABLE_SUFFIX) + "(\"" + aFeatureName + "\")");
        }
      }
      if(floatFeatureNames != null && floatFeatureNames.length > 0) {
        for(String aFeatureName : floatFeatureNames) {
          indexStatements.add(
              "CREATE INDEX IF NOT EXISTS " + tableName("IDX", L2_TABLE_SUFFIX + aFeatureName) +  
              " ON " + tableName(null, L2_TABLE_SUFFIX) + "(\"" + aFeatureName + "\")");          
        }
      }
      if(textFeatureNames != null && textFeatureNames.length > 0) {
        for(String aFeatureName : textFeatureNames) {
          indexStatements.add(
                  "CREATE INDEX IF NOT EXISTS " + tableName("IDX", L2_TABLE_SUFFIX + aFeatureName) +  
                  " ON " + tableName(null, L2_TABLE_SUFFIX) + "(\"" + aFeatureName + "\")");
        }
      }
      // create the indexes
      for(String aStmt : indexStatements) {
        logger.debug("Index statement:\n" + aStmt);
        stmt.execute(aStmt);  
      }
    }
    
    // /////////////////////////////
    // Mentions table
    // /////////////////////////////
    // all other fields are either primary or foreign keys, so they get indexes
    String idxStmt = "CREATE INDEX IF NOT EXISTS " + 
        tableName("IDX", MENTIONS_TABLE_SUFFIX + "Length") + " ON " + 
        tableName(null, MENTIONS_TABLE_SUFFIX) + " (Length)";
    logger.debug("Index statement:\n" + idxStmt);
    stmt.execute(idxStmt);    
  }
  
  /**
   * Creates a table (index, etc.) name. Uses the value in 
   * {@link #tableBaseName} as a base name, to which it prepends the supplied 
   * prefix (if any), it appends the supplied suffix(if any). The constructed 
   * string is then surrounded with double quotes.  
   * @param prefix optional prefix to prepend to the table name
   * @param suffix optional suffix to append to the table name
   * @return quoted table name suitable for use in SQL statements.
   */
  protected String tableName(String prefix, String suffix) {
    StringBuilder str = new StringBuilder("\"");
    if(prefix != null) str.append(prefix);
    str.append(tableBaseName);
    if(suffix != null) str.append(suffix);
    str.append("\"");
    return str.toString();
  }
  
  @Override
  public String[] getMentionUris(Annotation ann, int length,
      AtomicAnnotationIndex index) {
    FeatureMap featuresToIndex;
    if(getMode() == Mode.DOCUMENT) {
      length = -1;
      featuresToIndex = documentFeatures;
    } else {
      featuresToIndex = ann.getFeatures();
    }

    if(!indexNulls) {
      // we don't want to index instances where all the features are null, so
      // check to see whether this is the case
      boolean allFeaturesNull = true;
      shortCircuit:do {
        for(String featureName : nominalFeatureNameSet) {
          if(featuresToIndex.get(featureName) != null) {
            allFeaturesNull = false;
            break shortCircuit;
          }
        }
        for(String featureName : nonNominalFeatureNameSet) {
          if(featuresToIndex.get(featureName) != null) {
            allFeaturesNull = false;
            break shortCircuit;
          }
        }
      } while(false);

      // no value found for any of the features, so drop this instance
      if(allFeaturesNull) {
        return EMPTY_STRING_ARRAY;
      }
    }
    
    try {
      // find the level 1 ID
      Long level1Tag = cache.getLevel1Id(featuresToIndex, 
          new Level1IdGenerator(featuresToIndex));
      
      // find the Level-1 Mention ID (ignoring the L2 values)
      Long mentionL1Tag = cache.getLevel3Id(level1Tag, null, length, 
          new Level3IdGenerator(level1Tag, null, length));
      
      Long mentionL2Tag = null;
      if(level2Used){
        // find the level 2 ID
        Long level2Tag = cache.getLevel2Id(level1Tag, featuresToIndex, 
            new Level2IdGenerator(level1Tag, featuresToIndex));
        
        // find the Level-2 Mention ID
        mentionL2Tag = cache.getLevel3Id(level1Tag, level2Tag, length,
            new Level3IdGenerator(level1Tag, level2Tag, length));
      }
      
      // now we finally have the mention ID
      if(level2Used) {
        return new String[] {
                annotationType + ":" + mentionL1Tag, 
                annotationType + ":" + mentionL2Tag};
      } else {
        return new String[] {
                annotationType + ":" + mentionL1Tag};
      }
    } catch(Exception e) {
      // something went bad: we can't fix it :(
      logger.error("Error while interogating database. Annotation was lost!", e);
      return EMPTY_STRING_ARRAY;
    }
  }

  
  
  /* (non-Javadoc)
   * @see gate.mimir.SemanticAnnotationHelper#isMentionUri(java.lang.String)
   */
  @Override
  public boolean isMentionUri(String mentionUri) {
    final String prefix = annotationType + ":";
    if(mentionUri.startsWith(prefix)) {
      try{
        return Long.parseLong(mentionUri.substring(prefix.length())) >= 0;
      } catch (Exception e) {}
    }
    return false;
  }

  /* (non-Javadoc)
   * @see gate.mimir.AbstractSemanticAnnotationHelper#getDescriptiveFeatureValues(java.lang.String)
   */
  @Override
  protected String[] getDescriptiveFeatureValues(String mentionUri) {
    long mentionId = -1;
    try {
      mentionId = Long.parseLong(
          mentionUri.substring(annotationType.length() + 1));
    } catch(Exception e) {
      logger.error("Could not describe mention with invalid URI: \"" + 
          mentionUri + "\" (" + e.getMessage() + ")." );
      return null;
    }
    if(level1DescribeStmt == null) return null;
    ResultSet res = null;
    try {
      level1DescribeStmt.setLong(1, mentionId);
      res = level1DescribeStmt.executeQuery();
      if(!res.next()) {
        // no level 1 results: try levels 1+2
        res.close();
        if(level2Used && level1And2DescribeStmt != null) {
          level1And2DescribeStmt.setLong(1, mentionId);
          res = level1And2DescribeStmt.executeQuery();
          if(!res.next()){
            logger.error("Was asked to describe mention with ID " + mentionId + 
              " but was unable to find it.");
            return null;
          }          
        } else {
          // no results from level 1, and level2 not used
          return null;
        }
      }
      // by this point the result set was advanced to the one and only row
      String[] result = new String[descriptiveFeatures.length];
      for(int i = 0; i < descriptiveFeatures.length; i++) {
        try {
          Object sqlValue = res.getObject(descriptiveFeatures[i]);
          if(sqlValue != null) result[i] = sqlValue.toString();
        } catch(SQLException e) {
          // non-nominal features are not available for level 1 mentions
          result[i] = null;
        } catch (Exception e) {
          logger.error("Error while obtaining description feature value.", e);
        }
      }
      return result;
    } catch(SQLException e) {
      logger.error("Database error while describing mention with ID: " + 
          mentionId, e);
      return null;
    } finally {
      if(res != null){
        try {
          res.close();
        } catch(SQLException e) {
          logger.error("Error while closing SQL result set", e);
        }
      }
    }
  }

  /**
   * Sets all the values for a prepared statement (which must be one of the 
   * cached transient statements!)
   * For level-2 statements, it does not set the L1_ID parameter (i.e. it starts 
   * with the parameter at position 2).
   * @param stmt the statement whose parameters are to be set
   * @param annFeats features from the annotation (or document, for doc-mode
   *         helpers) to use as the source of column values
   * @throws SQLException if an error occurs (e.g. type mismatch)
   */
  protected void setStatementParameters(PreparedStatement stmt, 
          FeatureMap annFeats) throws SQLException {
    if(stmt == level1InsertStmt || stmt == level1SelectStmt) {
      if(nominalFeatureNames != null){
        int paramIdx = 1;
        for(String aFeatureName : nominalFeatureNames) {
          Object value = annFeats.get(aFeatureName);
          if(value != null) {
            stmt.setString(paramIdx++, value.toString());
          } else {
            stmt.setNull(paramIdx++, Types.VARCHAR);
          }
        }
      }
    } else if(stmt == level2InsertStmt || stmt == level2SelectStmt) {
      if(!level2Used) throw new RuntimeException(
          "Was asked to populate a Level-2 statement, but Level-2 is not in use!");
      int paramIdx = 2;
      if(integerFeatureNames != null){
        for(String aFeatureName : integerFeatureNames) {
          Object valueObj = annFeats.get(aFeatureName);
          Long value = null;
          if(valueObj != null){
            if(valueObj instanceof Number) {
              value = ((Number)valueObj).longValue();
            } else if(valueObj instanceof String) {
              try {
                value = Long.valueOf((String)valueObj);
              } catch(NumberFormatException e) {
                logger.warn("Value provided for feature \"" + aFeatureName
                                + "\" is a String that cannot be parsed to a Long. Value ("
                                + valueObj.toString() + ") will be ignored!");
              }
            } else {
              logger.warn("Value provided for feature \"" + aFeatureName
                      + "\" is not a subclass of java.lang.Number. Value ("
                      + valueObj.toString() + ") will be ignored!");
            }            
          }
          if(value != null) {
            stmt.setLong(paramIdx++, value);
          } else {
            stmt.setNull(paramIdx++, Types.BIGINT);
          }
        }
      }
      if(floatFeatureNames != null){
        for(String aFeatureName : floatFeatureNames) {
          Object valueObj = annFeats.get(aFeatureName);
          Double value = null;
          if(valueObj != null){
            if(valueObj instanceof Number) {
              value = ((Number)valueObj).doubleValue();
            } else if(valueObj instanceof String) {
              try {
                value = Double.valueOf((String)valueObj);
              } catch(NumberFormatException e) {
                logger.warn("Value provided for feature \"" + aFeatureName
                                + "\" is a String that cannot be parsed to a Double. Value ("
                                + valueObj.toString() + ") will be ignored!");
              }
            } else {
              logger.warn("Value provided for feature \"" + aFeatureName
                      + "\" is not a subclass of java.lang.Number. Value ("
                      + valueObj.toString() + ") will be ignored!");
            }            
          }
          if(value != null) {
            stmt.setDouble(paramIdx++, value);
          } else {
            stmt.setNull(paramIdx++, Types.DOUBLE);
          }
        }  
      }
      if(textFeatureNames != null) {
        for(String aFeatureName : textFeatureNames) {
          Object valueObj = annFeats.get(aFeatureName);
          if(valueObj != null) {
            stmt.setString(paramIdx++, valueObj.toString());
          } else {
            stmt.setNull(paramIdx++, Types.VARCHAR);
          }
        }
      }
    } else {
      throw new RuntimeException("Cannot recognise the the provided prepared statement!");
    }
  }

  @Override
  public List<Mention> getMentions(String annotationType,
          List<Constraint> constraints, QueryEngine engine) {
    if(!annotationType.equals(this.annotationType)) {
      throw new IllegalArgumentException("Wrong annotation type \"" + 
          annotationType + "\", this helper can only handle " + 
          this.annotationType + "!");
    }
    List<Mention> mentions = new LinkedList<Mention>();
    boolean hasLevel1Constraints = false;
    for(Constraint aConstraint : constraints) {
      if(nominalFeatureNameSet.contains(aConstraint.getFeatureName())) {
        hasLevel1Constraints = true;
        break;
      }
    }
    boolean hasLevel2Constraints = false;
    for(Constraint aConstraint : constraints) {
      if(nonNominalFeatureNameSet.contains(aConstraint.getFeatureName())) {
        hasLevel2Constraints = true;
        break;
      }
    }
    List<Object> params = new ArrayList<Object>();
    StringBuilder selectStr = new StringBuilder(
        "SELECT DISTINCT " + tableName(null, MENTIONS_TABLE_SUFFIX) + ".ID, " +
        tableName(null, MENTIONS_TABLE_SUFFIX) + ".Length FROM " + 
        tableName(null, MENTIONS_TABLE_SUFFIX));
    if(hasLevel1Constraints) {
      selectStr.append(", " + tableName(null, L1_TABLE_SUFFIX));
    }
    if(hasLevel2Constraints) {
      selectStr.append(", " + tableName(null, L2_TABLE_SUFFIX));
    }
    boolean firstWhere = true;
    // add constraints
    List<Constraint> unusedConstraints = new ArrayList<Constraint>(constraints);
    if(hasLevel1Constraints) {
      if(nominalFeatureNames != null) {
        for(String aFeatureName : nominalFeatureNames) {
          for(Constraint aConstraint : constraints) {
            if(aFeatureName.equals(aConstraint.getFeatureName())){
              if(firstWhere){
                firstWhere = false;
                selectStr.append(" WHERE");
              } else {
                selectStr.append(" AND");
              }
              selectStr.append(" " + tableName(null, L1_TABLE_SUFFIX) + ".\"" + 
                  aFeatureName + "\"");
              switch( aConstraint.getPredicate() ) {
                case EQ:
                  selectStr.append(" =");
                  break;
                case GT:
                  selectStr.append(" >");
                  break;
                case GE:
                  selectStr.append(" >=");
                  break;
                case LT:
                  selectStr.append(" <");
                  break;
                case LE:
                  selectStr.append(" <=");
                  break;
                case REGEX:
                  selectStr.append(" REGEXP");
              }
              if(aConstraint.getValue() instanceof String) {
                selectStr.append(" ?");
                params.add(aConstraint.getValue());
              } else if(aConstraint.getValue() instanceof String[]) {
                // this only makes sense for REGEX
                if(aConstraint.getPredicate() != ConstraintType.REGEX) {
                  throw new IllegalArgumentException("Got a two-valued constraint that is not a REGEXP!");
                }
                selectStr.append(" ?");
                params.add("(?" + ((String[])aConstraint.getValue())[1] + ")"
                        + ((String[])aConstraint.getValue())[0]);
              }
              unusedConstraints.remove(aConstraint);
            }
          }
        }        
      }
      // join L1 with Mentions
      selectStr.append(" AND " + tableName(null, L1_TABLE_SUFFIX) + ".ID = " +
          tableName(null, MENTIONS_TABLE_SUFFIX) + ".L1_ID");
      if(hasLevel2Constraints) {
        // join L1 with L2
        selectStr.append(" AND " + tableName(null, L1_TABLE_SUFFIX) + ".ID = " + 
                tableName(null, L2_TABLE_SUFFIX) + ".L1_ID");
      }
    }
    
    if(hasLevel2Constraints) {
      if(integerFeatureNames != null) {
        for(String aFeatureName : integerFeatureNames) {
          for(Constraint aConstraint : constraints) {
            if(aFeatureName.equals(aConstraint.getFeatureName())){
              if(firstWhere){
                firstWhere = false;
                selectStr.append(" WHERE");
              } else {
                selectStr.append(" AND");
              }
              selectStr.append(" " + tableName(null, L2_TABLE_SUFFIX) + ".\"" + 
                  aFeatureName + "\"");
              switch( aConstraint.getPredicate() ) {
                case EQ:
                  selectStr.append(" =");
                  break;
                case GT:
                  selectStr.append(" >");
                  break;
                case GE:
                  selectStr.append(" >=");
                  break;
                case LT:
                  selectStr.append(" <");
                  break;
                case LE:
                  selectStr.append(" <=");
                  break;
                case REGEX:
                  throw new IllegalArgumentException("Cannot use a REGEX predicate for numeric features!");
              }
              selectStr.append(" ?");
              if(aConstraint.getValue() instanceof Number) {
                params.add(Long.valueOf(((Number)aConstraint.getValue()).longValue()));
              } else {
                params.add(Long.valueOf(aConstraint.getValue().toString()));
              }
              unusedConstraints.remove(aConstraint);
            }
          }
        }        
      }
      if(floatFeatureNames != null) {
        for(String aFeatureName : floatFeatureNames) {
          for(Constraint aConstraint : constraints) {
            if(aFeatureName.equals(aConstraint.getFeatureName())){
              if(firstWhere){
                firstWhere = false;
                selectStr.append(" WHERE");
              } else {
                selectStr.append(" AND");
              }
              selectStr.append(" " + tableName(null, L2_TABLE_SUFFIX) + ".\"" + 
                  aFeatureName + "\"");
              switch( aConstraint.getPredicate() ) {
                case EQ:
                  selectStr.append(" =");
                  break;
                case GT:
                  selectStr.append(" >");
                  break;
                case GE:
                  selectStr.append(" >=");
                  break;
                case LT:
                  selectStr.append(" <");
                  break;
                case LE:
                  selectStr.append(" <=");
                  break;
                case REGEX:
                  throw new IllegalArgumentException("Cannot use a REGEX predicate for numeric features!");
              }
              selectStr.append(" ?");
              if(aConstraint.getValue() instanceof Number) {
                params.add(Double.valueOf(((Number)aConstraint.getValue()).doubleValue()));
              } else {
                params.add(Double.valueOf(aConstraint.getValue().toString()));
              }              
              unusedConstraints.remove(aConstraint);
            }
          }
        }        
      }
      if(textFeatureNames != null) {
        for(String aFeatureName : textFeatureNames) {
          for(Constraint aConstraint : constraints) {
            if(aFeatureName.equals(aConstraint.getFeatureName())){
              if(firstWhere){
                firstWhere = false;
                selectStr.append(" WHERE");
              } else {
                selectStr.append(" AND");
              }
              selectStr.append(" " + tableName(null, L2_TABLE_SUFFIX) + ".\"" + 
                  aFeatureName + "\"");
              switch( aConstraint.getPredicate() ) {
                case EQ:
                  selectStr.append(" =");
                  break;
                case GT:
                  selectStr.append(" >");
                  break;
                case GE:
                  selectStr.append(" >=");
                  break;
                case LT:
                  selectStr.append(" <");
                  break;
                case LE:
                  selectStr.append(" <=");
                  break;
                case REGEX:
                  selectStr.append(" REGEXP");
              }
              selectStr.append(" ?");
              
              if(aConstraint.getValue() instanceof String) {
                params.add(aConstraint.getValue());
              } else if(aConstraint.getValue() instanceof String[]) {
                // this only makes sense for REGEX
                if(aConstraint.getPredicate() != ConstraintType.REGEX) {
                  throw new IllegalArgumentException("Got a two-valued constraint that is not a REGEXP!");
                }
                params.add("(?" + ((String[])aConstraint.getValue())[1] + ")"
                        + ((String[])aConstraint.getValue())[0]);
              }
              unusedConstraints.remove(aConstraint);
            }
          }
        }        
      }
      
      // join L2 with Mentions
      selectStr.append(" AND "+ tableName(null, L2_TABLE_SUFFIX) + ".ID = " + 
          tableName(null, MENTIONS_TABLE_SUFFIX) + ".L2_ID");
    }
    if(unusedConstraints.size() > 0) {
      StringBuilder msg = new StringBuilder();
      if(unusedConstraints.size() == 1) {
        msg.append("The following constraint name was not recognised: \"");
        msg.append(unusedConstraints.get(0).getFeatureName());
        msg.append("\".");
      } else {
        msg.append("The following constraint names were not recognised: ");
        boolean first = true;
        for(Constraint aConstraint : unusedConstraints) {
          if(first) first = false; else msg.append(", ");
          msg.append('"');
          msg.append(aConstraint.getFeatureName());
          msg.append('"');
        }
        msg.append(".");
      }
      throw new RuntimeException(msg.toString());
    }
    if(!hasLevel2Constraints && level2Used) {
      // no level 2 constraints
      if(firstWhere){
        firstWhere = false;
        selectStr.append(" WHERE ");
      } else {
        selectStr.append(" AND ");
      }
      selectStr.append(tableName(null, MENTIONS_TABLE_SUFFIX) + ".L2_ID IS NULL");
    }
    
    logger.debug("Select query:\n" + selectStr.toString());
    try {
      PreparedStatement stmt = dbConnection.prepareStatement(selectStr.toString());
      int pos = 1;
      for(Object val : params) {
        stmt.setObject(pos++, val);
      }
      ResultSet res = stmt.executeQuery();
      while(res.next()) {
        long id = res.getLong(1);
        int length = getMode() == Mode.DOCUMENT? Mention.NO_LENGTH : res.getInt(2);
        mentions.add(new Mention(annotationType + ":" + id, length));
      }
      stmt.close();
    } catch(SQLException e) {
      logger.error("DB error", e);
      throw new RuntimeException("DB error", e);
    }
    return mentions;
  }
  
  
  
  @Override
  public void documentStart(Document document) {
    if(getMode() == Mode.DOCUMENT) {
      documentFeatures = document.getFeatures();
    }
  }

  @Override
  public void documentEnd() {
    documentFeatures = null;
    if(cache != null) {
      double l1ratio = cache.getL1CacheHitRatio();
      double l2ratio = cache.getL2CacheHitRatio();
      double l3ratio = cache.getL3CacheHitRatio();
      logger.debug("Cache size("
              + annotationType
              + "):"
              + cache.size()
              + ". Hit ratios L1, L2, L3: "
              + (Double.isNaN(l1ratio) ? "N/A" : percentFormat.format(l1ratio))
              + ", "
              + (Double.isNaN(l2ratio) ? "N/A" : percentFormat.format(l2ratio))
              + ", "
              + (Double.isNaN(l3ratio) ? "N/A" : percentFormat.format(l3ratio)));
      docsSoFar++;
    } else {
      logger.debug("Cache size(" + annotationType + "): null");
    }
  }

  @Override
  public void close(AtomicAnnotationIndex indexer) {
    closeDB();
  }

  @Override
  public void close(QueryEngine qEngine) {
    closeDB();
  }
  
  private void closeDB() {
    //Explicitly close and nullify all the prepared statements.
    level1InsertStmt = closeAndNullify(level1InsertStmt);
    level1SelectStmt = closeAndNullify(level1SelectStmt);
    level2InsertStmt = closeAndNullify(level2InsertStmt);
    level2SelectStmt = closeAndNullify(level2SelectStmt);
    mentionsInsertStmt = closeAndNullify(mentionsInsertStmt);
    mentionsSelectStmt = closeAndNullify(mentionsSelectStmt);
	 
    //now close the connection
    try {
      if(dbConnection != null) {
        dbConnection.close();
        dbConnection = null;
      }
    } catch(SQLException e) {
      logger.warn("Error while closing DB COnnection", e);
    }
  }
  
  /**
   * Close a prepared statement to help free resources
   * @param stmt the statement to close
   * @return null, as a utility for easily nullifying the original object
   */
  private PreparedStatement closeAndNullify(PreparedStatement stmt) {
    try {
      if (stmt != null) stmt.close();
    } catch (SQLException e) {
      logger.warn("Error closing DB statement");
    }
  
    return null;
  }
  
  /**
   * Sets the size for the three level caches used by this helper.
   * 
   * A negative value for each cache size sets the cache size to its default
   * value.
   * 
   * @param level1 the size for the Level 1 cache. The Level 1 cache stores 
   * previously seen combinations of nominal feature values.
   *  
   * @param level2 the size for the Level 2 cache. The Level 2 cache stores 
   * previously seen combinations of non-nominal feature values.
   * 
   * @param level3 the size for the Level 3 cache. The Level 1 cache stores 
   * previously seen mention IDs.
   */
  public void setCacheSizes(int level1, int level2, int level3) {
    this.level1CacheSize = level1;
    this.level2CacheSize = level2;
    this.level3CacheSize = level3;
    if(cache != null) {
      cache.setL1CacheSize(level1CacheSize);
      cache.setL2CacheSize(level2CacheSize);
      cache.setL3CacheSize(level3CacheSize);
    }
  }
  
}
