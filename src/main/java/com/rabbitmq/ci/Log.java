/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.ci;

interface Log {

  default void logGreen(String message) {
    log(green(message));
  }

  default void logYellow(String message) {
    log(yellow(message));
  }

  default void logRed(String message) {
    log(red(message));
  }

  default String green(String message) {
    return colored("[32m", message);
  }

  default String yellow(String message) {
    return colored("[33m", message);
  }

  default String red(String message) {
    return colored("[31m", message);
  }

  default String italic(String message) {
    return "\033[3m" + message + "\033[0m";
  }

  default String colored(String color, String message) {
    return "\u001B" + color + message + "\u001B[0m";
  }

  default void newLine() {
    log("");
  }

  default void logIndent(String message) {
    log(indent(message));
  }

  default String indent(String message) {
    return "    " + message;
  }

  default void printIndent(String message) {
    print(indent(message));
  }

  void log(String message);

  void out(String message);

  void print(String message);

  class ConcourseLog implements Log {

    public void out(String message) {
      System.out.println(message);
    }

    public void print(String message) {
      System.err.print(message);
    }

    public void log(String message) {
      System.err.println(message);
    }
  }

  class GitHubActionsLog implements Log {

    public void out(String message) {
      // no-op, only for Concourse
    }

    public void print(String message) {
      System.out.print(message);
    }

    public void log(String message) {
      System.out.println(message);
    }
  }
}
