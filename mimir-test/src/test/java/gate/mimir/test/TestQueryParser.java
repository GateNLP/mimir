/*
 *  TestQueryParser.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id: TestQueryParser.java 19385 2016-05-31 19:15:27Z ian_roberts $
 */
package gate.mimir.test;

import static org.junit.Assert.fail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.BeforeClass;
import org.junit.Test;


import gate.Gate;
import gate.creole.Plugin;
import gate.mimir.search.query.parser.QueryParser;
import gate.util.GateException;

import java.io.File;

/**
 * A JUnit test class for testing the query parser.
 * 
 * @author niraj
 */
public class TestQueryParser {
  
  private static final Logger logger = LoggerFactory.getLogger(TestQueryParser.class);

  @BeforeClass
  public static void init() {
    logger.debug("Initializing gate for query tests");
    try {
      Gate.init();
      //Gate.getCreoleRegister().registerDirectories(new File(Gate.getPluginsHome(), "ANNIE").toURI().toURL());
      Gate.getCreoleRegister().registerPlugin(new Plugin.Maven("uk.ac.gate.plugins", "annie", "8.6"));
    } catch(Exception e) {
      fail("Gate initialization failed!");
    }
  }

  @Test
  public void testMesurementQuery() {
    String query = "{Measurement normalisedUnit=\"m\"  normalisedValue<=30}";
    executeParsing(query);
  }
  
  @Test
  public void testStringQuery() {
    String query = "\"A AND OR B\"";
    executeParsing(query);
  }
  
  @Test
  public void testStringQueryWithEscapedCharacters() {
    String query = "A \"AND\" \\+ \"OR\" B";
    executeParsing(query);
  }
  
  @Test
  public void testAndQuery() {
    String query = "A AND B";
    executeParsing(query);
  }
  
  @Test
  public void testBareTokens() {
    String query = "15 September 2007";
    executeParsing(query);
  }

  @Test
  public void testNonAsciiTokens() {
    String query = "Thîs ís à teßt €12 and 23¢";
    executeParsing(query);
  }
  
  @Test
  public void testOrQuery() {
    String query = "A OR B";
    executeParsing(query);
  }
  
  @Test
  public void testAnnotationQuery() {
    String query = "{A}";
    executeParsing(query);
  }
  
  @Test
  public void testAnnotationQueryWithFeatures() {
    String query = "{A f1Key.REGEX(\"f1Value\", \"flasgs\") f2Key=\"f2Value IN Quotes\" f3Key>= 5.4 unit=\"1©\" }";
    executeParsing(query);
  }
  
  @Test
  public void testContainsQuery() {
    String query = "{A} OVER {B}";
    executeParsing(query);
  }
  
  @Test
  public void testWithinQuery() {
    String query = "{A} IN {B}";
    executeParsing(query);
  }
  
  @Test
  public void testGapQuery() {
    String query = "{A} [1..4] {B}";
    executeParsing(query);
  }
  
  @Test
  public void testRepeatsQuery() {
    String query = "{A}+3..5";
    executeParsing(query);
  }
  
  @Test
  public void testRepeats1Query() {
    String query = "{A}+3";
    executeParsing(query);
  }
  
  @Test
  public void testNamedIndexQuery() {
    String query = "root:be";
    executeParsing(query);
  }
  
  @Test
  public void testSequenceQUery() {
    String query = "{A} {B} ({A} | {B})";
    executeParsing(query);
  }
  
  @Test
  public void testComplexQuery() {
    String query = "({A} | {B}) IN (\\\"Going for\\\" [1..4] (root:trade | root:sale))";
    executeParsing(query);
  }
  
  private void executeParsing(String query) {
    logger.debug("Parsing query: " + query);
    try {
      QueryParser.parse(query);
    } catch(Exception e) {
      e.printStackTrace();
      fail();
    }
  }

}
