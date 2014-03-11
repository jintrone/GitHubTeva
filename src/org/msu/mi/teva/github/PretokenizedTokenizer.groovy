package org.msu.mi.teva.github

import edu.mit.cci.text.preprocessing.Tokenizer

/**
 * Created by josh on 1/23/14.
 */
class PretokenizedTokenizer implements Tokenizer<String> {

    int limit = -1

    @Override
    List<String> tokenize(String text) {
        def result =(text - ~/[\s,]\d+[\s,]/).tokenize(", ")
        limit>-1?result[0..<Math.min(limit,result.size())]:result
    }
}
