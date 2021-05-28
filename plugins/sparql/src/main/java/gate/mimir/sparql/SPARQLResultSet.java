/*
 *  SPARQLResultSet.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 20 Apr 2011
 *  
 *  $Id: SPARQLResultSet.java 14541 2011-11-14 19:31:23Z ian_roberts $
 */
package gate.mimir.sparql;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class representing the result set from a SPARQL query.
 */
public class SPARQLResultSet {
  
  /**
   * Re-usable XML input factory used when parsing XML streams.
   */
  private static XMLInputFactory xmlInputFactory;
  
  static {
    xmlInputFactory =  XMLInputFactory.newInstance();
    xmlInputFactory.setProperty("javax.xml.stream.isCoalescing", Boolean.TRUE);
  }
  
  private static final String XMLNS = "http://www.w3.org/2005/sparql-results#";
  
  private static final Logger logger = LoggerFactory.getLogger(SPARQLResultSet.class);
  
  
  private String[] variableNames;
  
  private String[][] rows;
  

  /**
   * Create a result set from an input stream (typically the HTTP response from
   * a SPARQL endpoint).  The supplied input stream will be drained and closed.
   *
   * @param is the input stream that is a source for this result set
   * @throws XMLStreamException if the stream cannot be opened by the XML
   *         parser
   */
  public SPARQLResultSet(InputStream is) throws XMLStreamException {
    this(xmlInputFactory.createXMLStreamReader(is));
    try{
      is.close();
    } catch(IOException e) {
      logger.error("Could not close the input stream!", e);
    }
  }
  
  public SPARQLResultSet(Reader reader) throws XMLStreamException {
    this(xmlInputFactory.createXMLStreamReader(reader));
    try{
      reader.close();
    } catch(IOException e) {
      logger.error("Could not close the input reader!", e);
    }
  }
  
  public SPARQLResultSet(XMLStreamReader xsr) throws XMLStreamException {
    try{
      // A SPARQL result, in XML looks like this:
      //<?xml version="1.0"?>
      //<sparql xmlns="http://www.w3.org/2005/sparql-results#">
      //
      //  <head>
      //    <variable name="x"/>
      //    <variable name="hpage"/>
      //    <variable name="name"/>
      //    <variable name="age"/>
      //    <variable name="mbox"/>
      //    <variable name="friend"/>
      //  </head>
      //
      //  <results>
      //
      //    <result> 
      //      <binding name="x">
      //  <bnode>r2</bnode>
      //      </binding>
      //      <binding name="hpage">
      //  <uri>http://work.example.org/bob/</uri>
      //      </binding>
      //      <binding name="name">
      //  <literal xml:lang="en">Bob</literal>
      //      </binding>
      //      <binding name="age">
      //  <literal datatype="http://www.w3.org/2001/XMLSchema#integer">30</literal>
      //      </binding>
      //      <binding name="mbox">
      //  <uri>mailto:bob@work.example.org</uri>
      //      </binding>
      //    </result>
      //
      //    ...
      //  </results>
      //
      //</sparql>
      
      // find the root element
      while(xsr.next() != XMLStreamConstants.START_ELEMENT) {
        //do nothing
      }
      xsr.require(XMLStreamConstants.START_ELEMENT, XMLNS, "sparql");
      // find the first element
      int type = xsr.nextTag(); 
      while(type == XMLStreamConstants.START_ELEMENT) {
        String elemName = xsr.getLocalName();
        if(elemName.equals("head")) {
          parseHead(xsr);
          xsr.require(XMLStreamConstants.END_ELEMENT, XMLNS, "head");
        } else if(elemName.equals("results")) {
          parseResults(xsr);
          xsr.require(XMLStreamConstants.END_ELEMENT, XMLNS, "results");
        } else {
          // unknown element -> skip it
          type = xsr.next();
          while(!(type == XMLStreamConstants.END_ELEMENT && 
                xsr.getLocalName().equals(elemName))) {
            type = xsr.next();
          }
        }
        // find the next element event
        type = xsr.nextTag();
      }
      xsr.require(XMLStreamConstants.END_ELEMENT, XMLNS, "sparql");
    } finally {
      xsr.close();
    }
  }
  
  private void parseHead(XMLStreamReader xsr) throws XMLStreamException {
    List<String> variables = new LinkedList<String>();
    xsr.require(XMLStreamConstants.START_ELEMENT, XMLNS, "head");
    int type = xsr.nextTag();
    while(!(type == XMLStreamConstants.END_ELEMENT && 
            xsr.getLocalName().equals("head"))) {
      if(type == XMLStreamConstants.START_ELEMENT) {
        String elemName = xsr.getLocalName();
        if(elemName.equals("variable")) {
          String varName = xsr.getAttributeValue(null, "name");
          if(varName != null) variables.add(varName);
        }
        // consume all till the end of this element 
        type = xsr.next();
        while(! (type == XMLStreamConstants.END_ELEMENT && 
                xsr.getLocalName().equals(elemName))){
          type = xsr.next();
        }
      }
      type = xsr.next();
    }
    variableNames = variables.toArray(new String[variables.size()]);
  }
  
