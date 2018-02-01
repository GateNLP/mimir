/*
 *  Constraint.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 5 Aug 2009
 *
 *  $Id: Constraint.java 14541 2011-11-14 19:31:23Z ian_roberts $
 */
package gate.mimir;

import java.io.Serializable;

/**
 * A constraint over an annotation feature value.
 */
public class Constraint implements Serializable{
	
  private static final long serialVersionUID = -4417366268078605872L;

  /**
   * The name of the feature to be tested. 
   */
  protected String featureName;

  /**
   * The predicate of the constraint (which condition should be satisfied).
   */
  protected ConstraintType predicate;

  /**
   * The value to be compared with the feature value (i.e. the right-hand 
   * operand of the constraint predicate)..
   */
  protected Object value;

  /**
   * Creates a constraint for an annotation feature.
   * @param predicate the predicate (condition to be checked). 
   * @param featureName the name of the feature to be tested.
   * @param value the value to be tested against (i.e. the right-hand operand
   * of the constraint predicate). This value should be of type {@link String}, or a 
   * subclass of {@link Number} for compare tests. In the case of 
   * {@link ConstraintType#REGEX} constraints, the value should be either one single
   * {@link String} (if only pattern is provided) or an array of two {@link String} 
   * values (the first being the pattern, the second being the flags).
   */
  public Constraint(ConstraintType predicate, String featureName, Object value) {
    this.predicate = predicate;
    this.featureName = featureName;
    this.value = value;
  }

  /**
   * @return the featureName
   */
  public String getFeatureName() {
    return featureName;
  }

  /**
   * @return the predicate
   */
  public ConstraintType getPredicate() {
    return predicate;
  }
  
  /**
   * @return the value
   */
  public Object getValue() {
    return value;
  }
  
  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    StringBuilder str = new StringBuilder(featureName);
    if(predicate == ConstraintType.REGEX){
      str.append(".REGEX(");
      if(value instanceof String[]){
        str.append(((String[])value)[0] + ", ");
        str.append(((String[])value)[1]);
      }else{
        str.append(value);
      }
      str.append(")");
    }else{
      str.append(" " + predicate + " " + value);
    }
    return str.toString();
  }

}
