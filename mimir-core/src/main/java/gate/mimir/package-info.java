/*
 * package-info.java
 * 
 * Copyright (c) 2007-2014, The University of Sheffield.
 * 
 * This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html),
 * and is free software, licenced under the GNU Lesser General Public License,
 * Version 3, June 2007 (also included with this distribution as file
 * LICENCE-LGPL3.html).
 * 
 * Valentin Tablan, 20 Feb 2014
 * 
 * $Id: package-info.java 17368 2014-02-20 15:00:45Z valyt $
 */
/**
 * This is the Mímir Java API. For more high-level information about Mímir, see
 * the <a href="http://gate.ac.uk/mimir">Mímir home page</a>.  
 * 
 * The top level entry point for the Mímir API is the 
 * {@link gate.mimir.MimirIndex} class, which can be used to create new indexes
 * or open existing ones. To create a new index you will need to supply a 
 * properly populated instance of {@link gate.mimir.IndexConfig} to 
 * {@link gate.mimir.MimirIndex#MimirIndex(IndexConfig)}. To open an
 * existing index, use {@link gate.mimir.MimirIndex#MimirIndex(java.io.File)}, 
 * and point it to the existing index directory.
 */
package gate.mimir;