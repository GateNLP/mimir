/*
 *  MeasurementAnnotationHelper.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 05 Aug 2009
 *  
 *  $Id: MeasurementAnnotationHelper.java 17261 2014-01-30 14:05:14Z valyt $
 */
package gate.mimir.measurements;

import gate.Gate;
import gate.creole.Parameter;
import gate.creole.ParameterException;
import gate.creole.ParameterList;
import gate.creole.ResourceReference;
import gate.creole.measurements.Measurement;
import gate.creole.measurements.MeasurementsParser;
import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.Constraint;
import gate.mimir.ConstraintType;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.AtomicAnnotationIndex;
import gate.mimir.index.Mention;
import gate.mimir.search.QueryEngine;
import gate.mimir.util.DelegatingSemanticAnnotationHelper;
import gate.util.GateRuntimeException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link SemanticAnnotationHelper} that supports querying of Measurement
 * annotations produced by the GATE <code>Tagger_Measurements</code> plugin.
 * The annotations are indexed by their normalized value and unit but can
 * be queried using a "virtual" feature named <code>spec</code>, expressing
 * a measurement specification in terms such as "1 to 3 feet".  The spec
 * is mapped into a range query over the appropriate normalized values, so
 * can match annotations that express the same measurement in different
 * terms (e.g. "400mm").
 */
