/*
 *  Query.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id: Query.java 16266 2012-11-13 15:56:47Z valyt $
 */
package gate.mimir.search.query.parser;


import gate.mimir.ConstraintType;

import java.util.*;


public interface Query {
	public String toString(String space);
}

class TermQuery implements Query {
	public String term;
	public String index;

	public String toString(String space) {
		return space + "TermQuery : " + term + "=>" + index;
	}
}

class FeatureValuePair {
	public String feature;
	public Object value;
	int constraintType;
	final static int EQ = 0, LT = 1, GT = 2, LE = 3, GE = 4, REGEX = 5;
	
	
}

class AnnotationQuery implements Query {
	public String type;
	public List<FeatureValuePair> pairs = new ArrayList<FeatureValuePair>();

	public void add(int constraintType, String feature, Object value) {
		FeatureValuePair fvp = new FeatureValuePair();
		fvp.feature = feature;
		fvp.value = value;
		fvp.constraintType = constraintType;
		pairs.add(fvp);
	}

	public void add(int constraintType, String feature,
			Object value, String flags) {
		FeatureValuePair fvp = new FeatureValuePair();
		fvp.feature = feature;
		fvp.value = value;
		fvp.constraintType = constraintType;
		pairs.add(fvp);
	}

	public String toString(String space) {
		StringBuilder sb = new StringBuilder();
		sb.append(space + "AnnotationQuery:{\n");
		sb.append(space + "  type=" + type + "\n");
		sb.append(space + "  features:\n");
		for (FeatureValuePair fvp : pairs) {
			
			sb.append(space + "    " + fvp.feature + ":" + fvp.value + "\n");
		}
		sb.append(space + "}");
		return sb.toString();
	}
}

class GapQuery implements Query {
	public int minGap = 1;
	public int maxGap = 1;

	public String toString(String space) {
		StringBuilder sb = new StringBuilder();
		sb.append(space + "GapQuery:{minGap=" + minGap + " maxGap=" + maxGap
				+ "}\n");
		return sb.toString();
	}
}

class InQuery implements Query {
	public Query innerQuery;
	public Query outerQuery;

	public String toString(String space) {
		StringBuilder sb = new StringBuilder();
		sb.append(space + "InQuery:{\n");
		sb.append(space + "  innerQuery=" + innerQuery.toString(space + "  ")
				+ "\n");
		sb.append(space + "  outerQuery=" + outerQuery.toString(space + "  ")
				+ "\n");
		sb.append(space + "}\n");
		return sb.toString();
	}
}

class OverQuery implements Query {
	public Query innerQuery;
	public Query overQuery;

	public String toString(String space) {
		StringBuilder sb = new StringBuilder();
		sb.append(space + "OverQuery:{\n");
		sb.append(space + "  overQuery=" + overQuery.toString(space + "  ")
				+ "\n");
		sb.append(space + "  innerQuery=" + innerQuery.toString(space + "  ")
				+ "\n");
		sb.append(space + "}\n");
		return sb.toString();
	}
}


class MinusQuery implements Query {
  public Query leftQuery;
  public Query rightQuery;

  public String toString(String space) {
    StringBuilder sb = new StringBuilder();
    sb.append(space + "MinusQuery:{\n");
    sb.append(space + "  leftQuery=" + leftQuery.toString(space + "  ")
        + "\n");
    sb.append(space + "  rightQuery=" + rightQuery.toString(space + "  ")
        + "\n");
    sb.append(space + "}\n");
    return sb.toString();
  }
}

class ORQuery implements Query {
	List<Query> queriesToOr = new ArrayList<Query>();

	public void add(Query q) {
		queriesToOr.add(q);
	}

	public List<Query> getQueries() {
		return queriesToOr;
	}

	public String toString(String space) {
		StringBuffer sb = new StringBuffer();
		sb.append(space + "ORQueries: {\n");
		for (Query q : queriesToOr) {
			sb.append(space + "  orQueryMember:" + q.toString(space + "  ")
					+ "\n");
		}
		sb.append(space + "}");
		return sb.toString();
	}
}

class ANDQuery implements Query {
	List<Query> queriesToAnd = new ArrayList<Query>();

	public void add(Query q) {
		queriesToAnd.add(q);
	}

	public List<Query> getQueries() {
		return queriesToAnd;
	}

	public String toString(String space) {
		StringBuffer sb = new StringBuffer();
		sb.append(space + "ANDQueries: {\n");
		for (Query q : queriesToAnd) {
			sb.append(space + "  andQueryMember:" + q.toString(space + "  ")
					+ "\n");
		}
		sb.append(space + "}");
		return sb.toString();
	}
}

class KleneQuery implements Query {
	Query query;
	int min = 1;
	int max = 1;

	public String toString(String space) {
		return space + "KleneQuery: {\n" + query.toString(space + "  ") + "\n"
				+ space + "}=>min:" + min + "=>max:" + max;
	}

}

class SequenceQuery implements Query {
	List<Query> queriesInOrder = new ArrayList<Query>();

	public void add(Query q) {
		queriesInOrder.add(q);
	}

	public void removeLastElement() {
		queriesInOrder.remove(queriesInOrder.size() - 1);
	}

	public int size() {
		return queriesInOrder.size();
	}

	public String toString(String space) {
		StringBuffer sb = new StringBuffer();
		sb.append(space + "InSequence: {\n");
		for (Query q : queriesInOrder) {
			sb.append(q.toString(space + "  ") + "\n");
		}
		sb.append(space + "}");
		return sb.toString();
	}
}
