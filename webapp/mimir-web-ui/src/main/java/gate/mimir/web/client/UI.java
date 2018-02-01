/**
 *  UI.java
 * 
 *  Copyright (c) 1995-2010, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 23 Nov 2011 
 */

package gate.mimir.web.client;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.ServiceDefTarget;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.InlineHyperlink;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.MultiWordSuggestOracle.MultiWordSuggestion;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class UI implements EntryPoint {
 
  private static final Logger logger = Logger.getLogger(UI.class.getName());
  
  /**
   * A Timer implementation that fetches the latest results information from the 
   * server and updates the results display accordingly.
   * It will re-schedule itself until all required data is made available by 
   * the server. 
   */
  protected class ResultsUpdater extends Timer {
    
    private int newFirstDocument;
    
    /**
     * Flag used to indicate if we previously encountered an error 
     * communicating with the remote endpoint. This flag gets cleared on every 
     * successful access.  
     */
    boolean remoteError = false;
    
    /**
     * Creates a new results updater.
     * @param newFirstDocument the first document to be displayed on the page. 
     * This can be used for navigating between pages.
     */
    public ResultsUpdater(int newFirstDocument) {
      super();
      this.newFirstDocument = newFirstDocument;
    }

    public void setFirstDocument(int newFirstDocument) {
      this.newFirstDocument = newFirstDocument;
    }
    
    @Override
    public void run() {
      if(newFirstDocument != firstDocumentOnPage) {
        // new page: clear data and old display
        firstDocumentOnPage = newFirstDocument;
        feedbackLabel.setText("Working...");
        updateResultsDisplay(null);
      }
      gwtRpcService.getResultsData(queryId, firstDocumentOnPage, maxDocumentsOnPage, 
        new AsyncCallback<ResultsData>() {
        @Override
        public void onSuccess(ResultsData result) {
          remoteError = false;
          updatePage(result);
          if(result.getResultsTotal() < 0) schedule(500);
        }
        
        @Override
        public void onFailure(Throwable caught) {
          if(remoteError) {
            // this is the second failure: bail out
            // re-throw the exception so it's seen (useful when debugging)
            String message = caught.getLocalizedMessage();
            if(message == null || message.length() == 0) {
              message = "Error connecting to index.";
            }
            feedbackLabel.setText(message);
            updateResultsDisplay(null);
            throw new RuntimeException(caught);
          } else {
            // update the flag so we get out next time
            remoteError = true;
          }
          if(caught instanceof MimirSearchException) {
            if(((MimirSearchException)caught).getErrorCode() ==
                MimirSearchException.QUERY_ID_NOT_KNOWN) {
              // query ID not known -> re-post the query
              if(queryString != null && queryString.length() > 0){
                queryId = null;
                postQuery(queryString);
                return;
              }
            } else if(((MimirSearchException)caught).getErrorCode() ==
                MimirSearchException.INTERNAL_SERVER_ERROR) {
              // server side error: try reposting the query once
              // clean up old state
              if(queryId != null) {
                // release old query
                gwtRpcService.releaseQuery(queryId, new AsyncCallback<Void>() {
                  @Override
                  public void onSuccess(Void result) {}
                  @Override
                  public void onFailure(Throwable caught) {}
                });
              }
              if(queryString != null && queryString.length() > 0){
                queryId = null;
                postQuery(queryString);
                return;
              }
            }
          }
          // ignore and try again later
          schedule(500);
          // re-throw the exception so it's seen (useful when debugging)
          throw new RuntimeException(caught);          
        }
      });
    }
  }
  
  
  private class MimirOracle extends SuggestOracle{

    
    public MimirOracle() {
      super();
      gwtRpcService.getAnnotationsConfig(getIndexId(), 
          new AsyncCallback<String[][]>() {
        public void onFailure(Throwable caught) {
          //we could not get the data from the server
          annotationsConfig = new String[][]{ new String[]{} };
        }
        public void onSuccess(String[][] result) { annotationsConfig = result; }
      });
    }

    /* (non-Javadoc)
     * @see com.google.gwt.user.client.ui.SuggestOracle#requestSuggestions(com.google.gwt.user.client.ui.SuggestOracle.Request, com.google.gwt.user.client.ui.SuggestOracle.Callback)
     */
    @Override
    public void requestSuggestions(Request request, Callback callback) {
      ArrayList<MultiWordSuggestion> suggestions =
        new ArrayList<MultiWordSuggestion>();
      String query = request.getQuery();
      int caretIndex = searchBox.getValueBox().getCursorPos();
      int startIndex = query.lastIndexOf('{', caretIndex - 1);
      int endIndex = query.indexOf('{', caretIndex);
      if (endIndex == -1) { endIndex = caretIndex; }
      int lastClose = query.lastIndexOf("}", caretIndex);
      if (startIndex != -1 && lastClose < startIndex) {
        // an open bracket '{' is present, and not followed by } yet
        //check if we have the annotation type already
        String annType = null;
        boolean nonSpaceSeen = false;
        int charIdx = startIndex + 1;
        for(; charIdx < endIndex; charIdx++){
          // this method is deprecated, but the replacement (isWhitespace())
          // is not implemented in GWT
          if(Character.isSpace(query.charAt(charIdx))){
            if(nonSpaceSeen){
              //we found some space, after some actual content was seen
              annType = query.substring(startIndex + 1, charIdx);
              break;
            }
          }else{
            nonSpaceSeen = true;
          }
        }
        if(annType == null){
          //we have not found an ann type -> suggest some
          //the string before the last open {, before the caret
          String before = query.substring(0, startIndex);
          //the string after the next {
          String after = query.substring(endIndex);
          //the string from the current open {, to the caret, or the next {
          String middle = (startIndex >= 0 && startIndex < endIndex) ?
            query.substring(startIndex+1, endIndex): "";
          for(int annTypeId = 0; annTypeId < annotationsConfig.length; annTypeId++){
            if(annotationsConfig[annTypeId][0].startsWith(middle)){
              //we have identified the annotation type
              String suggestion = "{" + annotationsConfig[annTypeId][0];
              suggestions.add(new MultiWordSuggestion(
                      before + suggestion + after, suggestion));
//              Window.alert("Suggestion is: \"" + before + suggestion + after + "\"!");
            }
          }
        }else{
          //we know the ann type -> consume everything until the last word
          int lastSpace = charIdx;
          int wordCount = 0;
          boolean inSpace = true;
          boolean inQuote = false;
          for(; charIdx < endIndex; charIdx++){
            if(inQuote){
              //while in quote, consume everything until the closing quote
              if(query.charAt(charIdx) == '"' && charIdx > 0 &&
                      query.charAt(charIdx -1) != '\\'){
                inQuote = false;
//                wordCount++;
              }
            }else{
              if(Character.isSpace(query.charAt(charIdx))){
                lastSpace = charIdx;
                if(!inSpace){
                  //we're starting a new space (so we just finished a word)
                  wordCount++;
                  inSpace = true;
                }
              }else if(query.charAt(charIdx) == ')'){
                //closing of REGEX
                wordCount = 0;
                inSpace = false;
              } else if(query.charAt(charIdx) == '"' && charIdx > 0 &&
                      query.charAt(charIdx -1 ) != '\\'){
                inQuote = true;
                inSpace = false;
              } else{
                //some other non-space char
                if(inSpace){
                  //we're starting a new word
                  inSpace = false;
                }
              }
            }
          }
          if(inQuote){
            //suggest nothing
          }else{
            String before = query.substring(0, lastSpace + 1);
            String after = query.substring(endIndex);
            String middle = lastSpace < endIndex ? 
                    query.substring(lastSpace + 1, endIndex) : "";
            //we are still typing the feature name or operator
            //words appear in this sequence: <feature> <operator> <value>
            if(wordCount % 3 == 0){
              //feature
              //find the ann type
              for(int annTypeId = 0; annTypeId < annotationsConfig.length; annTypeId++){
                if(annotationsConfig[annTypeId][0].equalsIgnoreCase(annType)){
                  //suggest some feature names
                  for(int featId = 1; featId < annotationsConfig[annTypeId].length; 
                      featId++){
                    if(annotationsConfig[annTypeId][featId].startsWith(middle)){
                      String suggestion = annotationsConfig[annTypeId][featId];
                      suggestions.add(new MultiWordSuggestion(
                              before + suggestion + after, suggestion));
                    }
                  }
                  //also offer to close the annotation
                  String suggestion = "}";
                  suggestions.add(new MultiWordSuggestion(
                          before + suggestion + after, suggestion));
                  //only one ann type can match
                  break;
                }
              }              
            } else if(wordCount % 3 == 1){
              //operator
              String[] strArray = new String[]{"= \"\"", "<", "<=", ">", ">=", ".REGEX()"};
              for(String suggestion : strArray){
                suggestions.add(new MultiWordSuggestion(
                        before + suggestion + after, suggestion));
              }
            }else{
              //value -> no suggestions
            }
          }
        }
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX        
        
//        //the string before the last open {, before the caret
//        String before = query.substring(0, startIndex);
//        //the string after the next {
//        String after = query.substring(endIndex);
//        //the string from the current open {, to the caret, or the next {
//        String middle = (startIndex >= 0 && startIndex < endIndex) ?
//          query.substring(startIndex+1, endIndex): "";
//        //find the annotation type
//        String[] inputs = middle.split("\\s");
//        if(inputs.length == 1){
//          //we're searching only for annotation type
//          for(int annTypeId = 0; annTypeId < indexConfig.length; annTypeId++){
//            if(indexConfig[annTypeId][0].startsWith(inputs[0])){
//              //we have identified the annotation type
//              String suggestion = "{" + indexConfig[annTypeId][0];
//              suggestions.add(new MultiWordSuggestion(
//                      before + suggestion + after, suggestion));
//            }
//          }
//        } else if(inputs.length > 1){
//          //we already have the ann type, we need to suggest feature names
//          for(int annTypeId = 0; annTypeId < indexConfig.length; annTypeId++){
//            if(indexConfig[annTypeId][0].equalsIgnoreCase(inputs[0])){
//              //now we need to suggest a feature name
//              //the before string must contain everything up to the current feature
//              
//              String lastPrefix = inputs[inputs.length -1];
//              if(lastPrefix.indexOf('=') <0){
//                //we are still typing the feature name
//                for(int featId = 1; featId < indexConfig[annTypeId].length; 
//                    featId++){
//                  if(indexConfig[annTypeId][featId].startsWith(lastPrefix)){
//                    String suggestion = indexConfig[annTypeId][featId] + " = ";
//                    suggestions.add(new MultiWordSuggestion(
//                            before + suggestion + after, suggestion));
//                  }
//                }
//              }
//              //we only match one annotation type, so we break now
//              break;
//            }
//          }
//        }//if(inputs.length > 1)
      }
      
      Response response = new Response(suggestions);
      callback.onSuggestionsReady(request, response);
    }
    
    private String[][] annotationsConfig = new String[][]{new String[]{}};

  }
  
  /**
   * Gets the Javascript variable value from the GSP view. 
   * @return
   */
  private native String getIndexId() /*-{
    return $wnd.indexId;
  }-*/;

  /**
   * Gets the Javascript variable value from the GSP view. 
   * @return
   */
  private native String getUriIsLink() /*-{
    return $wnd.uriIsLink;
  }-*/;

  /**
   * The remote service used to communicate with the server.
   */
  private GwtRpcServiceAsync gwtRpcService;
  
  /**
   * The TextArea where the query string is typed by the user.
   */
  protected SuggestBox searchBox;
  
  /**
   * The Search button.
   */
  protected Button searchButton;
  
  /**
   * The current query ID (used when communicating with the server).
   */
  protected String queryId;
  
  /**
   * The current query string (used to re-post the query if the session expired
   * (e.g. the link was bookmarked).
   */
  protected String queryString;
  
  /**
   * Cached value for the current index ID (obtained once from 
   * {@link #getIndexId()}, then cached).
   */
  protected String indexId;
  
  /**
   * Cached value for the Javascript var (obtained once from 
   * {@link #getUriIsLink()}, then cached).
   */  
  protected boolean uriIsLink;
  
  /**
   * The label displaying feedback to the user (e.g. how many documents were 
   * found, or the current error message).
   */
  protected Label feedbackLabel;
  
  /**
   * The panel covering the centre of the page, where the results documents are
   * listed.
   */
  protected HTMLPanel searchResultsPanel;
  
  /**
   * The panel at the bottom of the page, containing links to other result 
   * pages. 
   */
  protected HTMLPanel pageLinksPanel;
  
  protected ResultsUpdater resultsUpdater;
  
  /**
   * The rank of the first document on page.
   */
  protected int firstDocumentOnPage;
  
  /**
   * How many documents should be shown on each result page.
   */
  protected int maxDocumentsOnPage = 20;
  
  /**
   * How many page links should be included at the bottom. The current page
   * would normally appear in the middle.
   */
  protected int maxPages = 20;
  
  /**
   * How many characters are displayed for each snippet (for longer snippets,
   * the middle content is truncated and replaced by an ellipsis).  
   */
  protected int maxSnippetLength = 150;
  
  /**
   * This is the entry point method.
   */
  public void onModuleLoad() {
    // connect to the server RPC endpoint
    gwtRpcService = (GwtRpcServiceAsync) GWT.create(GwtRpcService.class);
    ServiceDefTarget endpoint = (ServiceDefTarget) gwtRpcService;
    String rpcUrl = GWT.getHostPageBaseURL() + "gwtRpc";
    endpoint.setServiceEntryPoint(rpcUrl);
    
    indexId = getIndexId();
    uriIsLink = Boolean.parseBoolean(getUriIsLink());
    
    resultsUpdater = new ResultsUpdater(0);
    initLocalData();
    initGui();
    initListeners();
  }
  
  protected void initLocalData() {
    queryId = null;
    firstDocumentOnPage = 0;
  }
  
  protected void initGui() {
    HTMLPanel searchDiv = HTMLPanel.wrap(Document.get().getElementById("searchBox"));
    
    TextArea searchTextArea =  new TextArea(); 
    searchTextArea.setCharacterWidth(60);
    searchTextArea.setVisibleLines(10);
    searchBox = new SuggestBox(new MimirOracle(), searchTextArea);
    searchBox.setTitle("Press Escape to hide suggestions list; " +
    		"press Ctrl+Space to show it again.");
    searchBox.addStyleName("mimirSearchBox");
    searchDiv.add(searchBox);
    
    searchButton = new Button();
    searchButton.setText("Search");
    searchButton.addStyleName("searchButton");
    searchDiv.add(searchButton);
    
    HTMLPanel resultsBar = HTMLPanel.wrap(Document.get().getElementById("feedbackBar"));
    feedbackLabel = new InlineLabel();
    resultsBar.add(feedbackLabel);
    resultsBar.add(new InlineHTML("&nbsp;"));

    searchResultsPanel = HTMLPanel.wrap(Document.get().getElementById("searchResults"));
    updateResultsDisplay(null);
    
    pageLinksPanel = HTMLPanel.wrap(Document.get().getElementById("pageLinks"));
    pageLinksPanel.add(new InlineHTML("&nbsp;"));
  }
  
  protected void initListeners() {
    searchBox.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        int keyCode = event.getNativeKeyCode();
        if(keyCode == KeyCodes.KEY_ENTER && event.isControlKeyDown()){
          // CTRL-ENTER -> fire the query
          startSearch();
        } else if(keyCode == KeyCodes.KEY_ESCAPE) {
          ((SuggestBox.DefaultSuggestionDisplay)
              searchBox.getSuggestionDisplay()).hideSuggestions();
        } else if(keyCode == ' ' && event.isControlKeyDown()) {
          // CTRL-Space: show suggestions
          searchBox.showSuggestionList();
        }
        if(((SuggestBox.DefaultSuggestionDisplay)
            searchBox.getSuggestionDisplay()).isSuggestionListShowing()) {
          // gobble up navigation keys
          if(keyCode == KeyCodes.KEY_UP ||
             keyCode == KeyCodes.KEY_DOWN ||
             keyCode == KeyCodes.KEY_ENTER) {
            event.stopPropagation();
            event.preventDefault();
          }
        }
      }
    });
    
    searchBox.addKeyDownHandler(new KeyDownHandler() {
      @Override
      public void onKeyDown(KeyDownEvent event) {
        int keyCode = event.getNativeKeyCode();
        if(((SuggestBox.DefaultSuggestionDisplay)
            searchBox.getSuggestionDisplay()).isSuggestionListShowing()) {
          // gobble up navigation keys
          if(keyCode == KeyCodes.KEY_UP ||
             keyCode == KeyCodes.KEY_DOWN ||
             keyCode == KeyCodes.KEY_ENTER) {
            event.stopPropagation();
            event.preventDefault();
          }
        }
      }
    });
    
    searchBox.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        int keyCode = event.getNativeEvent().getKeyCode();
        if(((SuggestBox.DefaultSuggestionDisplay)
            searchBox.getSuggestionDisplay()).isSuggestionListShowing()) {
          // gobble up navigation keys
          if(keyCode == KeyCodes.KEY_UP ||
             keyCode == KeyCodes.KEY_DOWN ||
             keyCode == KeyCodes.KEY_ENTER) {
            event.stopPropagation();
            event.preventDefault();
          }
        }
      }
    });
    
    searchButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        startSearch();
      }
    });
    
    History.addValueChangeHandler(new ValueChangeHandler<String>() {
      @Override
      public void onValueChange(ValueChangeEvent<String> event) {
        String historyToken = event.getValue();
        logger.info("History token " + historyToken);
        if(historyToken != null && historyToken.length() > 0) {
          String newQueryId = null;
          String newQueryString = null;
          int newFirstDoc = 0;
          
          String[] elems = historyToken.split("\\&");
          for(String elem : elems) {
            String[] keyVal = elem.split("=", 2);
            String key = keyVal[0].trim();
            String value = keyVal[1].trim();
            if(key.equalsIgnoreCase("queryId")) {
              newQueryId = URL.decodeQueryString(value);
            } else if(key.equalsIgnoreCase("queryString")) {
              newQueryString = URL.decodeQueryString(value);
            } else if(key.equalsIgnoreCase("firstDoc")) {
              try{
                newFirstDoc =Integer.parseInt(value);
              } catch (NumberFormatException nfe) {
                // ignore, and start results from zero
              }
            }
          }
          // now update the display accordingly
          if(newQueryId != null && newQueryId.length() > 0) {
            queryId = newQueryId;
            queryString = newQueryString;
            if(!searchBox.getText().trim().equalsIgnoreCase(
              newQueryString.trim())){
              searchBox.setText(newQueryString);
            }
            resultsUpdater.setFirstDocument(newFirstDoc);
            resultsUpdater.schedule(10);
          }
        }
      }
    });
    // now read the current history
    String historyToken = History.getToken(); 
    if(historyToken != null && historyToken.length() > 0) {
      History.fireCurrentHistoryState();
    }
  }
  
  protected void updateResultsDisplay (List<DocumentData> documentsData) {
    searchResultsPanel.clear();
    if(documentsData == null || documentsData.isEmpty()) {
      for(int  i = 0; i < 20; i++) searchResultsPanel.add(new HTML("&nbsp;"));
    } else {
      for(DocumentData docData : documentsData) {
        searchResultsPanel.add(buildDocumentDisplay(docData));
      }      
    }
  }
  
  protected void startSearch() {
    // clean up old state
    if(queryId != null) {
      // release old query
      gwtRpcService.releaseQuery(queryId, new AsyncCallback<Void>() {
        @Override
        public void onSuccess(Void result) {}
        @Override
        public void onFailure(Throwable caught) {}
      });
    }
    // reset internal data
    initLocalData();
    // clear the results pages list
    pageLinksPanel.clear();
    // post the new query
    postQuery(searchBox.getText());
  }
  
  protected void postQuery(final String newQueryString) {
    feedbackLabel.setText("Working...");
    updateResultsDisplay(null);
    gwtRpcService.search(getIndexId(), newQueryString, new AsyncCallback<String>() {
      @Override
      public void onFailure(Throwable caught) {
        feedbackLabel.setText(caught.getLocalizedMessage());
      }
      @Override
      public void onSuccess(String newQueryId) {
        History.newItem(createHistoryToken(newQueryId, newQueryString, 
          firstDocumentOnPage));
      }
    });    
  }
  
  protected String createHistoryToken(String queryId, String queryString, 
                                      int firstDocument) {
    return "queryId=" + URL.encodeQueryString(queryId) + 
        "&queryString=" + URL.encodeQueryString(queryString) + 
        "&firstDoc=" + firstDocument;
  }
  
  /**
   * Updates the results display (including the feedback label)
   * @param resultsData
   */
  protected void updatePage(ResultsData resultsData) {
    int resTotal = resultsData.getResultsTotal();
    int resPartial = resultsData.getResultsPartial();
    StringBuilder textBuilder = new StringBuilder();
    if(resTotal == 0) {
      // no results
      textBuilder.append("No results, try rephrasing the query.");
    } else {
      textBuilder.append("Documents ");
      textBuilder.append(firstDocumentOnPage + 1);
      textBuilder.append(" to ");
      if(firstDocumentOnPage + maxDocumentsOnPage < resPartial) {
        textBuilder.append(firstDocumentOnPage + maxDocumentsOnPage);
      } else {
        textBuilder.append(resPartial - firstDocumentOnPage);
      }
      textBuilder.append(" of ");
      
      if(resTotal >= 0) {
        // all results obtained
        textBuilder.append(resultsData.getResultsTotal());
      } else {
        // more to come
        textBuilder.append("at least ");
        textBuilder.append(resPartial);
      }
      textBuilder.append(":");
    }
    
    feedbackLabel.setText(textBuilder.toString());
    // now update the documents display
    if(resultsData.getDocuments() != null){
      updateResultsDisplay(resultsData.getDocuments());
      // page links
      pageLinksPanel.clear();
      int currentPage = firstDocumentOnPage / maxDocumentsOnPage;
      int firstPage = Math.max(0, currentPage - (maxPages / 2));
      int maxPage = resultsData.getResultsPartial() / maxDocumentsOnPage;
      if(resultsData.getResultsPartial() % maxDocumentsOnPage > 0) maxPage++;
      maxPage = Math.min(maxPage, firstPage + maxPages);
      for(int pageNo = firstPage; pageNo < maxPage; pageNo++) {
        Widget pageLink;
        if(pageNo != currentPage) {
          pageLink = new InlineHyperlink("" + (pageNo + 1), 
            createHistoryToken(queryId, queryString, 
              pageNo * maxDocumentsOnPage));
        } else {
          pageLink = new InlineLabel("" + (pageNo + 1));
        }
        pageLink.addStyleName("pageLink");
        pageLinksPanel.add(pageLink);
      }      
    }
  }
  
  private HTMLPanel buildDocumentDisplay(DocumentData docData) {
    HTMLPanel documentDisplay = new HTMLPanel("");
    documentDisplay.setStyleName("hit");
    String documentUri = docData.documentUri;
    String documentTitle = docData.documentTitle;
    if(documentTitle == null || documentTitle.trim().length() == 0) {
      // we got no title to display: use the URI file
      String [] pathElems = documentUri.split("/");
      documentTitle = pathElems[pathElems.length -1];
    }
    String documentTitleText = "<span title=\"" + documentUri + 
        "\" class=\"document-title\">" +
        docData.documentTitle + "</span>";
    FlowPanel docLinkPanel = new FlowPanel();
//    docLinkPanel.setStyleName("document-title");
    if(uriIsLink) {
      // generate two links: original doc and cached
      docLinkPanel.add(new Anchor(documentTitleText, true, documentUri));
      docLinkPanel.add(new InlineLabel(" ("));
      docLinkPanel.add(new Anchor("cached", false, 
          "document?documentRank=" + docData.documentRank + 
          "&queryId=" + queryId));
      docLinkPanel.add(new InlineLabel(")"));
    } else {
      // generate one link: cached, with document name as text
      docLinkPanel.add(new Anchor(documentTitle, true,
          "document?documentRank=" + docData.documentRank + 
          "&queryId=" + queryId));
    }
    documentDisplay.add(docLinkPanel);
    if(docData.snippets != null) {
      StringBuilder snippetsText = new StringBuilder("<div class=\"snippets\">");
      // each row is left context, snippet, right context
      for(String[] snippet : docData.snippets) {
        snippetsText.append("<span class=\"snippet\">");
        snippetsText.append(snippet[0]);
        snippetsText.append("<span class=\"snippet-text\">");
        String snipText = snippet[1];
        int snipLen = snipText.length();
        if(snipLen > maxSnippetLength) {
          int toRemove = snipLen - maxSnippetLength;
          snipText = snipText.substring(0, (snipLen - toRemove) / 2) + 
              " ... " + 
              snipText.substring((snipLen + toRemove) / 2);
        }
        snippetsText.append(snipText);
        //close snippet-text span
        snippetsText.append("</span>");
        snippetsText.append(snippet[2]);
        //close snippet span
        snippetsText.append("</span>");
      }
      documentDisplay.add(new HTML(snippetsText.toString()));
    }
    return documentDisplay;
  }
}
