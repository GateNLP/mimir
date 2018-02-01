/*
 *  OriginalMarkupMetadataHelper.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 7 Oct 2009
 *
 *  $Id: OriginalMarkupMetadataHelper.java 17321 2014-02-17 11:11:40Z valyt $
 */
package gate.mimir.index;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import gate.Annotation;
import gate.AnnotationSet;
import gate.GateConstants;
import gate.mimir.DocumentMetadataHelper;
import gate.mimir.DocumentRenderer;
import gate.mimir.search.query.Binding;

/**
 * An implementation of {@link DocumentMetadataHelper} and 
 * {@link DocumentRenderer} that imports relevant markup tags from the indexed
 * document's original markups annotation set, saves them as document metadata
 * in the zip collection, and then uses these saved values to render the
 * document at search time.
 * 
 * The metadata saved by this class is stored in the main document metadata map 
 * using this class's name as a key. The value save is itself a Map, with 
 * multiple metadata fields. 
 */
public class OriginalMarkupMetadataHelper implements DocumentMetadataHelper, 
    DocumentRenderer {
  
  /**
   * Creates a new document helper/renderer.
   * @param markupAnnTypes the types of annotations from the original markups 
   * set that should be saved as document metadata (and used for rendering 
   * documents). If the value given for this parameter is <code>null</code>, 
   * then the default set of tags is used (see {@link #DEFAULT_TAG_TYPES}). 
   */
  public OriginalMarkupMetadataHelper(Set<String> markupAnnTypes) {
    if(markupAnnTypes == null){
      markupAnnTypes = new HashSet<String>();
      for(String aTag : DEFAULT_TAG_TYPES) markupAnnTypes.add(aTag);
    }
    this.markupAnnTypes = markupAnnTypes;
  }

  /**
   * Renders a document, using the saved original markup tags, and adding tags
   * for the provided query hits. This will generate HTML (XML, XHTML, etc) 
   * content by printing out the document tokens, spaces, and tags.
   * 
   * @param documentData the {@link DocumentData} object for the document to be
   * rendered.
   * @param hits the list of hits that need to also be rendered.
   * @param output a {@link Appendable} to which the output is written. 
   * @see gate.mimir.DocumentRenderer#render(gate.mimir.index.DocumentData, java.util.List, java.lang.Appendable)
   */
  public void render(DocumentData documentData, List<Binding> hits,
          Appendable output) throws IOException {
    String[] tokens = documentData.getTokens();
    String[] nonTokens = documentData.getNonTokens();
    
    DocumentTags docTags = (DocumentTags)getMetadataField(documentData, TAGS_KEY);
    //tags that have been opened and need to close:
    //key = token offset for close tag
    //value: list of tag IDs that end at that location
    SortedMap<Integer, LinkedList<String>> spansToEnd = 
      new TreeMap<Integer, LinkedList<String>>();
    Iterator<int[]> tagIter = docTags.tags != null ? 
            docTags.tags.iterator() : null;
    int[] currentTag = (tagIter != null && tagIter.hasNext()) ? 
            tagIter.next() : null;
    Iterator<Binding> hitIter = hits != null ? hits.iterator() : null;
    Binding currentHit = (hitIter != null && hitIter.hasNext()) ? 
            hitIter.next() : null;
    for(int tokIdx = 0; tokIdx < tokens.length; tokIdx++){
      if(docTags != null){
        //check if we need to open any tags here
        while((currentTag != null && currentTag[1] == tokIdx) ||
              (currentHit != null && currentHit.getTermPosition() == tokIdx)){
          //we need to open a tag or a hit
          if(currentTag != null && currentTag[1] == tokIdx &&
             currentHit != null && currentHit.getTermPosition() == tokIdx){
            //we have both a tag and a hit, starting at the same position
            //we start the one that ends later, with a preference for a tag
            //(as hits should be inner-most)
            if(currentTag[2] >= (currentHit.getTermPosition() + currentHit.getLength())){
              //consume the TAG
              String openingTag = docTags.tagDescriptors.get(currentTag[0]);
              output.append(openingTag);
              String closingTag = getClosingTag(openingTag);
              if(currentTag[2] == -1) {
                // zero-length tag
                output.append(closingTag);
              } else {
                LinkedList<String> spans = spansToEnd.get(currentTag[2]);
                if(spans == null){
                  spans = new LinkedList<String>();
                  spansToEnd.put(currentTag[2], spans);
                }
                spans.addFirst(closingTag);                
              }
              //consume the tag
              currentTag = (tagIter != null && tagIter.hasNext()) ? 
                      tagIter.next() : null;
            }else{
              //consume the HIT
              output.append(HIT_OPENING_TAG);
              int spanEnd = currentHit.getTermPosition() + currentHit.getLength() -1; 
              LinkedList<String> spans = spansToEnd.get(spanEnd);
              if(spans == null){
                spans = new LinkedList<String>();
                spansToEnd.put(spanEnd, spans);
              }
              spans.addFirst(HIT_CLOSING_TAG);
              //consume the hit
              currentHit = (hitIter != null && hitIter.hasNext()) ? 
                      hitIter.next() : null;
            }
          }else if(currentTag != null && currentTag[1] == tokIdx){
            //we only have a TAG to use
            String openingTag = docTags.tagDescriptors.get(currentTag[0]);
            output.append(openingTag);
            String closingTag = getClosingTag(openingTag);
            if(currentTag[2] == -1) {
              // zero-length tag
              output.append(closingTag);
            } else {
              LinkedList<String> spans = spansToEnd.get(currentTag[2]);
              if(spans == null){
                spans = new LinkedList<String>();
                spansToEnd.put(currentTag[2], spans);
              }
              spans.addFirst(closingTag);                
            }
            //consume the tag
            currentTag = (tagIter != null && tagIter.hasNext()) ? 
                    tagIter.next() : null;
          }else{
            //we only have a HIT to use
            output.append(HIT_OPENING_TAG);
            int spanEnd = currentHit.getTermPosition() + currentHit.getLength() -1;
            LinkedList<String> spans = spansToEnd.get(spanEnd);
            if(spans == null){
              spans = new LinkedList<String>();
              spansToEnd.put(spanEnd, spans);
            }
            spans.addFirst(HIT_CLOSING_TAG);
            //consume the hit
            currentHit = (hitIter != null && hitIter.hasNext()) ? 
                    hitIter.next() : null;
          }
        }
      }
      //write the token
      output.append(tokens[tokIdx]);
      
      //check if we need to close any spans here
      while(spansToEnd.size() > 0 && spansToEnd.firstKey() == tokIdx){
        LinkedList<String> closingTags = spansToEnd.remove(spansToEnd.firstKey());
        for(String aTag : closingTags){
          output.append(aTag);
        }
      }
      //write the non-token, if any
      if(tokIdx < nonTokens.length) output.append(nonTokens[tokIdx]);

    }
  }

  /* (non-Javadoc)
   * @see gate.mimir.index.DocumentMetadataHelper#documentEnd(gate.Document, gate.mimir.index.mg4j.zipcollection.DocumentData)
   */
  public void documentEnd(GATEDocument document, DocumentData documentData) {
    //here we need to store the relevant markup as a metadata field
    DocumentTags documentTags = new DocumentTags();
    //a list of annotations to save
    AnnotationSet omSet = document.getDocument().getAnnotations(
            GateConstants.ORIGINAL_MARKUPS_ANNOT_SET_NAME);
    AnnotationSet tagsSet = null;
    synchronized(omSet) {
      tagsSet = omSet.get(markupAnnTypes);
    }
    List<Annotation> tagsToSave = new ArrayList<Annotation>(tagsSet);
    Collections.sort(tagsToSave, new StartComparator());
    //a structure holding the tags that need to be closed.
    //key (long): the document offset where the tag should close
    //value (int): the index in the tags array of the current document metadata
    SortedMap<Long, LinkedList<Integer>> tagsToEnd = 
      new TreeMap<Long, LinkedList<Integer>>();
    Annotation[] tokens = document.getTokenAnnots();
    Iterator<Annotation> tagsiter = tagsToSave.iterator();
    Annotation currentTag = tagsiter.hasNext() ? tagsiter.next() : null;
    long tagStart = currentTag == null ? -1 : currentTag.getStartNode().getOffset();
    long tagEnd = currentTag == null ? -1 : currentTag.getEndNode().getOffset();
    for(int tokIdx = 0; tokIdx < tokens.length; tokIdx++) {
      long tokStart = tokens[tokIdx].getStartNode().getOffset();
      long tokEnd = tokens[tokIdx].getEndNode().getOffset();
      //see if there are any tags to close at this offset
      while(tagsToEnd.size() > 0 && tagsToEnd.firstKey() <= tokStart){
        //get all tags ending inside the previous token or the space before the 
        //current token
        LinkedList<Integer> tags = tagsToEnd.remove(tagsToEnd.firstKey());
        for(int aTag : tags){
          documentTags.tags.get(aTag)[2] = tokIdx -1;
        }
      }
      //see if we need to save any tags at this offset
      while(currentTag != null){
        if(tagStart < tokEnd){
          //the current tag starts within the current token
          int tagDescId = getTagId(currentTag, documentTags);
          documentTags.tags.add(new int[]{tagDescId, tokIdx, -1});
          if(tagEnd <= tokStart){
            // the tag starts and ends before the current token starts, so it's
            // either zero-length, or whitespace-only
            // leave the end position as -1.
          } else {
            // not a zero-length tag, 
            // so we'll need to find the closing position later
            LinkedList<Integer> tagsEnding = tagsToEnd.get(tagEnd);
            if(tagsEnding == null){
              tagsEnding = new LinkedList<Integer>();
              tagsToEnd.put(tagEnd, tagsEnding);
            }
            tagsEnding.addFirst(documentTags.tags.size() -1);            
          }
          //update the current tag
          currentTag = tagsiter.hasNext() ? tagsiter.next() : null;
          tagStart = currentTag == null ? -1 : currentTag.getStartNode().getOffset();
          tagEnd = currentTag == null ? -1 : currentTag.getEndNode().getOffset();
        }else{
          //current tag not inside the current token yet.
          break;
        }
      }//while currentTag != null
    }//for tokens
    while(tagsToEnd.size() > 0){
      //we did not close all tags yet
      int tokIdx = tokens.length -1;
      LinkedList<Integer> tags = tagsToEnd.remove(tagsToEnd.firstKey());
      for(int aTag : tags){
        documentTags.tags.get(aTag)[2] = tokIdx;
      }
    }
    
    while(currentTag != null){
      //we did not exhaust all tags, we'll assign all remaining tags to the last
      //token
      int tokIdx = tokens.length -1;
      int tagDescId = getTagId(currentTag, documentTags);
      documentTags.tags.add(new int[]{tagDescId, tokIdx, tokIdx});
      //update the current tag
      currentTag = tagsiter.hasNext() ? tagsiter.next() : null;
      tagStart = currentTag == null ? -1 : currentTag.getStartNode().getOffset();
      tagEnd = currentTag == null ? -1 : currentTag.getEndNode().getOffset();
    }
    addMetadataField(documentData, TAGS_KEY, documentTags);
  }

  
  /**
   * Adds a new field to the metadata map saved by this class. 
   * @param key
   * @param value
   */
  protected void addMetadataField(DocumentData documentData, String key, 
          Serializable value){
    @SuppressWarnings("unchecked")
    HashMap<String, Serializable> myMetadata = 
      (HashMap<String, Serializable>)documentData.getMetadataField(
              getClass().getName());
    if(myMetadata == null){
      //this is the first time - let's add the map.
      myMetadata = new HashMap<String, Serializable>();
      documentData.putMetadataField(getClass().getName(), myMetadata);
    }
    myMetadata.put(key, value);
  }

  
  /**
   * Gets a metadata field value from the metadata map saved by this class. 
   * @param key
   * @param value
   */
  protected Serializable getMetadataField(DocumentData documentData, String key){
    @SuppressWarnings("unchecked")
    HashMap<String, Serializable> myMetadata = 
      (HashMap<String, Serializable>)documentData.getMetadataField(
              getClass().getName());
    return myMetadata == null ? null : myMetadata.get(key);
  }
  
  /**
   * Calculates the closing tag for a  given opening tag.
   * @param openingTag
   * @return
   */
  protected static String getClosingTag(String openingTag){
    StringBuilder closeTag = new StringBuilder("</");
    //are we inside the name?
    boolean inName = false;
    for(int charIdx = 0; charIdx < openingTag.length(); charIdx++){
      char currentChar = openingTag.charAt(charIdx);
      if(inName){
        //we're consuming non-space or > characters
        if(currentChar == ' ' || currentChar == '>'){
          //we're done!
          break;
        }else{
          closeTag.append(currentChar);
        }
      }else{
        //we're looking for the opening <
        if(currentChar == '<') inName = true;
      }
    }
    closeTag.append('>');
    return closeTag.toString();
  }
  
  /**
   * Gets the ID in the current list of tag descriptors for a given annotation
   * @param ann
   * @return
   */
  protected int getTagId(Annotation ann, DocumentTags documentMetadata){
    StringBuilder tagDesc = new StringBuilder("<");
    tagDesc.append(tagNameForAnnotation(ann));
    List<String> featNames = new ArrayList<String>();
    for(Map.Entry<Object, Object> entry : ann.getFeatures().entrySet()){
      if((entry.getKey() instanceof String) && (entry.getValue() != null)){
        featNames.add((String)entry.getKey());
      }
    }
    Collections.sort(featNames);
    for(String featName : featNames){
      String featValue = ann.getFeatures().get(featName).toString().replace("\"", "&quot;");
      tagDesc.append(' ');
      tagDesc.append(featName);
      tagDesc.append("=\"");
      tagDesc.append(featValue);
      tagDesc.append('"');
    }
    tagDesc.append(">");
    return documentMetadata.getTagDescriptorIndex(tagDesc.toString());
  }

  /**
   * Returns the tag name that should be used to represent the given
   * annotation.  This implementation simply returns the (trimmed)
   * annotation type, but subclasses may implement a more sophisticated
   * mapping.
   * 
   * @param ann an annotation
   * @return the tag name that should be used to represent the annotation
   */
  protected String tagNameForAnnotation(Annotation ann) {
    return ann.getType().trim();
  }
  
  
  /* (non-Javadoc)
   * @see gate.mimir.index.DocumentMetadataHelper#documentStart(gate.Document)
   */
  public void documentStart(GATEDocument document) {
    //we do nothing here
  }
  
  
  public static final String TAGS_KEY = "tags";
  
  /**
   * The names of the original tags that should be preserved as 
   * document metadata in the zip collection, and used for rendering documents.
   */
  public static final String[] DEFAULT_TAG_TYPES = new String[]{
    "b", "div", "i", "li", "ol", "p", "span", "sup", "sub", "table", "th", "td", "tr", "u", "ul"};

  /**
   * The tag used to mark-up query hits (opening tag).
   */
  public static final String HIT_OPENING_TAG = "<span class=\"mimir-hit\">";

  
  /**
   * The tag used to mark-up query hits (closing tag).
   */
  public static final String HIT_CLOSING_TAG = "</span>";
  
  /**
   * The types of annotations to be saved as markup metadata.
   */
  private Set<String> markupAnnTypes;
  
  /**
   * An object storing a list of tags (obtained from the original markup) that 
   * should be saved as document metadata. They are stored as triples of int 
   * values:
   * <ol>
   *   <li>the index in the {@link #tagDescriptors} array for the tag</li>
   *   <li>the start offset for the tag (in terms of token position);</li>
   *   <li>the end offset for the tag (in terms of token position); That is the
   *   position of the last token that is part of this tag.  Zero-length tags
   *   are represented by setting this position to -1.</li>
   * </ol>
   * 
   */
  protected static class DocumentTags implements Serializable{
    
    /**
     * Serialisation UID 
     */
    private static final long serialVersionUID = 5449290166356815305L;
    
    
    public DocumentTags(){
      tagDescriptorsSet = new HashSet<String>();
      tagDescriptors = new ArrayList<String>();
      tags = new ArrayList<int[]>();
    }
    
    /**
     * Gets the index in the {@link #tagDescriptors} list for a given tag 
     * descriptor. If no such descriptor is known, then a new one is added to 
     * the {@link #tagDescriptors} list and its index is returned. 
     * @param tagDescriptor
     * @return
     */
    public int getTagDescriptorIndex(String tagDescriptor){
      if(tagDescriptorsSet.add(tagDescriptor)){
        tagDescriptors.add(tagDescriptor);
        return tagDescriptors.size() -1;
      }else{
        return tagDescriptors.indexOf(tagDescriptor);
      }
    }
    
    
    @Override
    public String toString() {
      StringBuffer str = new StringBuffer();
      boolean first = true;
      for(int[] aTag : tags) {
        if(first) first = false;
        else str.append(' ');
        str.append(tagDescriptors.get(aTag[0])).append('(').append(aTag[1])
            .append(':').append(aTag[2]).append(')');
      }
      return str.toString();
    }
    
    /**
     * A set used internally to ensure uniqueness of the tag descriptors. 
     */
    private transient Set<String> tagDescriptorsSet;
    
    /**
     * A list of strings storing tag descriptors that are used to reduce the 
     * size of data stored as metadata.
     */
    private List<String> tagDescriptors;
    
    /**
     * A list of tags that are to be stored as document metadata. Each element 
     * is an array if 3 ints:
     * <ol>
     *   <li>the index in the {@link #tagDescriptors} array for the tag</li>
     *   <li>the start offset for the tag (in terms of token position);</li>
     *   <li>the end offset for the tag (in terms of token position);</li>
     * </ol>
     * 
     * This list is ordered based on the start offset, end offset and ID of the 
     * GATE annotations from which it was constructed, so that the tags should
     * appear in the correct document order. 
     */
    private List<int[]> tags;
    
    
  }
  
  /**
   * Compares annotation by start offset, end offset, and ID. Can be used to 
   * sort a list of markup tags in [a good approximation of] document order with
   * regard to their starting position.
   */
  protected static class StartComparator implements Comparator<Annotation>{

    /* (non-Javadoc)
     * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
     */
    public int compare(Annotation ann1, Annotation ann2) {
      long start1 = ann1.getStartNode().getOffset();
      long start2 = ann2.getStartNode().getOffset();
      if(start1 < start2){
        return -1;
      } else if(start1 > start2){
        return 1;
      }else{
        //same start offset
        long end1 = ann1.getEndNode().getOffset();
        long end2 = ann2.getEndNode().getOffset();
        if(end1 < end2){
          //first annotation ends sooner, so should start later
          return 1;
        }else if(end1 > end2){
          return -1;
        }else{
          //same end offset too -> used ann ID
         return ann1.getId() - ann2.getId(); 
        }
      }
    }
  }
}
