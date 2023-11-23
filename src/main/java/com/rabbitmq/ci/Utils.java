/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.ci;

import com.google.gson.*;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.BitSet;
import java.util.function.Consumer;

final class Utils {

  static final Gson GSON =
      new GsonBuilder()
          .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeDeserializer())
          .create();

  static class ZonedDateTimeDeserializer implements JsonDeserializer<ZonedDateTime> {

    @Override
    public ZonedDateTime deserialize(
        JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      return ZonedDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }
  }

  private static final Charset CHARSET_UTF8 = StandardCharsets.UTF_8;

  private Utils() {}

  static void testSequence() {
    Log log = new Log.GitHubActionsLog();
    Consumer<String> display = log::logGreen;
    String message;
    int exitCode = 0;
    try {
      String testUri = "https://www.wikipedia.org/";
      log.logYellow("Starting test sequence, trying to reach " + testUri);
      HttpRequest request = HttpRequest.newBuilder().uri(new URI(testUri)).GET().build();
      HttpResponse<Void> response =
          HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(60))
              .build()
              .send(request, HttpResponse.BodyHandlers.discarding());
      int statusClass = response.statusCode() - response.statusCode() % 100;
      message = "Response code is " + response.statusCode();
      if (statusClass != 200) {
        display = log::logRed;
        exitCode = 1;
      }
    } catch (Exception e) {
      message = "Error during test sequence: " + e.getMessage();
      display = log::logRed;
      exitCode = 1;
    }
    display.accept(message);
    System.exit(exitCode);
  }

  /* from https://github.com/apache/httpcomponents-client/commit/b58e7d46d75e1d3c42f5fd6db9bd45f32a49c639#diff-a74b24f025e68ec11e4550b42e9f807d */

  static String encodePath(String content, Charset charset) {
    final StringBuilder buf = new StringBuilder();
    final ByteBuffer bb = charset.encode(content);
    while (bb.hasRemaining()) {
      final int b = bb.get() & 0xff;
      if (PATHSAFE.get(b)) {
        buf.append((char) b);
      } else {
        buf.append("%");
        final char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, RADIX));
        final char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, RADIX));
        buf.append(hex1);
        buf.append(hex2);
      }
    }
    return buf.toString();
  }

  static String encodeHttpParameter(String value) {
    try {
      return URLEncoder.encode(value, CHARSET_UTF8.name());
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("Unknown charset for encoding", e);
    }
  }

  static String encodePath(String content) {
    return encodePath(content, CHARSET_UTF8);
  }

  private static final int RADIX = 16;

  /**
   * Unreserved characters, i.e. alphanumeric, plus: {@code _ - ! . ~ ' ( ) *}
   *
   * <p>This list is the same as the {@code unreserved} list in <a
   * href="https://www.ietf.org/rfc/rfc2396.txt">RFC 2396</a>
   */
  private static final BitSet UNRESERVED = new BitSet(256);

  /**
   * Punctuation characters: , ; : $ & + =
   *
   * <p>These are the additional characters allowed by userinfo.
   */
  private static final BitSet PUNCT = new BitSet(256);

  /**
   * Characters which are safe to use in userinfo, i.e. {@link #UNRESERVED} plus {@link
   * #PUNCT}uation
   */
  private static final BitSet USERINFO = new BitSet(256);

  /**
   * Characters which are safe to use in a path, i.e. {@link #UNRESERVED} plus {@link #PUNCT}uation
   * plus / @
   */
  private static final BitSet PATHSAFE = new BitSet(256);

  /**
   * Characters which are safe to use in a query or a fragment, i.e. {@link #RESERVED} plus {@link
   * #UNRESERVED}
   */
  private static final BitSet URIC = new BitSet(256);

  /**
   * Reserved characters, i.e. {@code ;/?:@&=+$,[]}
   *
   * <p>This list is the same as the {@code reserved} list in <a
   * href="https://www.ietf.org/rfc/rfc2396.txt">RFC 2396</a> as augmented by <a
   * href="https://www.ietf.org/rfc/rfc2732.txt">RFC 2732</a>
   */
  private static final BitSet RESERVED = new BitSet(256);

  /**
   * Safe characters for x-www-form-urlencoded data, as per java.net.URLEncoder and browser
   * behaviour, i.e. alphanumeric plus {@code "-", "_", ".", "*"}
   */
  private static final BitSet URLENCODER = new BitSet(256);

  static {
    // unreserved chars
    // alpha characters
    for (int i = 'a'; i <= 'z'; i++) {
      UNRESERVED.set(i);
    }
    for (int i = 'A'; i <= 'Z'; i++) {
      UNRESERVED.set(i);
    }
    // numeric characters
    for (int i = '0'; i <= '9'; i++) {
      UNRESERVED.set(i);
    }
    UNRESERVED.set('_'); // these are the charactes of the "mark" list
    UNRESERVED.set('-');
    UNRESERVED.set('.');
    UNRESERVED.set('*');
    URLENCODER.or(UNRESERVED); // skip remaining unreserved characters
    UNRESERVED.set('!');
    UNRESERVED.set('~');
    UNRESERVED.set('\'');
    UNRESERVED.set('(');
    UNRESERVED.set(')');
    // punct chars
    PUNCT.set(',');
    PUNCT.set(';');
    PUNCT.set(':');
    PUNCT.set('$');
    PUNCT.set('&');
    PUNCT.set('+');
    PUNCT.set('=');
    // Safe for userinfo
    USERINFO.or(UNRESERVED);
    USERINFO.or(PUNCT);

    // URL path safe
    PATHSAFE.or(UNRESERVED);
    // here we want to encode the segment separator, because we encode segment by segment
    // PATHSAFE.set('/'); // segment separator
    PATHSAFE.set(';'); // param separator
    PATHSAFE.set(':'); // rest as per list in 2396, i.e. : @ & = + $ ,
    PATHSAFE.set('@');
    PATHSAFE.set('&');
    PATHSAFE.set('=');
    PATHSAFE.set('+');
    PATHSAFE.set('$');
    PATHSAFE.set(',');

    RESERVED.set(';');
    RESERVED.set('/');
    RESERVED.set('?');
    RESERVED.set(':');
    RESERVED.set('@');
    RESERVED.set('&');
    RESERVED.set('=');
    RESERVED.set('+');
    RESERVED.set('$');
    RESERVED.set(',');
    RESERVED.set('['); // added by RFC 2732
    RESERVED.set(']'); // added by RFC 2732

    URIC.or(RESERVED);
    URIC.or(UNRESERVED);
  }

  /* end of from https://github.com/apache/httpcomponents-client/commit/b58e7d46d75e1d3c42f5fd6db9bd45f32a49c639#diff-a74b24f025e68ec11e4550b42e9f807d */

}
