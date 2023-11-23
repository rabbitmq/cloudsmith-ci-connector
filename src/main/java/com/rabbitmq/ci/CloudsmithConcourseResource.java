/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.ci;

import java.io.IOException;
import java.util.Scanner;

public class CloudsmithConcourseResource {

  public static void main(String[] args) throws IOException, InterruptedException {
    Scanner scanner = new Scanner(System.in);
    StringBuilder builder = new StringBuilder();
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      builder.append(line);
    }

    String command = args[0];
    if ("test".equals(command)) {
      Utils.testSequence();
    } else {
      Input input = Utils.GSON.fromJson(builder.toString(), Input.class);
      CloudsmithLogic logic = new CloudsmithLogic(input, new Log.ConcourseLog());
      if ("check".equals(command)) {
        logic.check();
      } else if ("in".equals(command)) {
        String outputDirectory = args[1];
        logic.in(outputDirectory);
      } else if ("out".equals(command)) {
        String inputDirectory = args[1];
        logic.out(inputDirectory);
      } else {
        throw new IllegalArgumentException("command not supported: " + command);
      }
    }
  }
}
