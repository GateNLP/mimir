/*
 *  TokenFeaturesHandler.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id$
 */
package gate.mimir.util

import gate.mimir.IndexConfig.TokenIndexerConfig
import it.unimi.di.big.mg4j.index.TermProcessor
import it.unimi.di.big.mg4j.index.DowncaseTermProcessor
import it.unimi.di.big.mg4j.index.NullTermProcessor

/**
 * Class used as closure delegate to handle the mimir.rpcIndexer.tokenFeatures
 * closure in mimir configuration.  Method calls are interpreted as the names
 * of features to index.  If a TermProcessor is passed as an argument to a
 * method call, that processor is used for the given feature.  If no processor
 * is passed in, the first feature is given a DowncaseTermProcessor and
 * subsequent features are unprocessed.
 */
class TokenFeaturesHandler {

  List indexerConfigs = []

  public void clear() {
    indexerConfigs?.clear()
  }

  def invokeMethod(String name, args) {
    def firstFeature = indexerConfigs.isEmpty()

    TermProcessor processor = null
    boolean directIndex = false
    if(args) {
      for(arg in args) {
        if(arg instanceof TermProcessor) {
          if(processor != null) {
            throw new IllegalArgumentException(
              "Term processor for token feature ${name} specified more than once")
          }
          processor = (TermProcessor)arg
        } else if(arg instanceof Map) {
          if(arg.directIndex) directIndex = true
          if(arg.termProcessor) {
            if(processor != null) {
              throw new IllegalArgumentException(
                "Term processor for token feature ${name} specified more than once")
            }
            if(arg.termProcessor instanceof TermProcessor) {
              processor = arg.termProcessor
            } else {
              throw new IllegalArgumentException(
                "termProcessor value for token feature ${name} is not a TermProcessor instance")
            }
          } 
        }
        else {
          throw new IllegalArgumentException("${args[0]} is not a TermProcessor")
        }
      }
    }
    
    if(processor == null) {
      if(firstFeature) {
        processor = DowncaseTermProcessor.getInstance()
      }
      else {
        processor = NullTermProcessor.getInstance()
      }
    }

    indexerConfigs << new TokenIndexerConfig(name, processor, directIndex)
  }
}
