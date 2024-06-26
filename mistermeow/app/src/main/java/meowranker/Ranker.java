
package meowranker;

import java.lang.Math;
import java.time.Instant;
import java.util.*;
import meowdbmanager.DBManager;
import meowindexer.Tokenizer;
import org.bson.Document;
import org.bson.types.ObjectId;

public abstract class Ranker {

  public static DBManager db;
  public static Tokenizer tokenizer;
  public List<Document>
      ProcessedDocs; // used to access db only once while crawling

  public Ranker() {
    db = new DBManager();
    tokenizer = new Tokenizer();
  }

  public List<ObjectId> rank(String query) {

    System.out.println("Ranking started!" + Instant.now());
    double[] popularity = getPopularityArr();

    // Tokenizing query
    List<String> searchTokens = tokenizer.tokenizeString(query, true);
    System.out.println(searchTokens);
    System.out.println("Tokenization finished!" + Instant.now());

    // getting docs common in all tokens & matches the query phrase

    List<ObjectId> matchedDocs = this.getMatchingDocs(searchTokens, query);

    System.out.println("Matching Docs finished!" + Instant.now());

    System.out.println(matchedDocs.size() + " || " + ProcessedDocs.size());
    // for (Document doc : ProcessedDocs) {
    // System.out.println(doc.getString("URL"));
    // System.out.println(doc.)
    // }

    // calculating relevance for each document
    List<Double> relevance =
        this.calculateRelevance(matchedDocs, searchTokens, popularity);

    // for (Double val: relevance){
    // System.out.println(val);
    // }
    //
    System.out.println("Relevance calculation started!" + Instant.now());

    List<Map.Entry<ObjectId, Double>> finalRank =
        combineRelWithPop(matchedDocs, relevance, popularity);

    System.out.println("Combining relevance with popularity finished!" +
                       Instant.now());

    finalRank.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

    System.out.println("Sorting finished!" + Instant.now());

    // System.out.println("======================================");
    // System.out.println("=========== Final Result =============");
    // System.out.println("======================================");

    List<ObjectId> SortedList = new ArrayList<>();
    for (Map.Entry<ObjectId, Double> e : finalRank) {
      SortedList.add(e.getKey());
      // System.out
      // .println("URL: " +
      // db.getDocument(e.getKey().toString()).getString("URL") + "
      // || Rank = " + e.getValue());
      // The previous printing is time costly, comment it if you're not testing
      // of debugging
    }

    System.out.println("Writing to file started!" + Instant.now());
    System.out.println("======================================");

    System.out.println("Ranking finished!");
    return SortedList;
  }

  protected abstract List<ObjectId> getMatchingDocs(List<String> searchTokens,
                                                    String query);

  // The function takes graph of links between documents
  // where edge from doc1 to doc2 is representing by adding
  // 1/(outgoing urls from doc1) in the cell M[doc2][doc1]
  // resulting in matrix with sum of it's columns always = 1
  public static double[] getPopularity(double[][] M, int UrlsCount) {

    double d = 0.85;
    for (int i = 0; i < UrlsCount; i++) {
      for (int j = 0; j < UrlsCount; j++) {
        M[i][j] = d * M[i][j];
      }
    }

    double[] prevRank = new double[UrlsCount];
    double[] currRank;

    for (int i = 0; i < UrlsCount; i++)
      prevRank[i] = 1.0 / (double)UrlsCount;

    currRank = calculateCurrRank(prevRank, UrlsCount, d, M);

    while (Norm(currRank, UrlsCount) - Norm(prevRank, UrlsCount) > 1e-10) {
      prevRank = currRank;
      currRank = calculateCurrRank(prevRank, UrlsCount, d, M);
    }

    // normalizing the final array
    double norm = Norm(currRank, UrlsCount);

    for (int i = 0; i < UrlsCount; i++) {
      currRank[i] /= norm;
    }
    return currRank;
  }

  public static double[] calculateCurrRank(double[] prevRank, int UrlsCount,
                                           double d, double[][] M_hat) {

    double[] currRank = new double[UrlsCount];

    for (int i = 0; i < UrlsCount; i++) {
      double val = 0;
      for (int j = 0; j < UrlsCount; j++) {
        val += M_hat[i][j] * prevRank[j];
      }
      currRank[i] = val + (1 - d);
    }

    return currRank;
  }

  public static double Norm(double[] vector, int size) {
    double norm = 0;

    for (int i = 0; i < size; i++) {
      norm += vector[i] * vector[i];
    }

    return Math.sqrt(norm);
  }

  /**
   * constructUrlsGraph - returns a constructed graph from the Urls data in the
   * database.
   *
   * @return double[][] - a matrix of doubles, with size N * N,
   *         where N equal to number of urls in the database.
   */
  public static double[][] constructUrlsGraph() {
    // Number of nodes in graph is number of urls in database.
    int nodesNum = db.getUrlsCount();
    // Create a 2D array filled with 0s initialiy.
    double[][] graph = new double[nodesNum][nodesNum];

    // Construct graph with parents arrays.
    constructMatrix(graph, nodesNum);

    // Scale graph with a certain formula
    scaleMatrix(graph, nodesNum);

    return graph;
  }

