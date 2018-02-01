/* Generated By:JavaCC: Do not edit this line. QueryParserConstants.java */
package gate.mimir.search.query.parser;

public interface QueryParserConstants {

  int EOF = 0;
  int string = 17;
  int number = 18;
  int escape = 19;
  int special = 20;
  int le = 21;
  int ge = 22;
  int lt = 23;
  int gt = 24;
  int leftbrace = 25;
  int rightbrace = 26;
  int leftbracket = 27;
  int rightbracket = 28;
  int period = 29;
  int equals = 30;
  int colon = 31;
  int comma = 32;
  int or = 33;
  int and = 34;
  int minus = 35;
  int plus = 36;
  int question = 37;
  int in = 38;
  int hyphen = 39;
  int over = 40;
  int leftsquarebracket = 41;
  int rightsquarebracket = 42;
  int regex = 43;
  int tok = 44;
  int LETTER = 45;
  int DIGIT = 46;

  int DEFAULT = 0;
  int IN_STRING = 1;

  String[] tokenImage = {
    "<EOF>",
    "\"\\n\"",
    "\"\\r\"",
    "\"\\r\\n\"",
    "\"\\t\"",
    "\" \"",
    "\"\\\"\"",
    "\"\\\\n\"",
    "\"\\\\r\"",
    "\"\\\\t\"",
    "\"\\\\b\"",
    "\"\\\\f\"",
    "\"\\\\\\\"\"",
    "\"\\\\\\\'\"",
    "\"\\\\\\\\\"",
    "<token of kind 15>",
    "<token of kind 16>",
    "\"\\\"\"",
    "<number>",
    "<escape>",
    "<special>",
    "\"<=\"",
    "\">=\"",
    "\"<\"",
    "\">\"",
    "\"{\"",
    "\"}\"",
    "\"(\"",
    "\")\"",
    "\".\"",
    "\"=\"",
    "\":\"",
    "\",\"",
    "<or>",
    "<and>",
    "\"MINUS\"",
    "\"+\"",
    "\"?\"",
    "\"IN\"",
    "\"-\"",
    "\"OVER\"",
    "\"[\"",
    "\"]\"",
    "\"REGEX\"",
    "<tok>",
    "<LETTER>",
    "<DIGIT>",
  };

}
