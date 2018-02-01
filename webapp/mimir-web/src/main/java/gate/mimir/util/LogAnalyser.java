/*
 *  LogAnalyser.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 14 Sep 2009
 *
 *  $Id: LogAnalyser.java 16403 2012-12-06 11:34:33Z valyt $
 */
package gate.mimir.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * A class containing some methods for simple analysis of Mimir logs.  
 */
public class LogAnalyser {
  
  /**
   * Parses a log file and outputs a CSV file with the number of document 
   * indexed per second for each 10 minutes of the indexing process.
   * @param logFile
   */
  public static void calculateIndexingRate(String logFileName, 
          String outputFileName) throws ParseException{
    //we report every 30 minutes
    long REPORTING_PERIOD = 30 * 60 * 1000;
    
    BufferedReader reader = null;
    BufferedWriter writer = null;
    try {
      reader = new BufferedReader(new InputStreamReader(
              new GZIPInputStream(new FileInputStream(logFileName))));
      writer = new BufferedWriter(new FileWriter(outputFileName));
      //write heading
      writer.write("\"Time\",\"Documents/s\",\"Tokens/s\"\n");
      
      DateFormat dateFormatIn = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
      DateFormat dateFormatOut = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      
      NumberFormat numberFormatIn = NumberFormat.getIntegerInstance();
      numberFormatIn.setGroupingUsed(true);
      
      NumberFormat numberFormatOut = NumberFormat.getNumberInstance();
      numberFormatOut.setMaximumFractionDigits(2);
      numberFormatOut.setMinimumFractionDigits(2);
      numberFormatOut.setGroupingUsed(false);
      
      Date startTime = null;
      //find the start time
      //the first log line (used for finding the start time) looks like this:
      //2009-09-11 13:04:19,977 [gate.mimir.index.mg4j.MG4JIndexer token-0-indexer] INFO  mg4j.TokenIndexBuilder  - Indexing documents...
      Pattern regexStart = Pattern.compile(
              "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3})\\Q [gate.mimir.index.mg4j.MG4JIndexer token-0-indexer] INFO  mg4j.TokenIndexBuilder  - Indexing documents...\\E$");
      String line = reader.readLine();
      int lineNo = 0;
      while(line != null && lineNo < 1000){
        Matcher matcher = regexStart.matcher(line);
        if(matcher.find()){
          //found the start time
          startTime = dateFormatIn.parse(matcher.group(1));
          break;
        }
        line = reader.readLine();
        lineNo++;
      }
      if(startTime != null){
        System.out.println("Start time: " + startTime);
        writer.write(dateFormatOut.format(startTime) + "," + 
                numberFormatOut.format(0.0) + "," + 
                numberFormatOut.format(0.0) + "\n");
      }else{
        System.out.println("Could not find start time in the first 1000 lines!");
        return;
      }
      regexStart = null;
      
      //now find logs of documents processed
      //a regular log file line looks like this:
      //2009-09-11 13:04:31,197 [gate.mimir.index.mg4j.MG4JIndexer token-0-indexer] INFO  mg4j.TokenIndexBuilder  - 8 documents, 11s, 0.71 documents/s; used/avail/free/total/max mem: 153.48M/7.48G/113.71M/267.19M/7.64G
      Pattern regexDocs = Pattern.compile(
              "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3})\\Q [gate.mimir.index.mg4j.MG4JIndexer token-0-indexer] INFO  mg4j.TokenIndexBuilder  - \\E((?:\\d|,)+) documents,");
      //logs of tokens being read
      //2009-09-11 13:04:30,797 [gate.mimir.index.mg4j.MG4JIndexer token-0-indexer] DEBUG mg4j.MimirIndexBuilder  - Starting document EP-1626022-A9. 15490 annotations to process
      Pattern regexTokens = Pattern.compile("\\Q[gate.mimir.index.mg4j.MG4JIndexer token-0-indexer] DEBUG mg4j.MimirIndexBuilder  - Starting document \\E.*\\. (\\d+) annotations to process$");
      line = reader.readLine();
      lineNo++;
      int docsLastTime = 0;
      int tokensThisInterval = 0;
      while(line != null){
        Matcher matcher = regexDocs.matcher(line);
        if(matcher.find()){
          //found a new relevant line
          Date newTime = dateFormatIn.parse(matcher.group(1));
          if(newTime.getTime() - startTime.getTime() > REPORTING_PERIOD){
            int newDocs = numberFormatIn.parse(matcher.group(2)).intValue();
            long seconds = (newTime.getTime() - startTime.getTime()) / 1000; 
            System.out.println("["+ lineNo + "] " + newDocs + " docs at " + 
                    dateFormatOut.format(newTime));
            double docRate = (double)(newDocs - docsLastTime) / seconds;
            double tokenRate = (double)tokensThisInterval / seconds;
            writer.write(dateFormatOut.format(newTime) + "," + 
                    numberFormatOut.format(docRate) + "," +
                    numberFormatOut.format(tokenRate) + "\n");
            docsLastTime = newDocs;
            tokensThisInterval = 0;
            startTime = newTime;
          }
        }else{
          matcher = regexTokens.matcher(line);
          if(matcher.find()){
            //we have a number of tokens
            int newTokens = numberFormatIn.parse(matcher.group(1)).intValue();
            tokensThisInterval += newTokens;
          }
        }
        
        line = reader.readLine();
        lineNo++;
      }
      
    } catch(Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }finally{
      if(reader != null){
        try {
          reader.close();
        } catch(IOException e) {
          e.printStackTrace();
        }
      }
      if(writer != null){
        try {
          writer.close();
        } catch(IOException e) {
          e.printStackTrace();
        }
      }
    }
  }
  
  /**
   * @param args
   * @throws Exception 
   */
  public static void main(String[] args) throws ParseException {
    calculateIndexingRate(args[0], args[1]);
  }
}