  public double[] getPopularityArr() {
    // int numberOfUrls = db.getUrlsCount();
    // double[] popularityArr = new double[numberOfUrls];

    // for (int i = 0; i < numberOfUrls; i++) {
    // double popularity = db.getPopularity(i);
    // popularityArr[i] = popularity;
    // }

    return db.getPopularityArr();
  }

  /**
   * calculatePopularity - returns the popularity of each url in the
   * database.
   *
   * @return double[] - an array of popularity for each url.
   */
  public static void calculatePopularity() {
    double[][] M = constructUrlsGraph();
    double[] popularity = getPopularity(M, M.length);

    for (int i = 0; i < popularity.length; i++) {
      db.updatePopularity(i, popularity[i]);
    }
  }

  /**
   * constructMatrix - takes a 2D matrix and fill it with edges from parents
   * arrays of each node.
   *
   * @param graph    - the 2D matrix to fill.
   * @param nodesNum - the number of nodes and the size of the graph.
   */
  public static void constructMatrix(double[][] graph, int nodesNum) {

    // Loop over each node (url) and get it's parents array and construct graph.
    for (int i = 0; i < nodesNum; i++) {
      List<Integer> nodeParents = db.getParentsArr(i);

      // Loop over the parents array and add 1 for each edge from parent to
      // node.
      for (int j = 0; j < nodeParents.size(); j++) {
        int parentId = nodeParents.get(j);

        if (parentId >= 0 && parentId < nodesNum) {
          graph[i][parentId]++;
        }
      }
    }
  }

  /**
   * scaleMatrix - takes a 2D Matrix and scales each column by a certain
   * formula.
   *
   * @param graph    - the 2D matrix to scale.
   * @param nodesNum - the number of nodes and the size of the graph.
   */
  public static void scaleMatrix(double[][] graph, int nodesNum) {

    // Loop over each column in the graph.
    for (int j = 0; j < nodesNum; j++) {
      double sum = 0;

      // Calculate sum of values in the j'th column
      for (int i = 0; i < nodesNum; i++)
        sum += graph[i][j];

      if (sum == 0)
        continue;

      // Divide each cell value by the sum of column, to scale it.
      for (int i = 0; i < nodesNum; i++)
        graph[i][j] /= sum;
    }
  }

  public List<Double> calculateRelevance(List<ObjectId> docIds,
                                         List<String> searchTokens,
                                         double[] popularity) {
    List<Double> relevance = new ArrayList<>();

    final double boost = 1.1; // 10% boost for the relevance

    for (int i = 0; i < docIds.size(); i++) {
      double val = 0;
      for (String token : searchTokens) {
        // summation(tf-idf)
        Document invertedInd =
            db.getTFandPositionFromInverted(token, docIds.get(i));
        if (invertedInd == null)
          continue;

        String position = invertedInd.getString("position");

        // val += db.getDocumentFromInverted(token, docIds.get(i)) *
        // getIDF(token);
        val += invertedInd.getDouble("TF") * getIDF(token);
        if (position != null && !position.equals("other"))
          val += boost;
        // NOTE: uncomment when testing
        // System.out.println(
        // "Token: " + token + " IDF: " + getIDF(token) +
        // " TF: " + db.getDocumentFromInverted(token, docIds.get(i)) +
        // " Position: " + position);
      }
      // System.out.println("Relevance: " + val);

      relevance.add(val);
    }

    if (this instanceof QueryRanker) {
      QueryRanker ranker = (QueryRanker)this;
      relevance = ranker.addQueryDocRel(relevance);
    }

    return relevance;
  }

  public double getIDF(String token) {
    double df;
    Document invertedInd = db.getInvertedIndex(token);

    if (invertedInd == null) // Handling tokens that are not in any documents
      return 0;

    df = (double)db.getInvertedIndex(token).getInteger("DF");
    return Math.log((double)db.getUrlsCount() / df);
  }

  protected List<Map.Entry<ObjectId, Double>>
  combineRelWithPop(List<ObjectId> docs, List<Double> relevance,
                    double[] popularity) {
    List<Map.Entry<ObjectId, Double>> finalRank = new ArrayList<>();
    int ind = 0;
    for (Document doc : ProcessedDocs) {
      int ranker_id = doc.getInteger("ranker_id");

      Map.Entry<ObjectId, Double> entry = new AbstractMap.SimpleEntry<>(
          docs.get(ind), relevance.get(ind) + popularity[ranker_id]);
      finalRank.add(entry);

      ind++;
    }

    return finalRank;
  }

  public void testTokenDocCount(List<String> searchTokens) {
    for (String token : searchTokens) {
      Document invertedInd = db.getInvertedIndex(token);
      if (invertedInd == null)
        System.out.println(0);
      else
        System.out.println((double)invertedInd.getInteger("DF"));
    }
  }
}
