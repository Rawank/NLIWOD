package org.aksw.hawk.experiment;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import org.aksw.hawk.controller.AbstractPipeline;
import org.aksw.hawk.controller.EvalObj;
import org.aksw.hawk.controller.PipelineStanford;
import org.aksw.hawk.datastructures.Answer;
import org.aksw.hawk.datastructures.HAWKQuestion;
import org.aksw.hawk.datastructures.HAWKQuestionFactory;
import org.aksw.hawk.ranking.FeatureBasedRanker;
import org.aksw.hawk.ranking.OptimalRanker;
import org.aksw.qa.commons.load.Dataset;
import org.aksw.qa.commons.load.LoaderController;
import org.aksw.qa.commons.sparql.SPARQLQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * F@N + all ranking experiments for ESWC 2015 publication Possibly extendible
 * for testing NER things
 * 
 * @author Lorenz Buehmann
 * @author ricardousbeck
 * 
 */
public class TrainingPipeline {
	static Logger log = LoggerFactory.getLogger(TrainingPipeline.class);

	public static void main(String args[]) throws IOException, ParserConfigurationException {
		log.info("Configuring controller");
		AbstractPipeline pipeline = new PipelineStanford();

		log.info("Loading dataset");
		List<HAWKQuestion> questions = HAWKQuestionFactory.createInstances(LoaderController.load(Dataset.QALD6_Train_Hybrid));

		double average = 0;
		double count = 0;
		double countNULLAnswer = 0;
		OptimalRanker optimal_ranker_1 = new OptimalRanker();
		List<List<Answer>> rankedAnswers = questions
			.stream()
			.map(q -> optimal_ranker_1.rank(pipeline.getAnswersToQuestion(q), q))
			.peek(answers -> {
				int a  =2;
			})
			.collect(Collectors.toList());

		for (HAWKQuestion q : questions) {
			if ((q.getAnswerType().equals("resource") & q.getOnlydbo() & !q.getAggregation()) || q.getLoadedAsASKQuery()) {
				log.info("Run pipeline on " + q.getLanguageToQuestion().get("en"));
				List<Answer> answers = pipeline.getAnswersToQuestion(q);

				if (answers.isEmpty()) {
					log.warn("Question#" + q.getId() + " returned no answers! (Q: " + q.getLanguageToQuestion().get("en") + ")");
					++countNULLAnswer;
					continue;
				}
				++count;

				// ##############~~RANKING~~##############
				log.info("Run ranking");
				int maximumPositionToMeasure = 10;
				OptimalRanker optimal_ranker = new OptimalRanker();
				FeatureBasedRanker feature_ranker = new FeatureBasedRanker();

				// optimal ranking
				log.info("Optimal ranking");
				List<Answer> rankedAnswer = optimal_ranker.rank(answers, q);
				List<EvalObj> eval = Measures.measure(rankedAnswer, q, maximumPositionToMeasure);
				log.debug(Joiner.on("\n\t").join(eval));

				Set<SPARQLQuery> queries = Sets.newHashSet();
				double fmax = 0;
				for (EvalObj e : eval) {
					if (e.getFmax() == fmax) {
						queries.add(e.getAnswer().query);
					} else if (e.getFmax() > fmax) {
						queries.clear();
						queries.add(e.getAnswer().query);
						fmax = e.getFmax();
					}
				}
				log.info("Max F-measure: " + fmax);
				average += fmax;
				log.info("Feature-based ranking begins training.");
				feature_ranker.learn(q, queries);
			}
		}
		log.info("Number of questions with answer: " + count + ", number of questions without answer: " + countNULLAnswer);
		log.info("Average F-measure: " + (average / count));

	}
}
