package org.msu.mi.teva.github

import edu.mit.cci.text.preprocessing.AlphaNumericTokenizer
import edu.mit.cci.text.preprocessing.Munger
import groovy.util.logging.Log4j
import org.parboiled.errors.ParserRuntimeException

/**
 * Created by josh on 1/22/14.
 */
@Log4j
class GithubTokenizer extends AlphaNumericTokenizer {

    MarkdownStripper stripper = new MarkdownStripper()

    GithubTokenizer(Munger... mungers) {
        super(mungers)
    }

    @Override
    List<String> tokenize(String input) {
        try {

            String stripped = stripper.stripMarkdown(input) - ~/(?s)\{.+\}/


            super.tokenize(stripped)
        } catch (ParserRuntimeException ex) {
            log.error("Encountered a parsing timout exception on input ${input}")
            return super.tokenize(input)
        }
    }
}
