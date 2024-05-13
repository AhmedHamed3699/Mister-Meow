package meowindexer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import opennlp.tools.stemmer.PorterStemmer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class Tokenizer {
  // NOTE: SUBCLASS Token
  // =====================

  /**
   * Token class: represents a token with its count and position in the document
   *
   * @word: The token
   * @count: The number of times the token appears in the document
   * @position: The position of the token in the document (title, h1, h2, or
   *            other)
   *
   */
  public class Token {
    public String word;
    public double count;
    public String position;

    /**
     * Constructor for the Token class
     *
     * @param word: The token
     */
    public Token(String word) {
      this.word = word;
      this.count = 1;
      this.position = "other";
    }

    /**
     * Increment the count of the token
     */
    public void increment() {
      count++;
    }
  }

  // NOTE: CLASS tokenizer |||| Data Members
  private HashSet<String> stopWords;

  /**
   * Constructor for the tokenizer: loads
   */
  public Tokenizer() {
    stopWords = loadStopWords("stopwords-en.txt");
  }

  /**
   * Load stop words from a file (stopwords-en.txt)
   *
   * @param filename: Name of the file containing stop words
   * @return HashSet of stop words
   */
  public static HashSet<String> loadStopWords(String filename) {
    HashSet<String> stopWords = new HashSet<String>();
    try {
      // NOTE: root path is src/meowindexer/app/. everything is relative to this
      BufferedReader reader = new BufferedReader(new FileReader("../data/" + filename));
      String line;
      while ((line = reader.readLine()) != null) {
        stopWords.add(line.trim());
      }
      reader.close();
      return stopWords;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Tokenize a document
   *
   * @param doc: Document to tokenize
   * @return Map of tokens
   */
  public HashMap<String, Token> tokenize(Document doc) {
    String text = doc.text();
    List<String> tokens = tokenizeString(text, true);
    HashMap<String, Token> tokenMap = new HashMap<String, Token>();

    for (String token : tokens) {
      if (tokenMap.containsKey(token)) {
        tokenMap.get(token).increment();
      } else {
        tokenMap.put(token, new Token(token));
      }
    }

    // NOTE: we will turn count into TF
    // The count property may be miss leading, now as it is normalized
    // TODO: rename count to TF
    for (String token : tokenMap.keySet()) {
      tokenMap.get(token).count = tokenMap.get(token).count / tokens.size();
    }

    fillPosistions(tokenMap, doc);

    return tokenMap;
  }

  /**
   * Tokenize a string into words
   * * Remove non-alphabetic characters
   * * Remove stop words
   * * Stem the words
   *
   * @param text: String to tokenize
   * @return List of tokens
   */
  public List<String> tokenizeString(String text, boolean stem) {
    List<String> tokens = new ArrayList<String>();
    PorterStemmer stemmer = new PorterStemmer();

    String cleanText = text.toLowerCase().replaceAll("[^a-z ]", "");
    String[] words = cleanText.split("\\s+");

    for (String word : words) {
      if (word.length() > 1 && !stopWords.contains(word)) {
        String stemmedWord = stem ? stemmer.stem(word) : word;

        if (!stemmedWord.equals(word))
          tokens.add(stemmer.stem(word));

        tokens.add(word);
      }
    }

    return tokens;
  }

  /**
   * Fill the position of each token in the document (title, h1, h2, or other)
   *
   * @param tokens: Map of tokens
   * @param doc:    Document to search for token positions
   */
  private void fillPosistions(HashMap<String, Token> tokens, Document doc) {
    List<String> titleTokens = tokenizeString(doc.title(), true);
    List<String> h1Tokens = tokenizeString(doc.select("h1").text(), true);
    List<String> h2Tokens = tokenizeString(doc.select("h2").text(), true);

    for (String token : tokens.keySet()) {
      Token t = tokens.get(token);
      if (titleTokens.contains(t.word)) {
        t.position = "title";
      } else if (h1Tokens.contains(t.word)) {
        t.position = "h1";
      } else if (h2Tokens.contains(t.word)) {
        t.position = "h2";
      }
    }
  }

  /**
   * Test the tokenizer
   * visit https://en.wikipedia.org/wiki/Cat and print the tokens
   */
  public void test() {
    final String ANSI_RESET = "\u001B[0m";
    final String ANSI_GREEN = "\u001B[32m";

    System.out.println(ANSI_GREEN);
    System.out.println("Testing tokenizer...");
    System.out.println(ANSI_RESET);

    Document doc = null;
    final String url = "https://www.imdb.com/chart/top/";
    try {
      doc = Jsoup.connect(url).get();
    } catch (IOException e) {
      e.printStackTrace();
    }

    System.out.println(doc);
    // System.out.println("tokenizing: " + url + " : " + doc.title());
    // HashMap<String, Token> tokens = tokenize(doc);
    // // print sorted by count
    // System.out.println("Sorted by count:");
    // final String ANSI_YELLOW = "\u001B[33m";
    // final String ANSI_RESET2 = "\u001B[0m";
    // tokens.entrySet().stream().forEach(
    // e -> System.out.println(ANSI_YELLOW + "{ "
    // + "word: " + e.getKey() + ", "
    // + "count: " + e.getValue().count + ", "
    // + "position: " + e.getValue().position + " }" +
    // ANSI_RESET2));
  }
}
