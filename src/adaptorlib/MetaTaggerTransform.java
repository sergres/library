// Copyright 2011 Google Inc. All Rights Reserved.
package adaptorlib;
import java.util.regex.Pattern;
import java.util.Comparator;
import java.util.Scanner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.OutputStream;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.SortedMap;

/**
 * The transform examines the document for regex patterns. If a pattern is found,
 * the associated metadata is inserted at the end of the HEAD section of the
 * HTML. If no HEAD section exists, nothing gets inserted.
 */
public class MetaTaggerTransform extends DocumentTransform {
  private static Logger LOG = Logger.getLogger(MetaTaggerTransform.class.getName());

  public MetaTaggerTransform() {
    super("MetaTaggerTransform");
  }

  public MetaTaggerTransform(String patternFile) {
    super("MetaTaggerTransform");
    try {
      loadPatternFile(patternFile);
    }
    catch (IOException ex) {
      LOG.log(Level.SEVERE, "MetaTaggerTransform encountered an error while " +
              "loading pattern file: " + patternFile, ex);
    }
  }

  @Override
  public void transform(ByteArrayOutputStream contentIn, ByteArrayOutputStream metadataIn,
                        OutputStream contentOut, OutputStream metadataOut,
                        Map<String, String> params) throws TransformException, IOException {
    String content = contentIn.toString();
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<Pattern, String> entry : patternMappings.entrySet()) {
      if (entry.getKey().matcher(content).find()) {
        sb.append(entry.getValue());
      }
    }
    // This is a very simple insertion mechanism. It looks for the closing
    // </HEAD> element and inserts the metadata right before it.
    content = content.replaceFirst("</(HEAD|head)", "\n" + sb.toString() + "</HEAD");
    contentOut.write(content.getBytes());
    metadataIn.writeTo(metadataOut);
  }

  private void loadPatternFile(String filename) throws IOException {
    Scanner sc = new Scanner(new File(filename));
    while (sc.hasNextLine()) {
      String line = sc.nextLine().trim();
      int sepIndex = line.indexOf(PATTERN_FILE_SEP);
      if (line.isEmpty() || sepIndex < 0)
        continue;

      Pattern pattern = Pattern.compile(line.substring(0, sepIndex));
      String metadata = line.substring(sepIndex+1, line.length());
      String existing = patternMappings.get(pattern);
      if (existing == null) {
        patternMappings.put(pattern, metadata + "\n");
      }
      else {
        patternMappings.put(pattern, existing + metadata + "\n");
      }
    }
  }

  /**
   * Maps Pattern to String representation of metadata.
   * The String is assumed to be a valid HTML fragment that is pasted into the
   * HEAD section of the HTML document.
   *
   * We use a SortedMap with this comparator to ensure we get the same metadata
   * ordering for each invocation. Serving different docs each time could lead
   * to unnecessary recrawls from the GSA.
   */
  private SortedMap<Pattern, String> patternMappings =
      new TreeMap<Pattern, String>(new PatternComparator());

  private char PATTERN_FILE_SEP = ' ';

  private class PatternComparator implements Comparator<Pattern> {
    public int compare(Pattern p1, Pattern p2) {
      return p1.toString().compareTo(p2.toString());
    }
    public boolean equals(Pattern p1, Pattern p2) {
      return p1.toString().equals(p2.toString());
    }
  }
}