package org.msu.mi.teva.github

import edu.mit.cci.teva.DefaultTevaFactory
import edu.mit.cci.teva.cpm.cos.CosCommunityFinder
import edu.mit.cci.teva.engine.CommunityFinder
import edu.mit.cci.teva.engine.CommunityMembershipStrategy
import edu.mit.cci.teva.engine.TevaParameters
import edu.mit.cci.teva.model.Conversation
import edu.mit.cci.teva.util.ExhaustiveAssignment
import edu.mit.cci.teva.util.SimilarityBasedAssignment
import edu.mit.cci.text.preprocessing.DictionaryMunger
import edu.mit.cci.text.preprocessing.Munger
import edu.mit.cci.text.preprocessing.StopwordMunger
import edu.mit.cci.text.preprocessing.Tokenizer
import edu.mit.cci.text.windowing.PrecomputedTimeWindowStrategy
import edu.mit.cci.text.windowing.TimeBasedSlidingWindowStrategy
import edu.mit.cci.text.windowing.WindowStrategy
import edu.mit.cci.text.windowing.Windowable
import edu.mit.cci.text.windowing.WindowingUtils
import groovy.util.logging.Log4j

/**
 * Created by josh on 1/23/14.
 */
@Log4j
class GitHubTevaFactory extends DefaultTevaFactory{

    Date[][] windows = null;

    GitHubTevaFactory(TevaParameters params, Conversation conversation) {
        super(params, conversation)
    }

    public Tokenizer<String> getTokenizer() throws IOException {
        return new PretokenizedTokenizer(limit:12)
        //return new GithubTokenizer(getMungers())
    }

    public WindowStrategy.Factory<Windowable> getTopicWindowingFactory() {
        return getTrafficSlicedFactory()


    }

    public Munger[] getMungers() throws IOException {
        List<Munger> mungers = new ArrayList<Munger>();
        if (params.getReplacementDictionary() != null && !params.getReplacementDictionary().isEmpty()) {
            if (params.getReplacementDictionary().startsWith("/") || params.getReplacementDictionary().startsWith(".")) {
                mungers.add(DictionaryMunger.read(new FileInputStream(params.getReplacementDictionary())));
                log.info("Loaded replacement list from file: "+params.getReplacementDictionary());
            } else {
                mungers.add(DictionaryMunger.read(getClass().getResourceAsStream("/" + params.getReplacementDictionary())));
                log.info("Loaded replacement list from resource: "+params.getReplacementDictionary());
            }
        }
        if (params.getStopwordList() != null && !params.getStopwordList().isEmpty()) {
            if (params.getStopwordList().startsWith("/") || params.getStopwordList().startsWith(".")) {
                mungers.add(StopwordMunger.readAndAdd(new FileInputStream(params.getStopwordList())));
                log.info("Loaded stopword list from file: "+params.getStopwordList());
            } else {
                mungers.add(StopwordMunger.readAndAdd(getClass().getResourceAsStream(("/" + params.getStopwordList()))));
                log.info("Loaded stopword list from resource: "+params.getStopwordList());
            }
        }
        return mungers.toArray(new Munger[mungers.size()]);
    }

    @Override
    public CommunityMembershipStrategy getMembershipMatchingStrategy() {
        return new ExhaustiveAssignment()
    }

    public CommunityFinder getFinder() {
        return new CosCommunityFinder(params);
    }

    public WindowStrategy.Factory<Windowable> getTrafficSlicedFactory() {
        return new WindowStrategy.Factory<Windowable>() {

            public WindowStrategy<Windowable> getStrategy() {
                if (windows == null) {
                    windows = WindowingUtils.analyzeMultipleThreadsBySize(conversation,getTokenizer(),(int)params.getWindowSize(),(int)params.getWindowDelta())
                }
                return new LookBehindWindowStrategy((int)params.getWindowSize(),true,windows)
            }
        };
    }

    public WindowStrategy.Factory<Windowable> getDateSlicedWindowingFactory() {
        return new WindowStrategy.Factory<Windowable>() {

            public WindowStrategy<Windowable> getStrategy() {
                if (windows == null) {
                    windows = WindowingUtils.analyzeMultipleThreadsBySize(conversation,getTokenizer(),(int)params.getWindowSize(),(int)params.getWindowDelta())
                }
                return new PrecomputedTimeWindowStrategy(windows)
            }
        };
    }






}