  private void parseResults(XMLStreamReader xsr) throws XMLStreamException {
    xsr.require(XMLStreamConstants.START_ELEMENT, XMLNS, "results");
    List<String[]> results = new LinkedList<String[]>();
    int type = xsr.nextTag();
    while(type == XMLStreamConstants.START_ELEMENT) {
      xsr.require(XMLStreamConstants.START_ELEMENT, XMLNS, "result");
      String[] aResult = new String[variableNames.length];
      Arrays.fill(aResult, null);
      type = xsr.nextTag();
      while(type == XMLStreamConstants.START_ELEMENT) {
        xsr.require(XMLStreamConstants.START_ELEMENT, XMLNS, "binding");
        String varName = xsr.getAttributeValue(null, "name");
        int column = 0;
        while(column < variableNames.length && 
              !variableNames[column].equals(varName)){
          column++;
        }
        if(column >= variableNames.length){
          throw new RuntimeException("Malformed input: could not find column " +
          		"for variable \"" + varName + "\" ");
        }
        type = xsr.nextTag();
        xsr.require(XMLStreamConstants.START_ELEMENT, XMLNS, null);
        String elemName = xsr.getLocalName();
        if(elemName.equals("uri")) {
          aResult[column] = xsr.getElementText();
          xsr.require(XMLStreamConstants.END_ELEMENT, XMLNS, elemName);
        } else if(elemName.equals("literal")) {
          aResult[column] = xsr.getElementText();
          xsr.require(XMLStreamConstants.END_ELEMENT, XMLNS, elemName);
        } else {
          // some other kind of element, we don't care about
          type = xsr.next();
          while(! (type == XMLStreamConstants.END_ELEMENT && 
                  xsr.getLocalName().equals(elemName))){
            type = xsr.next();
          }
        }
        // find the closing binding tag
        type = xsr.nextTag();
        xsr.require(XMLStreamConstants.END_ELEMENT, XMLNS, "binding");
        // ...and open the next one  
        type = xsr.nextTag();
      }
      xsr.require(XMLStreamConstants.END_ELEMENT, XMLNS, "result");
      // save the new result
      results.add(aResult);
      type = xsr.nextTag();
    }
    xsr.require(XMLStreamConstants.END_ELEMENT, XMLNS, "results");
    rows = results.toArray(new String[results.size()][]);
  }
  
  /**
   * The names of the bound variables, as returned by the SPARQL endpoint.
   * @return the names of the variables (i.e. the columns) in this result set.
   */
  public String[] getColumnNames() {
    return variableNames;
  }
  
  /**
   * The values returned by the SPARQL endpoint. Each row is an array of String
   * values, each entry in the array being a value for the corresponding column 
   * (as returned by {@link #getColumnNames()}).  
   * @return a two-dimensional array of Strings, where the first index selects 
   * the row, and the second index selects the column. 
   */
  public String[][] getRows() {
    return rows;
  }
  
  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    if(variableNames != null) {
      for(int i = 0; i < variableNames.length; i++) {
        if(i > 0) str.append(", ");
        str.append(variableNames[i] != null ? 
                "\"" + variableNames[i] + "\"" : "null");
      }
      str.append("\n");
    }
    
    if(rows != null) {
      for(String[] aRow : rows) {
        for(int i = 0; i < aRow.length; i++) {
          if(i > 0) str.append(", ");
          str.append(aRow[i] != null ? "\"" + aRow[i] + "\"" : "null");
        }
        str.append("\n");  
      }
    }
    return str.toString();
  }

//  /**
//   * Test code.
//   * TODO: remove it
//   * @param args
//   * @throws Exception
//   */
//  public static void main(String[] args) throws Exception{
//    String query = "PREFIX :<http://dbpedia.org/ontology/> THIS IS AN ERROR SELECT ?inst WHERE { ?inst a ?class . FILTER (?class = :Mountain)} LIMIT 100";
//    SPARQLSemanticAnnotationHelper ssah = new SPARQLSemanticAnnotationHelper(
//            "annType", "http://gateservice5:8081/openrdf-workbench/repositories/DBPedia/query", new String[]{}, 
//            new String[]{}, new String[]{}, new String[]{}, new String[]{}, 
//            null);
//    System.out.println(ssah.runQuery(query));
//  }
  
}
