// Copyright 2016 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.adaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/** Tests for {@link SimpleGsaFeedFileMaker}. */
public class SimpleGsaFeedFileMakerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private DocIdEncoder encoder = new MockDocIdCodec();
  private SimpleGsaFeedFileMaker maker;
  private static final String RESOURCES_DIRECTORY
      = "test/com/google/enterprise/adaptor/prebuilt/resources/";
  private static final String TOM_SAWYER_FILE = RESOURCES_DIRECTORY
      + "TomSawyer.txt";
  private static final String MOBY_DICK_FILE = RESOURCES_DIRECTORY
      + "MobyDick.txt";
  private static final String URLS_FILE = RESOURCES_DIRECTORY + "urls.txt";
  private static final String SCRIPT_FILE = RESOURCES_DIRECTORY + "script.txt";
  private static final String TOM_SAWYER_CONTENT
      = "<content encoding=\"base64binary\">"
          + "c25pcHBldCBmcm9tIFRvbSBTYXd5ZXIsIGJ5IE1hcmsgVHdhaW4uCgoiVG9tIHNhaW"
          + "QgdG8gaGltc2VsZiB0aGF0IGl0IHdhcyBub3Qgc3VjaCBhIGhvbGxvdyB3b3JsZCwg"
          + "YWZ0ZXIgYWxsLiBIZSBoYWQKZGlzY292ZXJlZCBhIGdyZWF0IGxhdyBvZiBodW1hbi"
          + "BhY3Rpb24sIHdpdGhvdXQga25vd2luZyBpdCAtLSBuYW1lbHksIHRoYXQgaW4Kb3Jk"
          + "ZXIgdG8gbWFrZSBhIG1hbiBvciBhIGJveSBjb3ZldCBhIHRoaW5nLCBpdCBpcyBvbm"
          + "x5IG5lY2Vzc2FyeSB0byBtYWtlIHRoZQp0aGluZyBkaWZmaWN1bHQgdG8gYXR0YWlu"
          + "LiIK</content>";
  private static final String MOBY_DICK_CONTENT
      = "<content encoding=\"base64binary\">"
          + "c25pcHBldCBmcm9tIE1vYnkgRGljaywgYnkgSGVybWFuIE1lbHZpbGxlLgoKIldoZW"
          + "5ldmVyIEkgZmluZCBteXNlbGYgZ3Jvd2luZyBncmltIGFib3V0IHRoZSBtb3V0aDsg"
          + "d2hlbmV2ZXIgaXQgaXMgYSBkYW1wLApkcml6emx5IE5vdmVtYmVyIGluIG15IHNvdW"
          + "w7IHdoZW5ldmVyIEkgZmluZCBteXNlbGYgaW52b2x1bnRhcmlseSBwYXVzaW5nIGJl"
          + "Zm9yZQpjb2ZmaW4gd2FyZWhvdXNlcywgYW5kIGJyaW5naW5nIHVwIHRoZSByZWFyIG"
          + "9mIGV2ZXJ5IGZ1bmVyYWwgSSBtZWV0OyBhbmQKZXNwZWNpYWxseSB3aGVuZXZlciBt"
          + "eSBoeXBvcyBnZXQgc3VjaCBhbiB1cHBlciBoYW5kIG9mIG1lLCB0aGF0IGl0IHJlcX"
          + "VpcmVzIGEKc3Ryb25nIG1vcmFsIHByaW5jaXBsZSB0byBwcmV2ZW50IG1lIGZyb20g"
          + "ZGVsaWJlcmF0ZWx5IHN0ZXBwaW5nIGludG8gdGhlIHN0cmVldCwKYW5kIG1ldGhvZG"
          + "ljYWxseSBrbm9ja2luZyBwZW9wbGXigJlzIGhhdHMgb2ZmIOKAlCB0aGVuLCBJIGFj"
          + "Y291bnQgaXQgaGlnaCB0aW1lIHRvCmdldCB0byBzZWEgYXMgc29vbiBhcyBJIGNhbi"
          + "4iCg==</content>";

  @Before
  public void setUp() {
    new File(TOM_SAWYER_FILE).setLastModified(1464800136000L);
    new File(MOBY_DICK_FILE).setLastModified(1464799981000L);
  }

  @Test
  public void testIncremental_Empty() {
    SimpleGsaFeedFileMaker maker =
        new SimpleGsaFeedFileMaker.ContentIncremental("t3sT");
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" "
        + "\"gsafeed.dtd\">\n"
        + "<gsafeed>\n"
        + "<!--Product Version: Application  (unknown version)-->\n"
        + "<!--Version X.Y.Z of Java is supported.-->\n"
        + "<header>\n"
        + "<datasource>t3sT</datasource>\n"
        + "<feedtype>incremental</feedtype>\n"
        + "</header>\n"
        + "<group/>\n"
        + "</gsafeed>\n";
    String xml = maker.toXmlString();
    xml = xml.replaceAll("\r\n", "\n");
    xml = removeJavaVersion(System.getProperty("java.version"), xml);
    assertEquals(golden, xml);
  }

  @Test
  public void testFull_Empty() {
    maker = new SimpleGsaFeedFileMaker.ContentFull("t3sT");
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" "
        + "\"gsafeed.dtd\">\n"
        + "<gsafeed>\n"
        + "<!--Product Version: Application  (unknown version)-->\n"
        + "<!--Version X.Y.Z of Java is supported.-->\n"
        + "<header>\n"
        + "<datasource>t3sT</datasource>\n"
        + "<feedtype>full</feedtype>\n"
        + "</header>\n"
        + "<group/>\n"
        + "</gsafeed>\n";
    String xml = maker.toXmlString();
    xml = xml.replaceAll("\r\n", "\n");
    xml = removeJavaVersion(System.getProperty("java.version"), xml);
    assertEquals(golden, xml);
  }

  @Test
  public void testMetadataAndUrl_Empty() {
    SimpleGsaFeedFileMaker.MetadataAndUrl maker
        = new SimpleGsaFeedFileMaker.MetadataAndUrl("t3sT");
    String golden =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" "
        + "\"gsafeed.dtd\">\n"
        + "<gsafeed>\n"
        + "<!--Product Version: Application  (unknown version)-->\n"
        + "<!--Version X.Y.Z of Java is supported.-->\n"
        + "<header>\n"
        + "<datasource>web</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group/>\n"
        + "</gsafeed>\n";
    String xml = maker.toXmlString();
    xml = xml.replaceAll("\r\n", "\n");
    xml = removeJavaVersion(System.getProperty("java.version"), xml);
    assertEquals(golden, xml);
  }

  @Test
  public void testFull_SampleContent() throws IOException {
    SimpleGsaFeedFileMaker.Content contentMaker =
        new SimpleGsaFeedFileMaker.ContentFull("sampleContent");
    maker = contentMaker;
    String hostname = getHostname();
    String goldenHeader =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" "
        + "\"gsafeed.dtd\">\n"
        + "<gsafeed>\n"
        + "<!--Product Version: Application  (unknown version)-->\n"
        + "<!--Version X.Y.Z of Java is supported.-->\n"
        + "<header>\n"
        + "<datasource>sampleContent</datasource>\n"
        + "<feedtype>full</feedtype>\n"
        + "</header>\n"
        + "<group>\n";
    String goldenBook =
        "<record last-modified=\"Wed, 01 Jun 2016 09:53:01 -0700\" "
            + "mimetype=\"\" url=\"googleconnector://" + hostname
            + "/test/com/google/enterprise/adaptor/prebuilt/resources/MobyDick"
            + ".txt\">\n"
            + MOBY_DICK_CONTENT
            + "\n</record>\n";
    String goldenBody =
        goldenBook
        + "</group>\n"
        + "</gsafeed>\n";
    String golden = goldenHeader + goldenBody;
    maker.setPublicAcl();
    File moby = new File(MOBY_DICK_FILE);
    contentMaker.addFile(moby);
    String xml = maker.toXmlString();
    xml = xml.replaceAll("\r\n", "\n");
    xml = removePathPrefixOfTestDirectory(hostname, xml, 1);
    xml = removeJavaVersion(System.getProperty("java.version"), xml);
    assertEquals(golden, xml);
  }

  @Test
  public void testIncremental_TwoFilesWithDifferentPermissions()
      throws IOException {
    SimpleGsaFeedFileMaker.Content contentMaker =
        new SimpleGsaFeedFileMaker.ContentIncremental("t3sT");
    maker = contentMaker;
    String hostname = getHostname();
    String goldenHeader =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" "
        + "\"gsafeed.dtd\">\n"
        + "<gsafeed>\n"
        + "<!--Product Version: Application  (unknown version)-->\n"
        + "<!--Version X.Y.Z of Java is supported.-->\n"
        + "<header>\n"
        + "<datasource>t3sT</datasource>\n"
        + "<feedtype>incremental</feedtype>\n"
        + "</header>\n"
        + "<group>\n";
    String goldenBook1 =
        "<record last-modified=\"Wed, 01 Jun 2016 09:55:36 -0700\" "
            + "mimetype=\"\" url=\"googleconnector://" + hostname
            + "/test/com/google/enterprise/adaptor/prebuilt/resources/TomSawyer"
            + ".txt\">\n"
            + TOM_SAWYER_CONTENT
            + "\n</record>\n";
    String goldenBook2 =
        "<record last-modified="
            + "\"Sun, 06 Nov 1994 00:49:38 -0800\" lock=\"true\" mimetype=\""
            + "text/other\" url=\"googleconnector://" + hostname
            + "/test/com/google/enterprise/adaptor/prebuilt/resources/MobyDick"
            + ".txt\">\n"
            + "<acl>\n"
            + "<principal access=\"permit\" case-sensitivity-type=\""
            + "everything-case-insensitive\" namespace=\"namespace\" "
            + "scope=\"user\">allowedUser</principal>\n"
            + "<principal access=\"permit\" case-sensitivity-type=\""
            + "everything-case-insensitive\" namespace=\"namespace\" "
            + "scope=\"group\">allowedGroup</principal>\n"
            + "<principal access=\"deny\" case-sensitivity-type=\""
            + "everything-case-insensitive\" namespace=\"namespace\" "
            + "scope=\"user\">deniedUser</principal>\n"
            + "<principal access=\"deny\" case-sensitivity-type=\""
            + "everything-case-insensitive\" namespace=\"namespace\" "
            + "scope=\"group\">deniedGroup</principal>\n"
            + "</acl>\n"
            + MOBY_DICK_CONTENT
            + "\n</record>\n";
    String goldenBody1 =
        goldenBook1
        + "</group>\n"
        + "</gsafeed>\n";
    String goldenBody2 =
        goldenBook1
        + goldenBook2
        + "</group>\n"
        + "</gsafeed>\n";
    String golden1 = goldenHeader + goldenBody1;
    String golden2 = goldenHeader + goldenBody2;
    maker.setPublicAcl();
    File tom = new File(TOM_SAWYER_FILE);
    contentMaker.addFile(tom);
    String xml1 = maker.toXmlString();
    xml1 = xml1.replaceAll("\r\n", "\n");
    xml1 = removePathPrefixOfTestDirectory(hostname, xml1, 1);
    xml1 = removeJavaVersion(System.getProperty("java.version"), xml1);
    assertEquals(golden1, xml1);

    // different permissions for second file
    maker.setLastModified(new Date(784111778L  * 1000));
    maker.setLock(true);
    maker.setMimetype("text/other");
    maker.setAclProperties(/* caseInsensitivity = */ true, "namespace",
        Collections.singletonList("allowedUser"),
        Collections.singletonList("allowedGroup"),
        Collections.singletonList("deniedUser"),
        Collections.singletonList("deniedGroup"));
    File moby = new File(MOBY_DICK_FILE);
    contentMaker.addFile(moby);
    String xml2 = maker.toXmlString();
    xml2 = xml2.replaceAll("\r\n", "\n");
    xml2 = removePathPrefixOfTestDirectory(hostname, xml2, 2);
    xml2 = removeJavaVersion(System.getProperty("java.version"), xml2);
    assertEquals(golden2, xml2);
  }

  @Test
  public void testMetadataAndUrl_TwoSetsOfUrlsWithDifferentPermissions()
      throws IOException, MalformedURLException {
    SimpleGsaFeedFileMaker.MetadataAndUrl maker
        = new SimpleGsaFeedFileMaker.MetadataAndUrl("t3sT");
    String url1 = "http://www.google.com/";
    String url2 = "https://www.google.com/";
    String url3 = "http://www.yahoo.com/";
    String goldenHeader =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE gsafeed PUBLIC \"-//Google//DTD GSA Feeds//EN\" "
        + "\"gsafeed.dtd\">\n"
        + "<gsafeed>\n"
        + "<!--Product Version: Application  (unknown version)-->\n"
        + "<!--Version X.Y.Z of Java is supported.-->\n"
        + "<header>\n"
        + "<datasource>web</datasource>\n"
        + "<feedtype>metadata-and-url</feedtype>\n"
        + "</header>\n"
        + "<group>\n";
    String goldenAcl = "<acl>\n"
        + "<principal access=\"permit\" "
        + "case-sensitivity-type=\"everything-case-insensitive\" "
        + "namespace=\"namespace\" scope=\"user\">one</principal>\n"
        + "<principal access=\"permit\" "
        + "case-sensitivity-type=\"everything-case-insensitive\" "
        + "namespace=\"namespace\" scope=\"user\">two</principal>\n"
        + "<principal access=\"permit\" "
        + "case-sensitivity-type=\"everything-case-insensitive\" "
        + "namespace=\"namespace\" scope=\"user\">three</principal>\n"
        + "<principal access=\"deny\" "
        + "case-sensitivity-type=\"everything-case-insensitive\" "
        + "namespace=\"namespace\" scope=\"user\">four</principal>\n"
        + "<principal access=\"deny\" "
        + "case-sensitivity-type=\"everything-case-insensitive\" "
        + "namespace=\"namespace\" scope=\"user\">five</principal>\n"
        + "<principal access=\"deny\" "
        + "case-sensitivity-type=\"everything-case-insensitive\" "
        + "namespace=\"namespace\" scope=\"user\">six</principal>\n"
        + "</acl>\n";
    String goldenUrls1 =
        "<record mimetype=\"text/plain\" url=\"" + url1 + "\"/>\n"
        + "<record mimetype=\"text/plain\" url=\"" + url2 + "\"/>\n"
        + "<record mimetype=\"text/plain\" url=\"" + url3 + "\"/>\n";
    String goldenUrls2 =
        "<record crawl-immediately=\"true\" crawl-once=\"true\" lock=\"true\" "
        + "mimetype=\"text/other\" url=\"" + url1 + "\">\n"
        + goldenAcl
        + "</record>\n"
        + "<record crawl-immediately=\"true\" crawl-once=\"true\" lock=\"true\""
        + " mimetype=\"text/other\" url=\"" + url2 + "\">\n"
        + goldenAcl
        + "</record>\n"
        + "<record crawl-immediately=\"true\" crawl-once=\"true\" lock=\"true\""
        + " mimetype=\"text/other\" url=\"" + url3 + "\">\n"
        + goldenAcl
        + "</record>\n";
    String goldenBody1 =
        goldenUrls1
        + "</group>\n"
        + "</gsafeed>\n";
    String goldenBody2 =
        goldenUrls1
        + goldenUrls2
        + "</group>\n"
        + "</gsafeed>\n";
    String golden1 = goldenHeader + goldenBody1;
    String golden2 = goldenHeader + goldenBody2;
    maker.setPublicAcl();
    maker.setMimetype("text/plain");
    maker.addUrl(new URL(url1));
    maker.addUrl(new URL(url2));
    maker.addUrl(new URL(url3));
    String xml1 = maker.toXmlString().replaceAll("\r\n", "\n");
    xml1 = removeJavaVersion(System.getProperty("java.version"), xml1);

    // different permissions for second set of (same) urls
    maker.setCrawlImmediately(true);
    maker.setCrawlOnce(true);
    maker.setLock(true);
    maker.setMimetype("text/other");
    maker.setAclProperties(/* caseInsensitivity = */ true, "namespace",
        Arrays.asList("one", "two", "three"),
        Collections.<String>emptyList(),
        Arrays.asList("four", "five", "six"),
        Collections.<String>emptyList());
    maker.addUrl(new URL(url1));
    maker.addUrl(new URL(url2));
    maker.addUrl(new URL(url3));
    String xml2 = maker.toXmlString().replaceAll("\r\n", "\n");
    xml2 = removeJavaVersion(System.getProperty("java.version"), xml2);
    assertEquals(golden2, xml2);
  }

  @Test
  public void testIncremental_IllegalDataSourceNull() {
    thrown.expect(IllegalArgumentException.class);
    maker = new SimpleGsaFeedFileMaker.ContentIncremental(null);
  }

  @Test
  public void testIncremental_IllegalDataSourceBadPattern() {
    thrown.expect(IllegalArgumentException.class);
    maker = new SimpleGsaFeedFileMaker.ContentIncremental("3_bad_first_char");
  }

  // helper routines

  private static String getHostname() {
    String hostname = null;
    try {
      hostname = InetAddress.getLocalHost().getCanonicalHostName();
      hostname = hostname.toLowerCase(Locale.ENGLISH);
      return hostname;
    } catch (UnknownHostException ex) {
      fail("could not determine hostname");
    }
    return hostname;
  }

  /**
   * Allows the xml to match the golden xml by removing the prefix of the
   * test directory (everything up to the leading "test/com/google/enterprise").
   */
  private static String removePathPrefixOfTestDirectory(String hostname,
      String xml, int times) {
    int hostnameLocation = xml.indexOf(hostname + "/");
    if (hostnameLocation == -1) {
      fail("could not find hostname in xml '" + xml + "'");
    }
    hostnameLocation += hostname.length() + 1;
    int testLocation = xml.indexOf("test/com/google/enterprise",
        hostnameLocation);
    if (testLocation == -1) {
      fail("could not find test directory in xml '" + xml + "'");
    }
    if (times < 2) {
      return xml.substring(0, hostnameLocation) + xml.substring(testLocation);
    } else {
      return xml.substring(0, hostnameLocation)
          + removePathPrefixOfTestDirectory(hostname,
              xml.substring(testLocation), times - 1);
    }
  }

  /**
   * Allows the xml to match the golden xml by removing the specific Java
   * version from the gsafeed header.
   */
  private static String removeJavaVersion(String javaVersion, String xml) {
    String versionPrefix = "<!--Version ";
    int javaLocation = xml.indexOf(versionPrefix + javaVersion);
    if (javaLocation == -1) {
      fail("could not find Java version string in xml '" + xml + "'");
    }
    return xml.replace(versionPrefix + javaVersion, versionPrefix + "X.Y.Z");
  }
}
