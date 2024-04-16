package meowcrawler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import meowdbmanager.DBManager;

public class Crawler implements Runnable {
  static private HashingManager hM = new HashingManager();
  static private QueueManager qM = new QueueManager();
  static private DBManager db = new DBManager();
  static private int countOfDocumentsCrawled = 0;

  /**
   * HandleHashing - takes a set of urls and hash and check that they were not
   * crawled before.
   *
   * @param urls - the set of urls extracted from the html document.
   * @return void.
   */
  public void HandleHashing(Set<String> urls) {
    final String ANSI_CYAN = "\u001B[36m";

    for (String url : urls) {
      // Create a Url object for the url string.
      Url nUrl = new Url(url, 1);

      // Hash and check if the url was not crawled (store it if it wasn't)
      synchronized (hM) {
        String hashedUrl = hM.HashAndCheckURL(nUrl);
        if (hashedUrl == null) {
          synchronized (db) { db.incrementPopularity("URL", url); }
          continue;
        }
      }

      // Fetch the document of the url, then hash and check it.
      if (nUrl.FillDocument()) {

        // Make sure that we are crawling english websites only.
        String docLang = nUrl.GetDocument().select("html").attr("lang");

        if (docLang != null && !docLang.contains("en") &&
            !docLang.contains("ar")) {
          continue;
        }

        // Get the text from the html document.
        String doc = nUrl.GetDocument().outerHtml();
        boolean insertOrNot = false;

        synchronized (hM) {
          // Hash and check the html document, and push the Url into the queue.
          String hashedDoc = hM.HashAndCheckDoc(nUrl, doc);

          if (hashedDoc != null) {
            insertOrNot = true;

            synchronized (qM) {
              qM.push(nUrl);
              qM.moveToDomainQ();
            }
          }
        }

        // check if the url & its doc needs to be put into the database.
        if (insertOrNot) {
          synchronized (db) {
            db.insertDocument(nUrl.getUrlString(), nUrl.getTitle(),
                              nUrl.getDomainName(), doc, nUrl.getHashedURL(),
                              nUrl.getHashedDoc());
            System.out.println(ANSI_CYAN + "|| Inserted " +
                               nUrl.getUrlString() + " into the database"
                               + " Count: " + ++countOfDocumentsCrawled +
                               " ||");
          }
        } else {
          synchronized (db) {
            db.incrementPopularity("hashedDoc", nUrl.getHashedDoc());
          }
        }
      }
    }
  }

  /**
   * run - the main function of the Crawler in which the whole operations are
   * happening async in each crawler.
   *
   * @return void.
   */
  public void run() {
    while (countOfDocumentsCrawled < 2000) {
      Url url = null;

      // Get the top Url from the queue.
      synchronized (qM) {
        try {
          url = qM.pop();
          System.out.println("|| Popped " + url.getUrlString() + " ||");
        } catch (Exception e) {
          System.out.println("Queue is empty");
          continue;
        }
      }

      // TODO: handle that the number of crawled urls doesn't exceed 6000.

      // Extract Urls and handle them, hash and check that they was not crawled
      // before
      URLsHandler urlH = new URLsHandler();
      Set<String> extractedUrls =
          urlH.HandleURLs(url.GetDocument(), url.getUrlString());

      HandleHashing(extractedUrls);
    }
  }

  /**
   * A static function that provides initial seed for the queueManager.
   */
  static public void ProvideSeed(List<Url> urls) {
    // FIXME: amir-kedis: Akram, What I did is probably wrong, Review/Refactor
    // this please.
    Set<String> seeds =
        urls.stream().map(Url::getUrlString).collect(Collectors.toSet());
    Crawler c = new Crawler();
    c.HandleHashing(seeds);
  }
}
