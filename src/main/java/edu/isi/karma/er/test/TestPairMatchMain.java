package edu.isi.karma.er.test;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.util.FileManager;
//import com.hp.hpl.jena.util.FileManager;

import edu.isi.karma.er.helper.Constants;
import edu.isi.karma.er.helper.ScoreBoardFileUtil;
import edu.isi.karma.er.helper.entity.MultiScore;
import edu.isi.karma.er.helper.entity.ResultRecord;
import edu.isi.karma.er.helper.entity.Score;
import edu.isi.karma.er.helper.entity.ScoreBoard;
import edu.isi.karma.er.linkage.LinkageFinder;

public class TestPairMatchMain {



	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Logger log = Logger.getRootLogger();								// log the output into both file and terminal, see log4j.properties
		double threshold = 0.9;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		long startTime = System.currentTimeMillis();
		log.info("Program execution start from: " + sdf.format(new java.util.Date()));
		Model model = TDBFactory.createDataset(Constants.PATH_REPOSITORY + "saam_a/").getDefaultModel();
		Model model2 = TDBFactory.createDataset(Constants.PATH_REPOSITORY + "dbpedia_a/").getDefaultModel();
		//Model model = FileManager.get().loadModel(Constants.PATH_N3_FILE + "saam_fullname_start_with_A.n3");
		//Model model2 = FileManager.get().loadModel(Constants.PATH_N3_FILE + "dbpedia_fullname_start_with_A.n3");
		
		log.info("finish loading."); 
		
		ScoreBoardFileUtil util = new ScoreBoardFileUtil();
		Map<String, ScoreBoard> map = util.loadScoreBoard();
		LinkageFinder finder = new LinkageFinder();
		List<ResultRecord> resultList = finder.findLinkage(model, model2);						// match and return linkage result in list.
		
		
		int count = 0;
		DecimalFormat df = new DecimalFormat("0.000");
		
		for (int i = 0; i < resultList.size(); i++) {	// output results.
			
			ResultRecord rec = resultList.get(i);
			String mark = "";
			if (rec.getCurrentMaxScore() >= threshold) {
				// prepare to write output csv file 
				ScoreBoard s = map.get(rec.getRes().getURI());
				System.out.println(rec.getRes().getURI());
				String res = null;
				if (rec.getRankList().size() > 0)
					res = rec.getRankList().get(0).getDstSubj().toString();
				s.setRankList(rec.getRankList());
				s.setKarmaUri(res);
				s.setFound(rec.getCurrentMaxScore()); 			// count means valid results with similarity greater than threshold 0.9
				map.put(s.getSubject(), s);
				
				// prepare to write log in log4j
				count ++;													// count means valid results with similarity greater than threshold 0.9
				mark = "(****** " + count + " *****)";		
			}
			
			log.info("==============================================================================");
			log.info(rec.getRes().getURI() + "\t\t\t\t\t " + mark);
		
			List<MultiScore> sList = rec.getRankList();
			for (int j = 0; j < sList.size(); j++) {
				MultiScore ms = sList.get(j);
				log.info("\t [" + df.format(ms.getFinalScore()) + "] " + ms.getDstSubj().getURI());
				
				for (Score s : ms.getScoreList()) {
					String srcObj = (s.getSrcObj() == null ? "" : s.getSrcObj().getObject().toString());
					String pred = (s.getSrcObj() == null ? "": s.getSrcObj().getPredicate().getURI());
					String dstObj = (s.getDstObj() == null ? "" : s.getDstObj().getObject().toString());
					log.info("\t\t\t[ " 
							+ df.format(s.getSimilarity()) 
							+ "]\t[ " + srcObj 
							+ " | " + dstObj 
							+ " ]\t" +  pred);
				}
				
			}
			log.info("==============================================================================\n");
			
		}
		log.info("************************************************************************************");
		log.info("Total results found:" + count + " of " + resultList.size());
		log.info("************************************************************************************");
		
		util.write2Log(map);		// write to output csv file.
		
		
		log.info("total time elapsed:" + (System.currentTimeMillis() - startTime) / 1000 + "s");
		log.info("Program finished at:" + sdf.format(new java.util.Date()));
		
	}
	
}