public class MeasurementAnnotationHelper extends
                                        DelegatingSemanticAnnotationHelper {

  private static final long serialVersionUID = 1875846689536452288L;

  protected static final String ANNOTATION_TYPE = "Measurement";

  protected static final String TYPE_FEATURE = "type";

  protected static final String TYPE_SCALAR = "scalar";

  protected static final String TYPE_INTERVAL = "interval";

  protected static final String DIMENSION_FEATURE = "dimension";

  protected static final String NORM_UNIT_FEATURE = "normalizedUnit";

  protected static final String NORM_VALUE_FEATURE = "normalizedValue";

  protected static final String NORM_MIN_VALUE_FEATURE = "normalizedMinValue";

  protected static final String NORM_MAX_VALUE_FEATURE = "normalizedMaxValue";

  protected static final String SPEC_FAKE_FEATURE = "spec";
  
  protected static final String OLD_DEFAULT_UNITS_FILE = "resources/units.dat";

  protected String unitsFileLocation = null;

  protected static final String OLD_DEFAULT_COMMON_WORDS = "resources/common_words.txt";
  
  protected String commonWordsLocation = null;
  
  protected String locale = null;
  
  protected String encoding = null;
  
  /**
   * The measurements parser used to parse the virtual "spec" feature.
   */
  protected transient MeasurementsParser measurementsParser;
  
  protected transient Class<? extends AbstractSemanticAnnotationHelper> delegateHelperType;

  public MeasurementAnnotationHelper() {
    super();
    setAnnotationType(ANNOTATION_TYPE);
    // our nominal features include "spec", those passed to the delegate
    // at init() time will not.
    super.setNominalFeatures(new String[]{SPEC_FAKE_FEATURE, TYPE_FEATURE,
      DIMENSION_FEATURE, NORM_UNIT_FEATURE});
    super.setFloatFeatures(new String[]{NORM_VALUE_FEATURE,
      NORM_MIN_VALUE_FEATURE, NORM_MAX_VALUE_FEATURE});
  }
  
  public String getUnitsFile() {
    return unitsFileLocation;
  }

  public void setUnitsFile(String unitsFile) {
    this.unitsFileLocation = unitsFile;
  }

  public String getCommonWords() {
    return commonWordsLocation;
  }

  public void setCommonWords(String commonWords) {
    this.commonWordsLocation = commonWords;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getEncoding() {
    return encoding;
  }

  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  public Class<? extends AbstractSemanticAnnotationHelper> getDelegateHelperType() {
    return delegateHelperType;
  }

  public void setDelegateHelperType(Class<? extends AbstractSemanticAnnotationHelper> delegateHelperType) {
    this.delegateHelperType = delegateHelperType;
  }

  // override feature setters as Measurement helper does not support additional
  // features

  @Override
  public void setNominalFeatures(String[] features) {
    throw new UnsupportedOperationException("MeasurementAnnotationHelper uses "
        + "a fixed feature set.");
  }

  @Override
  public void setIntegerFeatures(String[] features) {
    throw new UnsupportedOperationException("MeasurementAnnotationHelper uses "
        + "a fixed feature set.");
  }

  @Override
  public void setFloatFeatures(String[] features) {
    throw new UnsupportedOperationException("MeasurementAnnotationHelper uses "
        + "a fixed feature set.");
  }

  @Override
  public void setTextFeatures(String[] features) {
    throw new UnsupportedOperationException("MeasurementAnnotationHelper uses "
        + "a fixed feature set.");
  }

  @Override
  public void setUriFeatures(String[] features) {
    throw new UnsupportedOperationException("MeasurementAnnotationHelper uses "
        + "a fixed feature set.");
  }

  @Override
  public void init(AtomicAnnotationIndex indexer) {
    // create the delegate - needs to happen before super.init
    if(getDelegate() == null) {
      if(delegateHelperType == null) { throw new IllegalArgumentException(
          "No value provided for delegateHelperType"); }
        try {
          AbstractSemanticAnnotationHelper theDelegate =
            delegateHelperType.newInstance();
          theDelegate.setAnnotationType(getAnnotationType());
          // nominal features for delegate do not include spec
          theDelegate.setNominalFeatures(new String[]{TYPE_FEATURE,
            DIMENSION_FEATURE, NORM_UNIT_FEATURE});
          theDelegate.setFloatFeatures(new String[]{NORM_VALUE_FEATURE,
            NORM_MIN_VALUE_FEATURE, NORM_MAX_VALUE_FEATURE});
          
          setDelegate(theDelegate);
        } catch(Exception e) {
          throw new IllegalArgumentException("The delegate helper class " +
            delegateHelperType.getName() + " could not be instantiated", e);
        }
    }
    super.init(indexer);
    
    String theEncoding = encoding;
    if(theEncoding == null) {
      theEncoding = (String)defaultParamValue("encoding");
    }
    String theLocale = locale;
    if(theLocale == null) {
      theLocale = (String)defaultParamValue("locale");
    }
    try {
      ResourceReference commonUrl = null;
      if(commonWordsLocation == null || OLD_DEFAULT_COMMON_WORDS.equals(commonWordsLocation)) {
        commonUrl = (ResourceReference)defaultParamValue("commonURL");
      } else {
        commonUrl = new ResourceReference(URI.create(commonWordsLocation));
      }
      ResourceReference unitsUrl = null;
      if(unitsFileLocation == null || OLD_DEFAULT_UNITS_FILE.equals(unitsFileLocation)) {
        unitsUrl = (ResourceReference)defaultParamValue("unitsURL");
      } else {
        unitsUrl = new ResourceReference(URI.create(unitsFileLocation));
      }

      measurementsParser = new MeasurementsParser(theEncoding, theLocale,
              unitsUrl.toURL(), commonUrl.toURL());
    } catch(IOException | URISyntaxException e) {
      throw new GateRuntimeException(
              "Could not create measurements parser for MeasurementAnnotationHelper", e);
    }
  }
  
  protected Object defaultParamValue(String paramName) {
    try {
      ParameterList pl = Gate.getCreoleRegister().get("gate.creole.measurements.MeasurementsTagger").getParameterList();
      for(List<Parameter> disj : pl.getInitimeParameters()) {
        for(Parameter p : disj) {
          if(p.getName().equals(paramName)) {
            return p.getDefaultValue();
          }
        }
      }
    } catch(ParameterException e) {
      // ignore
    }

    throw new GateRuntimeException("Couldn't find parameter " + paramName + " in MeasurementsTagger PR");
  }
  
  protected List<Constraint>[] convertSpecConstraint(Constraint specConstraint){
    if(!(specConstraint.getValue() instanceof String)) { throw new IllegalArgumentException(
            "The custom feature " + SPEC_FAKE_FEATURE
                    + " only accepts String values!"); }
    if(specConstraint.getPredicate() != ConstraintType.EQ) { throw new IllegalArgumentException(
            "The custom feature " + SPEC_FAKE_FEATURE
                    + " only accepts 'equals' constraints!"); }
    String specString = (String)specConstraint.getValue();
    // a spec string is one of:
    // "<number> <unit>", or
    // "<number> to <number> <unit>"
    String[] elements = specString.split("\\s+");
    if(elements.length < 2){
      throw new IllegalArgumentException(
              "Invalid measurement specification; the valid syntax is:\n" +
              "number unit, or" +
              "number to number unit");
    }
    if(elements[1].equalsIgnoreCase("to")) {
      //interval
      if(elements.length < 4){
        throw new IllegalArgumentException(
                "Invalid measurement specification; the valid syntax is:\n" +
                "number unit, or\n" +
                "number to number unit");
      }
      double minValue;
      try{
        minValue = Double.parseDouble(elements[0]);
      }catch(NumberFormatException e) {
        throw new IllegalArgumentException(elements[0] + 
                " is not a valid number", e);
      }
      double maxValue;
      try{
        maxValue = Double.parseDouble(elements[2]);
      }catch(NumberFormatException e) {
        throw new IllegalArgumentException(elements[2] + 
                " is not a valid number", e);
      }
      StringBuilder unitBuilder = new StringBuilder(elements[3]);
      for(int i = 4; i < elements.length; i++){
        unitBuilder.append(' ');
        unitBuilder.append(elements[i]);
      }
      String unit = unitBuilder.toString();
      //parse the two values
      Measurement minV = measurementsParser.parse(minValue, unit);
      if(minV == null){
        throw new IllegalArgumentException("Don't understand measurement "+
                minValue + " " + unit + ". Please rephrase."); 
      }
      Measurement maxV = measurementsParser.parse(maxValue, unit);
      if(maxV == null){
        throw new IllegalArgumentException("Don't understand measurement "+
                minValue + " " + unit + ". Please rephrase."); 
      }
      //finally, rephrase the query in terms that the standard helper 
      //understands.

      //we need to match simple measurements that fall inside the interval
      List<Constraint> alternative1 = new ArrayList<Constraint>();
//      alternative1.add(new Constraint(ConstraintType.EQ, TYPE_FEATURE, 
//              TYPE_SCALAR));
      alternative1.add(new Constraint(ConstraintType.EQ, NORM_UNIT_FEATURE, 
              maxV.getNormalizedUnit()));
      alternative1.add(new Constraint(ConstraintType.GE, NORM_VALUE_FEATURE, 
              minV.getNormalizedValue()));
      alternative1.add(new Constraint(ConstraintType.LE, NORM_VALUE_FEATURE, 
              maxV.getNormalizedValue()));
      //we need to match interval measurements whose start falls inside the interval
      List<Constraint> alternative2 = new ArrayList<Constraint>();
//      alternative2.add(new Constraint(ConstraintType.EQ, TYPE_FEATURE, 
//              TYPE_INTERVAL));
      alternative2.add(new Constraint(ConstraintType.EQ, NORM_UNIT_FEATURE, 
              maxV.getNormalizedUnit()));
      alternative2.add(new Constraint(ConstraintType.GE, NORM_MIN_VALUE_FEATURE, 
              minV.getNormalizedValue()));
      alternative2.add(new Constraint(ConstraintType.LE, NORM_MIN_VALUE_FEATURE, 
              maxV.getNormalizedValue()));
      //we need to match interval measurements whose end falls inside the interval
      List<Constraint> alternative3 = new ArrayList<Constraint>();
//      alternative3.add(new Constraint(ConstraintType.EQ, TYPE_FEATURE, 
//              TYPE_INTERVAL));
      alternative3.add(new Constraint(ConstraintType.EQ, NORM_UNIT_FEATURE, 
              maxV.getNormalizedUnit()));
      alternative3.add(new Constraint(ConstraintType.GE, NORM_MAX_VALUE_FEATURE, 
              minV.getNormalizedValue()));
      alternative3.add(new Constraint(ConstraintType.LE, NORM_MAX_VALUE_FEATURE, 
              maxV.getNormalizedValue()));
      return new List[]{alternative1, alternative2, alternative3};
    }else{
      //simple measurement
      double value;
      try{
        value = Double.parseDouble(elements[0]);
      }catch(NumberFormatException e) {
        throw new IllegalArgumentException(elements[0] + 
                " is not a valid number", e);
      }
      StringBuilder unitBuilder = new StringBuilder(elements[1]);
      for(int i = 2; i < elements.length; i++){
        unitBuilder.append(' ');
        unitBuilder.append(elements[i]);
      }
      String unit = unitBuilder.toString();
      //parse the single value
      Measurement valV = measurementsParser.parse(value, unit);
      if(valV == null){
        throw new IllegalArgumentException("Don't understand measurement "+
                value + " " + unit + ". Please rephrase."); 
      }
      
      //we need to match simple measurements that are equal to the value (with 
      //the double precision taken into account)      
      List<Constraint> alternative1 = new ArrayList<Constraint>();
//      alternative1.add(new Constraint(ConstraintType.EQ, TYPE_FEATURE, 
//              TYPE_SCALAR));
      alternative1.add(new Constraint(ConstraintType.EQ, NORM_UNIT_FEATURE, 
              valV.getNormalizedUnit()));
      alternative1.add(new Constraint(ConstraintType.GE, NORM_VALUE_FEATURE, 
              valV.getNormalizedValue() - Double.MIN_VALUE));
      alternative1.add(new Constraint(ConstraintType.LE, NORM_VALUE_FEATURE, 
              valV.getNormalizedValue() + Double.MIN_VALUE));
      //we need to match interval measurements which contain the value
      List<Constraint> alternative2 = new ArrayList<Constraint>();
//      alternative2.add(new Constraint(ConstraintType.EQ, TYPE_FEATURE, 
//              TYPE_INTERVAL));
      alternative2.add(new Constraint(ConstraintType.EQ, NORM_UNIT_FEATURE, 
              valV.getNormalizedUnit()));
      alternative2.add(new Constraint(ConstraintType.LE, NORM_MIN_VALUE_FEATURE, 
              valV.getNormalizedValue()));
      alternative2.add(new Constraint(ConstraintType.GE, NORM_MAX_VALUE_FEATURE, 
              valV.getNormalizedValue()));
      return new List[]{alternative1, alternative2};
    }
  }

  /**
   * Query for Measurement annotations, handling the "spec" feature by
   * converting it to the equivalent constraints on the indexed features.
   */
  @Override
  public List<Mention> getMentions(String annotationType,
          List<Constraint> constraints, QueryEngine engine) {
    List<Constraint> passThroughConstraints = new ArrayList<Constraint>(
            constraints.size());
    Constraint specConstraint = null;
    //filter custom constraints
    for(Constraint aConstraint : constraints) {
      if(aConstraint.getFeatureName().equals(SPEC_FAKE_FEATURE)) {
        if(specConstraint != null){
          throw new IllegalArgumentException("Only one constraint of type ." +
                  SPEC_FAKE_FEATURE + " is permitted!"); 
        }else{
          specConstraint = aConstraint;
        }
      } else {
        passThroughConstraints.add(aConstraint);
      }
    }
    if(specConstraint == null){
      //no custom constraints
      return super.getMentions(annotationType, passThroughConstraints, engine);
    }else{
      //process the custom constraints
      List<Constraint>[] alternatives = convertSpecConstraint(specConstraint);
      //use Set to avoid duplicates
      Set<Mention> results = new HashSet<Mention>();
      for(List<Constraint> anAlternative : alternatives){
        List<Constraint> superConstraints = new ArrayList<Constraint>(
                passThroughConstraints.size() + anAlternative.size());
        superConstraints.addAll(passThroughConstraints);
        superConstraints.addAll(anAlternative);
        results.addAll(super.getMentions(annotationType, superConstraints, 
                engine));
      }
      return new ArrayList<Mention>(results);
    }
  }
}
